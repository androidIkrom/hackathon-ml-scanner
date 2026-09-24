# Nungil Scan Engine (Person A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build everything between the camera and the spoken sentence: the camera session with detector, heading and field of view, the pure-Kotlin scan brain (angles, coverage, deduplication, colour, summary in English and Korean), and the full/live scan screen that saves history.

**Architecture:** Pure Kotlin in `com.nungil.core.scan` (no Android imports, unit-tested on the JVM) holds every rule and tuned number. The Android layer in `com.nungil.scan` is glue: `CameraSession` turns CameraX frames into `VisionFrame`s, `ScanFragment` feeds them to `ScanSession` and speaks through I's `Speaker`. Y's `NameTagger`s run on a separate extras thread and rename boxes.

**Tech Stack:** Kotlin 2.1.0, CameraX 1.4.2, MediaPipe tasks-vision 1.0.0 (ObjectDetector, ImageClassifier), Camera2 characteristics, rotation-vector sensor, Room 2.7.2, Material 3, JUnit 4.

**Spec:** `docs/build-guide.md` (§4, §6, §8, §9.5), `docs/hackathon-brief.md` (§3–§5), and the team plan `docs/superpowers/plans/2026-09-24-00-team-plan.md`. **Read the team plan first; its Global Constraints, ownership rules, contracts (Appendix A), design rules (§4) and canonical sentences (§5) apply to every task here.**

## Global Constraints

These add to the team plan's Global Constraints; they apply only to A's work.

