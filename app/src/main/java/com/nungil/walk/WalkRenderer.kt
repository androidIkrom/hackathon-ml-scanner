package com.nungil.walk

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.SemanticLabel
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.asin
import kotlin.math.max

/**
 * Everything one analysis needs, captured on the GL thread into reused buffers. The worker owns it
 * until it clears its busy flag; the GL thread never writes while the worker is busy.
 *
 * @param rgba bottom-up RGBA pixels, [width] × [height], shaped like the screen.
 * @param depthMm depth image in millimetres ([depthWidth] × [depthHeight], sensor orientation), or null.
 * @param lut for each cell of the [gridWidth] × [gridHeight] screen-shaped grid, the index into [depthMm] (-1 = none).
 * @param focalGridPx vertical focal length in grid pixels; [pitchRad] positive when the phone looks up.
 * @param groundLabel ARCore SemanticLabel number under the user's feet (bottom centre), or -1.
 */
class WalkInput(
    val rgba: ByteBuffer,
    val width: Int,
    val height: Int,
    val depthMm: ShortArray?,
    val depthWidth: Int,
    val depthHeight: Int,
    val lut: IntArray,
    val gridWidth: Int,
    val gridHeight: Int,
    val focalGridPx: Float,
    val pitchRad: Float,
    val groundLabel: Int,
    val groundConfidence: Int,
    val skyFraction: Float,
    val timestampMs: Long,
    /** ARCore depth is on for this phone; a missing [depthMm] then means "could not measure", not "no depth". */
    val depthExpected: Boolean,
)

/** Receives captures; [tryReserve] must succeed before [submit]. */
interface WalkSink {
    fun tryReserve(): Boolean
    fun submit(input: WalkInput)
    fun cancelReservation()
}

/**
 * Draws the camera and, every [ANALYSE_EVERY_MS], captures the screen-shaped image, the depth
 * image and the ground label for the worker. Depth is read through a lookup table from screen
 * space to the depth image, rebuilt whenever the display geometry or the depth size changes:
 * ARCore hands depth out in sensor orientation, not as the screen shows it (build guide §7).
 */
