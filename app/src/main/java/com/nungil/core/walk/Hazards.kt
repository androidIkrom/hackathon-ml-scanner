package com.nungil.core.walk

import kotlin.math.max

/** Which detector results count as walking hazards (build guide §7). */
object HazardPolicy {
    const val MIN_SCORE = 0.7f
    val CLASSES = setOf(
        "person", "bicycle", "car", "motorcycle", "bus", "truck", "dog", "bench", "chair", "couch",
        "dining table", "potted plant", "stop sign", "fire hydrant", "suitcase",
    )

    fun isHazard(label: String, score: Float): Boolean = label in CLASSES && score >= MIN_SCORE
}

/** A hazard is real once its label appears in [need] of the last [window] frames. */
class HazardConfirmer(private val window: Int = WINDOW, private val need: Int = NEED) {
    private val history = ArrayDeque<Set<String>>()

    fun update(labels: Set<String>): Set<String> {
        history.addLast(labels)
        while (history.size > window) history.removeFirst()
        return history.flatten().groupingBy { it }.eachCount().filterValues { it >= need }.keys
    }

    fun clear() = history.clear()

    companion object {
        const val WINDOW = 5
        const val NEED = 3
    }
}

/**
 * Turns the 1000-class classifier's guess into a word worth saying while walking. A blank wall is
 * otherwise named "file" or "refrigerator", the nearest ImageNet classes to a flat pale surface.
 */
object ObstacleName {
    const val WALKING_SCORE = 0.5f
    const val OTHER_SCORE = 0.85f

    /** ImageNet name fragment -> simple word (English, Korean). */
    private val WALKING = linkedMapOf(
        "door" to ("door" to "문"),
        "fence" to ("fence" to "울타리"),
        "wall" to ("wall" to "벽"),
        "chair" to ("chair" to "의자"),
        "table" to ("table" to "탁자"),
        "bench" to ("bench" to "벤치"),
        "window" to ("window" to "창문"),
        "pole" to ("pole" to "기둥"),
        "banister" to ("railing" to "난간"),
        "railing" to ("railing" to "난간"),
        "turnstile" to ("turnstile" to "개찰구"),
        "mailbox" to ("mailbox" to "우체통"),
        "sign" to ("sign" to "표지판"),
        "carton" to ("box" to "상자"),
        "box" to ("box" to "상자"),
    )

    const val OBSTACLE_EN = "obstacle"
    const val OBSTACLE_KO = "장애물"

    /** (English, Korean) word for the classifier's [label] at [score]. */
    fun of(label: String, score: Float): Pair<String, String> {
        val l = label.lowercase()
        if (score >= WALKING_SCORE) {
            WALKING.entries.firstOrNull { l.contains(it.key) }?.let { return it.value }
        }
        // A confident guess outside the walking list is said in English only; Korean says "장애물".
        if (score >= OTHER_SCORE) return l to OBSTACLE_KO
        return OBSTACLE_EN to OBSTACLE_KO
    }
}

enum class LightColor { RED, GREEN }

/** Colour of a traffic-light crop from its bright, saturated pixels (ARGB ints). */
object TrafficLightColor {
    const val MIN_SHARE = 0.03f
    const val DOMINANCE = 2f

    fun classify(argb: IntArray): LightColor? {
        if (argb.isEmpty()) return null
        var red = 0
        var green = 0
        val hsv = FloatArray(3)
        for (p in argb) {
            rgbToHsv((p shr 16) and 255, (p shr 8) and 255, p and 255, hsv)
            if (hsv[2] < 0.5f || hsv[1] < 0.4f) continue
            val h = hsv[0]
            if (h < 20f || h >= 340f) red++ else if (h in 120f..200f) green++
        }
        val minCount = max(1f, argb.size * MIN_SHARE)
        return when {
            red >= minCount && red >= green * DOMINANCE -> LightColor.RED
            green >= minCount && green >= red * DOMINANCE -> LightColor.GREEN
            else -> null
        }
    }

    /** Pure-Kotlin RGB (0..255) to HSV (h 0..360, s and v 0..1). */
    fun rgbToHsv(r: Int, g: Int, b: Int, out: FloatArray) {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val mx = maxOf(rf, gf, bf)
        val mn = minOf(rf, gf, bf)
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == rf -> 60f * (((gf - bf) / d) % 6f)
            mx == gf -> 60f * ((bf - rf) / d + 2f)
            else -> 60f * ((rf - gf) / d + 4f)
        }
        out[0] = if (h < 0) h + 360f else h
        out[1] = if (mx == 0f) 0f else d / mx
        out[2] = mx
    }
}

enum class GroundKind { ROAD, SIDEWALK, TERRAIN }

/** ARCore scene semantics guesses indoors too, so ground class is spoken outdoors only (build guide §7). */
object GroundRule {
    const val MIN_CONFIDENCE = 200
    const val SKY_FRACTION = 0.02f
    const val SKY_MEMORY_MS = 60_000L

    fun speakable(confidence: Int, lastSkySeenAtMs: Long?, nowMs: Long): Boolean =
        confidence >= MIN_CONFIDENCE && lastSkySeenAtMs != null && nowMs - lastSkySeenAtMs <= SKY_MEMORY_MS
}