- You own only: `app/src/main/java/com/nungil/core/scan/`, `…/scan/`, `…/walk/`, `app/src/main/res-a/`, the matching folders under `app/src/test/java/` and `app/src/androidTest/java/`, plus the build files, `tools/`, `.githooks/` and `.github/`.
- Resource names in `res-a/` start with `scan_`, `walk_`, `detector_` or `color_`.
- **Two classes move between tasks, compared with the team timeline.** `DetectionFilter` is created in **A2**, not A8, because `CameraSession` (A3) needs it. A8 is `Directions` only. `ColorName` is created in **A6**, because the clusterer votes on it; A7 adds the colour rules.
- Analysis runs at 640 × 480 (4:3, `OUTPUT_IMAGE_FORMAT_RGBA_8888`, `STRATEGY_KEEP_ONLY_LATEST`). The detector runs on the **upright** bitmap, so boxes come out upright and no box rotation maths is needed.
- White-balance gains come from the **whole frame** (a 32 × 32 grid), never from the object's own box: gray-world on one blue chair would erase its blue.
- New tuned numbers introduced here, each pinned by a test: `DARK_FRAME_V = 0.25f` (mean HSV value of a frame too dark for colours), `NO_COMPASS_GRACE_MS = 1_500L` (the sensor's first reading can arrive after the first frame), `HeadingFilter.ALPHA = 0.2f`, `SpinGuard.MIN_WINDOW_MS = 100L`, `AUTO_START_MS = 1_500L` (the intro is spoken before the scan starts).
- Never create `TextToSpeech`, `SpeechRecognizer`, `Vibrator` or `ToneGenerator`. Speak with `services().speaker`, vibrate with `services().haptics`.
- `Repeat` is handled by I. `ScanFragment` handles only `Start`, `Stop`, `SwitchCamera`, `WhatIsThis` and `WhoIsThis`.
- Test command for one task: `.\gradlew.bat testDebugUnitTest --tests "<class>" -PskipModels`. Before a push: `.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest -PskipModels`.

## Review Focus

Five conditions most likely to hurt a real user that normal tests do not reach. Each is pinned where noted.

1. **No rotation-vector sensor** (`headingDeg == null` for more than 1.5 s): a full scan says "Compass not available. Switching to live scan." once, then announces objects live and never pretends to finish. Pinned by `ScanSessionTest.missingCompassSwitchesAFullScanToLive` (A10).
2. **Dark room:** objects are still named, never coloured, in both languages. Pinned by `ColorVoteTest.darkFrameGivesNull`, `ColorVoteTest.darknessThreshold`, `ColorMapperTest.darkFrameGivesNoColourAtAll` (A7) and `SummaryBuilderTest.darkRoomNamesObjectsWithoutColourWords` (A9).
3. **Camera permission denied**, including "don't ask again": the camera card shows "Camera needed", the main button becomes "Allow camera", "Open settings" works, and returning with the permission granted starts the camera. Checked on the phone in A11 Step 9.
4. **Turning too fast:** "Slow down." at most once every 5 s, and coverage never jumps more than 45° per frame. Pinned by `ScanSessionTest.turningTooFastSaysSlowDown`, `ScanSessionTest.slowDownIsNotRepeatedWithinFiveSeconds` (A10) and `CoverageTrackerTest.jumpLargerThan45MarksOnlyTheEnds` (A5).
5. **GPU delegate that loads but cannot run** (common on budget phones): a 64 × 64 trial detect proves it, and the CPU takes over without a crash. Pinned by `DetectorPlanTest.gpuFallsBackToCpu` and `DetectorPlanTest.int8ModelNeverRunsOnTheGpu` (A2); checked on the phone in A3 Step 9 (logcat names the delegate that won).

---

### Task A1: Angles and boxes

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/AngleMath.kt`
- Create: `app/src/main/java/com/nungil/core/scan/BoxGeometry.kt`
- Test: `app/src/test/java/com/nungil/core/scan/AngleMathTest.kt`
- Test: `app/src/test/java/com/nungil/core/scan/BoxGeometryTest.kt`

**Interfaces:**
- Consumes: `com.nungil.contract.Box`, `com.nungil.contract.Facing` (Appendix A).
- Produces:
  - `object AngleMath { fun normalize(deg: Float): Float; fun diff(a: Float, b: Float): Float; fun sector8(relDeg: Float): Int; fun sector4(relDeg: Float): Int; fun weightedMean(mean: Float, count: Int, sample: Float): Float }`
  - `object BoxGeometry { const val EDGE_MARGIN = 0.02f; fun horizontalCenter(box: Box): Float; fun objectAngle(relHeading: Float, centerX: Float, hfovDeg: Float, facing: Facing): Float; fun touchesOneSideEdge(box: Box, margin: Float = EDGE_MARGIN): Boolean; fun iou(a: Box, b: Box): Float }`

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A1-angles
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/nungil/core/scan/AngleMathTest.kt`

```kotlin
package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class AngleMathTest {
    private val eps = 0.001f

    @Test fun normalizeWrapsNegativeAndLarge() {
        assertEquals(270f, AngleMath.normalize(-90f), eps)
        assertEquals(0f, AngleMath.normalize(720f), eps)
        assertEquals(0f, AngleMath.normalize(360f), eps)
    }

    @Test fun normalizeNeverReturns360() = assertEquals(0f, AngleMath.normalize(-1e-6f), eps)

    @Test fun diffIsShortestSignedTurn() {
        assertEquals(20f, AngleMath.diff(10f, 350f), eps)
        assertEquals(-20f, AngleMath.diff(350f, 10f), eps)
        assertEquals(-180f, AngleMath.diff(0f, 180f), eps)
    }

    @Test fun sector8Has45DegreeSectorsCentredOnFront() {
        assertEquals(0, AngleMath.sector8(0f))
        assertEquals(0, AngleMath.sector8(22.4f))
        assertEquals(1, AngleMath.sector8(22.6f))
        assertEquals(0, AngleMath.sector8(-10f))
        assertEquals(2, AngleMath.sector8(90f))
        assertEquals(7, AngleMath.sector8(-30f))
    }

    @Test fun sector4Has90DegreeSectorsCentredOnFront() {
        assertEquals(0, AngleMath.sector4(44f))
        assertEquals(1, AngleMath.sector4(46f))
        assertEquals(2, AngleMath.sector4(180f))
        assertEquals(3, AngleMath.sector4(-46f))
        assertEquals(0, AngleMath.sector4(-44f))
    }

    @Test fun weightedMeanIsCircular() {
        assertEquals(0f, AngleMath.normalize(AngleMath.weightedMean(350f, 1, 10f) + 0.0001f), 0.01f)
        assertEquals(90f, AngleMath.weightedMean(0f, 0, 90f), eps)
        assertEquals(15f, AngleMath.weightedMean(10f, 1, 20f), 0.01f)
    }
}
```

`app/src/test/java/com/nungil/core/scan/BoxGeometryTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Facing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxGeometryTest {
    private val eps = 0.001f

    @Test fun centreOfTheImageIsTheHeading() =
        assertEquals(0f, BoxGeometry.objectAngle(0f, 0.5f, 60f, Facing.BACK), eps)

    @Test fun rightEdgeIsHalfTheFieldOfViewClockwise() =
        assertEquals(30f, BoxGeometry.objectAngle(0f, 1f, 60f, Facing.BACK), eps)

    @Test fun leftEdgeWrapsBelowZero() =
        assertEquals(330f, BoxGeometry.objectAngle(0f, 0f, 60f, Facing.BACK), eps)

    @Test fun frontCameraLooksBehind() =
        assertEquals(180f, BoxGeometry.objectAngle(0f, 0.5f, 60f, Facing.FRONT), eps)

    @Test fun headingAndOffsetAdd() =
        assertEquals(5f, BoxGeometry.objectAngle(350f, 0.75f, 60f, Facing.BACK), eps)

    @Test fun edgeMarginIsTwoPercent() = assertEquals(0.02f, BoxGeometry.EDGE_MARGIN)

    @Test fun boxTouchingOnlyTheLeftEdgeIsHalfSeen() =
        assertTrue(BoxGeometry.touchesOneSideEdge(Box(0.01f, 0.2f, 0.5f, 0.8f)))

    @Test fun boxTouchingOnlyTheRightEdgeIsHalfSeen() =
        assertTrue(BoxGeometry.touchesOneSideEdge(Box(0.5f, 0.2f, 0.985f, 0.8f)))

    @Test fun boxSpanningTheWholeWidthIsKept() =
        assertFalse(BoxGeometry.touchesOneSideEdge(Box(0.01f, 0.2f, 0.99f, 0.8f)))

    @Test fun boxAwayFromEdgesIsKept() =
        assertFalse(BoxGeometry.touchesOneSideEdge(Box(0.1f, 0.2f, 0.9f, 0.8f)))

    @Test fun boxJustInsideTheMarginIsKept() =
        assertFalse(BoxGeometry.touchesOneSideEdge(Box(0.03f, 0.2f, 0.5f, 0.8f)))

    @Test fun horizontalCenterIsClamped() {
        assertEquals(0.5f, BoxGeometry.horizontalCenter(Box(0.4f, 0f, 0.6f, 1f)), eps)
        assertEquals(1f, BoxGeometry.horizontalCenter(Box(0.9f, 0f, 1.3f, 1f)), eps)
    }

    @Test fun iouOfSameBoxIsOne() {
        val b = Box(0.1f, 0.1f, 0.5f, 0.5f)
        assertEquals(1f, BoxGeometry.iou(b, b), eps)
    }

    @Test fun iouOfDisjointBoxesIsZero() =
        assertEquals(0f, BoxGeometry.iou(Box(0f, 0f, 0.2f, 0.2f), Box(0.5f, 0.5f, 0.7f, 0.7f)), eps)

    @Test fun iouOfHalfOverlap() =
        assertEquals(1f / 3f, BoxGeometry.iou(Box(0f, 0f, 0.2f, 0.2f), Box(0.1f, 0f, 0.3f, 0.2f)), eps)
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.AngleMathTest" --tests "com.nungil.core.scan.BoxGeometryTest" -PskipModels`
Expected: FAIL in `compileDebugUnitTestKotlin` with `Unresolved reference 'AngleMath'` and `Unresolved reference 'BoxGeometry'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/nungil/core/scan/AngleMath.kt`

```kotlin
package com.nungil.core.scan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Compass maths in degrees. 0 = the reference direction, angles grow clockwise. */
object AngleMath {
    /** Any angle into 0 until 360. */
    fun normalize(deg: Float): Float {
        val r = deg % 360f
        val n = if (r < 0f) r + 360f else r
        return if (n >= 360f) 0f else n
    }

    /** Signed shortest turn from [b] to [a], in -180 until 180. diff(10, 350) = 20. */
    fun diff(a: Float, b: Float): Float {
        val d = normalize(a - b)
        return if (d >= 180f) d - 360f else d
    }

    /** 8 sectors of 45°: 0 front, 1 front right, 2 right, 3 back right, 4 back, 5 back left, 6 left, 7 front left. */
    fun sector8(relDeg: Float): Int = ((normalize(relDeg) + 22.5f) / 45f).toInt() % 8

    /** 4 sectors of 90° for speech: 0 front, 1 right, 2 behind, 3 left. */
    fun sector4(relDeg: Float): Int = ((normalize(relDeg) + 45f) / 90f).toInt() % 4

    /**
     * Circular mean of a running mean over [count] samples and one new [sample].
     * Stays stable across 0°/360°, where a plain average of 350 and 10 would give 180.
     */
    fun weightedMean(mean: Float, count: Int, sample: Float): Float {
        val m = Math.toRadians(mean.toDouble())
        val s = Math.toRadians(sample.toDouble())
        val x = cos(m) * count + cos(s)
        val y = sin(m) * count + sin(s)
        if (abs(x) < 1e-9 && abs(y) < 1e-9) return normalize(sample)
        return normalize(Math.toDegrees(atan2(y, x)).toFloat())
    }
}
```

`app/src/main/java/com/nungil/core/scan/BoxGeometry.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Facing
import kotlin.math.max
import kotlin.math.min

/** Turns a detector box into a direction; the whole sensor fusion lives in [objectAngle]. */
object BoxGeometry {
    /** A box this close to a side edge counts as touching it. */
    const val EDGE_MARGIN = 0.02f

    /** Box centre across the upright image, 0 = left edge, 1 = right edge. */
    fun horizontalCenter(box: Box): Float = box.centerX.coerceIn(0f, 1f)

    /**
     * Compass angle of an object: relHeading + (centerX - 0.5) * hfov.
     * The front camera looks the other way, so its base direction is turned by 180°.
     */
    fun objectAngle(relHeading: Float, centerX: Float, hfovDeg: Float, facing: Facing): Float {
        val base = if (facing == Facing.FRONT) relHeading + 180f else relHeading
        return AngleMath.normalize(base + (centerX - 0.5f) * hfovDeg)
    }

    /**
     * True when the box touches exactly one side edge: a half-seen object that will be seen whole
     * in another frame. Dropping these removed most double counts. A box spanning the full width is kept.
     */
    fun touchesOneSideEdge(box: Box, margin: Float = EDGE_MARGIN): Boolean {
        val left = box.left <= margin
        val right = box.right >= 1f - margin
        return left != right
    }

    /** Intersection over union of two boxes, 0..1. */
    fun iou(a: Box, b: Box): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        val union = a.area + b.area - inter
        return if (union <= 0f) 0f else inter / union
    }
}
```

- [ ] **Step 5: Run them and see them pass**

Run the same command. Expected: `BUILD SUCCESSFUL`, 21 tests (AngleMathTest 6, BoxGeometryTest 15), 0 failures.

- [ ] **Step 6: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/AngleMath.kt app/src/main/java/com/nungil/core/scan/BoxGeometry.kt app/src/test/java/com/nungil/core/scan/AngleMathTest.kt app/src/test/java/com/nungil/core/scan/BoxGeometryTest.kt
git commit -m "Give every detected object a compass direction"
git push -u origin a/A1-angles
```

Open a pull request into `main`; merge when CI is green and one teammate has checked it.

---

### Task A2: Detector and per-label filter

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/DetectionFilter.kt`
- Create: `app/src/main/java/com/nungil/core/scan/DetectorPlan.kt`
- Create: `app/src/main/java/com/nungil/core/scan/InferenceStats.kt`
- Create: `app/src/main/java/com/nungil/scan/DetectorFactory.kt`
- Test: `app/src/test/java/com/nungil/core/scan/DetectionFilterTest.kt`
- Test: `app/src/test/java/com/nungil/core/scan/DetectorPlanTest.kt`

**Interfaces:**
- Consumes: `Detection`, `Compute`, `ModelChoice`, `ScanSettings` (contract).
- Produces:
  - `object DetectionFilter { const val DETECTOR_THRESHOLD = 0.3f; const val PERSON_BONUS_TENTHS = 2; fun minScoreFor(label: String, minScore: Float): Float; fun keep(detections: List<Detection>, minScore: Float): List<Detection>; fun keepAtLeast(detections: List<Detection>, floor: Float): List<Detection> }`
  - `object DetectorPlan { const val MAX_RESULTS = 10; const val TRIAL_SIZE_PX = 64; const val WALK_MODEL_FILE = "efficientdet-lite0-int8.tflite"; fun modelFile(model: ModelChoice): String; fun delegateOrder(compute: Compute, modelFile: String): List<Compute> }`
  - `class InferenceStats(every: Int = 30) { fun add(ms: Long): Long? }`
  - `class DetectorFactory(context: Context) { class Built(val detector: ObjectDetector, val compute: Compute, val modelFile: String); fun create(modelFile: String, compute: Compute): Built }` — call `create` on the thread that will run the detector.

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A2-detector
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/nungil/core/scan/DetectionFilterTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Detection
import com.nungil.contract.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionFilterTest {
    private val box = Box(0.2f, 0.2f, 0.4f, 0.4f)

    @Test fun detectorRunsAtPointThree() = assertEquals(0.3f, DetectionFilter.DETECTOR_THRESHOLD)

    @Test fun defaultSettingNeedsPointSeven() {
        assertEquals(0.7f, ScanSettings.DEFAULT_MIN_SCORE)
        assertEquals(0.7f, DetectionFilter.minScoreFor("chair", 0.7f), 0.0001f)
    }

    @Test fun peopleAreAcceptedPointTwoLower() =
        assertEquals(0.5f, DetectionFilter.minScoreFor("person", 0.7f), 0.0001f)

    @Test fun peopleNeverGoBelowTheDetectorThreshold() =
        assertEquals(0.3f, DetectionFilter.minScoreFor("person", 0.4f), 0.0001f)

    @Test fun keepFiltersPerLabel() {
        val kept = DetectionFilter.keep(
            listOf(
                Detection("chair", 0.69f, box),
                Detection("chair", 0.7f, box),
                Detection("person", 0.5f, box),
                Detection("person", 0.49f, box),
            ),
            0.7f,
        )
        assertEquals(listOf(0.7f to "chair", 0.5f to "person"), kept.map { it.score to it.label })
    }

    @Test fun fixedFloorIgnoresLabels() {
        val kept = DetectionFilter.keepAtLeast(
            listOf(Detection("person", 0.39f, box), Detection("backpack", 0.4f, box)),
            0.4f,
        )
        assertEquals(listOf("backpack"), kept.map { it.label })
    }
}
```

`app/src/test/java/com/nungil/core/scan/DetectorPlanTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Compute
import com.nungil.contract.ModelChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorPlanTest {
    @Test fun modelFiles() {
        assertEquals("ssd-mobilenet-v2.tflite", DetectorPlan.modelFile(ModelChoice.LIGHT))
        assertEquals("efficientdet-lite0.tflite", DetectorPlan.modelFile(ModelChoice.FAST))
        assertEquals("efficientdet-lite2.tflite", DetectorPlan.modelFile(ModelChoice.ACCURATE))
    }

    @Test fun gpuFallsBackToCpu() =
        assertEquals(listOf(Compute.GPU, Compute.CPU), DetectorPlan.delegateOrder(Compute.GPU, "efficientdet-lite2.tflite"))

    @Test fun cpuStaysOnCpu() =
        assertEquals(listOf(Compute.CPU), DetectorPlan.delegateOrder(Compute.CPU, "efficientdet-lite2.tflite"))

    @Test fun int8ModelNeverRunsOnTheGpu() =
        assertEquals(listOf(Compute.CPU), DetectorPlan.delegateOrder(Compute.GPU, DetectorPlan.WALK_MODEL_FILE))

    @Test fun tenResultsAndA64PixelTrial() {
        assertEquals(10, DetectorPlan.MAX_RESULTS)
        assertEquals(64, DetectorPlan.TRIAL_SIZE_PX)
    }

    @Test fun inferenceIsReportedEveryThirtyFrames() {
        val stats = InferenceStats()
        assertEquals(30, InferenceStats.EVERY_FRAMES)
        repeat(29) { assertNull(stats.add(10)) }
        assertEquals(20L, stats.add(310))
        assertNull(stats.add(10))
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.DetectionFilterTest" --tests "com.nungil.core.scan.DetectorPlanTest" -PskipModels`
Expected: FAIL in `compileDebugUnitTestKotlin` with `Unresolved reference 'DetectionFilter'`, `'DetectorPlan'` and `'InferenceStats'`.

- [ ] **Step 4: Implement the pure rules**

`app/src/main/java/com/nungil/core/scan/DetectionFilter.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Detection
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The detector itself runs at [DETECTOR_THRESHOLD]; this decides per label what is kept.
 * People are accepted 0.2 lower than everything else (far and half-hidden people score low),
 * but never below the detector threshold. Maths in tenths, so 0.7 - 0.2 is exactly 0.5.
 */
object DetectionFilter {
    const val DETECTOR_THRESHOLD = 0.3f
    const val PERSON_BONUS_TENTHS = 2
    private const val MIN_TENTHS = 3

    /** Score a detection of [label] needs when the user's setting is [minScore]. */
    fun minScoreFor(label: String, minScore: Float): Float {
        val tenths = (minScore * 10f).roundToInt()
        return if (label == "person") max(MIN_TENTHS, tenths - PERSON_BONUS_TENTHS) / 10f else tenths / 10f
    }

    /** Per-label filtering for scans. */
    fun keep(detections: List<Detection>, minScore: Float): List<Detection> =
        detections.filter { it.score >= minScoreFor(it.label, minScore) }

    /** One fixed floor for every label (search runs at 0.4, enrolment at 0.3). */
    fun keepAtLeast(detections: List<Detection>, floor: Float): List<Detection> =
        detections.filter { it.score >= floor }
}
```

`app/src/main/java/com/nungil/core/scan/DetectorPlan.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Compute
import com.nungil.contract.ModelChoice

/** Which model file to load and in which order to try the delegates. */
object DetectorPlan {
    const val MAX_RESULTS = 10

    /** Size of the throwaway image used to prove a delegate before trusting it. */
    const val TRIAL_SIZE_PX = 64

    /**
     * Walking model. Its input tensor is UINT8 while MediaPipe feeds the GPU FLOAT32, so on the GPU it
     * fails on the first frame ("ToTensorConverter: input data size does not match"). CPU only.
     */
    const val WALK_MODEL_FILE = "efficientdet-lite0-int8.tflite"

    fun modelFile(model: ModelChoice): String = when (model) {
        ModelChoice.LIGHT -> "ssd-mobilenet-v2.tflite"
        ModelChoice.FAST -> "efficientdet-lite0.tflite"
        ModelChoice.ACCURATE -> "efficientdet-lite2.tflite"
    }

    /** GPU is always followed by CPU as the fallback; int8 models never touch the GPU. */
    fun delegateOrder(compute: Compute, modelFile: String): List<Compute> = when {
        modelFile.contains("int8") -> listOf(Compute.CPU)
        compute == Compute.GPU -> listOf(Compute.GPU, Compute.CPU)
        else -> listOf(Compute.CPU)
    }
}
```

`app/src/main/java/com/nungil/core/scan/InferenceStats.kt`

```kotlin
package com.nungil.core.scan

/** Averages inference times and reports once every [every] frames, to decide CPU versus GPU on the real phone. */
class InferenceStats(private val every: Int = EVERY_FRAMES) {
    private var count = 0
    private var total = 0L

    /** Adds one timing; returns the average of the last [every] frames when it is time to log, else null. */
    fun add(ms: Long): Long? {
        count++
        total += ms
        if (count < every) return null
        val average = total / count
        count = 0
        total = 0
        return average
    }

    companion object {
        const val EVERY_FRAMES = 30
    }
}
```

- [ ] **Step 5: Run them and see them pass**

Run the same command. Expected: `BUILD SUCCESSFUL`, 12 tests (6 + 6), 0 failures.

- [ ] **Step 6: Implement the Android factory**

`app/src/main/java/com/nungil/scan/DetectorFactory.kt`

```kotlin
package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.nungil.contract.Compute
import com.nungil.core.scan.DetectionFilter
import com.nungil.core.scan.DetectorPlan

/**
 * Builds the MediaPipe object detector. Call it on the thread that will run the detector:
 * the GPU delegate must be created and used on the same thread.
 */
class DetectorFactory(private val context: Context) {

    class Built(val detector: ObjectDetector, val compute: Compute, val modelFile: String)

    /**
     * Tries the delegates from [DetectorPlan.delegateOrder] and proves each with one 64x64 detect:
     * a GPU that loads a model it cannot execute otherwise fails on the first real frame and crashes on close.
     */
    fun create(modelFile: String, compute: Compute): Built {
        var lastError: Throwable? = null
        for (candidate in DetectorPlan.delegateOrder(compute, modelFile)) {
            val detector = try {
                build(modelFile, candidate)
            } catch (t: Throwable) {
                Log.i(TAG, "Detector $modelFile could not load on $candidate: ${t.message}")
                lastError = t
                continue
            }
            if (prove(detector)) {
                Log.i(TAG, "Detector $modelFile running on $candidate")
                return Built(detector, candidate, modelFile)
            }
            Log.i(TAG, "Detector $modelFile failed its trial on $candidate, falling back")
            closeQuietly(detector)
        }
        throw IllegalStateException("No delegate can run $modelFile", lastError)
    }

    private fun build(modelFile: String, compute: Compute): ObjectDetector {
        val base = BaseOptions.builder()
            .setModelAssetPath(modelFile)
            .setDelegate(if (compute == Compute.GPU) Delegate.GPU else Delegate.CPU)
            .build()
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(DetectionFilter.DETECTOR_THRESHOLD)
            .setMaxResults(DetectorPlan.MAX_RESULTS)
            .build()
        return ObjectDetector.createFromOptions(context, options)
    }

    private fun prove(detector: ObjectDetector): Boolean = try {
        val size = DetectorPlan.TRIAL_SIZE_PX
        val trial = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        detector.detect(BitmapImageBuilder(trial).build())
        true
    } catch (t: Throwable) {
        Log.i(TAG, "Delegate trial failed: ${t.message}")
        false
    }

    private fun closeQuietly(detector: ObjectDetector) {
        try {
            detector.close()
        } catch (t: Throwable) {
            Log.i(TAG, "Closing a failed detector threw: ${t.message}")
        }
    }

    companion object {
        const val TAG = "Nungil"
    }
}
```

- [ ] **Step 7: Check it compiles**

Run: `.\gradlew.bat assembleDebug -PskipModels`
Expected: `BUILD SUCCESSFUL`. (The factory is exercised on the phone in A3 Step 9.)

- [ ] **Step 8: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/DetectionFilter.kt app/src/main/java/com/nungil/core/scan/DetectorPlan.kt app/src/main/java/com/nungil/core/scan/InferenceStats.kt app/src/main/java/com/nungil/scan/DetectorFactory.kt app/src/test/java/com/nungil/core/scan/DetectionFilterTest.kt app/src/test/java/com/nungil/core/scan/DetectorPlanTest.kt
git commit -m "Load the object detector on the GPU with a proven CPU fallback"
git push -u origin a/A2-detector
```

---

### Task A3: Camera session, heading and field of view (Y needs this by h5)

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/FrameMath.kt`
- Create: `app/src/main/java/com/nungil/core/scan/FovMath.kt`
- Create: `app/src/main/java/com/nungil/core/scan/HeadingFilter.kt`
- Create: `app/src/main/java/com/nungil/scan/HeadingProvider.kt`
- Create: `app/src/main/java/com/nungil/scan/FovReader.kt`
- Modify: `app/src/main/java/com/nungil/scan/CameraSession.kt` (replace the bootstrap stub body; the signature stays exactly the same)
- Modify (temporary, replaced in A11): `app/src/main/java/com/nungil/scan/ScanFragment.kt`
- Test: `app/src/test/java/com/nungil/core/scan/CameraMathTest.kt`

**Interfaces:**
- Consumes: `DetectorFactory`, `DetectorPlan`, `DetectionFilter`, `InferenceStats` (A2); `SettingsStore`, `VisionFrame`, `Facing`, `Detection` (contract).
- Produces (frozen, used by Y): `CameraSession(fragment: Fragment, previewView: PreviewView, options: CameraSession.Options, onFrame: (VisionFrame) -> Unit, onError: (Throwable) -> Unit)` with `data class Options(facing: Facing = BACK, detect: Boolean = true, keepBitmap: Boolean = false, minScore: Float? = null)`, `var facing: Facing` (private set), `val hfovDeg: Float`, `fun start()`, `fun switchCamera()`, `fun stop()`.
  - `onFrame` runs on the analysis thread, once per frame. While it runs, newer frames are dropped (KEEP_ONLY_LATEST).
  - `onError` runs on the main thread. A bad frame never stops the session.
  - With `minScore == null`, detections pass `DetectionFilter.keep(…, settings.minScore)`; otherwise they pass `keepAtLeast(…, minScore)`.
  - `headingDeg` is null until the first sensor reading, and always null without a rotation-vector sensor.
- Also produces: `object FrameMath { fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int>; fun normalizeBox(left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int): Box }`, `object FovMath { const val FALLBACK_DEG = 65f; fun horizontalFovDeg(sensorWidthMm: Float, sensorHeightMm: Float, focalMm: Float, sensorOrientation: Int): Float }`, `class HeadingFilter(alpha: Float = 0.2f) { fun update(sample: Float): Float }`.

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A3-camera-session
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/nungil/core/scan/CameraMathTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraMathTest {
    private val eps = 0.001f

    @Test fun rotationBy90SwapsWidthAndHeight() {
        assertEquals(480 to 640, FrameMath.uprightSize(640, 480, 90))
        assertEquals(480 to 640, FrameMath.uprightSize(640, 480, 270))
        assertEquals(640 to 480, FrameMath.uprightSize(640, 480, 180))
        assertEquals(640 to 480, FrameMath.uprightSize(640, 480, 0))
    }

    @Test fun pixelBoxIsNormalisedAndClamped() =
        assertEquals(Box(0.25f, 0.5f, 1f, 0f), FrameMath.normalizeBox(120f, 320f, 500f, -10f, 480, 640))

    @Test fun fovUsesSensorHeightForSidewaysSensors() {
        // 4.8 x 3.6 mm sensor, 3.0 mm lens, mounted at 90°: 2*atan(3.6/6) = 61.93°
        assertEquals(61.93f, FovMath.horizontalFovDeg(4.8f, 3.6f, 3.0f, 90), 0.01f)
        // mounted at 0°: 2*atan(4.8/6) = 77.32°
        assertEquals(77.32f, FovMath.horizontalFovDeg(4.8f, 3.6f, 3.0f, 0), 0.01f)
    }

    @Test fun fovFallsBackTo65() {
        assertEquals(65f, FovMath.FALLBACK_DEG)
        assertEquals(65f, FovMath.horizontalFovDeg(0f, 0f, 3f, 90), eps)
        assertEquals(65f, FovMath.horizontalFovDeg(4.8f, 3.6f, 0f, 90), eps)
        assertEquals(65f, FovMath.horizontalFovDeg(Float.NaN, Float.NaN, 3f, 90), eps)
    }

    @Test fun headingFilterStartsAtFirstReading() =
        assertEquals(100f, HeadingFilter().update(100f), eps)

    @Test fun headingFilterMovesAFifthOfTheWay() {
        val f = HeadingFilter()
        f.update(0f)
        assertEquals(0.2f, HeadingFilter.ALPHA)
        assertEquals(2f, f.update(10f), eps)
    }

    @Test fun headingFilterTurnsTheShortWayAcrossNorth() {
        val f = HeadingFilter()
        f.update(350f)
        assertEquals(354f, f.update(10f), eps)
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.CameraMathTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'FrameMath'`, `'FovMath'` and `'HeadingFilter'`.

- [ ] **Step 4: Implement the pure helpers**

`app/src/main/java/com/nungil/core/scan/FrameMath.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box

/** Pixel bookkeeping between the camera buffer and the upright image. */
object FrameMath {
    /** Size of the image after rotating a [width] x [height] buffer by [rotationDegrees] (0, 90, 180 or 270). */
    fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 == 0) width to height else height to width

    /** Pixel rectangle of an upright [width] x [height] image to a normalised [Box], clamped to 0..1. */
    fun normalizeBox(left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int): Box {
        val w = width.toFloat()
        val h = height.toFloat()
        return Box(
            (left / w).coerceIn(0f, 1f),
            (top / h).coerceIn(0f, 1f),
            (right / w).coerceIn(0f, 1f),
            (bottom / h).coerceIn(0f, 1f),
        )
    }
}
```

`app/src/main/java/com/nungil/core/scan/FovMath.kt`

```kotlin
package com.nungil.core.scan

import kotlin.math.atan

/** Horizontal field of view of the upright (portrait) image from the camera's physical data. */
object FovMath {
    const val FALLBACK_DEG = 65f

    /**
     * 2 * atan(side / (2 * focal)) in degrees. Sensors are mounted sideways (orientation 90 or 270), so the
     * portrait image's width is the sensor's physical height; for 0 or 180 it is the sensor's width.
     * Anything unusable gives [FALLBACK_DEG].
     */
    fun horizontalFovDeg(sensorWidthMm: Float, sensorHeightMm: Float, focalMm: Float, sensorOrientation: Int): Float {
        val side = if (sensorOrientation % 180 == 0) sensorWidthMm else sensorHeightMm
        if (!(side > 0f) || !(focalMm > 0f)) return FALLBACK_DEG
        val deg = Math.toDegrees(2.0 * atan(side / (2.0 * focalMm))).toFloat()
        return if (deg in 10f..170f) deg else FALLBACK_DEG
    }
}
```

`app/src/main/java/com/nungil/core/scan/HeadingFilter.kt`

```kotlin
package com.nungil.core.scan

/** Low-pass filter for a compass heading that turns the short way across 0°/360°. */
class HeadingFilter(private val alpha: Float = ALPHA) {
    private var value: Float? = null

    /** Feeds one raw heading (degrees) and returns the smoothed heading in 0 until 360. */
    fun update(sample: Float): Float {
        val prev = value
        val next = if (prev == null) AngleMath.normalize(sample) else AngleMath.normalize(prev + alpha * AngleMath.diff(sample, prev))
        value = next
        return next
    }

    companion object {
        /** Share of each new reading; 0.2 settles within about ten sensor events at SENSOR_DELAY_GAME. */
        const val ALPHA = 0.2f
    }
}
```

- [ ] **Step 5: Run them and see them pass**

Run the same command. Expected: `BUILD SUCCESSFUL`, 7 tests, 0 failures.

- [ ] **Step 6: Implement heading and field of view on Android**

`app/src/main/java/com/nungil/scan/HeadingProvider.kt`

```kotlin
package com.nungil.scan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.nungil.core.scan.AngleMath
import com.nungil.core.scan.HeadingFilter

/**
 * Compass heading of the back camera for an upright phone, from the rotation-vector sensor.
 * remapCoordinateSystem(AXIS_X, AXIS_Z) makes the azimuth describe where the camera points, not the
 * top of the phone. [headingDeg] is null when the phone has no rotation-vector sensor.
 */
class HeadingProvider(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val filter = HeadingFilter()
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    /** Latest smoothed heading; written on the main thread, read on the analysis thread. */
    @Volatile
    var headingDeg: Float? = null
        private set

    val available: Boolean get() = sensor != null

    fun start() {
        sensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensors.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, orientation)
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        headingDeg = filter.update(AngleMath.normalize(azimuth))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
```

`app/src/main/java/com/nungil/scan/FovReader.kt`

```kotlin
package com.nungil.scan

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.nungil.contract.Facing
import com.nungil.core.scan.FovMath

/** Reads the horizontal field of view of the first camera facing [Facing] from Camera2 characteristics. */
object FovReader {
    fun read(context: Context, facing: Facing): Float = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val wanted = if (facing == Facing.FRONT) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == wanted
        }
        if (id == null) {
            FovMath.FALLBACK_DEG
        } else {
            val c = manager.getCameraCharacteristics(id)
            val size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            val orientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            FovMath.horizontalFovDeg(size?.width ?: 0f, size?.height ?: 0f, focal ?: 0f, orientation)
        }
    } catch (e: Exception) {
        Log.i("Nungil", "Field of view unavailable, using ${FovMath.FALLBACK_DEG}: ${e.message}")
        FovMath.FALLBACK_DEG
    }
}
```

- [ ] **Step 7: Replace the body of `CameraSession` (same public signature as the bootstrap stub)**

`app/src/main/java/com/nungil/scan/CameraSession.kt`

```kotlin
package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.nungil.contract.Detection
import com.nungil.contract.Facing
import com.nungil.contract.ScanSettings
import com.nungil.contract.app.VisionFrame
import com.nungil.core.scan.DetectionFilter
import com.nungil.core.scan.DetectorPlan
import com.nungil.core.scan.FrameMath
import com.nungil.core.scan.InferenceStats
import com.nungil.data.SettingsStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Binds CameraX preview + analysis to [fragment]'s view lifecycle, runs the object detector chosen in
 * SettingsStore and delivers one [VisionFrame] per analysed frame to [onFrame] on the analysis thread.
 * Frames that arrive while [onFrame] is still running are dropped. [onError] runs on the main thread.
 */
class CameraSession(
    private val fragment: Fragment,
    private val previewView: PreviewView,
    private val options: Options,
    private val onFrame: (VisionFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    data class Options(
        val facing: Facing = Facing.BACK,
        /** Run the object detector. false = frames only (face enrolment). */
        val detect: Boolean = true,
        /** Attach the upright bitmap to every frame (faces, items, colours). */
        val keepBitmap: Boolean = false,
        /** Fixed detector score floor; null = per-label DetectionFilter with the saved settings. */
        val minScore: Float? = null,
    )

    var facing: Facing = options.facing
        private set

    /** Horizontal field of view of the upright image; 65° until the camera reports its own. */
    val hfovDeg: Float
        get() = fov

    @Volatile
    private var fov = DEFAULT_HFOV_DEG

    @Volatile
    private var running = false

    @Volatile
    private var currentFacing = options.facing

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var executor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var heading: HeadingProvider? = null
    private var settings = ScanSettings()

    // Touched only on the analysis thread.
    private var detector: DetectorFactory.Built? = null
    private var detectorFailed = false
    private val stats = InferenceStats()

    /** Call from onViewCreated after the camera permission is granted. */
    fun start() {
        if (running) return
        val context = fragment.requireContext().applicationContext
        appContext = context
        settings = SettingsStore(context).load()
        executor = Executors.newSingleThreadExecutor()
        heading = HeadingProvider(context).also { it.start() }
        fov = FovReader.read(context, facing)
        running = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!running) return@addListener
            try {
                provider = future.get()
                bind()
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera() {
        facing = if (facing == Facing.BACK) Facing.FRONT else Facing.BACK
        currentFacing = facing
        appContext?.let { fov = FovReader.read(it, facing) }
        if (running && provider != null) bind()
    }

    /** Call from onDestroyView. Waits at most 2 s for the analysis thread. */
    fun stop() {
        running = false
        heading?.stop()
        heading = null
        val p = provider
        val useCases = listOfNotNull(preview, analysis)
        if (p != null && useCases.isNotEmpty()) {
            try {
                p.unbind(*useCases.toTypedArray())
            } catch (e: Exception) {
                Log.i(TAG, "Unbinding the camera threw: ${e.message}")
            }
        }
        preview = null
        analysis = null
        provider = null
        val ex = executor ?: return
        executor = null
        // Close the detector on its own thread: the GPU delegate must be used and closed on one thread.
        ex.execute {
            try {
                detector?.detector?.close()
            } catch (t: Throwable) {
                Log.i(TAG, "Closing the detector threw: ${t.message}")
            }
            detector = null
        }
        ex.shutdown()
        try {
            if (!ex.awaitTermination(2, TimeUnit.SECONDS)) ex.shutdownNow()
        } catch (e: InterruptedException) {
            ex.shutdownNow()
        }
    }

    private fun bind() {
        val p = provider ?: return
        val ex = executor ?: return
        if (fragment.view == null) return
        val owner = fragment.viewLifecycleOwner
        val fourByThree = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val newPreview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(fourByThree).build())
            .build()
        newPreview.setSurfaceProvider(previewView.surfaceProvider)
        val analysisSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(fourByThree)
            .setResolutionStrategy(
                ResolutionStrategy(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        val newAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        newAnalysis.setAnalyzer(ex) { image -> analyze(image) }
        val selector = if (facing == Facing.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            val old = listOfNotNull(preview, analysis)
            if (old.isNotEmpty()) p.unbind(*old.toTypedArray())
            p.bindToLifecycle(owner, selector, newPreview, newAnalysis)
            preview = newPreview
            analysis = newAnalysis
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun analyze(image: ImageProxy) {
        try {
            if (!running) return
            val upright = upright(image)
            val started = SystemClock.uptimeMillis()
            val detections = if (options.detect) detect(upright) else emptyList()
            val inferenceMs = SystemClock.uptimeMillis() - started
            if (options.detect) {
                stats.add(inferenceMs)?.let { average ->
                    val built = detector
                    Log.i(TAG, "Inference $average ms average on ${built?.compute} (${built?.modelFile})")
                }
            }
            val frame = VisionFrame(
                detections = detections,
                bitmap = if (options.keepBitmap) upright else null,
                imageWidth = upright.width,
                imageHeight = upright.height,
                facing = currentFacing,
                headingDeg = heading?.headingDeg,
                hfovDeg = fov,
                timestampMs = System.currentTimeMillis(),
                inferenceMs = inferenceMs,
            )
            onFrame(frame)
        } catch (t: Throwable) {
            // A bad frame must never stop the session or disable the screen's buttons.
            Log.i(TAG, "Frame failed: ${t.message}")
            main.post { onError(t) }
        } finally {
            image.close()
        }
    }

    private fun upright(image: ImageProxy): Bitmap {
        val raw = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) return raw
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    private fun detect(bitmap: Bitmap): List<Detection> {
        if (detectorFailed) return emptyList()
        val built = detector ?: try {
            val context = appContext ?: return emptyList()
            DetectorFactory(context).create(DetectorPlan.modelFile(settings.model), settings.compute).also { detector = it }
        } catch (t: Throwable) {
            detectorFailed = true
            main.post { onError(t) }
            return emptyList()
        }
        val result = built.detector.detect(BitmapImageBuilder(bitmap).build())
        val raw = result.detections().mapNotNull { d ->
            val category = d.categories().firstOrNull() ?: return@mapNotNull null
            val r = d.boundingBox()
            Detection(
                category.categoryName(),
                category.score(),
                FrameMath.normalizeBox(r.left, r.top, r.right, r.bottom, bitmap.width, bitmap.height),
            )
        }
        val floor = options.minScore
        return if (floor != null) DetectionFilter.keepAtLeast(raw, floor) else DetectionFilter.keep(raw, settings.minScore)
    }

    companion object {
        const val DEFAULT_HFOV_DEG = 65f
        private const val ANALYSIS_WIDTH = 640
        private const val ANALYSIS_HEIGHT = 480
        private const val TAG = "Nungil"
    }
}
```

- [ ] **Step 8: Put a temporary smoke screen in `ScanFragment` (A11 replaces this whole file)**

`app/src/main/java/com/nungil/scan/ScanFragment.kt` (temporary)

```kotlin
package com.nungil.scan

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import com.nungil.contract.app.VisionFrame

/** TEMPORARY smoke check for CameraSession (task A3). Task A11 replaces this whole file. */
class ScanFragment : Fragment() {
    private var camera: CameraSession? = null
    private var frames = 0
    private lateinit var preview: PreviewView
    private lateinit var status: TextView

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = FrameLayout(requireContext())
        preview = PreviewView(requireContext())
        status = TextView(requireContext()).apply { textSize = 20f; setPadding(32, 32, 32, 32) }
        root.addView(preview)
        root.addView(status)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun start() {
        camera = CameraSession(this, preview, CameraSession.Options(keepBitmap = true), ::onFrame) { e ->
            Log.i("Nungil", "Camera error: ${e.message}")
        }.also { it.start() }
    }

    private fun onFrame(frame: VisionFrame) {
        frames++
        if (frames % 30 != 0) return
        val text = "frames=$frames heading=${frame.headingDeg} fov=${frame.hfovDeg} " +
            "size=${frame.imageWidth}x${frame.imageHeight} ${frame.detections.map { it.label }}"
        Log.i("Nungil", text)
        view?.post { status.text = text }
    }

    override fun onDestroyView() {
        camera?.stop()
        camera = null
        super.onDestroyView()
    }
}
```

Run: `.\gradlew.bat installDebug` (without `-PskipModels`, so the models are in the APK)
Expected: `BUILD SUCCESSFUL` and the app installed.

- [ ] **Step 9: Check on the phone**

```powershell
adb logcat -c; adb logcat -v time -s Nungil:V
```

Open the app → "Full scan" → allow the camera, then point the phone at a chair and a laptop. Expected:
- the preview is upright, and the status text updates about every 30 frames with `size=480x640`, a heading between 0 and 360 that changes as you turn, `fov` between 50 and 80, and labels such as `[chair, laptop]`;
- logcat shows one line `Detector efficientdet-lite2.tflite running on GPU` (or `… failed its trial on GPU, falling back` followed by `… running on CPU`), then `Inference N ms average on GPU (efficientdet-lite2.tflite)` every 30 frames. **Write the number down**: it goes on the architecture slide.
- Press Back: no crash, and the camera light turns off.

- [ ] **Step 10: Commit and open a pull request, then tell Y**

```powershell
git add app/src/main/java/com/nungil/core/scan/FrameMath.kt app/src/main/java/com/nungil/core/scan/FovMath.kt app/src/main/java/com/nungil/core/scan/HeadingFilter.kt app/src/main/java/com/nungil/scan/HeadingProvider.kt app/src/main/java/com/nungil/scan/FovReader.kt app/src/main/java/com/nungil/scan/CameraSession.kt app/src/main/java/com/nungil/scan/ScanFragment.kt app/src/test/java/com/nungil/core/scan/CameraMathTest.kt
git commit -m "Stream camera frames with detections, heading and field of view"
git push -u origin a/A3-camera-session
```

After merging, post in the team chat: "CameraSession is live on main (A3). Frames arrive on the analysis thread; keepBitmap = true gives upright ARGB bitmaps."

---

### Task A4: Box overlay

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/OverlayMath.kt`
- Modify: `app/src/main/java/com/nungil/scan/OverlayView.kt` (replace the stub body; API unchanged)
- Test: `app/src/test/java/com/nungil/core/scan/OverlayMathTest.kt`

**Interfaces:**
- Consumes: `Box`; theme attributes `R.attr.ngPrimary`, `ngOnPrimary`, `ngFocus`, `ngLine` (I, frozen names).
- Produces (frozen, used by Y): `OverlayView.show(marks: List<Mark>, imageWidth: Int, imageHeight: Int, mirrored: Boolean)`, `OverlayView.clear()`, `data class Mark(box: Box, text: String?, style: Style = NORMAL)`, `enum class Style { NORMAL, TARGET, DIM }`. Main thread only. It assumes the PreviewView uses the default `FILL_CENTER` scale type.
- Also produces: `object OverlayMath { data class ViewRect(…); fun toView(box: Box, imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int, mirrored: Boolean): ViewRect }`.

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A4-overlay
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/nungil/core/scan/OverlayMathTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayMathTest {
    private val eps = 0.01f
    private val full = Box(0f, 0f, 1f, 1f)

    @Test fun sameAspectFillsTheView() {
        val r = OverlayMath.toView(full, 480, 640, 960, 1280, mirrored = false)
        assertEquals(OverlayMath.ViewRect(0f, 0f, 960f, 1280f), r)
    }

    @Test fun tallerViewCropsTheSides() {
        // 3:4 image in a 1000 x 2000 view: scale = max(1000/480, 2000/640) = 3.125 -> 1500 x 2000, 250 px cut each side.
        val r = OverlayMath.toView(full, 480, 640, 1000, 2000, mirrored = false)
        assertEquals(-250f, r.left, eps)
        assertEquals(1250f, r.right, eps)
        assertEquals(0f, r.top, eps)
        assertEquals(2000f, r.bottom, eps)
    }

    @Test fun centreBoxStaysCentred() {
        val r = OverlayMath.toView(Box(0.4f, 0.4f, 0.6f, 0.6f), 480, 640, 1000, 2000, mirrored = false)
        assertEquals(500f, (r.left + r.right) / 2f, eps)
        assertEquals(1000f, (r.top + r.bottom) / 2f, eps)
    }

    @Test fun mirroredFlipsLeftAndRight() {
        val r = OverlayMath.toView(Box(0f, 0f, 0.25f, 1f), 480, 640, 480, 640, mirrored = true)
        assertEquals(360f, r.left, eps)
        assertEquals(480f, r.right, eps)
    }
}
```

- [ ] **Step 3: Run it and see it fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.OverlayMathTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'OverlayMath'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/nungil/core/scan/OverlayMath.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import kotlin.math.max

/** Where a normalised box lands on screen when the picture is shown like PreviewView's FILL_CENTER. */
object OverlayMath {
    data class ViewRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * FILL_CENTER scales the [imageWidth] x [imageHeight] picture by max(viewW / imageW, viewH / imageH), so it
     * covers the view, and centres it; the overflow is cropped equally on both sides. [mirrored] flips the box
     * left-right, because the front camera's preview is mirrored while the analysed frame is not.
     */
    fun toView(box: Box, imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int, mirrored: Boolean): ViewRect {
        val scale = max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        val shownW = imageWidth * scale
        val shownH = imageHeight * scale
        val dx = (viewWidth - shownW) / 2f
        val dy = (viewHeight - shownH) / 2f
        val left = if (mirrored) 1f - box.right else box.left
        val right = if (mirrored) 1f - box.left else box.right
        return ViewRect(dx + left * shownW, dy + box.top * shownH, dx + right * shownW, dy + box.bottom * shownH)
    }
}
```

`app/src/main/java/com/nungil/scan/OverlayView.kt`

```kotlin
package com.nungil.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import com.nungil.R
import com.nungil.contract.Box
import com.nungil.core.scan.OverlayMath

/**
 * Draws boxes over a PreviewView that uses the default FILL_CENTER scale type.
 * Call [show] and [clear] on the main thread.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Style { NORMAL, TARGET, DIM }

    /** [text] null draws the box without a label. */
    data class Mark(val box: Box, val text: String?, val style: Style = Style.NORMAL)

    private var marks: List<Mark> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var mirrored = false

    private val density = resources.displayMetrics.density
    private val corner = 12f * density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val pill = RectF()

    init {
        // Boxes are decoration for sighted helpers; everything they show is also spoken.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** [imageWidth] x [imageHeight] is the upright analysed image; [mirrored] for the front camera. */
    fun show(marks: List<Mark>, imageWidth: Int, imageHeight: Int, mirrored: Boolean) {
        this.marks = marks
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.mirrored = mirrored
        invalidate()
    }

    fun clear() {
        marks = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth <= 0 || imageHeight <= 0 || width == 0 || height == 0) return
        val primary = MaterialColors.getColor(this, R.attr.ngPrimary)
        val onPrimary = MaterialColors.getColor(this, R.attr.ngOnPrimary)
        val focus = MaterialColors.getColor(this, R.attr.ngFocus)
        val line = MaterialColors.getColor(this, R.attr.ngLine)
        for (mark in marks) {
            val r = OverlayMath.toView(mark.box, imageWidth, imageHeight, width, height, mirrored)
            rect.set(r.left, r.top, r.right, r.bottom)
            val color = when (mark.style) {
                Style.NORMAL -> primary
                Style.TARGET -> focus
                Style.DIM -> line
            }
            if (mark.style == Style.TARGET) {
                fillPaint.color = Color.argb(TARGET_FILL_ALPHA, Color.red(focus), Color.green(focus), Color.blue(focus))
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
            }
            strokePaint.color = color
            strokePaint.strokeWidth = (if (mark.style == Style.TARGET) 6f else 3f) * density
            canvas.drawRoundRect(rect, corner, corner, strokePaint)
            val text = mark.text ?: continue
            val padding = 8f * density
            val textWidth = textPaint.measureText(text)
            val pillHeight = textPaint.textSize + padding * 2
            val top = (rect.top - pillHeight).coerceAtLeast(0f)
            pill.set(rect.left, top, rect.left + textWidth + padding * 2, top + pillHeight)
            pillPaint.color = color
            canvas.drawRoundRect(pill, pillHeight / 2, pillHeight / 2, pillPaint)
            textPaint.color = if (mark.style == Style.DIM) primary else onPrimary
            canvas.drawText(text, pill.left + padding, pill.bottom - padding - textPaint.descent() / 2, textPaint)
        }
    }

    private companion object {
        const val TARGET_FILL_ALPHA = 0x40
    }
}
```

- [ ] **Step 5: Run it and see it pass; build**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.OverlayMathTest" assembleDebug -PskipModels`
Expected: `BUILD SUCCESSFUL`, 4 tests, 0 failures. (On the phone, the boxes are checked in A11 Step 9.)

- [ ] **Step 6: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/OverlayMath.kt app/src/main/java/com/nungil/scan/OverlayView.kt app/src/test/java/com/nungil/core/scan/OverlayMathTest.kt
git commit -m "Draw labelled boxes that line up with the camera preview"
git push -u origin a/A4-overlay
```

---

### Task A5: Coverage of a 360° turn

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/CoverageTracker.kt`
- Test: `app/src/test/java/com/nungil/core/scan/CoverageTrackerTest.kt`

**Interfaces:**
- Consumes: `AngleMath` (A1).
- Produces: `class CoverageTracker(binCount: Int = 36) { fun mark(relHeading: Float); fun markArc(from: Float, to: Float); fun percent(): Int; fun isComplete(): Boolean; fun bins(): BooleanArray }` with `BIN_COUNT = 36`, `MAX_ARC_DEG = 45f`, `STEP_DEG = 5f`. `bins()` is what I's `CoverageRingView.setCoverage(percent, bins)` expects: 36 slices, index 0 = the start heading.

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A5-coverage
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/nungil/core/scan/CoverageTrackerTest.kt`

```kotlin
package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTrackerTest {
    @Test fun tunedNumbers() {
        assertEquals(36, CoverageTracker.BIN_COUNT)
        assertEquals(45f, CoverageTracker.MAX_ARC_DEG)
        assertEquals(5f, CoverageTracker.STEP_DEG)
    }

    @Test fun startsEmpty() {
        val c = CoverageTracker()
        assertEquals(0, c.percent())
        assertFalse(c.isComplete())
    }

    @Test fun markFillsOneTenDegreeBin() {
        val c = CoverageTracker()
        c.mark(15f)
        assertTrue(c.bins()[1])
        assertEquals(1, c.bins().count { it })
    }

    @Test fun negativeHeadingsWrap() {
        val c = CoverageTracker()
        c.mark(-5f)
        assertTrue(c.bins()[35])
    }

    @Test fun shortArcFillsEveryBinBetween() {
        val c = CoverageTracker()
        c.markArc(0f, 40f)
        assertEquals(listOf(0, 1, 2, 3, 4), c.bins().indices.filter { c.bins()[it] })
    }

    @Test fun arcBackwardsAcrossZeroFillsTheShortWay() {
        val c = CoverageTracker()
        c.markArc(10f, -20f)
        assertEquals(listOf(0, 1, 34, 35), c.bins().indices.filter { c.bins()[it] })
    }

    @Test fun jumpLargerThan45MarksOnlyTheEnds() {
        val c = CoverageTracker()
        c.markArc(0f, 90f)
        assertEquals(listOf(0, 9), c.bins().indices.filter { c.bins()[it] })
    }

    @Test fun fullTurnIsComplete() {
        val c = CoverageTracker()
        var h = 0f
        while (h < 360f) {
            c.markArc(h, h + 30f)
            h += 30f
        }
        assertTrue(c.isComplete())
        assertEquals(100, c.percent())
    }

    @Test fun halfTurnIsFiftyPercent() {
        val c = CoverageTracker()
        for (i in 0 until 18) c.mark(i * 10f + 1f)
        assertEquals(50, c.percent())
    }

    @Test fun binsIsACopy() {
        val c = CoverageTracker()
        c.bins()[0] = true
        assertEquals(0, c.percent())
    }
}
```

- [ ] **Step 3: Run it and see it fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.CoverageTrackerTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'CoverageTracker'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/nungil/core/scan/CoverageTracker.kt`

```kotlin
package com.nungil.core.scan

import kotlin.math.abs

/**
 * Which parts of a 360° turn the camera has seen: [binCount] bins of 360 / binCount degrees,
 * bin 0 starting at the heading where the scan began. Drives the coverage ring and the auto-stop.
 */
class CoverageTracker(val binCount: Int = BIN_COUNT) {
    private val seen = BooleanArray(binCount)
    private val binDeg = 360f / binCount

    /** Marks the bin that holds [relHeading] (degrees from the start heading). */
    fun mark(relHeading: Float) {
        seen[binOf(relHeading)] = true
    }

    /**
     * Marks both ends and, when the turn between them is at most [MAX_ARC_DEG], every [STEP_DEG] in between,
     * so a normal turn leaves no holes but a jump (a fast spin or a sensor glitch) cannot claim the room.
     */
    fun markArc(from: Float, to: Float) {
        mark(from)
        mark(to)
        val turn = AngleMath.diff(to, from)
        if (abs(turn) > MAX_ARC_DEG) return
        val direction = if (turn >= 0f) 1f else -1f
        var step = STEP_DEG
        while (step < abs(turn)) {
            mark(from + direction * step)
            step += STEP_DEG
        }
    }

    fun percent(): Int = seen.count { it } * 100 / binCount

    fun isComplete(): Boolean = seen.all { it }

    /** Copy of the bins for the coverage ring; index 0 = the start heading. */
    fun bins(): BooleanArray = seen.copyOf()

    private fun binOf(relHeading: Float): Int = (AngleMath.normalize(relHeading) / binDeg).toInt().coerceIn(0, binCount - 1)

    companion object {
        const val BIN_COUNT = 36
        const val MAX_ARC_DEG = 45f
        const val STEP_DEG = 5f
    }
}
```

- [ ] **Step 5: Run it and see it pass**

Run the same command. Expected: `BUILD SUCCESSFUL`, 10 tests, 0 failures.

- [ ] **Step 6: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/CoverageTracker.kt app/src/test/java/com/nungil/core/scan/CoverageTrackerTest.kt
git commit -m "Track how much of the room a full scan has seen"
git push -u origin a/A5-coverage
```

---

### Task A6: Deduplication, sticky names and named people

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/ColorName.kt`
- Create: `app/src/main/java/com/nungil/core/scan/ObjectSummary.kt`
- Create: `app/src/main/java/com/nungil/core/scan/ObjectClusterer.kt`
- Create: `app/src/main/java/com/nungil/core/scan/StickyNames.kt`
- Create: `app/src/main/java/com/nungil/core/scan/NamedPeople.kt`
- Test: `app/src/test/java/com/nungil/core/scan/ObjectClustererTest.kt`
- Test: `app/src/test/java/com/nungil/core/scan/StickyNamesTest.kt`
- Test: `app/src/test/java/com/nungil/core/scan/NamedPeopleTest.kt`

**Interfaces:**
- Consumes: `AngleMath`, `BoxGeometry.iou` (A1).
- Produces:
  - `enum class ColorName(val en: String, val koAdjective: String, val koNoun: String) { RED … GRAY; val isChromatic: Boolean }`
  - `data class FrameDetection(label: String, angle: Float, color: ColorName?, isName: Boolean = false, wasPerson: Boolean = false)`
  - `data class ObjectSummary(label: String, count: Int, color: ColorName?, angle: Float, isName: Boolean = false, wasPerson: Boolean = false)`
  - `class ObjectClusterer(mergeDeg = 20f, confirmFrames = 3) { fun addFrame(detections: List<FrameDetection>): List<ObjectSummary> /* newly confirmed */; fun confirmed(): List<ObjectSummary> }`
  - `class StickyNames(confirmHits = 2, forgetAfterFrames = 15, minIou = 0.3f) { data class Sticky(name: String, isPerson: Boolean); fun recognized(box: Box, name: String, isPerson: Boolean); fun apply(boxes: List<Box>): List<Sticky?> }`
  - `object NamedPeople { const val SHADOW_DEG = 20f; fun dropShadowedPersons(objects: List<ObjectSummary>, shadowDeg: Float = SHADOW_DEG): List<ObjectSummary> }`

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A6-clusterer
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/nungil/core/scan/ObjectClustererTest.kt`

```kotlin
package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectClustererTest {
    private fun chair(angle: Float, color: ColorName? = null) = FrameDetection("chair", angle, color)

    @Test fun tunedNumbers() {
        assertEquals(20f, ObjectClusterer.MERGE_DEG)
        assertEquals(3, ObjectClusterer.CONFIRM_FRAMES)
        assertEquals(2, ObjectClusterer.MIN_COLOR_VOTES)
    }

    @Test fun countIsTheMaximumInOneFrameNeverASum() {
        val c = ObjectClusterer()
        repeat(100) { c.addFrame(listOf(chair(0f), chair(5f), chair(10f))) }
        val all = c.confirmed()
        assertEquals(1, all.size)
        assertEquals(3, all[0].count)
    }

    @Test fun countGrowsWhenOneFrameSeesMore() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f)))
        c.addFrame(listOf(chair(0f), chair(4f)))
        c.addFrame(listOf(chair(0f)))
        assertEquals(2, c.confirmed().single().count)
    }

    @Test fun unconfirmedClustersAreNeverReturned() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f)))
        c.addFrame(listOf(chair(0f)))
        assertTrue(c.confirmed().isEmpty())
    }

    @Test fun addFrameReportsTheFrameThatConfirms() {
        val c = ObjectClusterer()
        assertTrue(c.addFrame(listOf(chair(0f))).isEmpty())
        assertTrue(c.addFrame(listOf(chair(0f))).isEmpty())
        assertEquals("chair", c.addFrame(listOf(chair(0f))).single().label)
        assertTrue(c.addFrame(listOf(chair(0f))).isEmpty())
    }

    @Test fun farApartAnglesMakeTwoClusters() {
        val c = ObjectClusterer()
        repeat(3) { c.addFrame(listOf(chair(0f), chair(90f))) }
        assertEquals(2, c.confirmed().size)
    }

    @Test fun mergeAcrossNorth() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(355f)))
        c.addFrame(listOf(chair(8f)))
        c.addFrame(listOf(chair(2f)))
        assertEquals(1, c.confirmed().size)
    }

    @Test fun differentLabelsNeverMerge() {
        val c = ObjectClusterer()
        repeat(3) { c.addFrame(listOf(chair(0f), FrameDetection("laptop", 0f, null))) }
        assertEquals(setOf("chair", "laptop"), c.confirmed().map { it.label }.toSet())
    }

    @Test fun colourFlickerDoesNotSplitTheCluster() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        c.addFrame(listOf(chair(0f, ColorName.GRAY)))
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        val only = c.confirmed().single()
        assertEquals(ColorName.BLUE, only.color)
    }

    @Test fun oneVoteIsNotEnoughForAColour() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        c.addFrame(listOf(chair(0f)))
        c.addFrame(listOf(chair(0f)))
        assertNull(c.confirmed().single().color)
    }

    @Test fun aTieGivesNoColour() {
        val c = ObjectClusterer()
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        c.addFrame(listOf(chair(0f, ColorName.RED)))
        c.addFrame(listOf(chair(0f, ColorName.BLUE)))
        c.addFrame(listOf(chair(0f, ColorName.RED)))
        assertNull(c.confirmed().single().color)
    }

    @Test fun namedPersonKeepsItsFlags() {
        val c = ObjectClusterer()
        repeat(3) { c.addFrame(listOf(FrameDetection("Ali", 10f, null, isName = true, wasPerson = true))) }
        val ali = c.confirmed().single()
        assertTrue(ali.isName)
        assertTrue(ali.wasPerson)
    }
}
```

`app/src/test/java/com/nungil/core/scan/StickyNamesTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickyNamesTest {
    private val face = Box(0.4f, 0.2f, 0.6f, 0.8f)
    private val moved = Box(0.42f, 0.2f, 0.62f, 0.8f)
    private val elsewhere = Box(0f, 0f, 0.1f, 0.1f)

    @Test fun tunedNumbers() {
        assertEquals(2, StickyNames.CONFIRM_HITS)
        assertEquals(15, StickyNames.FORGET_AFTER_FRAMES)
        assertEquals(0.3f, StickyNames.MIN_IOU)
    }

    @Test fun oneHitIsNotEnough() {
        val s = StickyNames()
        s.recognized(face, "Ali", isPerson = true)
        assertNull(s.apply(listOf(face)).single())
    }

    @Test fun twoHitsMakeTheNameStickToTheMovingBox() {
        val s = StickyNames()
        s.recognized(face, "Ali", isPerson = true)
        s.recognized(face, "Ali", isPerson = true)
        assertEquals(StickyNames.Sticky("Ali", true), s.apply(listOf(moved)).single())
        assertEquals(StickyNames.Sticky("Ali", true), s.apply(listOf(moved)).single())
    }

    @Test fun otherBoxesStayUnnamed() {
        val s = StickyNames()
        repeat(2) { s.recognized(face, "Ali", isPerson = true) }
        assertEquals(listOf(StickyNames.Sticky("Ali", true), null), s.apply(listOf(face, elsewhere)))
    }

    @Test fun aDifferentNameRestartsTheCount() {
        val s = StickyNames()
        repeat(2) { s.recognized(face, "Ali", isPerson = true) }
        s.recognized(face, "Mina", isPerson = true)
        assertNull(s.apply(listOf(face)).single())
    }

    @Test fun trackIsForgottenAfter15FramesUnseen() {
        val s = StickyNames()
        repeat(2) { s.recognized(face, "Ali", isPerson = true) }
        repeat(16) { s.apply(emptyList()) }
        assertNull(s.apply(listOf(face)).single())
    }
}
```

`app/src/test/java/com/nungil/core/scan/NamedPeopleTest.kt`

```kotlin
package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class NamedPeopleTest {
    private val ali = ObjectSummary("Ali", 1, null, 10f, isName = true, wasPerson = true)

    @Test fun shadowDistanceIs20Degrees() = assertEquals(20f, NamedPeople.SHADOW_DEG)

    @Test fun plainPersonNextToANamedPersonIsDropped() {
        val out = NamedPeople.dropShadowedPersons(listOf(ali, ObjectSummary("person", 1, null, 25f)))
        assertEquals(listOf("Ali"), out.map { it.label })
    }

    @Test fun plainPersonFarAwayStays() {
        val out = NamedPeople.dropShadowedPersons(listOf(ali, ObjectSummary("person", 1, null, 40f)))
        assertEquals(listOf("Ali", "person"), out.map { it.label })
    }

    @Test fun aNamedItemDoesNotHideAPerson() {
        val bag = ObjectSummary("My bag", 1, null, 10f, isName = true, wasPerson = false)
        val out = NamedPeople.dropShadowedPersons(listOf(bag, ObjectSummary("person", 1, null, 12f)))
        assertEquals(listOf("My bag", "person"), out.map { it.label })
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.ObjectClustererTest" --tests "com.nungil.core.scan.StickyNamesTest" --tests "com.nungil.core.scan.NamedPeopleTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'FrameDetection'`, `'ObjectClusterer'`, `'StickyNames'`, `'ObjectSummary'` and `'NamedPeople'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/nungil/core/scan/ColorName.kt`

```kotlin
package com.nungil.core.scan

/**
 * The 11 basic colour names, with the words each language needs:
 * English, the Korean form before a noun ("파란 의자") and the Korean noun used in lists ("파란색, 빨간색").
 */
enum class ColorName(val en: String, val koAdjective: String, val koNoun: String) {
    RED("red", "빨간", "빨간색"),
    ORANGE("orange", "주황색", "주황색"),
    YELLOW("yellow", "노란", "노란색"),
    GREEN("green", "초록색", "초록색"),
    BLUE("blue", "파란", "파란색"),
    PURPLE("purple", "보라색", "보라색"),
    PINK("pink", "분홍색", "분홍색"),
    BROWN("brown", "갈색", "갈색"),
    BLACK("black", "검은", "검은색"),
    WHITE("white", "흰", "흰색"),
    GRAY("gray", "회색", "회색");

    /** Black, white and gray are not "colourful"; a colourful name wins with only a 30% share. */
    val isChromatic: Boolean get() = this != BLACK && this != WHITE && this != GRAY
}
```

`app/src/main/java/com/nungil/core/scan/ObjectSummary.kt`

```kotlin
package com.nungil.core.scan

/**
 * One detection in one frame, already turned into a direction.
 * @param label COCO label, or the saved name when [isName] is true.
 * @param angle degrees relative to the scan's start heading.
 * @param wasPerson true when the detector saw a "person" (so a name on it is a person's name).
 */
data class FrameDetection(
    val label: String,
    val angle: Float,
    val color: ColorName?,
    val isName: Boolean = false,
    val wasPerson: Boolean = false,
)

/** A confirmed object (cluster) as the summary speaks it and history stores it. */
data class ObjectSummary(
    val label: String,
    val count: Int,
    val color: ColorName?,
    val angle: Float,
    val isName: Boolean = false,
    val wasPerson: Boolean = false,
)
```

`app/src/main/java/com/nungil/core/scan/ObjectClusterer.kt`

```kotlin
package com.nungil.core.scan

import kotlin.math.abs
import kotlin.math.max

/**
 * Deduplication, the single most important algorithm.
 * A detection joins a cluster when the label matches and the angle is within [mergeDeg].
 * A cluster's count is the MAXIMUM seen in one frame, never a sum across frames: summing turns three chairs
 * into three hundred over a hundred frames. A cluster is spoken only after [confirmFrames] frames.
 * Colour is a majority vote inside the cluster and is not part of the key, so colour flicker cannot split
 * one chair into two.
 */
class ObjectClusterer(val mergeDeg: Float = MERGE_DEG, val confirmFrames: Int = CONFIRM_FRAMES) {

    private class Cluster(val label: String, var angle: Float, val isName: Boolean, var wasPerson: Boolean) {
        var samples = 0
        var framesSeen = 0
        var count = 0
        val colorVotes = LinkedHashMap<ColorName, Int>()

        /** The winning colour: at least [MIN_COLOR_VOTES] votes and strictly more than any other colour. */
        fun color(): ColorName? {
            val sorted = colorVotes.entries.sortedByDescending { it.value }
            val best = sorted.firstOrNull() ?: return null
            if (best.value < MIN_COLOR_VOTES) return null
            val runnerUp = sorted.getOrNull(1)?.value ?: 0
            return if (best.value > runnerUp) best.key else null
        }

        fun summary() = ObjectSummary(label, count, color(), angle, isName, wasPerson)
    }

    private val clusters = mutableListOf<Cluster>()

    /** Adds one frame; returns the clusters that became confirmed with this frame (for live announcements). */
    fun addFrame(detections: List<FrameDetection>): List<ObjectSummary> {
        val inThisFrame = LinkedHashMap<Cluster, Int>()
        for (d in detections) {
            val cluster = clusters
                .filter { it.label == d.label && abs(AngleMath.diff(it.angle, d.angle)) <= mergeDeg }
                .minByOrNull { abs(AngleMath.diff(it.angle, d.angle)) }
                ?: Cluster(d.label, d.angle, d.isName, d.wasPerson).also { clusters += it }
            cluster.angle = AngleMath.weightedMean(cluster.angle, cluster.samples, d.angle)
            cluster.samples++
            cluster.wasPerson = cluster.wasPerson || d.wasPerson
            d.color?.let { cluster.colorVotes[it] = (cluster.colorVotes[it] ?: 0) + 1 }
            inThisFrame[cluster] = (inThisFrame[cluster] ?: 0) + 1
        }
        val newlyConfirmed = mutableListOf<ObjectSummary>()
        for ((cluster, n) in inThisFrame) {
            cluster.framesSeen++
            cluster.count = max(cluster.count, n)
            if (cluster.framesSeen == confirmFrames) newlyConfirmed += cluster.summary()
        }
        return newlyConfirmed
    }

    /** Every cluster seen in at least [confirmFrames] frames. Unconfirmed clusters are never spoken. */
    fun confirmed(): List<ObjectSummary> = clusters.filter { it.framesSeen >= confirmFrames }.map { it.summary() }

    companion object {
        const val MERGE_DEG = 20f
        const val CONFIRM_FRAMES = 3
        const val MIN_COLOR_VOTES = 2
    }
}
```

`app/src/main/java/com/nungil/core/scan/StickyNames.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box

/**
 * Once a tracked box has been recognised as a saved name it keeps that name across frames instead of
 * flickering back to "person". Not thread-safe: call it from one thread (the analysis thread).
 */
class StickyNames(
    val confirmHits: Int = CONFIRM_HITS,
    val forgetAfterFrames: Int = FORGET_AFTER_FRAMES,
    val minIou: Float = MIN_IOU,
) {
    data class Sticky(val name: String, val isPerson: Boolean)

    private class Track(var box: Box, var name: String, var isPerson: Boolean, var hits: Int, var lastSeen: Long)

    private val tracks = mutableListOf<Track>()
    private var frame = 0L

    /** A recogniser said [box] is [name]. Two agreeing hits on overlapping boxes make the name stick. */
    fun recognized(box: Box, name: String, isPerson: Boolean) {
        val track = bestTrack(box)
        when {
            track == null -> tracks += Track(box, name, isPerson, 1, frame)
            track.name == name -> {
                track.hits++
                track.box = box
                track.lastSeen = frame
            }
            else -> {
                track.name = name
                track.isPerson = isPerson
                track.hits = 1
                track.box = box
                track.lastSeen = frame
            }
        }
    }

    /** Call once per frame with that frame's boxes; returns the sticky name for each box, or null. */
    fun apply(boxes: List<Box>): List<Sticky?> {
        frame++
        tracks.removeAll { frame - it.lastSeen > forgetAfterFrames }
        return boxes.map { box ->
            val track = bestTrack(box)
            if (track == null) {
                null
            } else {
                track.box = box
                track.lastSeen = frame
                if (track.hits >= confirmHits) Sticky(track.name, track.isPerson) else null
            }
        }
    }

    private fun bestTrack(box: Box): Track? =
        tracks.map { it to BoxGeometry.iou(it.box, box) }
            .filter { it.second >= minIou }
            .maxByOrNull { it.second }
            ?.first

    companion object {
        const val CONFIRM_HITS = 2
        const val FORGET_AFTER_FRAMES = 15
        const val MIN_IOU = 0.3f
    }
}
```

`app/src/main/java/com/nungil/core/scan/NamedPeople.kt`

```kotlin
package com.nungil.core.scan

import kotlin.math.abs

/** Nobody is announced twice: a plain "person" next to a recognised person is that same person. */
object NamedPeople {
    const val SHADOW_DEG = 20f

    fun dropShadowedPersons(objects: List<ObjectSummary>, shadowDeg: Float = SHADOW_DEG): List<ObjectSummary> {
        val named = objects.filter { it.isName && it.wasPerson }
        return objects.filterNot { o ->
            o.label == "person" && !o.isName && named.any { abs(AngleMath.diff(it.angle, o.angle)) <= shadowDeg }
        }
    }
}
```

- [ ] **Step 5: Run them and see them pass**

Run the same command. Expected: `BUILD SUCCESSFUL`, 22 tests (12 + 6 + 4), 0 failures.

- [ ] **Step 6: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/ColorName.kt app/src/main/java/com/nungil/core/scan/ObjectSummary.kt app/src/main/java/com/nungil/core/scan/ObjectClusterer.kt app/src/main/java/com/nungil/core/scan/StickyNames.kt app/src/main/java/com/nungil/core/scan/NamedPeople.kt app/src/test/java/com/nungil/core/scan/ObjectClustererTest.kt app/src/test/java/com/nungil/core/scan/StickyNamesTest.kt app/src/test/java/com/nungil/core/scan/NamedPeopleTest.kt
git commit -m "Count each object once, however many frames see it"
git push -u origin a/A6-clusterer
```

---

### Task A7: Colour naming

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/ColorMapper.kt`
- Create: `app/src/main/java/com/nungil/core/scan/WhiteBalance.kt`
- Create: `app/src/main/java/com/nungil/core/scan/ColorVote.kt` (also holds `ColorPolicy`)
- Create: `app/src/main/java/com/nungil/scan/ColorSampler.kt`
- Test: `app/src/test/java/com/nungil/core/scan/ColorMapperTest.kt`
- Test: `app/src/test/java/com/nungil/core/scan/ColorVoteTest.kt`

**Interfaces:**
- Consumes: `ColorName` (A6), `Box`.
- Produces:
  - `object ColorMapper { fun hsv(r: Int, g: Int, b: Int): FloatArray; fun nameFromHsv(h: Float, s: Float, v: Float, frameIsDark: Boolean): ColorName? }`
  - `object WhiteBalance { const val MIN_GAIN = 0.6f; const val MAX_GAIN = 1.6f; fun gains(pixels: IntArray): FloatArray; fun apply(pixel: Int, gains: FloatArray): Int }`
  - `object ColorVote { SAMPLE_GRID = 24; CENTER_SHARE = 0.5f; MIN_CHROMATIC_SHARE = 0.3f; FRAME_GRID = 32; DARK_FRAME_V = 0.25f; fun sampleRegion(box: Box): Box; fun gridPoints(region: Box, width: Int, height: Int, grid: Int = SAMPLE_GRID): IntArray; fun isDark(framePixels: IntArray): Boolean; fun vote(pixels: IntArray, gains: FloatArray, frameIsDark: Boolean): ColorName? }`
  - `object ColorPolicy { fun hasColor(label: String): Boolean }`
  - `object ColorSampler { class FrameLight(gains, isDark); fun frameLight(bitmap: Bitmap): FrameLight; fun colorOf(bitmap: Bitmap, box: Box, light: FrameLight): ColorName? }` (worker thread)

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A7-colour
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/nungil/core/scan/ColorMapperTest.kt`

```kotlin
package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMapperTest {
    private fun name(h: Float, s: Float, v: Float) = ColorMapper.nameFromHsv(h, s, v, frameIsDark = false)

    @Test fun darkFrameGivesNoColourAtAll() = assertNull(ColorMapper.nameFromHsv(220f, 0.9f, 0.9f, frameIsDark = true))

    @Test fun lowValueIsBlack() {
        assertEquals(ColorName.BLACK, name(220f, 0.9f, 0.19f))
        assertEquals(ColorName.BLUE, name(220f, 0.9f, 0.2f))
    }

    @Test fun lowSaturationIsWhiteOrGray() {
        assertEquals(ColorName.WHITE, name(0f, 0.14f, 0.81f))
        assertEquals(ColorName.GRAY, name(0f, 0.14f, 0.8f))
        assertEquals(ColorName.RED, name(0f, 0.15f, 0.5f))
    }

    @Test fun hueBoundaries() {
        assertEquals(ColorName.RED, name(14.9f, 0.9f, 0.9f))
        assertEquals(ColorName.RED, name(345f, 0.9f, 0.9f))
        assertEquals(ColorName.ORANGE, name(15f, 0.9f, 0.9f))
        assertEquals(ColorName.YELLOW, name(45f, 0.9f, 0.9f))
        assertEquals(ColorName.GREEN, name(70f, 0.9f, 0.9f))
        assertEquals(ColorName.BLUE, name(170f, 0.9f, 0.9f))
        assertEquals(ColorName.PURPLE, name(260f, 0.9f, 0.9f))
        assertEquals(ColorName.PINK, name(290f, 0.9f, 0.9f))
        assertEquals(ColorName.PINK, name(344.9f, 0.9f, 0.9f))
    }

    @Test fun darkOrangeIsBrown() {
        assertEquals(ColorName.BROWN, name(30f, 0.8f, 0.59f))
        assertEquals(ColorName.ORANGE, name(30f, 0.8f, 0.6f))
    }

    @Test fun lightRedIsPink() {
        assertEquals(ColorName.PINK, name(5f, 0.49f, 0.71f))
        assertEquals(ColorName.RED, name(5f, 0.5f, 0.71f))
        assertEquals(ColorName.RED, name(5f, 0.49f, 0.7f))
    }

    @Test fun hsvConversion() {
        val red = ColorMapper.hsv(255, 0, 0)
        assertEquals(0f, red[0], 0.01f)
        assertEquals(1f, red[1], 0.01f)
        assertEquals(1f, red[2], 0.01f)
        assertEquals(240f, ColorMapper.hsv(0, 0, 255)[0], 0.01f)
        assertEquals(120f, ColorMapper.hsv(0, 255, 0)[0], 0.01f)
        assertEquals(330f, ColorMapper.hsv(255, 0, 128)[0], 0.5f)
        assertEquals(0f, ColorMapper.hsv(128, 128, 128)[1], 0.01f)
    }

    @Test fun elevenNamesInBothLanguages() {
        assertEquals(11, ColorName.entries.size)
        assertEquals("파란", ColorName.BLUE.koAdjective)
        assertEquals("파란색", ColorName.BLUE.koNoun)
        assertEquals("주황색", ColorName.ORANGE.koAdjective)
        assertEquals("gray", ColorName.GRAY.en)
        assertTrue(ColorName.BROWN.isChromatic)
        assertFalse(ColorName.GRAY.isChromatic)
    }

    @Test fun peopleNeverGetAColour() {
        assertFalse(ColorPolicy.hasColor("person"))
        assertTrue(ColorPolicy.hasColor("chair"))
    }
}
```

`app/src/test/java/com/nungil/core/scan/ColorVoteTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorVoteTest {
    private val neutral = floatArrayOf(1f, 1f, 1f)
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun pixels(vararg groups: Pair<Int, Int>): IntArray = groups.flatMap { (color, n) -> List(n) { color } }.toIntArray()

    private val blue = rgb(30, 60, 200)
    private val gray = rgb(120, 120, 120)

    @Test fun tunedNumbers() {
        assertEquals(24, ColorVote.SAMPLE_GRID)
        assertEquals(0.5f, ColorVote.CENTER_SHARE)
        assertEquals(0.3f, ColorVote.MIN_CHROMATIC_SHARE)
        assertEquals(0.6f, WhiteBalance.MIN_GAIN)
        assertEquals(1.6f, WhiteBalance.MAX_GAIN)
    }

    @Test fun colourfulNameWinsWithThirtyPercent() =
        assertEquals(ColorName.BLUE, ColorVote.vote(pixels(blue to 30, gray to 70), neutral, frameIsDark = false))

    @Test fun belowThirtyPercentTheGrayWins() =
        assertEquals(ColorName.GRAY, ColorVote.vote(pixels(blue to 29, gray to 71), neutral, frameIsDark = false))

    @Test fun darkFrameGivesNull() =
        assertNull(ColorVote.vote(pixels(blue to 100), neutral, frameIsDark = true))

    @Test fun emptyGivesNull() = assertNull(ColorVote.vote(IntArray(0), neutral, frameIsDark = false))

    @Test fun warmLampIsCorrected() {
        // A gray chair under a warm lamp looks orange; the frame is orange-tinted everywhere.
        val warmGray = rgb(170, 130, 90)
        val frame = pixels(warmGray to 100)
        val gains = WhiteBalance.gains(frame)
        assertEquals(ColorName.ORANGE, ColorVote.vote(frame, neutral, frameIsDark = false))
        assertEquals(ColorName.GRAY, ColorVote.vote(frame, gains, frameIsDark = false))
    }

    @Test fun gainsAreClamped() {
        val gains = WhiteBalance.gains(pixels(rgb(250, 10, 10) to 10))
        assertEquals(0.6f, gains[0], 0.001f)
        assertEquals(1.6f, gains[1], 0.001f)
    }

    @Test fun blackFrameHasNeutralGainsAndIsDark() {
        val frame = pixels(rgb(0, 0, 0) to 10)
        assertEquals(1f, WhiteBalance.gains(frame)[0], 0.001f)
        assertTrue(ColorVote.isDark(frame))
    }

    @Test fun darknessThreshold() {
        assertEquals(0.25f, ColorVote.DARK_FRAME_V)
        assertTrue(ColorVote.isDark(pixels(rgb(60, 60, 60) to 10)))
        assertFalse(ColorVote.isDark(pixels(rgb(70, 70, 70) to 10)))
    }

    @Test fun sampleRegionIsTheCentreHalf() =
        assertEquals(Box(0.25f, 0.25f, 0.75f, 0.75f), ColorVote.sampleRegion(Box(0f, 0f, 1f, 1f)))

    @Test fun gridPointsCoverTheRegion() {
        val pts = ColorVote.gridPoints(Box(0f, 0f, 1f, 1f), 240, 480)
        assertEquals(24 * 24 * 2, pts.size)
        assertEquals(5, pts[0])
        assertEquals(10, pts[1])
        assertTrue(pts[pts.size - 2] in 234..235)
        assertTrue(pts[pts.size - 1] in 469..470)
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.ColorMapperTest" --tests "com.nungil.core.scan.ColorVoteTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'ColorMapper'`, `'ColorPolicy'`, `'ColorVote'` and `'WhiteBalance'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/nungil/core/scan/ColorMapper.kt`

```kotlin
package com.nungil.core.scan

import kotlin.math.max
import kotlin.math.min

/** HSV rules that give 11 basic colour names (build-guide §8.4). */
object ColorMapper {
    /** HSV of 8-bit RGB: hue 0 until 360, saturation and value 0..1. Pure Kotlin so it runs in unit tests. */
    fun hsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC
        val h = when {
            delta == 0f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        val s = if (maxC == 0f) 0f else delta / maxC
        return floatArrayOf(AngleMath.normalize(h), s, maxC)
    }

    /**
     * v < 0.2 black; s < 0.15 white above v 0.8, else gray; hues: red < 15 or >= 345, orange 15-45 (brown below
     * v 0.6), yellow 45-70, green 70-170, blue 170-260, purple 260-290, pink 290-345; a light red
     * (s < 0.5, v > 0.7) is pink. A dark frame gives no colour at all.
     */
    fun nameFromHsv(h: Float, s: Float, v: Float, frameIsDark: Boolean): ColorName? {
        if (frameIsDark) return null
        if (v < 0.2f) return ColorName.BLACK
        if (s < 0.15f) return if (v > 0.8f) ColorName.WHITE else ColorName.GRAY
        return when {
            h < 15f || h >= 345f -> if (s < 0.5f && v > 0.7f) ColorName.PINK else ColorName.RED
            h < 45f -> if (v < 0.6f) ColorName.BROWN else ColorName.ORANGE
            h < 70f -> ColorName.YELLOW
            h < 170f -> ColorName.GREEN
            h < 260f -> ColorName.BLUE
            h < 290f -> ColorName.PURPLE
            else -> ColorName.PINK
        }
    }
}
```

`app/src/main/java/com/nungil/core/scan/WhiteBalance.kt`

```kotlin
package com.nungil.core.scan

/**
 * Gray-world white balance: scale R, G and B so their frame averages become equal. Warm lamps and shadows
 * otherwise turn everything brown or gray. Gains come from the WHOLE frame; computed on one object they
 * would erase that object's own colour.
 */
object WhiteBalance {
    const val MIN_GAIN = 0.6f
    const val MAX_GAIN = 1.6f

    /** Gains for R, G and B from ARGB [pixels]. An empty or black frame gives neutral gains. */
    fun gains(pixels: IntArray): FloatArray {
        if (pixels.isEmpty()) return floatArrayOf(1f, 1f, 1f)
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val n = pixels.size.toFloat()
        val means = floatArrayOf(r / n, g / n, b / n)
        val gray = (means[0] + means[1] + means[2]) / 3f
        return FloatArray(3) { i ->
            if (means[i] <= 0f) 1f else (gray / means[i]).coerceIn(MIN_GAIN, MAX_GAIN)
        }
    }

    /** One ARGB pixel with the gains applied, each channel clamped to 0..255. */
    fun apply(pixel: Int, gains: FloatArray): Int {
        val r = (((pixel shr 16) and 0xFF) * gains[0]).toInt().coerceIn(0, 255)
        val g = (((pixel shr 8) and 0xFF) * gains[1]).toInt().coerceIn(0, 255)
        val b = ((pixel and 0xFF) * gains[2]).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
```

`app/src/main/java/com/nungil/core/scan/ColorVote.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import kotlin.math.roundToInt

/**
 * Colour of one object: sample a 24x24 grid from the centre 50% of its box, white-balance each pixel with the
 * frame's gains, let every pixel vote for a name, and let a colourful name win once colourful pixels make up
 * at least 30% of the named pixels (a blue chair is mostly shadow and gray seat).
 */
object ColorVote {
    const val SAMPLE_GRID = 24
    const val CENTER_SHARE = 0.5f
    const val MIN_CHROMATIC_SHARE = 0.3f

    /** Grid used over the whole frame for white balance and darkness. */
    const val FRAME_GRID = 32

    /** A frame whose mean brightness (HSV value) is below this is too dark to name colours. */
    const val DARK_FRAME_V = 0.25f

    /** The centre [CENTER_SHARE] of [box]: half its width and half its height, same centre. */
    fun sampleRegion(box: Box): Box {
        val halfW = box.width * CENTER_SHARE / 2f
        val halfH = box.height * CENTER_SHARE / 2f
        return Box(box.centerX - halfW, box.centerY - halfH, box.centerX + halfW, box.centerY + halfH)
    }

    /** Pixel coordinates (x0, y0, x1, y1, …) of a [grid] x [grid] sample over [region] of a [width] x [height] image. */
    fun gridPoints(region: Box, width: Int, height: Int, grid: Int = SAMPLE_GRID): IntArray {
        val out = IntArray(grid * grid * 2)
        var k = 0
        for (row in 0 until grid) {
            val y = ((region.top + (row + 0.5f) * region.height / grid) * height).toInt().coerceIn(0, height - 1)
            for (col in 0 until grid) {
                val x = ((region.left + (col + 0.5f) * region.width / grid) * width).toInt().coerceIn(0, width - 1)
                out[k++] = x
                out[k++] = y
            }
        }
        return out
    }

    /** True when the frame's mean HSV value is below [DARK_FRAME_V]. */
    fun isDark(framePixels: IntArray): Boolean {
        if (framePixels.isEmpty()) return true
        var sum = 0f
        for (p in framePixels) {
            val maxC = maxOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
            sum += maxC / 255f
        }
        return sum / framePixels.size < DARK_FRAME_V
    }

    /** Majority colour of [pixels] after white balance with [gains]; null in a dark frame. */
    fun vote(pixels: IntArray, gains: FloatArray, frameIsDark: Boolean): ColorName? {
        if (frameIsDark || pixels.isEmpty()) return null
        val counts = IntArray(ColorName.entries.size)
        for (p in pixels) {
            val q = WhiteBalance.apply(p, gains)
            val hsv = ColorMapper.hsv((q shr 16) and 0xFF, (q shr 8) and 0xFF, q and 0xFF)
            val name = ColorMapper.nameFromHsv(hsv[0], hsv[1], hsv[2], frameIsDark = false) ?: continue
            counts[name.ordinal]++
        }
        val named = counts.sum()
        if (named == 0) return null
        val chromatic = ColorName.entries.filter { it.isChromatic }
        val chromaticTotal = chromatic.sumOf { counts[it.ordinal] }
        // Whole percents, so exactly 30% counts (0.3f * 100 is 30.000002 in floating point).
        val sharePercent = (MIN_CHROMATIC_SHARE * 100).roundToInt()
        val pool = if (chromaticTotal > 0 && chromaticTotal * 100 >= sharePercent * named) chromatic else ColorName.entries
        return pool.maxByOrNull { counts[it.ordinal] }
    }
}

/** Which labels may carry a colour. People never get one. */
object ColorPolicy {
    fun hasColor(label: String): Boolean = label != "person"
}
```

`app/src/main/java/com/nungil/scan/ColorSampler.kt`

```kotlin
package com.nungil.scan

import android.graphics.Bitmap
import com.nungil.contract.Box
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ColorVote
import com.nungil.core.scan.WhiteBalance

/** Reads the pixels ColorVote needs from an upright frame bitmap. Call on a worker thread. */
object ColorSampler {
    /** Light of the whole frame: white-balance gains and whether it is too dark for colours. */
    class FrameLight(val gains: FloatArray, val isDark: Boolean)

    fun frameLight(bitmap: Bitmap): FrameLight {
        val pixels = read(bitmap, Box(0f, 0f, 1f, 1f), ColorVote.FRAME_GRID)
        return FrameLight(WhiteBalance.gains(pixels), ColorVote.isDark(pixels))
    }

    /** Colour of the object in [box], or null when the frame is dark or nothing is named. */
    fun colorOf(bitmap: Bitmap, box: Box, light: FrameLight): ColorName? {
        if (light.isDark) return null
        val pixels = read(bitmap, ColorVote.sampleRegion(box), ColorVote.SAMPLE_GRID)
        return ColorVote.vote(pixels, light.gains, light.isDark)
    }

    private fun read(bitmap: Bitmap, region: Box, grid: Int): IntArray {
        val points = ColorVote.gridPoints(region, bitmap.width, bitmap.height, grid)
        return IntArray(points.size / 2) { i -> bitmap.getPixel(points[2 * i], points[2 * i + 1]) }
    }
}
```

- [ ] **Step 5: Run them and see them pass; build**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.ColorMapperTest" --tests "com.nungil.core.scan.ColorVoteTest" assembleDebug -PskipModels`
Expected: `BUILD SUCCESSFUL`, 20 tests (9 + 11), 0 failures.

- [ ] **Step 6: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/ColorMapper.kt app/src/main/java/com/nungil/core/scan/WhiteBalance.kt app/src/main/java/com/nungil/core/scan/ColorVote.kt app/src/main/java/com/nungil/scan/ColorSampler.kt app/src/test/java/com/nungil/core/scan/ColorMapperTest.kt app/src/test/java/com/nungil/core/scan/ColorVoteTest.kt
git commit -m "Name object colours reliably under warm light and shadow"
git push -u origin a/A7-colour
```

---

### Task A8: Direction words

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/Directions.kt`
- Test: `app/src/test/java/com/nungil/core/scan/DirectionsTest.kt`

**Interfaces:**
- Consumes: `AngleMath` (A1), `Lang`.
- Produces: `object Directions { fun sector4Phrase(sector: Int, lang: Lang): String; fun sector8Name(sector: Int, lang: Lang): String; fun of(relAngle: Float, lang: Lang): String }`. English phrases follow the objects ("in front", "on your right", "behind you", "on your left"); Korean words come before them ("앞", "오른쪽", "뒤", "왼쪽", followed by "에").

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A8-directions
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/nungil/core/scan/DirectionsTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionsTest {
    @Test fun englishFourSectors() {
        assertEquals("in front", Directions.of(10f, Lang.EN))
        assertEquals("on your right", Directions.of(90f, Lang.EN))
        assertEquals("behind you", Directions.of(200f, Lang.EN))
        assertEquals("on your left", Directions.of(-80f, Lang.EN))
    }

    @Test fun koreanFourSectors() {
        assertEquals("앞", Directions.of(-30f, Lang.KO))
        assertEquals("오른쪽", Directions.of(60f, Lang.KO))
        assertEquals("뒤", Directions.of(180f, Lang.KO))
        assertEquals("왼쪽", Directions.of(270f, Lang.KO))
    }

    @Test fun eightSectorNames() {
        assertEquals("front right", Directions.sector8Name(1, Lang.EN))
        assertEquals("왼쪽 뒤", Directions.sector8Name(5, Lang.KO))
        assertEquals("front", Directions.sector8Name(8, Lang.EN))
    }
}
```

- [ ] **Step 3: Run it and see it fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.DirectionsTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'Directions'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/nungil/core/scan/Directions.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Lang

/** Direction words (team plan §5 glossary). Speech uses 4 sectors; history stores 8. */
object Directions {
    private val sector4En = arrayOf("in front", "on your right", "behind you", "on your left")
    private val sector4Ko = arrayOf("앞", "오른쪽", "뒤", "왼쪽")
    private val sector8En = arrayOf("front", "front right", "right", "back right", "back", "back left", "left", "front left")
    private val sector8Ko = arrayOf("앞", "오른쪽 앞", "오른쪽", "오른쪽 뒤", "뒤", "왼쪽 뒤", "왼쪽", "왼쪽 앞")

    /** English phrase placed after the objects ("3 chairs in front"); Korean word placed before them ("앞에 …"). */
    fun sector4Phrase(sector: Int, lang: Lang): String =
        if (lang == Lang.KO) sector4Ko[sector.mod(4)] else sector4En[sector.mod(4)]

    /** Name of one of the 8 sectors, for lists such as history. */
    fun sector8Name(sector: Int, lang: Lang): String =
        if (lang == Lang.KO) sector8Ko[sector.mod(8)] else sector8En[sector.mod(8)]

    /** Direction phrase for an angle relative to where the user faces. */
    fun of(relAngle: Float, lang: Lang): String = sector4Phrase(AngleMath.sector4(relAngle), lang)
}
```

- [ ] **Step 5: Run it and see it pass**

Run the same command. Expected: `BUILD SUCCESSFUL`, 3 tests, 0 failures.

- [ ] **Step 6: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/Directions.kt app/src/test/java/com/nungil/core/scan/DirectionsTest.kt
git commit -m "Say directions in English and Korean"
git push -u origin a/A8-directions
```

---

### Task A9: The spoken summary and scan phrases (English and Korean)

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/SummaryBuilder.kt`
- Create: `app/src/main/java/com/nungil/core/scan/ScanPhrases.kt`
- Test: `app/src/test/java/com/nungil/core/scan/SummaryBuilderTest.kt`
- Test: `app/src/test/java/com/nungil/core/scan/ScanPhrasesTest.kt`

**Interfaces:**
- Consumes: `ObjectSummary`, `ColorName` (A6), `ColorPolicy` (A7), `Directions`, `AngleMath`; `LabelNames`, `KoNumbers`, `Josa` (contract `core/lang`).
- Produces:
  - `object SummaryBuilder { fun plural(label: String): String; fun article(word: String): String; fun describe(o: ObjectSummary, lang: Lang, colorsOn: Boolean = true): String; fun livePhrase(o: ObjectSummary, lang: Lang, colorsOn: Boolean = true): String; fun fullSummary(objects: List<ObjectSummary>, coveragePercent: Int, lang: Lang, colorsOn: Boolean = true): String; fun coveragePrefix(percent: Int, lang: Lang): String; fun nothingFound(lang: Lang): String; fun joinAnd(items: List<String>): String; fun joinKorean(items: List<String>): String }`
  - `object ScanPhrases { intro, slowDown, noCompass, stopped, cameraNeeded, cameraProblem, cameraSwitched, looksLike, unknownThing, thisIs, unknownPerson, nobody, walkNotReady }`, each taking a `Lang`.
- Every canonical sentence in team plan §5 that belongs to the scan is a test case below, in both languages.

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A9-summary
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/nungil/core/scan/SummaryBuilderTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SummaryBuilderTest {
    private val blueChairs = ObjectSummary("chair", 3, ColorName.BLUE, 0f)
    private val blackLaptop = ObjectSummary("laptop", 1, ColorName.BLACK, 90f)
    private val person = ObjectSummary("person", 1, null, 5f)
    private val ali = ObjectSummary("Ali", 1, null, 0f, isName = true, wasPerson = true)

    @Test fun canonicalEnglishSummary() = assertEquals(
        "Around you: 3 blue chairs in front; a black laptop on your right.",
        SummaryBuilder.fullSummary(listOf(blueChairs, blackLaptop), 100, Lang.EN),
    )

    @Test fun canonicalKoreanSummary() = assertEquals(
        "앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요.",
        SummaryBuilder.fullSummary(listOf(blueChairs, blackLaptop), 100, Lang.KO),
    )

    @Test fun directionsAreAlwaysFrontRightBehindLeft() = assertEquals(
        "Around you: 3 blue chairs in front; a black laptop on your right.",
        SummaryBuilder.fullSummary(listOf(blackLaptop, blueChairs), 100, Lang.EN),
    )

    @Test fun twoGroupsInOneDirectionEnglish() = assertEquals(
        "Around you: 3 blue chairs and a person in front.",
        SummaryBuilder.fullSummary(listOf(blueChairs, person), 100, Lang.EN),
    )

    @Test fun twoGroupsInOneDirectionKorean() = assertEquals(
        "앞에 파란 의자 세 개와 사람 한 명이 있어요.",
        SummaryBuilder.fullSummary(listOf(blueChairs, person), 100, Lang.KO),
    )

    private val mixedChairs = listOf(
        ObjectSummary("chair", 1, ColorName.BLUE, 0f),
        ObjectSummary("chair", 1, ColorName.RED, 30f),
        ObjectSummary("chair", 2, ColorName.GRAY, 40f),
    )

    @Test fun mixedColoursEnglish() = assertEquals(
        "Around you: 4 chairs in blue, red and gray in front.",
        SummaryBuilder.fullSummary(mixedChairs, 100, Lang.EN),
    )

    @Test fun mixedColoursKorean() = assertEquals(
        "앞에 파란색, 빨간색, 회색 의자 네 개가 있어요.",
        SummaryBuilder.fullSummary(mixedChairs, 100, Lang.KO),
    )

    @Test fun savedNameHasNoArticleOrColour() {
        assertEquals("Around you: Ali in front.", SummaryBuilder.fullSummary(listOf(ali), 100, Lang.EN))
        assertEquals("앞에 Ali가 있어요.", SummaryBuilder.fullSummary(listOf(ali), 100, Lang.KO))
    }

    @Test fun partialCoverageIsAdmitted() {
        assertEquals(
            "I scanned 70 percent of the room. Around you: 3 blue chairs in front.",
            SummaryBuilder.fullSummary(listOf(blueChairs), 70, Lang.EN),
        )
        assertEquals(
            "방의 70퍼센트를 살펴봤어요. 앞에 파란 의자 세 개가 있어요.",
            SummaryBuilder.fullSummary(listOf(blueChairs), 70, Lang.KO),
        )
    }

    @Test fun emptyRoom() {
        assertEquals("No objects found. Try better lighting and turn slowly.", SummaryBuilder.fullSummary(emptyList(), 100, Lang.EN))
        assertEquals("찾은 물건이 없어요. 밝은 곳에서 천천히 돌아 주세요.", SummaryBuilder.fullSummary(emptyList(), 100, Lang.KO))
    }

    @Test fun peopleNeverGetAColour() {
        val redPeople = ObjectSummary("person", 2, ColorName.RED, 0f)
        assertEquals("Around you: 2 people in front.", SummaryBuilder.fullSummary(listOf(redPeople), 100, Lang.EN))
        assertEquals("앞에 사람 두 명이 있어요.", SummaryBuilder.fullSummary(listOf(redPeople), 100, Lang.KO))
    }

    @Test fun darkRoomNamesObjectsWithoutColourWords() {
        val dark = listOf(ObjectSummary("chair", 3, null, 0f), ObjectSummary("laptop", 1, null, 90f))
        val en = SummaryBuilder.fullSummary(dark, 100, Lang.EN)
        val ko = SummaryBuilder.fullSummary(dark, 100, Lang.KO)
        assertEquals("Around you: 3 chairs in front; a laptop on your right.", en)
        assertEquals("앞에 의자 세 개, 오른쪽에 노트북 한 대가 있어요.", ko)
        for (c in ColorName.entries) {
            assertFalse(en.contains(c.en))
            assertFalse(ko.contains(c.koNoun) || ko.contains(c.koAdjective + " "))
        }
    }

    @Test fun coloursCanBeSwitchedOff() = assertEquals(
        "Around you: 3 chairs in front.",
        SummaryBuilder.fullSummary(listOf(blueChairs), 100, Lang.EN, colorsOn = false),
    )

    @Test fun threeGroupsJoinWithCommasAndTheLastPair() {
        val items = listOf(
            ObjectSummary("chair", 2, null, 0f),
            ObjectSummary("book", 1, null, 0f),
            ObjectSummary("cup", 3, null, 0f),
        )
        assertEquals("Around you: 2 chairs, a book and 3 cups in front.", SummaryBuilder.fullSummary(items, 100, Lang.EN))
        assertEquals("앞에 의자 두 개, 책 한 권과 컵 세 개가 있어요.", SummaryBuilder.fullSummary(items, 100, Lang.KO))
    }

    @Test fun plurals() {
        assertEquals("chairs", SummaryBuilder.plural("chair"))
        assertEquals("buses", SummaryBuilder.plural("bus"))
        assertEquals("people", SummaryBuilder.plural("person"))
        assertEquals("wine glasses", SummaryBuilder.plural("wine glass"))
        assertEquals("benches", SummaryBuilder.plural("bench"))
        assertEquals("cell phones", SummaryBuilder.plural("cell phone"))
        assertEquals("scissors", SummaryBuilder.plural("scissors"))
    }

    @Test fun articles() {
        assertEquals("an orange chair", SummaryBuilder.describe(ObjectSummary("chair", 1, ColorName.ORANGE, 0f), Lang.EN))
        assertEquals("an umbrella", SummaryBuilder.describe(ObjectSummary("umbrella", 1, null, 0f), Lang.EN))
        assertEquals("a book", SummaryBuilder.describe(ObjectSummary("book", 1, null, 0f), Lang.EN))
    }

    @Test fun livePhrases() {
        val blueChair = ObjectSummary("chair", 1, ColorName.BLUE, 0f)
        val chairsLeft = ObjectSummary("chair", 3, null, 270f)
        assertEquals("a blue chair in front", SummaryBuilder.livePhrase(blueChair, Lang.EN))
        assertEquals("앞에 파란 의자", SummaryBuilder.livePhrase(blueChair, Lang.KO))
        assertEquals("3 chairs on your left", SummaryBuilder.livePhrase(chairsLeft, Lang.EN))
        assertEquals("왼쪽에 의자 세 개", SummaryBuilder.livePhrase(chairsLeft, Lang.KO))
        assertEquals("Ali in front", SummaryBuilder.livePhrase(ali, Lang.EN))
        assertEquals("앞에 Ali", SummaryBuilder.livePhrase(ali, Lang.KO))
    }
}
```

`app/src/test/java/com/nungil/core/scan/ScanPhrasesTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanPhrasesTest {
    @Test fun canonicalSlowDown() {
        assertEquals("Slow down.", ScanPhrases.slowDown(Lang.EN))
        assertEquals("천천히 돌아 주세요.", ScanPhrases.slowDown(Lang.KO))
    }

    @Test fun canonicalNoCompass() {
        assertEquals("Compass not available. Switching to live scan.", ScanPhrases.noCompass(Lang.EN))
        assertEquals("나침반을 쓸 수 없어서 실시간 안내로 바꿀게요.", ScanPhrases.noCompass(Lang.KO))
    }

    @Test fun intro() {
        assertEquals("휴대폰을 세우고 천천히 한 바퀴 돌아 주세요.", ScanPhrases.intro(ScanMode.FULL, Lang.KO))
        assertEquals("Point the phone around. I will say what I find.", ScanPhrases.intro(ScanMode.LIVE, Lang.EN))
    }

    @Test fun thisIsUsesTheRightKoreanEnding() {
        assertEquals("This is Ali.", ScanPhrases.thisIs("Ali", Lang.EN))
        assertEquals("Ali예요.", ScanPhrases.thisIs("Ali", Lang.KO))
        assertEquals("민준이에요.", ScanPhrases.thisIs("민준", Lang.KO))
    }

    @Test fun looksLike() {
        assertEquals("It looks like an umbrella.", ScanPhrases.looksLike("umbrella", Lang.EN))
        assertEquals("keyboard 같아요.", ScanPhrases.looksLike("keyboard", Lang.KO))
    }

    @Test fun people() {
        assertEquals("I don't know this person.", ScanPhrases.unknownPerson(Lang.EN))
        assertEquals("누군지 모르겠어요.", ScanPhrases.unknownPerson(Lang.KO))
        assertEquals("사람이 보이지 않아요.", ScanPhrases.nobody(Lang.KO))
    }

    @Test fun cameraAndWalk() {
        assertEquals("Front camera.", ScanPhrases.cameraSwitched(Facing.FRONT, Lang.EN))
        assertEquals("후면 카메라예요.", ScanPhrases.cameraSwitched(Facing.BACK, Lang.KO))
        assertEquals("Walk mode is not ready yet.", ScanPhrases.walkNotReady(Lang.EN))
        assertEquals("걷기 모드는 아직 준비 중이에요.", ScanPhrases.walkNotReady(Lang.KO))
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.SummaryBuilderTest" --tests "com.nungil.core.scan.ScanPhrasesTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'SummaryBuilder'` and `'ScanPhrases'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/nungil/core/scan/SummaryBuilder.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa
import com.nungil.core.lang.KoNumbers
import com.nungil.core.lang.LabelNames

/**
 * What the app says about a scan, in English and Korean (team plan §5 canonical sentences).
 * Angles in [ObjectSummary] are relative to where the user faces now.
 */
object SummaryBuilder {
    private val irregular = mapOf(
        "person" to "people",
        "mouse" to "mice",
        "knife" to "knives",
        "skis" to "skis",
        "scissors" to "scissors",
        "sheep" to "sheep",
    )

    /** English plural of a COCO label: "chair" -> "chairs", "bus" -> "buses", "person" -> "people". */
    fun plural(label: String): String {
        irregular[label]?.let { return it }
        return if (label.endsWith("s") || label.endsWith("x") || label.endsWith("ch") || label.endsWith("sh")) {
            label + "es"
        } else {
            label + "s"
        }
    }

    /** "a" or "an" before [word]. */
    fun article(word: String): String {
        val first = word.firstOrNull()?.lowercaseChar() ?: return "a"
        return if (first in VOWELS) "an" else "a"
    }

    /** Same label in one direction: counts add up, colours are listed per cluster. */
    private class Group(val label: String, val isName: Boolean, var count: Int, val colors: MutableList<ColorName?>)

    /** "3 blue chairs", "a person", "4 chairs in blue, red and gray" / "파란 의자 세 개", "사람 한 명". */
    fun describe(o: ObjectSummary, lang: Lang, colorsOn: Boolean = true): String =
        groupText(Group(o.label, o.isName, o.count, mutableListOf(o.color)), lang, colorsOn, liveKo = false)

    /** One live announcement: "a blue chair in front" / "앞에 파란 의자"; "3 chairs on your left" / "왼쪽에 의자 세 개". */
    fun livePhrase(o: ObjectSummary, lang: Lang, colorsOn: Boolean = true): String {
        val group = Group(o.label, o.isName, o.count, mutableListOf(o.color))
        val sector = AngleMath.sector4(o.angle)
        return if (lang == Lang.KO) {
            "${Directions.sector4Phrase(sector, lang)}에 ${groupText(group, lang, colorsOn, liveKo = true)}"
        } else {
            "${groupText(group, lang, colorsOn, liveKo = false)} ${Directions.sector4Phrase(sector, lang)}"
        }
    }

    /**
     * The full-scan sentence. EN: "Around you: 3 blue chairs in front; a black laptop on your right."
     * KO: "앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요." Below 100% coverage it admits how much was seen.
     */
    fun fullSummary(objects: List<ObjectSummary>, coveragePercent: Int, lang: Lang, colorsOn: Boolean = true): String {
        val prefix = if (coveragePercent in 0..99) coveragePrefix(coveragePercent, lang) else ""
        if (objects.isEmpty()) return prefix + nothingFound(lang)
        val bySector = (0 until 4).map { sector -> groupsIn(objects.filter { AngleMath.sector4(it.angle) == sector }) }
        val clauses = bySector.withIndex().filter { it.value.isNotEmpty() }.map { (sector, groups) ->
            sector to groups.map { groupText(it, lang, colorsOn, liveKo = false) }
        }
        return prefix + if (lang == Lang.KO) koreanSummary(clauses) else englishSummary(clauses)
    }

    fun coveragePrefix(percent: Int, lang: Lang): String =
        if (lang == Lang.KO) "방의 ${percent}퍼센트를 살펴봤어요. " else "I scanned $percent percent of the room. "

    fun nothingFound(lang: Lang): String =
        if (lang == Lang.KO) "찾은 물건이 없어요. 밝은 곳에서 천천히 돌아 주세요."
        else "No objects found. Try better lighting and turn slowly."

    private fun englishSummary(clauses: List<Pair<Int, List<String>>>): String =
        "Around you: " + clauses.joinToString("; ") { (sector, groups) ->
            "${joinAnd(groups)} ${Directions.sector4Phrase(sector, Lang.EN)}"
        } + "."

    private fun koreanSummary(clauses: List<Pair<Int, List<String>>>): String {
        val parts = clauses.mapIndexed { i, (sector, groups) ->
            val last = i == clauses.lastIndex
            val items = if (last) groups.dropLast(1) + Josa.iGa(groups.last()) else groups
            "${Directions.sector4Phrase(sector, Lang.KO)}에 ${joinKorean(items)}"
        }
        return parts.joinToString(", ") + " 있어요."
    }

    private fun groupsIn(objects: List<ObjectSummary>): List<Group> {
        val groups = mutableListOf<Group>()
        for (o in objects) {
            val existing = if (o.isName) null else groups.firstOrNull { !it.isName && it.label == o.label }
            if (existing == null) {
                groups += Group(o.label, o.isName, o.count, mutableListOf(o.color))
            } else {
                existing.count += o.count
                existing.colors += o.color
            }
        }
        return groups
    }

    private fun groupText(g: Group, lang: Lang, colorsOn: Boolean, liveKo: Boolean): String {
        if (g.isName) return g.label
        val useColor = colorsOn && ColorPolicy.hasColor(g.label)
        val distinct = if (useColor) g.colors.filterNotNull().distinct() else emptyList()
        val single = distinct.singleOrNull()?.takeIf { g.colors.all { c -> c == it } }
        val mixed = if (distinct.size >= 2) distinct else null
        return if (lang == Lang.KO) {
            val name = LabelNames.name(g.label, Lang.KO)
            val amount = if (liveKo && g.count == 1) "" else " " + KoNumbers.count(g.count, LabelNames.counterKo(g.label))
            when {
                single != null -> "${single.koAdjective} $name$amount"
                mixed != null -> "${mixed.joinToString(", ") { it.koNoun }} $name$amount"
                else -> "$name$amount"
            }
        } else {
            when {
                single != null && g.count == 1 -> "${article(single.en)} ${single.en} ${g.label}"
                single != null -> "${g.count} ${single.en} ${plural(g.label)}"
                mixed != null -> "${g.count} ${plural(g.label)} in ${joinAnd(mixed.map { it.en })}"
                g.count == 1 -> "${article(g.label)} ${g.label}"
                else -> "${g.count} ${plural(g.label)}"
            }
        }
    }

    /** "a", "a and b", "a, b and c". */
    fun joinAnd(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    /** "a", "a와 b", "a, b와 c" with 와/과 chosen by the word before it. */
    fun joinKorean(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> {
            val head = items.dropLast(2)
            val pair = Josa.waGwa(items[items.size - 2]) + " " + items.last()
            (head + pair).joinToString(", ")
        }
    }

    private const val VOWELS = "aeiou"
}
```

`app/src/main/java/com/nungil/core/scan/ScanPhrases.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.core.lang.Josa

/** Every other sentence the scan screens speak, in English and Korean (해요체). */
object ScanPhrases {
    private fun pick(lang: Lang, en: String, ko: String) = if (lang == Lang.KO) ko else en

    fun intro(mode: ScanMode, lang: Lang): String = when (mode) {
        ScanMode.FULL -> pick(lang, "Hold the phone upright and turn slowly in a full circle.", "휴대폰을 세우고 천천히 한 바퀴 돌아 주세요.")
        ScanMode.LIVE -> pick(lang, "Point the phone around. I will say what I find.", "휴대폰을 천천히 움직여 보세요. 찾는 대로 알려 드릴게요.")
    }

    fun slowDown(lang: Lang) = pick(lang, "Slow down.", "천천히 돌아 주세요.")

    fun noCompass(lang: Lang) = pick(lang, "Compass not available. Switching to live scan.", "나침반을 쓸 수 없어서 실시간 안내로 바꿀게요.")

    fun stopped(lang: Lang) = pick(lang, "Stopped.", "멈췄어요.")

    fun cameraNeeded(lang: Lang) = pick(lang, "To see what is around you, allow the camera.", "주변을 보려면 카메라를 허용해 주세요.")

    fun cameraProblem(lang: Lang) = pick(lang, "The camera has a problem. Go back and try again.", "카메라에 문제가 있어요. 뒤로 갔다가 다시 해 주세요.")

    fun cameraSwitched(facing: Facing, lang: Lang) = when (facing) {
        Facing.FRONT -> pick(lang, "Front camera.", "전면 카메라예요.")
        Facing.BACK -> pick(lang, "Back camera.", "후면 카메라예요.")
    }

    /** [name] is a COCO display name or an English ImageNet word (the classifier has no Korean names). */
    fun looksLike(name: String, lang: Lang) =
        pick(lang, "It looks like ${SummaryBuilder.article(name)} $name.", "$name 같아요.")

    fun unknownThing(lang: Lang) = pick(lang, "I can't tell what this is.", "무엇인지 모르겠어요.")

    fun thisIs(name: String, lang: Lang) =
        pick(lang, "This is $name.", name + if (Josa.hasBatchim(name)) "이에요." else "예요.")

    fun unknownPerson(lang: Lang) = pick(lang, "I don't know this person.", "누군지 모르겠어요.")

    fun nobody(lang: Lang) = pick(lang, "I don't see anyone.", "사람이 보이지 않아요.")

    fun walkNotReady(lang: Lang) = pick(lang, "Walk mode is not ready yet.", "걷기 모드는 아직 준비 중이에요.")
}
```

- [ ] **Step 5: Run them and see them pass**

Run the same command. Expected: `BUILD SUCCESSFUL`, 24 tests (17 + 7), 0 failures.

- [ ] **Step 6: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/SummaryBuilder.kt app/src/main/java/com/nungil/core/scan/ScanPhrases.kt app/src/test/java/com/nungil/core/scan/SummaryBuilderTest.kt app/src/test/java/com/nungil/core/scan/ScanPhrasesTest.kt
git commit -m "Describe the room in one natural sentence in English and Korean"
git push -u origin a/A9-summary
```

---

### Task A10: Scan session, spin guard and text log

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/SpinGuard.kt`
- Create: `app/src/main/java/com/nungil/core/scan/ScanLogState.kt`
- Create: `app/src/main/java/com/nungil/core/scan/ScanSession.kt` (also holds `ScanResult`)
- Test: `app/src/test/java/com/nungil/core/scan/ScanSessionTest.kt`

**Interfaces:**
- Consumes: everything in A1 and A5–A9.
- Produces:
  - `data class ScanResult(mode: ScanMode, startedAtMs: Long, coveragePercent: Int, summary: String, objects: List<ObjectSummary>)`
  - `class ScanSession(mode: ScanMode, startedAtMs: Long, lang: Lang, colorsOn: Boolean = true, timeoutMs: Long = 60_000L)` with `data class Seen(label, centerX, color, isName = false, wasPerson = false)`, `class Step(phrases, coveragePercent, bins, done)`, `fun onFrame(nowMs: Long, headingDeg: Float?, hfovDeg: Float, facing: Facing, seen: List<Seen>): Step`, `fun finish(): ScanResult`, `val noCompass`, `val announcesLive`. Not thread-safe.
  - `class SpinGuard(maxDegPerSec = 60f, repeatMs = 5_000L) { fun update(nowMs: Long, headingDeg: Float): Boolean }`
  - `class ScanLogState(maxLines = 200) { fun add(line: String); fun lines(): List<String>; fun text(): String; fun clear() }`

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A10-scan-session
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/nungil/core/scan/ScanSessionTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionTest {
    private val chair = ScanSession.Seen("chair", 0.5f, null)
    private fun ScanSession.frame(t: Long, heading: Float?, vararg seen: ScanSession.Seen) =
        onFrame(t, heading, 60f, Facing.BACK, seen.toList())

    @Test fun tunedNumbers() {
        assertEquals(60_000L, ScanSession.TIMEOUT_MS)
        assertEquals(60f, SpinGuard.MAX_DEG_PER_SEC)
        assertEquals(5_000L, SpinGuard.REPEAT_MS)
    }

    @Test fun aFullTurnFinishesTheScan() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        for (i in 0..34) assertFalse(s.frame(i * 200L, i * 10f).done)
        val last = s.frame(35 * 200L, 350f)
        assertTrue(last.done)
        assertEquals(100, last.coveragePercent)
        assertTrue(last.phrases.isEmpty())
    }

    @Test fun fullScanStopsAfterSixtySeconds() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        assertFalse(s.frame(0L, 0f).done)
        assertFalse(s.frame(59_999L, 0f).done)
        assertTrue(s.frame(60_000L, 0f).done)
    }

    @Test fun turningTooFastSaysSlowDown() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.KO)
        s.frame(0L, 0f)
        assertEquals(listOf("천천히 돌아 주세요."), s.frame(200L, 30f).phrases)
    }

    @Test fun slowDownIsNotRepeatedWithinFiveSeconds() {
        val g = SpinGuard()
        g.update(0L, 0f)
        assertTrue(g.update(200L, 30f))
        assertFalse(g.update(400L, 60f))
        assertFalse(g.update(5_000L, 60f))
        assertTrue(g.update(5_200L, 90f))
    }

    @Test fun turningSlowlyIsFine() {
        val g = SpinGuard()
        g.update(0L, 0f)
        assertFalse(g.update(100L, 5f))
    }

    @Test fun missingCompassSwitchesAFullScanToLive() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        assertTrue(s.frame(0L, null, chair).phrases.isEmpty())
        assertTrue(s.frame(1_400L, null, chair).phrases.isEmpty())
        assertEquals(listOf("Compass not available. Switching to live scan.", "a chair in front"), s.frame(1_500L, null, chair).phrases)
        assertTrue(s.announcesLive)
        val later = s.frame(90_000L, null, chair)
        assertTrue(later.phrases.isEmpty())
        assertFalse(later.done)
        assertEquals(100, s.finish().coveragePercent)
    }

    @Test fun liveScanAnnouncesEachObjectOnce() {
        val s = ScanSession(ScanMode.LIVE, 0L, Lang.EN)
        assertTrue(s.frame(0L, 0f, chair).phrases.isEmpty())
        assertTrue(s.frame(200L, 0f, chair).phrases.isEmpty())
        assertEquals(listOf("a chair in front"), s.frame(400L, 0f, chair).phrases)
        assertTrue(s.frame(600L, 0f, chair).phrases.isEmpty())
    }

    @Test fun fullScanDoesNotAnnounceLive() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        repeat(4) { assertTrue(s.frame(it * 200L, 0f, chair).phrases.isEmpty()) }
    }

    @Test fun summaryIsRelativeToWhereTheUserFacesAtTheEnd() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.EN)
        s.frame(0L, 100f, chair)
        s.frame(200L, 100f, chair)
        s.frame(400L, 100f, chair)
        s.frame(3_000L, 190f)
        val result = s.finish()
        assertEquals("I scanned 5 percent of the room. Around you: a chair on your left.", result.summary)
        assertEquals(5, result.coveragePercent)
        assertEquals(270f, result.objects.single().angle, 0.01f)
    }

    @Test fun koreanSummaryFromFrames() {
        val s = ScanSession(ScanMode.FULL, 0L, Lang.KO)
        val chairs = arrayOf(
            ScanSession.Seen("chair", 0.45f, ColorName.BLUE),
            ScanSession.Seen("chair", 0.5f, ColorName.BLUE),
            ScanSession.Seen("chair", 0.55f, ColorName.BLUE),
        )
        for (i in 0..35) {
            val heading = i * 10f
            if (heading == 0f || heading == 10f || heading == 20f) {
                s.frame(i * 200L, heading, *chairs)
            } else if (heading == 90f || heading == 100f || heading == 110f) {
                s.frame(i * 200L, heading, ScanSession.Seen("laptop", 0.5f, ColorName.BLACK))
            } else {
                s.frame(i * 200L, heading)
            }
        }
        s.frame(36 * 200L, 0f)
        assertEquals("앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요.", s.finish().summary)
    }

    @Test fun aRecognisedPersonHidesThePlainPersonBesideIt() {
        val s = ScanSession(ScanMode.LIVE, 0L, Lang.EN)
        repeat(3) {
            s.frame(it * 200L, 0f, ScanSession.Seen("Ali", 0.5f, null, isName = true, wasPerson = true), ScanSession.Seen("person", 0.55f, null))
        }
        assertEquals("Around you: Ali in front.", s.finish().summary)
    }

    @Test fun logKeepsTheLast200Lines() {
        val log = ScanLogState()
        repeat(205) { log.add("line $it") }
        log.add("  ")
        assertEquals(200, log.lines().size)
        assertEquals("line 5", log.lines().first())
        assertTrue(log.text().endsWith("line 204"))
    }
}
```

- [ ] **Step 3: Run it and see it fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.ScanSessionTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'ScanSession'`, `'SpinGuard'` and `'ScanLogState'`.

- [ ] **Step 4: Implement**

`app/src/main/java/com/nungil/core/scan/SpinGuard.kt`

```kotlin
package com.nungil.core.scan

import kotlin.math.abs

/** Says when the user turns faster than [maxDegPerSec], at most once every [repeatMs]. */
class SpinGuard(val maxDegPerSec: Float = MAX_DEG_PER_SEC, val repeatMs: Long = REPEAT_MS) {
    private var refTime: Long? = null
    private var refHeading = 0f
    private var lastWarning: Long? = null

    /** Feed every heading; true means "say Slow down now". Speed is measured over at least [MIN_WINDOW_MS]. */
    fun update(nowMs: Long, headingDeg: Float): Boolean {
        val since = refTime
        if (since == null) {
            refTime = nowMs
            refHeading = headingDeg
            return false
        }
        val dt = nowMs - since
        if (dt < MIN_WINDOW_MS) return false
        val speed = abs(AngleMath.diff(headingDeg, refHeading)) * 1000f / dt
        refTime = nowMs
        refHeading = headingDeg
        if (speed <= maxDegPerSec) return false
        val last = lastWarning
        if (last != null && nowMs - last < repeatMs) return false
        lastWarning = nowMs
        return true
    }

    companion object {
        const val MAX_DEG_PER_SEC = 60f
        const val REPEAT_MS = 5_000L
        const val MIN_WINDOW_MS = 100L
    }
}
```

`app/src/main/java/com/nungil/core/scan/ScanLogState.kt`

```kotlin
package com.nungil.core.scan

/** The on-screen text log of everything the scan said, so judges and helpers can read it. */
class ScanLogState(private val maxLines: Int = MAX_LINES) {
    private val lines = ArrayDeque<String>()

    fun add(line: String) {
        if (line.isBlank()) return
        lines.addLast(line)
        while (lines.size > maxLines) lines.removeFirst()
    }

    fun lines(): List<String> = lines.toList()

    fun text(): String = lines.joinToString("\n")

    fun clear() = lines.clear()

    companion object {
        const val MAX_LINES = 200
    }
}
```

`app/src/main/java/com/nungil/core/scan/ScanSession.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode

/** What a finished scan hands to speech and history. Object angles are relative to where the user faces at the end. */
data class ScanResult(
    val mode: ScanMode,
    val startedAtMs: Long,
    val coveragePercent: Int,
    val summary: String,
    val objects: List<ObjectSummary>,
)

/**
 * One scan from start to finish. Owns the clusterer, the coverage tracker and the spin guard; returns the
 * phrases to speak for each frame; stops a full scan when every bin is covered or after [timeoutMs].
 * Not thread-safe: the scan screen calls it from one thread at a time.
 */
class ScanSession(
    val mode: ScanMode,
    val startedAtMs: Long,
    private val lang: Lang,
    private val colorsOn: Boolean = true,
    val timeoutMs: Long = TIMEOUT_MS,
) {
    /** One usable detection of a frame, before it has a direction. [label] is the saved name when [isName]. */
    data class Seen(
        val label: String,
        val centerX: Float,
        val color: ColorName?,
        val isName: Boolean = false,
        val wasPerson: Boolean = false,
    )

    class Step(val phrases: List<String>, val coveragePercent: Int, val bins: BooleanArray, val done: Boolean)

    private val clusterer = ObjectClusterer()
    private val coverage = CoverageTracker()
    private val spin = SpinGuard()
    private var startHeading: Float? = null
    private var lastRel: Float? = null
    private var done = false

    /** True once a full scan found no compass and switched to live behaviour. */
    var noCompass = false
        private set

    /** Live scans, and full scans without a compass, announce each object as it is confirmed. */
    val announcesLive: Boolean get() = mode == ScanMode.LIVE || noCompass

    fun onFrame(nowMs: Long, headingDeg: Float?, hfovDeg: Float, facing: Facing, seen: List<Seen>): Step {
        val phrases = mutableListOf<String>()
        if (headingDeg != null) {
            val start = startHeading ?: headingDeg.also { startHeading = it }
            val rel = AngleMath.normalize(headingDeg - start)
            val prev = lastRel
            if (prev == null) coverage.mark(rel) else coverage.markArc(prev, rel)
            lastRel = rel
            if (spin.update(nowMs, headingDeg)) phrases += ScanPhrases.slowDown(lang)
        } else if (startHeading == null && !noCompass && nowMs - startedAtMs >= NO_COMPASS_GRACE_MS) {
            // The sensor gets a moment to deliver its first reading before we decide there is none.
            noCompass = true
            if (mode == ScanMode.FULL) phrases += ScanPhrases.noCompass(lang)
        }
        val rel = lastRel ?: 0f
        val detections = seen.map {
            FrameDetection(it.label, BoxGeometry.objectAngle(rel, it.centerX, hfovDeg, facing), it.color, it.isName, it.wasPerson)
        }
        val confirmedNow = clusterer.addFrame(detections)
        if (announcesLive) {
            for (o in confirmedNow) {
                phrases += SummaryBuilder.livePhrase(o.copy(angle = AngleMath.diff(o.angle, rel)), lang, colorsOn)
            }
        }
        if (!done && mode == ScanMode.FULL && !noCompass && startHeading != null) {
            done = coverage.isComplete() || nowMs - startedAtMs >= timeoutMs
        }
        return Step(phrases, coverage.percent(), coverage.bins(), done)
    }

    /** The summary and objects, turned so that "in front" is where the user faces now. */
    fun finish(): ScanResult {
        val facingNow = lastRel ?: 0f
        val objects = NamedPeople.dropShadowedPersons(clusterer.confirmed())
            .map { it.copy(angle = AngleMath.normalize(AngleMath.diff(it.angle, facingNow))) }
        val percent = if (mode == ScanMode.FULL && !noCompass) coverage.percent() else 100
        return ScanResult(mode, startedAtMs, percent, SummaryBuilder.fullSummary(objects, percent, lang, colorsOn), objects)
    }

    companion object {
        const val TIMEOUT_MS = 60_000L
        const val NO_COMPASS_GRACE_MS = 1_500L
    }
}
```

- [ ] **Step 5: Run the whole suite**

Run: `.\gradlew.bat testDebugUnitTest -PskipModels`
Expected: `BUILD SUCCESSFUL`. ScanSessionTest has 13 tests, and A's suites from A1 to A10 together have 136 tests (plus the 17 bootstrap tests), 0 failures.

- [ ] **Step 6: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/SpinGuard.kt app/src/main/java/com/nungil/core/scan/ScanLogState.kt app/src/main/java/com/nungil/core/scan/ScanSession.kt app/src/test/java/com/nungil/core/scan/ScanSessionTest.kt
git commit -m "Run a whole scan: coverage, auto-stop, slow-down warning and no-compass fallback"
git push -u origin a/A10-scan-session
```

---

### Task A11: The scan screen (needs I2's coverage ring on main)

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/SceneRules.kt`
- Create: `app/src/main/java/com/nungil/scan/SceneClassifier.kt`
- Create: `app/src/main/java/com/nungil/scan/ScanRecords.kt`
- Create: `app/src/main/res-a/layout/scan_fragment.xml`
- Create: `app/src/main/res-a/layout/scan_log_sheet.xml`
- Create: `app/src/main/res-a/layout/walk_fragment.xml`
- Modify: `app/src/main/res-a/values/strings.xml`
- Modify: `app/src/main/res-a/values-ko/strings.xml`
- Modify: `app/src/main/java/com/nungil/scan/ScanFragment.kt` (replaces the A3 smoke screen)
- Modify: `app/src/main/java/com/nungil/walk/WalkFragment.kt`
- Test: `app/src/test/java/com/nungil/core/scan/SceneRulesTest.kt`
- Test: `app/src/test/java/com/nungil/scan/ScanRecordsTest.kt`

**Interfaces:**
- Consumes: `CameraSession`, `OverlayView`, `ColorSampler` (A3, A4, A7); `ScanSession` and the rest of `core/scan`; `CoverageRingView.setCoverage(percent, bins)` (I); `createNameTaggers(context)` (Y, returns an empty list until Y4); `services().speaker / haptics / lang`; `AppDatabase.get(context).scans().insertScanWithObjects(…)` on `AppScope`; `ScanFragmentArgs.mode` (safe-args from I's nav graph).
- Produces: `ScanFragment : VoiceHandler` handling `Start`, `Stop`, `SwitchCamera`, `WhatIsThis`, `WhoIsThis`; a history row per finished scan (I's History screen reads it); `object SceneRules { MIN_SCORE = 0.35f; CENTER_SHARE = 0.5f; fun centerCrop(width: Int, height: Int): IntArray; fun centerDetection(detections: List<Detection>): Detection?; fun nearestToCenter(detections: List<Detection>, indices: List<Int>): Int? }`; `class SceneClassifier(context: Context) : Closeable { fun nameCenter(bitmap: Bitmap): String? }`; `object ScanRecords { fun scanEntity(result: ScanResult, lang: Lang): ScanEntity; fun objectEntities(result: ScanResult): List<DetectedObjectEntity> }`.
- Known limitation, stated on purpose: the 1000-class classifier knows English names only, so in Korean mode "what is this" may say "keyboard 같아요". COCO objects at the centre always get their Korean name.

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A11-scan-screen
```

- [ ] **Step 2: Write the failing tests**

`app/src/test/java/com/nungil/core/scan/SceneRulesTest.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Detection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneRulesTest {
    @Test fun tunedNumbers() {
        assertEquals(0.35f, SceneRules.MIN_SCORE)
        assertEquals(0.5f, SceneRules.CENTER_SHARE)
    }

    @Test fun centreCropIsTheMiddleHalf() =
        assertArrayEquals(intArrayOf(120, 160, 240, 320), SceneRules.centerCrop(480, 640))

    @Test fun centreDetectionPrefersTheBestScoreAtTheCentre() {
        val chair = Detection("chair", 0.8f, Box(0.3f, 0.3f, 0.7f, 0.7f))
        val table = Detection("dining table", 0.9f, Box(0.1f, 0.4f, 0.9f, 0.9f))
        val side = Detection("cup", 0.99f, Box(0f, 0f, 0.2f, 0.2f))
        assertEquals("dining table", SceneRules.centerDetection(listOf(chair, table, side))?.label)
    }

    @Test fun noBoxAtTheCentre() =
        assertNull(SceneRules.centerDetection(listOf(Detection("cup", 0.9f, Box(0f, 0f, 0.2f, 0.2f)))))

    @Test fun nearestToCenter() {
        val list = listOf(
            Detection("person", 0.9f, Box(0f, 0f, 0.2f, 1f)),
            Detection("person", 0.9f, Box(0.4f, 0f, 0.62f, 1f)),
            Detection("chair", 0.9f, Box(0.45f, 0f, 0.55f, 1f)),
        )
        assertEquals(1, SceneRules.nearestToCenter(list, listOf(0, 1)))
        assertNull(SceneRules.nearestToCenter(list, emptyList()))
    }
}
```

`app/src/test/java/com/nungil/scan/ScanRecordsTest.kt`

```kotlin
package com.nungil.scan

import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ObjectSummary
import com.nungil.core.scan.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanRecordsTest {
    private val result = ScanResult(
        mode = ScanMode.FULL,
        startedAtMs = 1_000L,
        coveragePercent = 80,
        summary = "I scanned 80 percent of the room. Around you: 3 blue chairs on your right.",
        objects = listOf(ObjectSummary("chair", 3, ColorName.BLUE, 90f), ObjectSummary("person", 1, null, 350f)),
    )

    @Test fun scanRow() {
        val row = ScanRecords.scanEntity(result, Lang.KO)
        assertEquals(1_000L, row.startedAt)
        assertEquals("FULL", row.mode)
        assertEquals(80, row.coveragePercent)
        assertEquals("ko", row.lang)
    }

    @Test fun objectRows() {
        val rows = ScanRecords.objectEntities(result)
        assertEquals("BLUE", rows[0].colorName)
        assertEquals(2, rows[0].sector8)
        assertNull(rows[1].colorName)
        assertEquals(0, rows[1].sector8)
        assertEquals(0L, rows[0].scanId)
    }
}
```

- [ ] **Step 3: Run them and see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.scan.SceneRulesTest" --tests "com.nungil.scan.ScanRecordsTest" -PskipModels`
Expected: FAIL with `Unresolved reference 'SceneRules'` and `'ScanRecords'`.

- [ ] **Step 4: Implement the rules, the classifier and the history mapping**

`app/src/main/java/com/nungil/core/scan/SceneRules.kt`

```kotlin
package com.nungil.core.scan

import com.nungil.contract.Detection
import kotlin.math.abs

/** Rules for "what is this": prefer a detector box at the centre, else classify the middle of the frame. */
object SceneRules {
    /** The 1000-class classifier's guess is spoken only above this score. */
    const val MIN_SCORE = 0.35f

    /** Share of the width and height the classifier looks at, around the centre. */
    const val CENTER_SHARE = 0.5f

    /** Pixel crop (left, top, width, height) of the middle [CENTER_SHARE] of a [width] x [height] image. */
    fun centerCrop(width: Int, height: Int): IntArray {
        val w = (width * CENTER_SHARE).toInt().coerceAtLeast(1)
        val h = (height * CENTER_SHARE).toInt().coerceAtLeast(1)
        return intArrayOf((width - w) / 2, (height - h) / 2, w, h)
    }

    /** The detection whose box contains the image centre, highest score first; null when none does. */
    fun centerDetection(detections: List<Detection>): Detection? =
        detections
            .filter { it.box.left <= 0.5f && it.box.right >= 0.5f && it.box.top <= 0.5f && it.box.bottom >= 0.5f }
            .maxByOrNull { it.score }

    /** Of the given indices into [detections], the one whose box centre is closest to the middle; null if empty. */
    fun nearestToCenter(detections: List<Detection>, indices: List<Int>): Int? =
        indices.minByOrNull { abs(detections[it].box.centerX - 0.5f) }
}
```

`app/src/main/java/com/nungil/scan/SceneClassifier.kt`

```kotlin
package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.nungil.core.scan.SceneRules
import java.io.Closeable

/**
 * Fallback namer: EfficientNet-Lite0 (1000 ImageNet classes) on the middle half of the frame.
 * Its names are English only, so Korean speech says the English word ("keyboard 같아요").
 * Create and use it on one worker thread; it is slow to load (about 18 MB).
 */
class SceneClassifier(context: Context) : Closeable {
    private val classifier: ImageClassifier = ImageClassifier.createFromOptions(
        context,
        ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_FILE).build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(1)
            .setScoreThreshold(SceneRules.MIN_SCORE)
            .build(),
    )

    /** The best guess for the middle of [bitmap], or null below [SceneRules.MIN_SCORE]. */
    fun nameCenter(bitmap: Bitmap): String? {
        val c = SceneRules.centerCrop(bitmap.width, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, c[0], c[1], c[2], c[3])
        val result = classifier.classify(BitmapImageBuilder(crop).build())
        val top = result.classificationResult().classifications().firstOrNull()?.categories()?.firstOrNull() ?: return null
        if (top.score() < SceneRules.MIN_SCORE) return null
        return top.displayName().ifBlank { top.categoryName() }
    }

    override fun close() = classifier.close()

    companion object {
        const val MODEL_FILE = "efficientnet-lite0.tflite"
    }
}
```

`app/src/main/java/com/nungil/scan/ScanRecords.kt`

```kotlin
package com.nungil.scan

import com.nungil.contract.Lang
import com.nungil.core.scan.AngleMath
import com.nungil.core.scan.ScanResult
import com.nungil.data.DetectedObjectEntity
import com.nungil.data.ScanEntity

/** Turns a finished scan into the Room rows that the History screen (I) shows. */
object ScanRecords {
    fun scanEntity(result: ScanResult, lang: Lang) = ScanEntity(
        startedAt = result.startedAtMs,
        mode = result.mode.name,
        coveragePercent = result.coveragePercent,
        summaryText = result.summary,
        lang = lang.tag,
    )

    fun objectEntities(result: ScanResult): List<DetectedObjectEntity> = result.objects.map {
        DetectedObjectEntity(
            label = it.label,
            count = it.count,
            colorName = it.color?.name,
            relAngleDeg = it.angle,
            sector8 = AngleMath.sector8(it.angle),
        )
    }
}
```

- [ ] **Step 5: Run them and see them pass**

Run the same command. Expected: `BUILD SUCCESSFUL`, 7 tests (5 + 2), 0 failures.

- [ ] **Step 6: Strings in both languages**

`app/src/main/res-a/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner A. Resource name prefixes owned by A: scan_, walk_, detector_, color_ -->
<resources>
    <string name="scan_title_full">Look around</string>
    <string name="scan_subtitle_full">Hold the phone upright and turn slowly in a full circle.</string>
    <string name="scan_title_live">Live guide</string>
    <string name="scan_subtitle_live">Point the phone around. I will say what I find.</string>
    <string name="scan_start">Start</string>
    <string name="scan_stop">Stop</string>
    <string name="scan_view_text">View text</string>
    <string name="scan_switch_camera">Switch camera</string>
    <string name="scan_preview_description">Camera view</string>
    <string name="scan_log_title">What I said</string>
    <string name="scan_log_empty">Nothing yet.</string>
    <string name="scan_permission_title">Camera needed</string>
    <string name="scan_permission_body">To see what is around you, allow the camera.</string>
    <string name="scan_permission_allow">Allow camera</string>
    <string name="scan_open_settings">Open settings</string>

    <string name="walk_title">Walk mode</string>
    <string name="walk_body">Walk mode is not ready yet.</string>
</resources>
```

`app/src/main/res-a/values-ko/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner A. -->
<resources>
    <string name="scan_title_full">주변을 둘러볼게요</string>
    <string name="scan_subtitle_full">휴대폰을 세우고 천천히 한 바퀴 돌아 주세요.</string>
    <string name="scan_title_live">실시간 안내</string>
    <string name="scan_subtitle_live">휴대폰을 천천히 움직여 보세요. 찾는 대로 알려 드릴게요.</string>
    <string name="scan_start">시작</string>
    <string name="scan_stop">멈춤</string>
    <string name="scan_view_text">글로 보기</string>
    <string name="scan_switch_camera">카메라 전환</string>
    <string name="scan_preview_description">카메라 화면</string>
    <string name="scan_log_title">말한 내용</string>
    <string name="scan_log_empty">아직 없어요.</string>
    <string name="scan_permission_title">카메라가 필요해요</string>
    <string name="scan_permission_body">주변을 보려면 카메라를 허용해 주세요.</string>
    <string name="scan_permission_allow">카메라 허용</string>
    <string name="scan_open_settings">설정 열기</string>

    <string name="walk_title">걷기 모드</string>
    <string name="walk_body">걷기 모드는 아직 준비 중이에요.</string>
</resources>
```

- [ ] **Step 7: Layouts (team plan §4: headline, subtitle, camera card, one main button at the bottom)**

`app/src/main/res-a/layout/scan_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner A. Team plan §4 screen pattern: headline, subtitle, camera card, one main button at the bottom. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/scan_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <TextView
        android:id="@+id/scan_subtitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <com.google.android.material.card.MaterialCardView
        android:id="@+id/scan_camera_card"
        style="@style/Widget.Nungil.Card"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:layout_weight="1"
        app:contentPadding="0dp">

        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <androidx.camera.view.PreviewView
                android:id="@+id/scan_preview"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:contentDescription="@string/scan_preview_description" />

            <com.nungil.scan.OverlayView
                android:id="@+id/scan_overlay"
                android:layout_width="match_parent"
                android:layout_height="match_parent" />

            <com.nungil.design.CoverageRingView
                android:id="@+id/scan_ring"
                android:layout_width="88dp"
                android:layout_height="88dp"
                android:layout_gravity="top|end"
                android:layout_margin="@dimen/ng_gap" />

            <LinearLayout
                android:id="@+id/scan_permission_panel"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="?attr/ngCard"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="@dimen/ng_gutter"
                android:visibility="gone">

                <TextView
                    android:id="@+id/scan_permission_title"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/scan_permission_title"
                    android:textAppearance="@style/TextAppearance.Nungil.Headline" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/ng_gap"
                    android:gravity="center"
                    android:text="@string/scan_permission_body"
                    android:textAppearance="@style/TextAppearance.Nungil.Body" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/scan_open_settings"
                    style="@style/Widget.Nungil.Button.Text"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/ng_gap"
                    android:text="@string/scan_open_settings" />
            </LinearLayout>
        </FrameLayout>
    </com.google.android.material.card.MaterialCardView>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:orientation="horizontal">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/scan_view_text"
            style="@style/Widget.Nungil.Button.Tonal"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/scan_view_text" />

        <Space
            android:layout_width="@dimen/ng_gap"
            android:layout_height="1dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/scan_switch_camera"
            style="@style/Widget.Nungil.Button.Tonal"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/scan_switch_camera" />
    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/scan_main_button"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/scan_start" />
</LinearLayout>
```

`app/src/main/res-a/layout/scan_log_sheet.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner A. Bottom sheet with everything the scan said. -->
<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/ngCard">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="@dimen/ng_gutter">

        <TextView
            android:id="@+id/scan_log_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/scan_log_title"
            android:textAppearance="@style/TextAppearance.Nungil.Headline" />

        <TextView
            android:id="@+id/scan_log_text"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap"
            android:textAppearance="@style/TextAppearance.Nungil.Body"
            android:textIsSelectable="true" />
    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

`app/src/main/res-a/layout/walk_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner A. Placeholder until walk mode gets its own plan. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingEnd="@dimen/ng_gutter">

    <TextView
        android:id="@+id/walk_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/walk_title"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:text="@string/walk_body"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />
</LinearLayout>
```

- [ ] **Step 8: The screens**

Replace the whole A3 smoke file:

`app/src/main/java/com/nungil/scan/ScanFragment.kt`

```kotlin
package com.nungil.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.nungil.R
import com.nungil.contract.Box
import com.nungil.contract.Buzz
import com.nungil.contract.Detection
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.contract.ScanSettings
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.lang.LabelNames
import com.nungil.core.scan.BoxGeometry
import com.nungil.core.scan.ColorPolicy
import com.nungil.core.scan.ScanLogState
import com.nungil.core.scan.ScanPhrases
import com.nungil.core.scan.ScanResult
import com.nungil.core.scan.ScanSession
import com.nungil.core.scan.SceneRules
import com.nungil.core.scan.StickyNames
import com.nungil.data.AppDatabase
import com.nungil.data.SettingsStore
import com.nungil.databinding.ScanFragmentBinding
import com.nungil.databinding.ScanLogSheetBinding
import com.nungil.people.createNameTaggers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full and live scan. Nav argument `mode: ScanMode`.
 * Threads: camera frames arrive on CameraSession's analysis thread; faces, items and the scene classifier
 * run on [extras] (one at a time, busy flag); everything touching views or speech runs on the main thread.
 */
class ScanFragment : Fragment(), VoiceHandler {

    private enum class State { NO_PERMISSION, IDLE, SCANNING }

    private class PendingTag(val box: Box, val name: String, val isPerson: Boolean)

    private var _binding: ScanFragmentBinding? = null
    private val binding get() = _binding!!
    private val args: ScanFragmentArgs by navArgs()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var settings = ScanSettings()
    private var state = State.IDLE
    private var camera: CameraSession? = null
    private var autoStart: Runnable? = null
    private var cameraErrorSpoken = false
    private val log = ScanLogState()

    @Volatile private var lang = Lang.EN
    @Volatile private var extras: ExecutorService? = null
    private val extrasBusy = AtomicBoolean(false)
    @Volatile private var taggers: List<NameTagger> = emptyList()
    @Volatile private var classifier: SceneClassifier? = null
    private val pendingTags = ConcurrentLinkedQueue<PendingTag>()

    // Analysis thread only.
    private val sticky = StickyNames()

    // Written on the analysis thread, read on the main thread for "what / who is this".
    @Volatile private var lastFrame: VisionFrame? = null
    @Volatile private var lastUsable: List<Detection> = emptyList()
    @Volatile private var lastNames: List<StickyNames.Sticky?> = emptyList()

    private val sessionLock = Any()
    private var session: ScanSession? = null

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (_binding == null) return@registerForActivityResult
        if (granted) startCamera() else showNoPermission()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ScanFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsStore(requireContext()).load()
        lang = services().lang
        val full = args.mode == ScanMode.FULL
        binding.scanTitle.setText(if (full) R.string.scan_title_full else R.string.scan_title_live)
        binding.scanSubtitle.setText(if (full) R.string.scan_subtitle_full else R.string.scan_subtitle_live)
        ViewCompat.setAccessibilityHeading(binding.scanTitle, true)
        binding.scanRing.isVisible = full
        binding.scanMainButton.setOnClickListener { onMainButton() }
        binding.scanViewText.setOnClickListener { showLog() }
        binding.scanSwitchCamera.setOnClickListener { switchCamera() }
        binding.scanOpenSettings.setOnClickListener { openAppSettings() }
        extras = Executors.newSingleThreadExecutor()
        if (hasCameraPermission()) startCamera() else askCamera.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the system settings with the permission granted.
        if (_binding != null && camera == null && hasCameraPermission()) startCamera()
    }

    override fun onDestroyView() {
        autoStart?.let(main::removeCallbacks)
        synchronized(sessionLock) { session = null }
        camera?.stop()
        camera = null
        val ex = extras
        extras = null
        if (ex != null) {
            val toClose = taggers
            val cls = classifier
            ex.execute {
                toClose.forEach { runCatching { it.close() } }
                runCatching { cls?.close() }
            }
            ex.shutdown()
            try {
                if (!ex.awaitTermination(2, TimeUnit.SECONDS)) ex.shutdownNow()
            } catch (e: InterruptedException) {
                ex.shutdownNow()
            }
        }
        taggers = emptyList()
        classifier = null
        main.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            if (state == State.NO_PERMISSION) speakNow(ScanPhrases.cameraNeeded(lang)) else if (state == State.IDLE) startScan()
            true
        }
        VoiceCommand.Stop -> {
            if (state == State.SCANNING) stopScan()
            true
        }
        VoiceCommand.SwitchCamera -> {
            switchCamera()
            true
        }
        VoiceCommand.WhatIsThis -> {
            whatIsThis()
            true
        }
        VoiceCommand.WhoIsThis -> {
            whoIsThis()
            true
        }
        else -> false
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (camera != null || _binding == null) return
        binding.scanPermissionPanel.isVisible = false
        val app = requireContext().applicationContext
        extras?.execute {
            taggers = try {
                createNameTaggers(app)
            } catch (t: Throwable) {
                Log.i(TAG, "Name taggers unavailable: ${t.message}")
                emptyList()
            }
        }
        camera = CameraSession(
            this,
            binding.scanPreview,
            CameraSession.Options(facing = settings.facing, keepBitmap = true),
            ::onFrame,
            ::onCameraError,
        ).also { it.start() }
        showState(State.IDLE)
        speak(ScanPhrases.intro(args.mode, lang))
        autoStart = Runnable { if (_binding != null && state == State.IDLE) startScan() }
            .also { main.postDelayed(it, AUTO_START_MS) }
    }

    private fun onMainButton() = when (state) {
        State.NO_PERMISSION -> askCamera.launch(Manifest.permission.CAMERA)
        State.IDLE -> startScan()
        State.SCANNING -> stopScan()
    }

    private fun startScan() {
        autoStart?.let(main::removeCallbacks)
        if (camera == null) return
        synchronized(sessionLock) {
            session = ScanSession(args.mode, System.currentTimeMillis(), lang, settings.colorsOn)
        }
        showState(State.SCANNING)
    }

    private fun stopScan() {
        val result = synchronized(sessionLock) {
            val s = session ?: return
            session = null
            s.finish()
        }
        showState(State.IDLE)
        services().haptics.buzz(Buzz.DONE)
        if (args.mode == ScanMode.FULL) {
            log.add(result.summary)
            if (settings.speechOn) services().speaker.sayFinal(result.summary)
            if (result.objects.isEmpty()) guessWhenEmpty()
        } else {
            speakNow(ScanPhrases.stopped(lang))
        }
        save(result)
    }

    private fun save(result: ScanResult) {
        val db = AppDatabase.get(requireContext())
        val scan = ScanRecords.scanEntity(result, lang)
        val objects = ScanRecords.objectEntities(result)
        AppScope.launch { db.scans().insertScanWithObjects(scan, objects) }
    }

    /** Analysis thread. */
    private fun onFrame(frame: VisionFrame) {
        lastFrame = frame
        while (true) {
            val tag = pendingTags.poll() ?: break
            sticky.recognized(tag.box, tag.name, tag.isPerson)
        }
        val usable = frame.detections.filterNot { BoxGeometry.touchesOneSideEdge(it.box) }
        val names = sticky.apply(usable.map { it.box })
        lastUsable = usable
        lastNames = names
        val scanning = synchronized(sessionLock) { session != null }
        var step: ScanSession.Step? = null
        if (scanning) {
            val bitmap = frame.bitmap
            val light = if (bitmap != null && settings.colorsOn) ColorSampler.frameLight(bitmap) else null
            val seen = usable.mapIndexed { i, d ->
                val name = names[i]
                val color = if (name == null && bitmap != null && light != null && ColorPolicy.hasColor(d.label)) {
                    ColorSampler.colorOf(bitmap, d.box, light)
                } else {
                    null
                }
                ScanSession.Seen(name?.name ?: d.label, d.box.centerX, color, isName = name != null, wasPerson = d.label == "person")
            }
            step = synchronized(sessionLock) {
                session?.onFrame(frame.timestampMs, frame.headingDeg, frame.hfovDeg, frame.facing, seen)
            }
        }
        val marks = usable.mapIndexed { i, d -> OverlayView.Mark(d.box, names[i]?.name ?: LabelNames.name(d.label, lang)) }
        main.post { render(frame, marks, step) }
        runTaggers(frame)
    }

    /** Main thread. */
    private fun render(frame: VisionFrame, marks: List<OverlayView.Mark>, step: ScanSession.Step?) {
        val b = _binding ?: return
        b.scanOverlay.show(marks, frame.imageWidth, frame.imageHeight, frame.facing == Facing.FRONT)
        if (step == null || state != State.SCANNING) return
        if (args.mode == ScanMode.FULL) b.scanRing.setCoverage(step.coveragePercent, step.bins)
        step.phrases.forEach { speak(it) }
        if (step.done) stopScan()
    }

    /** Analysis thread: hand the frame to the name taggers when the extras thread is free. */
    private fun runTaggers(frame: VisionFrame) {
        val ex = extras ?: return
        if (frame.bitmap == null || taggers.isEmpty()) return
        if (!extrasBusy.compareAndSet(false, true)) return
        try {
            ex.execute {
                try {
                    for (tagger in taggers) {
                        for (tag in tagger.tag(frame)) {
                            val d = frame.detections.getOrNull(tag.detectionIndex) ?: continue
                            pendingTags.add(PendingTag(d.box, tag.name, tag.kind == TagKind.PERSON))
                        }
                    }
                } catch (t: Throwable) {
                    Log.i(TAG, "Name tagger failed: ${t.message}")
                } finally {
                    extrasBusy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            extrasBusy.set(false)
        }
    }

    private fun whatIsThis() {
        val centre = SceneRules.centerDetection(lastUsable)
        if (centre != null) {
            speakNow(ScanPhrases.looksLike(LabelNames.name(centre.label, lang), lang))
            return
        }
        classifyCenter { guess -> speakNow(if (guess != null) ScanPhrases.looksLike(guess, lang) else ScanPhrases.unknownThing(lang)) }
    }

    private fun whoIsThis() {
        val usable = lastUsable
        val names = lastNames
        val people = usable.indices.filter { usable[it].label == "person" }
        val nearest = SceneRules.nearestToCenter(usable, people)
        val name = nearest?.let { names.getOrNull(it) }?.takeIf { it.isPerson }?.name
        speakNow(
            when {
                name != null -> ScanPhrases.thisIs(name, lang)
                nearest != null -> ScanPhrases.unknownPerson(lang)
                else -> ScanPhrases.nobody(lang)
            },
        )
    }

    /** When a full scan finds nothing, the 1000-class classifier names the middle of the frame. */
    private fun guessWhenEmpty() {
        classifyCenter { guess -> if (guess != null) speak(ScanPhrases.looksLike(guess, lang)) }
    }

    private fun classifyCenter(onResult: (String?) -> Unit) {
        val bitmap = lastFrame?.bitmap
        val ex = extras
        if (bitmap == null || ex == null) {
            onResult(null)
            return
        }
        val app = requireContext().applicationContext
        try {
            ex.execute {
                val guess = try {
                    (classifier ?: SceneClassifier(app).also { classifier = it }).nameCenter(bitmap)
                } catch (t: Throwable) {
                    Log.i(TAG, "Scene classifier failed: ${t.message}")
                    null
                }
                main.post { if (_binding != null) onResult(guess) }
            }
        } catch (e: RejectedExecutionException) {
            onResult(null)
        }
    }

    private fun switchCamera() {
        val c = camera ?: return
        c.switchCamera()
        speakNow(ScanPhrases.cameraSwitched(c.facing, lang))
    }

    private fun onCameraError(error: Throwable) {
        Log.i(TAG, "Camera error: ${error.message}")
        if (cameraErrorSpoken || _binding == null) return
        cameraErrorSpoken = true
        services().haptics.buzz(Buzz.ERROR)
        speakNow(ScanPhrases.cameraProblem(lang))
    }

    private fun showNoPermission() {
        showState(State.NO_PERMISSION)
        binding.scanPermissionPanel.isVisible = true
        speakNow(ScanPhrases.cameraNeeded(lang))
    }

    private fun showState(newState: State) {
        state = newState
        val b = _binding ?: return
        val button = b.scanMainButton
        val (text, fill, onFill) = when (newState) {
            State.NO_PERMISSION -> Triple(R.string.scan_permission_allow, R.attr.ngPrimary, R.attr.ngOnPrimary)
            State.IDLE -> Triple(R.string.scan_start, R.attr.ngPrimary, R.attr.ngOnPrimary)
            State.SCANNING -> Triple(R.string.scan_stop, R.attr.ngDanger, R.attr.ngOnDanger)
        }
        button.setText(text)
        button.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(button, fill))
        button.setTextColor(MaterialColors.getColor(button, onFill))
        b.scanSwitchCamera.isEnabled = newState != State.NO_PERMISSION
    }

    private fun showLog() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetBinding = ScanLogSheetBinding.inflate(layoutInflater)
        sheetBinding.scanLogText.text = log.text().ifEmpty { getString(R.string.scan_log_empty) }
        sheet.setContentView(sheetBinding.root)
        sheet.show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null))
        startActivity(intent)
    }

    private fun speak(text: String) {
        log.add(text)
        if (settings.speechOn) services().speaker.say(text)
    }

    private fun speakNow(text: String) {
        log.add(text)
        if (settings.speechOn) services().speaker.sayNow(text)
    }

    private companion object {
        const val TAG = "Nungil"

        /** Let the spoken introduction start before the scan begins. */
        const val AUTO_START_MS = 1_500L
    }
}
```

`app/src/main/java/com/nungil/walk/WalkFragment.kt`

```kotlin
package com.nungil.walk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.nungil.contract.app.services
import com.nungil.core.scan.ScanPhrases
import com.nungil.databinding.WalkFragmentBinding

/** Walk mode placeholder: says it is not ready. Walk mode gets its own plan only after Gate 4 (team plan). */
class WalkFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        WalkFragmentBinding.inflate(inflater, container, false).also {
            ViewCompat.setAccessibilityHeading(it.walkTitle, true)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val services = services()
        services.speaker.sayNow(ScanPhrases.walkNotReady(services.lang))
    }
}
```

Run: `.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels`, then `.\gradlew.bat installDebug`
Expected: `BUILD SUCCESSFUL` both times.

- [ ] **Step 9: Check on the phone (English, then Korean)**

With `adb logcat -v time -s Nungil:V` running:

1. **Full scan:** Home → "Full scan". You hear "Hold the phone upright and turn slowly in a full circle." After about 1.5 s the button turns red and says "Stop". Turn once, slowly, in a room with chairs and a laptop. The ring fills; boxes sit on the objects with their names; the scan stops by itself and you hear one sentence like "Around you: 3 blue chairs in front; a black laptop on your right." Counts match the room, with no duplicates.
2. **Too fast:** start again and spin quickly. You hear "Slow down." once, not repeatedly, and the summary begins "I scanned N percent of the room."
3. **Live scan:** "Live scan" → objects are announced one by one as they are confirmed ("a chair in front"), without talking over each other. "Stop" says "Stopped."
4. **View text:** it lists every sentence spoken, newest last.
5. **Switch camera:** you hear "Front camera."; the boxes are mirrored like the preview.
6. **Voice (once I9 is merged):** "what is this" while pointing at a cup → "It looks like a cup."; "who is this" at a stranger → "I don't know this person."
7. **Permission denied:** clear app data, open "Full scan" and deny the camera. The card says "Camera needed", the button says "Allow camera", and you hear "To see what is around you, allow the camera." Deny with "don't ask again", tap "Open settings", allow the camera there, press Back: the camera starts without restarting the app.
8. **History:** after a full scan, `adb shell run-as com.nungil ls databases` lists `nungil.db` (I's History screen shows the entry once I7 is merged).
9. **Korean:** set the phone (or the app, once I5 is merged) to Korean and repeat 1: title "주변을 둘러볼게요", and a sentence like "앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요."
10. **Walk:** Home → "Walk" says "Walk mode is not ready yet." / "걷기 모드는 아직 준비 중이에요."
11. **Leaving mid-scan:** press Back during a scan: no crash, no summary, the camera light turns off.

- [ ] **Step 10: Commit and open a pull request**

```powershell
git add app/src/main/java/com/nungil/core/scan/SceneRules.kt app/src/main/java/com/nungil/scan/SceneClassifier.kt app/src/main/java/com/nungil/scan/ScanRecords.kt app/src/main/res-a app/src/main/java/com/nungil/scan/ScanFragment.kt app/src/main/java/com/nungil/walk/WalkFragment.kt app/src/test/java/com/nungil/core/scan/SceneRulesTest.kt app/src/test/java/com/nungil/scan/ScanRecordsTest.kt
git commit -m "Scan the room and speak what is around, in English and Korean"
git push -u origin a/A11-scan-screen
```

Ask I to review the screen against team plan §4 in the pull request.

---

### Task A12: Prove the history write on the phone

**Files:**
- Test: `app/src/androidTest/java/com/nungil/scan/ScanHistoryDaoTest.kt` (package `com.nungil.scan`, so it belongs to A; the `data/` package is contract)

**Interfaces:**
- Consumes: `AppDatabase`, `ScanDao` (contract), `ScanRecords` (A11).
- Produces: an instrumented test proving `insertScanWithObjects` and cascade delete.

- [ ] **Step 1: Start the branch**

```powershell
git checkout main; git pull
git checkout -b a/A12-history-test
```

- [ ] **Step 2: Write the test**

`app/src/androidTest/java/com/nungil/scan/ScanHistoryDaoTest.kt`

```kotlin
package com.nungil.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ObjectSummary
import com.nungil.core.scan.ScanResult
import com.nungil.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the history write path on a real device: parent and children in one transaction, cascade delete. */
@RunWith(AndroidJUnit4::class)
class ScanHistoryDaoTest {
    private lateinit var db: AppDatabase

