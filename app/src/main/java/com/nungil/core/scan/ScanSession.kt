package com.nungil.core.scan

import com.nungil.contract.DirectionStyle
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
    /** Words or clock hours (Settings), read for each sentence: it can change during a scan. */
    private val style: () -> DirectionStyle = { DirectionStyle.WORDS },
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

    /** A confirmed thing in view: [key] stays the same for the thing; [bearing] is from where the user faces. */
    data class LiveObject(val key: Int, val text: String, val bearing: Float)

    /**
     * [phrases] are notices to say in turn. [inView]: the confirmed things in view in a live scan, every frame; the
     * screen's Announcer chooses which to say and when.
     */
    class Step(
        val phrases: List<String>,
        val coveragePercent: Int,
        val bins: BooleanArray,
        val done: Boolean,
        val inView: List<LiveObject> = emptyList(),
    )

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
        clusterer.addFrame(detections)
        val inView = if (!announcesLive) emptyList() else clusterer.inView().map { (key, o) ->
            val bearing = AngleMath.diff(o.angle, rel)
            LiveObject(key, SummaryBuilder.livePhrase(o.copy(angle = bearing), lang, style(), colorsOn), bearing)
        }
        if (!done && mode == ScanMode.FULL && !noCompass && startHeading != null) {
            done = coverage.isComplete() || nowMs - startedAtMs >= timeoutMs
        }
        return Step(phrases, coverage.percent(), coverage.bins(), done, inView)
    }

    /** The summary and objects, turned so that "in front" is where the user faces now. */
    fun finish(): ScanResult {
        val facingNow = lastRel ?: 0f
        val objects = NamedPeople.dropShadowedPersons(clusterer.confirmed())
            .map { it.copy(angle = AngleMath.normalize(AngleMath.diff(it.angle, facingNow))) }
        val percent = if (mode == ScanMode.FULL && !noCompass) coverage.percent() else 100
        return ScanResult(mode, startedAtMs, percent, SummaryBuilder.fullSummary(objects, percent, lang, style(), colorsOn), objects)
    }

    companion object {
        const val TIMEOUT_MS = 60_000L
        const val NO_COMPASS_GRACE_MS = 1_500L
    }
}
