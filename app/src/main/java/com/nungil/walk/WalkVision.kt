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
import com.nungil.BuildConfig
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
import com.nungil.core.walk.Standpoint
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
    /** Compass heading of the camera, or null: it still knows about turns when ARCore is lost. */
    private val heading: () -> Float?,
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
    private var clearOfferUntil = 0L
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
    private var lastProfileLogAt = 0L
    private val recorder = if (BuildConfig.DEBUG) WalkRecorder(context) else null
    @Volatile private var closed = false

    /** ML Kit loads its models inside the first call, for seconds; signs and codes wait for that, walls do not. */
    @Volatile private var textReady = false
    @Volatile private var codesReady = false

    init {
        // The detector and ML Kit take seconds to load: do it while ARCore starts, not inside the first frame.
        executor.execute {
            runCatching {
                val blank = InputImage.fromBitmap(Bitmap.createBitmap(WARM_UP_PX, WARM_UP_PX, Bitmap.Config.ARGB_8888), 0)
                textRecognizer.process(blank).addOnCompleteListener { textReady = true }
                barcodes.process(blank).addOnCompleteListener { codesReady = true }
            }.onFailure {
                textReady = true
                codesReady = true
            }
            runCatching { detector() }
        }
    }

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
                recorder?.frame(input, gridM)
                // An empty or one-value depth image is "could not measure", like no image at all.
                val measuredM = gridM?.takeIf { DepthGrid.measured(it) }
                val report = analyse(
                    bitmap = bitmap,
                    detections = detect(bitmap),
                    gridM = measuredM,
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
                    advancedM = input.advancedM,
                    here = input.standpoint,
                    featureless = input.featureless,
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

    // The last analysed picture and its detections, for "what / who is this" (CameraScreen.lastFrame).
    @Volatile private var lastBitmap: Bitmap? = null
    @Volatile private var lastDetections: List<Detection> = emptyList()
    @Volatile private var lastAt = 0L

    /**
     * The last analysed frame. The picture is copied: the worker reuses its bitmap for the next frame, and the
     * answer is worked out on another thread. Null before the first frame.
     */
    fun lastFrame(): VisionFrame? {
        val bitmap = lastBitmap ?: return null
        val copy = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return null
        return VisionFrame(lastDetections, copy, copy.width, copy.height, Facing.BACK, null, HFOV_DEG, lastAt, 0)
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
        advancedM: Float = 0f,
        here: Standpoint? = null,
        featureless: Boolean = false,
    ): WalkReport {
        lastBitmap = bitmap
        lastDetections = detections
        lastAt = now
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
            val frameHeight = if (focalGridPx > 0f) GridGeometry.floorHeight(gridM, gridWidth, gridHeight, focalGridPx, pitchRad) else null
            val geometry = if (focalGridPx > 0f) {
                GridGeometry(focalGridPx, pitchRad, gridHeight, cameraHeight.update(frameHeight))
            } else {
                null
            }
            val zones = DepthObstacles.read(gridM, gridWidth, gridHeight, geometry)
            val ahead = zones.first { it.zone == Zone.AHEAD }
            if (loggedFrames % LOG_EVERY == 0) {
                Log.i(
                    TAG,
                    "Walk depth: camH=%.2f%s frame=%s pitch=%.0f° ahead valid=%.2f blocked=%s d=%s raw=%s".format(
                        geometry?.cameraHeightM ?: -1f, if (cameraHeight.measured) "" else " (guess)", frameHeight,
                        Math.toDegrees(pitchRad.toDouble()), ahead.validShare,
                        ahead.blocked, ahead.distanceM, DepthDiag.centre(gridM, gridWidth, gridHeight),
                    ),
                )
            }
            // Steps and drops are judged against the floor in view (GroundProfile.read), then the change has to
            // stay where it is while the user walks (FloorConfirmer).
            val rows = geometry?.let { GroundProfile.rows(gridM, gridWidth, gridHeight, it) }
            val seen = rows?.let { GroundProfile.read(it) }
            floor = floorConfirmer.update(seen, advancedM)
            if (seen != null && now - lastProfileLogAt >= PROFILE_LOG_EVERY_MS) {
                lastProfileLogAt = now
                Log.i(
                    TAG,
                    "Walk floor seen: ${seen.change} at %.2f m (%s) camH=%.2f pitch=%.0f° profile %s".format(
                        seen.distanceM, if (floor == null) "waiting" else "confirmed", cameraHeight.value,
                        Math.toDegrees(pitchRad.toDouble()), DepthDiag.profile(rows),
                    ),
                )
            }
            // Stairs going up look like a wall at chest height: say "stairs", not "wall".
            val stairsUp = floor?.change == FloorChange.STAIRS && floor.distanceM <= STAIRS_HIDE_WALL_M
            if (ahead.blocked && !ahead.unknown) ahead.distanceM?.let { approach.measured(now, it) } else if (!ahead.unknown) approach.clear()
            closeHold.update(now, ahead, walked, here, heading())?.let { d ->
                aheadBlocked = true
                beep = d
                if (!stairsUp) alerts.add(wallAhead(d, l))
            }
            aheadMeasuredClear = !ahead.unknown && !ahead.blocked
            // A wall in front fills the left and the right third too: with something ahead, only that is said.
            zones.filter { !aheadBlocked && it.zone != Zone.AHEAD && it.blocked && (it.distanceM ?: 9f) < SIDE_WARN_M }.forEach {
                alerts.add(Alert(AlertKind.HAZARD, "wall:${it.zone}", WalkPhrases.wall(it.zone, it.distanceM!!, stepM, l)))
            }
            // Nothing behind a close wall can be seen: a "floor change" beyond it is the wall's own noise.
            val wallAt = if (aheadBlocked) beep else null
            if (floor != null && wallAt != null && floor.distanceM > wallAt - BEHIND_WALL_MARGIN_M) floor = null
            if (floor != null && DepthObstacles.boxedIn(zones)) floor = null
            floor?.let { Log.i(TAG, "Walk floor: ${it.change} at %.2f m camH=%.2f pitch=%.0f°".format(it.distanceM, geometry?.cameraHeightM ?: -1f, Math.toDegrees(pitchRad.toDouble()))) }
            floor?.let {
                val text = WalkPhrases.floor(it.change, it.distanceM, stepM, l)
                alerts.add(Alert(AlertKind.FLOOR, "floor:${it.change}", text, topic = "floor", level = WalkPhrases.distanceLevel(it.distanceM, stepM), ahead = true))
            }
        } else if (depthExpected) {
            if (loggedFrames % LOG_EVERY == 0) Log.i(TAG, "Walk depth: none this frame (not tracking or not ready)")
            // Right in front of a plain wall ARCore loses tracking and depth: that is "could not measure", not "clear".
            closeHold.update(now, ZoneReading(Zone.AHEAD, false, null, 0f), walked, here, heading())?.let { d ->
                aheadBlocked = true
                beep = d
                alerts.add(wallAhead(d, l))
            }
        }

        if (depthExpected) {
            depthStatus.update(now, gridM != null && gridWidth > 0 && gridHeight > 0, tooDark, l, featureless)?.let { alerts.add(it) }
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
            val level = d?.let { WalkPhrases.distanceLevel(it, stepM) } ?: Alert.FAR
            alerts.add(Alert(AlertKind.HAZARD, "hazard:$label:$zone", WalkPhrases.hazard(label, zone, d, stepM, l), topic = "hazard:$label", level = level, ahead = zone == Zone.AHEAD))
        }

        if (!aheadBlocked) wallLevel = Int.MAX_VALUE

        // Once the way ahead has been clear for a moment after a warning, say so once (never "safe"). It stays
        // on offer for a while: it waits for the sentence being said, and a single frame would lose it.
        if (aheadBlocked || hazardAhead || floor != null) {
            warnedAheadAt = now
            clearSince = null
            clearOfferUntil = 0L
        } else if (aheadMeasuredClear && warnedAheadAt != null) {
            val since = clearSince ?: now.also { clearSince = it }
            if (now - since >= CLEAR_AFTER_MS) {
                clearOfferUntil = now + CLEAR_OFFER_MS
                warnedAheadAt = null
                clearSince = null
            }
        }
        if (now < clearOfferUntil) alerts.add(Alert(AlertKind.CLEAR, "clear", WalkPhrases.nothingAhead(l)))

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
            if (textReady) readSign(bitmap)?.let { alerts.add(Alert(AlertKind.SIGN, "sign:$it", WalkPhrases.sign(it, l))) }
            if (codesReady) readCode(bitmap)?.let { alerts.add(Alert(AlertKind.CODE, "code:$it", WalkPhrases.code(it, l))) }
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
        return Alert(AlertKind.HAZARD, "wall:ahead:$wallLevel", WalkPhrases.wall(Zone.AHEAD, d, stepM, l), urgent = wallLevel <= 1, ahead = true)
    }

    // ---- models (worker thread) ------------------------------------------------------------------

    private fun detect(bitmap: Bitmap): List<Detection> {
        val started = SystemClock.elapsedRealtime()
        val result = detector().detect(BitmapImageBuilder(bitmap).build())
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

    private fun detector(): ObjectDetector =
        detector ?: DetectorFactory(context).create(DetectorPlan.WALK_MODEL_FILE, Compute.CPU).detector.also {
            detector = it
            Log.i(TAG, "Walk detector ${DetectorPlan.WALK_MODEL_FILE} on CPU")
        }

    private fun savedTags(bitmap: Bitmap, detections: List<Detection>): List<Pair<String, Zone>> {
        if (taggers.isEmpty()) taggers = runCatching { createNameTaggers(context) }.getOrDefault(emptyList())
        if (taggers.isEmpty()) return emptyList()
        val frame = VisionFrame(detections, bitmap, bitmap.width, bitmap.height, Facing.BACK, null, HFOV_DEG, SystemClock.elapsedRealtime(), 0)
        return taggers.flatMap { t -> runCatching { t.tag(frame) }.getOrDefault(emptyList()) }
            .mapNotNull { tag -> (tag.box ?: detections.getOrNull(tag.detectionIndex)?.box)?.let { tag.name to DepthGrid.zoneOf(it.centerX) } }
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
            recorder?.close()
        }
        executor.shutdown()
        runCatching { executor.awaitTermination(SHUTDOWN_WAIT_S, TimeUnit.SECONDS) }
    }

    companion object {
        const val TAG = "Nungil"
        const val SIDE_WARN_M = 1.5f
        const val STAIRS_HIDE_WALL_M = 4f
        const val BEHIND_WALL_MARGIN_M = 0.2f
        const val CLEAR_AFTER_MS = 1_000L
        const val CLEAR_OFFER_MS = 4_000L
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
        const val WARM_UP_PX = 64
        const val PROFILE_LOG_EVERY_MS = 1_000L
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

    /** Log helper: the floor ahead as "metres:centimetres above the floor", one median per quarter metre. */
    fun profile(rows: List<GroundProfile.Row>): String =
        rows.groupBy { (it.forwardM / PROFILE_BIN_M).toInt() }.toSortedMap().entries.joinToString(" ") { (bin, inBin) ->
            val heights = inBin.map { it.heightM }.sorted()
            "%.2f:%+.0f".format(bin * PROFILE_BIN_M, heights[heights.size / 2] * 100)
        }

    private const val PROFILE_BIN_M = 0.25f
}
