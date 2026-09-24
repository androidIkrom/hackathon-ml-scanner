package com.nungil.walk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nungil.contract.Box
import com.nungil.contract.Compute
import com.nungil.contract.Detection
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.VisionFrame
import com.nungil.core.scan.DetectorPlan
import com.nungil.core.walk.Alert
import com.nungil.core.walk.CameraHeight
import com.nungil.core.walk.ApproachSpeed
import com.nungil.core.walk.AlertKind
import com.nungil.core.walk.CloseHold
import com.nungil.core.walk.DepthGrid
import com.nungil.core.walk.DepthObstacles
import com.nungil.core.walk.DepthStatus
import com.nungil.core.walk.FloorChange
import com.nungil.core.walk.FloorConfirmer
import com.nungil.core.walk.FloorReading
import com.nungil.core.walk.GridGeometry
import com.nungil.core.walk.GroundProfile
import com.nungil.core.walk.GroundRule
import com.nungil.core.walk.HazardConfirmer
import com.nungil.core.walk.HazardPolicy
import com.nungil.core.walk.ObstacleName
import com.nungil.core.walk.SemanticGround
import com.nungil.core.walk.TrafficLightColor
import com.nungil.core.walk.WalkPhrases
import com.nungil.core.walk.Zone
import com.nungil.core.walk.ZoneReading
import com.nungil.people.createNameTaggers
import com.nungil.scan.DetectorFactory
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** What one analysed frame found. [beepM] is the nearest thing straight ahead, for the beeps. */
class WalkReport(val alerts: List<Alert>, val beepM: Float?, val depthWorking: Boolean)

/**
 * Walk mode's worker thread: turns captures into alerts. Heavy work (detector, OCR, codes, saved
 * things, obstacle names) runs here, one frame at a time; a frame that arrives while it is busy is
 * dropped. Reports are delivered on the main thread.
 */