class WalkRenderer(
    private val displayRotation: () -> Int,
    private val sink: WalkSink,
) : GLSurfaceView.Renderer {

    @Volatile
    var session: Session? = null

    /** Set before pausing the GL view: onDrawFrame then returns immediately. */
    @Volatile
    var closing = false

    @Volatile
    var depthEnabled = false

    @Volatile
    var semanticsEnabled = false

    /** Last time ARCore was tracking (elapsedRealtime). Walk mode restarts the session when this goes stale. */
    @Volatile
    var lastTrackingAt = 0L

    /** Why ARCore is not tracking right now (NONE while tracking). */
    @Volatile
    var failureReason = TrackingFailureReason.NONE

    private var lastFailureLogAt = 0L

    private val background = BackgroundRenderer()
    private val capture = OffscreenCapture()
    private var viewWidth = 0
    private var viewHeight = 0
    private var gridHeight = 0
    private var geometryDirty = true
    private var textureSet = false
    private var lastCaptureMs = 0L

    private var depthMm = ShortArray(0)
    private var lut = IntArray(0)
    private var lutDepthWidth = 0
    private var lutDepthHeight = 0
    private var lutStale = true
    private val groundPoint = FloatArray(2)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.create()
        textureSet = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
        capture.resize(CAPTURE_WIDTH, width, height)
        gridHeight = (GRID_WIDTH.toLong() * height / width).toInt()
        geometryDirty = true
        lutStale = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (closing) return
        val s = session ?: return
        if (!textureSet) {
            s.setCameraTextureName(background.textureId)
            textureSet = true
        }
        if (geometryDirty) {
            s.setDisplayGeometry(displayRotation(), viewWidth, viewHeight)
            geometryDirty = false
        }
        val frame = try {
            s.update()
        } catch (e: Exception) {
            return
        }
        background.update(frame)
        if (frame.hasDisplayGeometryChanged()) lutStale = true
        if (frame.timestamp == 0L) return
        background.draw()

        val now = SystemClock.elapsedRealtime()
        if (now - lastCaptureMs < ANALYSE_EVERY_MS || !sink.tryReserve()) return
        lastCaptureMs = now
        try {
            sink.submit(grab(frame, now))
        } catch (e: Exception) {
            Log.w(TAG, "walk capture failed", e)
            sink.cancelReservation()
        }
    }

    /** Only on the GL thread (queueEvent) before the view is paused. */
    fun release() {
        capture.release()
    }

    private fun grab(frame: Frame, now: Long): WalkInput {
        capture.read(background, viewWidth, viewHeight)
        val camera = frame.camera
        failureReason = camera.trackingFailureReason
        if (camera.trackingState == TrackingState.TRACKING) {
            lastTrackingAt = now
        } else if (now - lastFailureLogAt >= FAILURE_LOG_EVERY_MS) {
            lastFailureLogAt = now
            Log.i(TAG, "ARCore not tracking: ${camera.trackingState} ${camera.trackingFailureReason}")
        }
        var focal = 0f
        var pitch = 0f
        var haveDepth = false
        if (camera.trackingState == TrackingState.TRACKING) {
            // The camera looks along -Z; world Y is up.
            val z = camera.displayOrientedPose.zAxis
            pitch = asin((-z[1]).coerceIn(-1f, 1f))
            // Focal length is in camera-image pixels; the portrait screen shows the image's long side top to bottom.
            val intrinsics = camera.imageIntrinsics
            val dims = intrinsics.imageDimensions
            focal = intrinsics.focalLength[0] * gridHeight / max(dims[0], dims[1]).toFloat()
            if (depthEnabled) haveDepth = copyDepth(frame)
        }
        var label = -1
        var confidence = 0
        var sky = 0f
        if (semanticsEnabled) {
            try {
                if (lutStale || lut.isEmpty()) mapGroundPoint(frame)
                frame.acquireSemanticImage().use { label = byteAt(it, groundPoint) }
                frame.acquireSemanticConfidenceImage().use { confidence = byteAt(it, groundPoint) }
                sky = frame.getSemanticLabelFraction(SemanticLabel.SKY)
            } catch (e: NotYetAvailableException) {
                label = -1
            }
        }
        return WalkInput(
            rgba = capture.pixels,
            width = capture.width,
            height = capture.height,
            depthMm = if (haveDepth) depthMm else null,
            depthWidth = lutDepthWidth,
            depthHeight = lutDepthHeight,
            lut = lut,
            gridWidth = GRID_WIDTH,
            gridHeight = gridHeight,
            focalGridPx = focal,
            pitchRad = pitch,
            groundLabel = label,
            groundConfidence = confidence,
            skyFraction = sky,
            timestampMs = now,
            depthExpected = depthEnabled,
        )
    }

    private fun copyDepth(frame: Frame): Boolean = try {
        frame.acquireDepthImage16Bits().use { image ->
            val w = image.width
            val h = image.height
            val plane = image.planes[0]
            val buffer = plane.buffer.order(ByteOrder.nativeOrder())
            if (depthMm.size != w * h) depthMm = ShortArray(w * h)
            if (plane.rowStride == w * 2) {
                buffer.asShortBuffer().get(depthMm, 0, w * h)
            } else {
                for (y in 0 until h) {
                    buffer.position(y * plane.rowStride)
                    buffer.asShortBuffer().get(depthMm, y * w, w)
                }
            }
            if (lutStale || w != lutDepthWidth || h != lutDepthHeight || lut.size != GRID_WIDTH * gridHeight) {
                buildLut(frame, w, h)
            }
            true
        }
    } catch (e: NotYetAvailableException) {
        false
    }

    private fun buildLut(frame: Frame, depthWidth: Int, depthHeight: Int) {
        val n = GRID_WIDTH * gridHeight
        val view = FloatArray(n * 2)
        for (y in 0 until gridHeight) for (x in 0 until GRID_WIDTH) {
            val i = (y * GRID_WIDTH + x) * 2
            view[i] = (x + 0.5f) / GRID_WIDTH
            view[i + 1] = (y + 0.5f) / gridHeight
        }
        val image = FloatArray(n * 2)
        frame.transformCoordinates2d(Coordinates2d.VIEW_NORMALIZED, view, Coordinates2d.IMAGE_NORMALIZED, image)
        val table = IntArray(n)
        for (i in 0 until n) {
            val u = image[i * 2]
            val v = image[i * 2 + 1]
            table[i] = if (u < 0f || u >= 1f || v < 0f || v >= 1f) -1 else (v * depthHeight).toInt() * depthWidth + (u * depthWidth).toInt()
        }
        lut = table
        lutDepthWidth = depthWidth
        lutDepthHeight = depthHeight
        mapGroundPoint(frame)
        lutStale = false
    }

    /** The point just above the bottom centre of the screen, in image coordinates. */
    private fun mapGroundPoint(frame: Frame) {
        frame.transformCoordinates2d(
            Coordinates2d.VIEW_NORMALIZED, floatArrayOf(0.5f, GROUND_Y),
            Coordinates2d.IMAGE_NORMALIZED, groundPoint,
        )
    }

    private fun byteAt(image: android.media.Image, p: FloatArray): Int {
        val x = (p[0] * image.width).toInt().coerceIn(0, image.width - 1)
        val y = (p[1] * image.height).toInt().coerceIn(0, image.height - 1)
        val plane = image.planes[0]
        return plane.buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xFF
    }

    companion object {
        const val TAG = "Nungil"
        const val ANALYSE_EVERY_MS = 150L
        const val CAPTURE_WIDTH = 360
        const val GRID_WIDTH = 90
        const val GROUND_Y = 0.93f
        const val FAILURE_LOG_EVERY_MS = 3_000L
    }
}