    private val result = ScanResult(
        mode = ScanMode.FULL,
        startedAtMs = 42L,
        coveragePercent = 100,
        summary = "Around you: 3 blue chairs in front.",
        objects = listOf(ObjectSummary("chair", 3, ColorName.BLUE, 0f), ObjectSummary("laptop", 1, null, 90f)),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertsScanWithItsObjects() = runBlocking {
        val id = db.scans().insertScanWithObjects(ScanRecords.scanEntity(result, Lang.EN), ScanRecords.objectEntities(result))
        val scans = db.scans().observeScans().first()
        assertEquals(listOf(id), scans.map { it.id })
        assertEquals("Around you: 3 blue chairs in front.", scans.single().summaryText)
        val objects = db.scans().objectsOf(id)
        assertEquals(listOf("chair", "laptop"), objects.map { it.label })
        assertTrue(objects.all { it.scanId == id })
    }

    @Test
    fun deletingAScanDeletesItsObjects() = runBlocking {
        val id = db.scans().insertScanWithObjects(ScanRecords.scanEntity(result, Lang.KO), ScanRecords.objectEntities(result))
        db.scans().deleteScan(id)
        assertNull(db.scans().getScan(id))
        assertTrue(db.scans().objectsOf(id).isEmpty())
    }
}
```

- [ ] **Step 3: Compile it**

Run: `.\gradlew.bat assembleDebugAndroidTest -PskipModels`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run it on the phone (a phone must be connected)**

Run: `.\gradlew.bat connectedDebugAndroidTest -PskipModels`
Expected: `BUILD SUCCESSFUL`, 2 tests on the device, 0 failures. Both tests use an in-memory database, so the app's real history is untouched.

- [ ] **Step 5: Commit and open a pull request**

```powershell
git add app/src/androidTest/java/com/nungil/scan/ScanHistoryDaoTest.kt
git commit -m "Prove scans and their objects are saved and deleted together"
git push -u origin a/A12-history-test
```

---

## After A12 (h19–h22)

- **Performance pass:** read the `Inference N ms average` lines. If the average is over 150 ms on GPU, try `ModelChoice.FAST` in I's Settings screen and compare. Record both numbers for the slide.
- **QA items owned by A** (team plan §8): 2, 3, 4 (with I), 8 and 13.
- **Walk mode:** only if every S3 check passes, write a separate plan for it, following build-guide §7.

## Hand-offs

| When | A tells | Message |
|---|---|---|
| h1.5 (Task 0 merged) | I, Y | "Bootstrap is on main. Clone, run `git config core.hooksPath .githooks`, and branch as i/… or y/…." |
| h5 (A3 merged) | Y | "CameraSession delivers frames. `Options(keepBitmap = true)` for faces and items, `minScore = 0.4f` for search. `onFrame` is on the analysis thread; post to the main thread before touching views." |
| h6 (A4 merged) | Y | "OverlayView is live: `Style.TARGET` for the search target (ngFocus colour, thick stroke), `DIM` for everything else." |
| S1 h8 | I | "The ring needs `setCoverage(percent, bins)` with 36 bins; index 0 is the start heading. `ScanFragment` speaks with `say` (live), `sayNow` (answers) and `sayFinal` (summary), and buzzes `DONE` and `ERROR`." |
| h12 | Y | "ScanFragment calls `createNameTaggers(appContext)` once per screen on its extras thread, calls `tag(frame)` whenever that thread is free, and closes every tagger in onDestroyView. A name must be confirmed twice (StickyNames) before it is spoken." |
| S2 h15 | I | "History rows: `ScanEntity.summaryText` is the exact sentence spoken, `lang` is `en` or `ko`, and `DetectedObjectEntity.sector8` indexes `Directions.sector8Name`." |
| S3 h22 | all | Inference numbers (GPU and CPU), battery and heat after 10 minutes, and the walk-mode decision. |