class WalkVision(
    private val context: Context,
    private val lang: () -> Lang,
    private val stepM: Float?,
    /** Steps taken since the last call (step detector); 0 when there is none. */
    private val takeSteps: () -> Int,
    private val onReport: (WalkReport) -> Unit,
) : WalkSink, Closeable {

    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    // Worker thread only.
    private var detector: ObjectDetector? = null
    private var classifier: ImageClassifier? = null
    private var taggers: List<NameTagger> = emptyList()
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val barcodes by lazy { BarcodeScanning.getClient() }
    private val confirmer = HazardConfirmer()
    private val closeHold = CloseHold()
    private val floorConfirmer = FloorConfirmer()
    private val cameraHeight = CameraHeight()
    private var warnedAheadAt: Long? = null
    private var wallLevel = Int.MAX_VALUE
    private val approach = ApproachSpeed()
    private var lastFrameAt = 0L
    private val depthStatus = DepthStatus()
    private var clearSince: Long? = null
    private var grid = FloatArray(0)
    private var raw: Bitmap? = null
    private var upright: Bitmap? = null
    private var lastSkyAt: Long? = null
    private var lastReadAt = 0L
    private var lastNameAt = 0L
    private var lastSavedAt = 0L
    private val savedSaidAt = HashMap<String, Long>()
    private var loggedFrames = 0
    private var inferenceTotalMs = 0L
    @Volatile private var closed = false

    override fun tryReserve(): Boolean = !closed && busy.compareAndSet(false, true)

    override fun cancelReservation() = busy.set(false)

    /** ARCore path: depth and semantics available. Called on the GL thread after [tryReserve]. */
    override fun submit(input: WalkInput) {
        executor.execute {
            try {
                val bitmap = toBitmap(input)
                val gridM = input.depthMm?.let { depth ->
                    if (grid.size != input.lut.size) grid = FloatArray(input.lut.size)
                    DepthGrid.fill(depth, input.lut, grid)
                    grid
                }
                val report = analyse(
                    bitmap = bitmap,
                    detections = detect(bitmap),
                    gridM = gridM,
                    gridWidth = input.gridWidth,
                    gridHeight = input.gridHeight,
                    focalGridPx = input.focalGridPx,
                    pitchRad = input.pitchRad,
                    groundLabel = input.groundLabel,
                    groundConfidence = input.groundConfidence,
                    skyFraction = input.skyFraction,
                    now = input.timestampMs,
                    depthExpected = input.depthExpected,
                    tooDark = input.tooDark,
                )
                deliver(report)
            } catch (e: Exception) {
                Log.w(TAG, "walk analysis failed", e)
            } finally {
                busy.set(false)
            }
        }
    }

    /** Fallback without ARCore: CameraX frames with their detections, no depth (build guide §16). */
    fun submitCameraFrame(frame: VisionFrame) {
        val bitmap = frame.bitmap ?: return
        if (!tryReserve()) return
        executor.execute {
            try {
                val report = analyse(
                    bitmap = bitmap,
                    detections = frame.detections,
                    gridM = null,
                    gridWidth = 0,
                    gridHeight = 0,
                    focalGridPx = 0f,
                    pitchRad = 0f,
                    groundLabel = -1,
                    groundConfidence = 0,
                    skyFraction = 0f,
                    now = SystemClock.elapsedRealtime(),
                    depthExpected = false,
                )
                deliver(report)
            } catch (e: Exception) {
                Log.w(TAG, "walk analysis failed", e)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun deliver(report: WalkReport) {
        if (!closed) main.post { if (!closed) onReport(report) }
    }

    private fun analyse(
        bitmap: Bitmap,
        detections: List<Detection>,
        gridM: FloatArray?,
        gridWidth: Int,
        gridHeight: Int,
        focalGridPx: Float,
        pitchRad: Float,
        groundLabel: Int,
        groundConfidence: Int,
        skyFraction: Float,
        now: Long,
        depthExpected: Boolean,
        tooDark: Boolean = false,
    ): WalkReport {
        val l = lang()
        val alerts = ArrayList<Alert>()
        var beep: Float? = null

        // Walls and floor changes from depth. The phone's height comes from the floor at the user's feet,
        // and floor points never count as walls (a phone tilted down sees the floor at chest-band height).
        // Walked since the last frame: steps, or the approach speed while the step detector lags.
        val stepWalked = stepM?.let { takeSteps() * it } ?: 0f
        val walked = if (lastFrameAt == 0L) stepWalked else maxOf(stepWalked, approach.walked(lastFrameAt, now))
        lastFrameAt = now
        var aheadBlocked = false
        var aheadMeasuredClear = false
        var floor: FloorReading? = null
        if (gridM != null && gridWidth > 0 && gridHeight > 0) {
            val geometry = if (focalGridPx > 0f) {
                val h = cameraHeight.update(GridGeometry.floorHeight(gridM, gridWidth, gridHeight, focalGridPx, pitchRad))
                GridGeometry(focalGridPx, pitchRad, gridHeight, h)
            } else {
                null
            }
            val zones = DepthObstacles.read(gridM, gridWidth, gridHeight, geometry)
            val ahead = zones.first { it.zone == Zone.AHEAD }
            if (loggedFrames % LOG_EVERY == 0) {
                Log.i(
                    TAG,
                    "Walk depth: camH=%.2f pitch=%.0f° ahead valid=%.2f blocked=%s d=%s raw=%s".format(
                        geometry?.cameraHeightM ?: -1f, Math.toDegrees(pitchRad.toDouble()), ahead.validShare,
                        ahead.blocked, ahead.distanceM, DepthDiag.centre(gridM, gridWidth, gridHeight),
                    ),
                )
            }
            floor = floorConfirmer.update(geometry?.let { GroundProfile.analyse(GroundProfile.rows(gridM, gridWidth, gridHeight, it)) })
            // Stairs going up look like a wall at chest height: say "stairs", not "wall".
            val stairsUp = floor?.change == FloorChange.STAIRS && floor.distanceM <= STAIRS_HIDE_WALL_M
            if (ahead.blocked && !ahead.unknown) ahead.distanceM?.let { approach.measured(now, it) } else if (!ahead.unknown) approach.clear()
            closeHold.update(now, ahead, walked)?.let { d ->
                aheadBlocked = true
                beep = d
                if (!stairsUp) alerts.add(wallAhead(d, l))
            }
            aheadMeasuredClear = !ahead.unknown && !ahead.blocked
            zones.filter { it.zone != Zone.AHEAD && it.blocked && (it.distanceM ?: 9f) < SIDE_WARN_M }.forEach {
                alerts.add(Alert(AlertKind.HAZARD, "wall:${it.zone}", WalkPhrases.wall(it.zone, it.distanceM!!, stepM, l)))
            }
            // Nothing behind a close wall can be seen: a "floor change" beyond it is the wall's own noise.
            val wallAt = if (aheadBlocked) beep else null
            if (floor != null && wallAt != null && floor.distanceM > wallAt - BEHIND_WALL_MARGIN_M) floor = null
            floor?.let { alerts.add(Alert(AlertKind.FLOOR, "floor:${it.change}", WalkPhrases.floor(it.change, it.distanceM, stepM, l))) }
        } else if (depthExpected) {
            if (loggedFrames % LOG_EVERY == 0) Log.i(TAG, "Walk depth: none this frame (not tracking or not ready)")
            // Right in front of a plain wall ARCore loses tracking and depth: that is "could not measure", not "clear".
            closeHold.update(now, ZoneReading(Zone.AHEAD, false, null, 0f), walked)?.let { d ->
                aheadBlocked = true
                beep = d
                alerts.add(wallAhead(d, l))
            }
        }

        if (depthExpected) {
            depthStatus.update(now, gridM != null && gridWidth > 0 && gridHeight > 0, tooDark, l)?.let { alerts.add(it) }
        }

        // Hazards: confirmed in 3 of the last 5 frames.
        val confirmed = confirmer.update(detections.filter { HazardPolicy.isHazard(it.label, it.score) }.map { it.label }.toSet())
        var hazardAhead = false
        for (label in confirmed) {
            val best = detections.filter { it.label == label }.maxByOrNull { it.box.area } ?: continue
            val zone = DepthGrid.zoneOf(best.box.centerX)
            val d = gridM?.let { DepthGrid.medianIn(it, gridWidth, gridHeight, best.box) }
            if (zone == Zone.AHEAD) {
                hazardAhead = true
                if (d != null) beep = minOf(beep ?: d, d)
            }
            alerts.add(Alert(AlertKind.HAZARD, "hazard:$label:$zone", WalkPhrases.hazard(label, zone, d, stepM, l)))
        }

        if (!aheadBlocked) wallLevel = Int.MAX_VALUE

        // Once the way ahead has been clear for a moment after a warning, say so once (never "safe").
        if (aheadBlocked || hazardAhead || floor != null) {
            warnedAheadAt = now
            clearSince = null
        } else if (aheadMeasuredClear && warnedAheadAt != null) {
            val since = clearSince ?: now.also { clearSince = it }
            if (now - since >= CLEAR_AFTER_MS) {
                alerts.add(Alert(AlertKind.CLEAR, "clear", WalkPhrases.nothingAhead(l)))
                warnedAheadAt = null
                clearSince = null
            }
        }

        // Ground class, outdoors only.
        if (skyFraction >= GroundRule.SKY_FRACTION) lastSkyAt = now
        SemanticGround.kind(groundLabel)?.let { kind ->
            if (GroundRule.speakable(groundConfidence, lastSkyAt, now)) {
                alerts.add(Alert(AlertKind.GROUND, "ground:$kind", WalkPhrases.ground(kind, l)))
            }
        }

        // Traffic light colour.
        detections.filter { it.label == "traffic light" && it.score >= LIGHT_SCORE }.maxByOrNull { it.box.area }?.let { d ->
            TrafficLightColor.classify(pixels(bitmap, d.box))?.let {
                alerts.add(Alert(AlertKind.LIGHT, "light:$it", WalkPhrases.light(it, l)))
            }
        }

        // Saved people and things.
        if (now - lastSavedAt >= SAVED_EVERY_MS) {
            lastSavedAt = now
            for (tag in savedTags(bitmap, detections)) {
                if ((savedSaidAt[tag.first] ?: Long.MIN_VALUE / 2) + SAVED_REPEAT_MS > now) continue
                savedSaidAt[tag.first] = now
                alerts.add(Alert(AlertKind.SAVED, "saved:${tag.first}", WalkPhrases.saved(tag.first, tag.second, l)))
            }
        }

        // Signs and codes.
        if (now - lastReadAt >= READ_EVERY_MS) {
            lastReadAt = now
            readSign(bitmap)?.let { alerts.add(Alert(AlertKind.SIGN, "sign:$it", WalkPhrases.sign(it, l))) }
            readCode(bitmap)?.let { alerts.add(Alert(AlertKind.CODE, "code:$it", WalkPhrases.code(it, l))) }
        }

        // Something close ahead that the detector does not know: give it a name worth saying.
        if (aheadBlocked && !hazardAhead && now - lastNameAt >= NAME_EVERY_MS) {
            lastNameAt = now
            nameObstacle(bitmap)?.let { (en, ko) ->
                alerts.add(Alert(AlertKind.HAZARD, "obstacle:$en", WalkPhrases.obstacle(en, ko, l)))
            }
        }
        return WalkReport(alerts, beep, gridM != null)
    }

    /**
     * The wall is announced again each time its spoken distance gets smaller (3 steps, 2 steps, very
     * close). The level only goes down while the wall is there, so depth noise cannot make it bounce.
     */
    private fun wallAhead(d: Float, l: Lang): Alert {
        wallLevel = minOf(wallLevel, WalkPhrases.distanceLevel(d, stepM))
        return Alert(AlertKind.HAZARD, "wall:ahead:$wallLevel", WalkPhrases.wall(Zone.AHEAD, d, stepM, l), urgent = wallLevel <= 1)
    }

    // ---- models (worker thread) ------------------------------------------------------------------

    private fun detect(bitmap: Bitmap): List<Detection> {
        val d = detector ?: DetectorFactory(context).create(DetectorPlan.WALK_MODEL_FILE, Compute.CPU).detector.also {
            detector = it
            Log.i(TAG, "Walk detector ${DetectorPlan.WALK_MODEL_FILE} on CPU")
        }
        val started = SystemClock.elapsedRealtime()
        val result = d.detect(BitmapImageBuilder(bitmap).build())
        inferenceTotalMs += SystemClock.elapsedRealtime() - started
        if (++loggedFrames % LOG_EVERY == 0) {
            Log.i(TAG, "Walk inference ${inferenceTotalMs / LOG_EVERY} ms average")
            inferenceTotalMs = 0
        }
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return result.detections().mapNotNull { det ->
            val c = det.categories().firstOrNull() ?: return@mapNotNull null
            val r = det.boundingBox()
            Detection(c.categoryName(), c.score(), Box(r.left / w, r.top / h, r.right / w, r.bottom / h))
        }
    }

    private fun savedTags(bitmap: Bitmap, detections: List<Detection>): List<Pair<String, Zone>> {
        if (taggers.isEmpty()) taggers = runCatching { createNameTaggers(context) }.getOrDefault(emptyList())
        if (taggers.isEmpty()) return emptyList()
        val frame = VisionFrame(detections, bitmap, bitmap.width, bitmap.height, Facing.BACK, null, HFOV_DEG, SystemClock.elapsedRealtime(), 0)
        return taggers.flatMap { t -> runCatching { t.tag(frame) }.getOrDefault(emptyList()) }
            .mapNotNull { tag -> detections.getOrNull(tag.detectionIndex)?.let { tag.name to DepthGrid.zoneOf(it.box.centerX) } }
    }

    private fun readSign(bitmap: Bitmap): String? = runCatching {
        val text = Tasks.await(textRecognizer.process(InputImage.fromBitmap(bitmap, 0)), ML_KIT_TIMEOUT_S, TimeUnit.SECONDS)
        text.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.length >= MIN_SIGN_CHARS }.maxByOrNull { it.length }
    }.getOrNull()

    private fun readCode(bitmap: Bitmap): String? = runCatching {
        Tasks.await(barcodes.process(InputImage.fromBitmap(bitmap, 0)), ML_KIT_TIMEOUT_S, TimeUnit.SECONDS)
            .firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
    }.getOrNull()

    private fun nameObstacle(bitmap: Bitmap): Pair<String, String>? = runCatching {
        val c = classifier ?: ImageClassifier.createFromOptions(
            context,
            ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(CLASSIFIER_FILE).build())
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(1)
                .build(),
        ).also { classifier = it }
        val cx = bitmap.width / 4
        val cy = bitmap.height / 4
        val crop = Bitmap.createBitmap(bitmap, cx, cy, bitmap.width / 2, bitmap.height / 2)
        val top = c.classify(BitmapImageBuilder(crop).build()).classificationResult().classifications()
            .firstOrNull()?.categories()?.firstOrNull() ?: return null
        ObstacleName.of(top.displayName().ifBlank { top.categoryName() }, top.score())
    }.getOrNull()

    // ---- pixels ----------------------------------------------------------------------------------

    /** The capture is bottom-up; draw it flipped into a reused upright bitmap. */
    private fun toBitmap(input: WalkInput): Bitmap {
        val r = raw?.takeIf { it.width == input.width && it.height == input.height }
            ?: Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).also { raw = it }
        input.rgba.position(0)
        r.copyPixelsFromBuffer(input.rgba)
        val u = upright?.takeIf { it.width == input.width && it.height == input.height }
            ?: Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888).also { upright = it }
        val flip = Matrix().apply {
            preScale(1f, -1f)
            postTranslate(0f, input.height.toFloat())
        }
        Canvas(u).drawBitmap(r, flip, null)
        return u
    }

    private fun pixels(bitmap: Bitmap, box: Box): IntArray {
        val x = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val w = (box.width * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
        val h = (box.height * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
        return IntArray(w * h).also { bitmap.getPixels(it, 0, w, x, y, w, h) }
    }

    override fun close() {
        closed = true
        executor.execute {
            runCatching { detector?.close() }
            runCatching { classifier?.close() }
            taggers.forEach { runCatching { it.close() } }
            runCatching { textRecognizer.close() }
            runCatching { barcodes.close() }
        }
        executor.shutdown()
        runCatching { executor.awaitTermination(SHUTDOWN_WAIT_S, TimeUnit.SECONDS) }
    }

    companion object {
        const val TAG = "Nungil"
        const val SIDE_WARN_M = 1.5f
        const val STAIRS_HIDE_WALL_M = 4f
        const val BEHIND_WALL_MARGIN_M = 0.2f
        const val CLEAR_AFTER_MS = 2_000L
        const val LIGHT_SCORE = 0.5f
        const val READ_EVERY_MS = 2_500L
        const val NAME_EVERY_MS = 1_500L
        const val SAVED_EVERY_MS = 900L
        const val SAVED_REPEAT_MS = 30_000L
        const val MIN_SIGN_CHARS = 3
        const val ML_KIT_TIMEOUT_S = 2L
        const val SHUTDOWN_WAIT_S = 2L
        const val LOG_EVERY = 30
        const val HFOV_DEG = 65f
        const val CLASSIFIER_FILE = "efficientnet-lite0.tflite"
    }
}


/** Log helper: median raw depth in the centre of the grid. */
internal object DepthDiag {
    fun centre(grid: FloatArray, w: Int, h: Int): String {
        val v = ArrayList<Float>()
        for (y in h * 2 / 5 until h * 3 / 5) for (x in w * 2 / 5 until w * 3 / 5) grid[y * w + x].takeIf { it > 0f }?.let { v.add(it) }
        if (v.isEmpty()) return "none"
        v.sort()
        return "%.2f".format(v[v.size / 2])
    }
}
