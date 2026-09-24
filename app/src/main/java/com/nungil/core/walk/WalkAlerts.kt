package com.nungil.core.walk

/** What walk mode may say, most urgent first (build guide §7 plus navigation below the saved things). */
enum class AlertKind { FLOOR, HAZARD, GROUND, LIGHT, DEPTH, SAVED, SIGN, CODE, NAVIGATION, BEACON, CLEAR, INFO }

/**
 * One thing worth saying. [key] identifies the situation (e.g. "wall:ahead"): the same key is not
 * said again while it lasts, so only changes are spoken. Alerts about the same [topic] (e.g. "hazard:car")
 * are said once per [WalkAlerts.TOPIC_REPEAT_MS] unless the thing got closer; [level] is how far away it
 * is (steps or metres as spoken, 0 = very close, [FAR] = unknown).
 */
data class Alert(
    val kind: AlertKind,
    val key: String,
    val text: String,
    val urgent: Boolean = false,
    val topic: String? = null,
    val level: Int = FAR,
) {
    companion object {
        const val FAR = Int.MAX_VALUE
    }
}

/**
 * Chooses at most one sentence per frame: the most urgent alert that is new. Never repeats the same
 * sentence inside [REPEAT_MS]. It never produces "all clear": when nothing is found it stays quiet.
 *
 * "New" is per key: a key that was said stays said while it keeps coming back, and only after it has been
 * gone for [GONE_MS] may it be said again. (Remembering one key per kind made a corridor alternate
 * "wall left", "wall right", "wall ahead" every second.)
 */
class WalkAlerts {
    /** Keys said, with the last time they were among the candidates. */
    private val said = HashMap<String, Long>()
    private val lastSaidAt = HashMap<String, Long>()
    private val topics = HashMap<String, Pair<Long, Int>>()

    fun choose(nowMs: Long, candidates: List<Alert>): Alert? {
        val keys = candidates.map { it.key }.toSet()
        // Not seen for GONE_MS: it went away, and may be said when it is back.
        said.entries.removeAll { (_, seen) -> nowMs - seen >= GONE_MS }
        for (k in keys) if (k in said) said[k] = nowMs
        val pick = candidates
            .sortedBy { it.kind.ordinal }
            .firstOrNull { it.key !in said && textAllowed(it, nowMs) && topicAllowed(it, nowMs) }
            ?: return null
        said[pick.key] = nowMs
        lastSaidAt[pick.text] = nowMs
        pick.topic?.let { topics[it] = nowMs to pick.level }
        return pick
    }

    private fun textAllowed(a: Alert, now: Long) = lastSaidAt[a.text]?.let { now - it >= REPEAT_MS } ?: true

    /** The same topic again only after a while, when urgent, or when clearly closer. */
    private fun topicAllowed(a: Alert, now: Long): Boolean {
        val topic = a.topic ?: return true
        if (a.urgent) return true
        val (at, level) = topics[topic] ?: return true
        if (now - at >= TOPIC_REPEAT_MS) return true
        return a.level <= level - 2 || (a.level <= NEAR_LEVEL && a.level < level)
    }

    fun reset() {
        said.clear()
        lastSaidAt.clear()
        topics.clear()
    }

    companion object {
        const val REPEAT_MS = 6_000L
        const val GONE_MS = 2_000L
        const val TOPIC_REPEAT_MS = 12_000L

        /** Within this many steps any step closer is worth saying. */
        const val NEAR_LEVEL = 3
    }
}

/** Saved places kept as text lines "name\tlat\tlon". */
object PlacesCodec {
    fun encode(places: List<Place>): String =
        places.joinToString("\n") { "${it.name.replace('\t', ' ').replace('\n', ' ').trim()}\t${it.point.lat}\t${it.point.lon}" }

    fun decode(text: String?): List<Place> =
        text.orEmpty().lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            if (parts[0].isBlank()) null else Place(parts[0], LatLon(lat, lon))
        }

    /** Saving a name that exists replaces it (case-insensitive). */
    fun put(places: List<Place>, place: Place): List<Place> =
        places.filterNot { it.name.equals(place.name, ignoreCase = true) } + place

    /** Exact name first, then containment either way ("home" finds "my home"). */
    fun find(places: List<Place>, query: String): Place? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        return places.firstOrNull { it.name.lowercase() == q }
            ?: places.firstOrNull { it.name.lowercase().contains(q) || q.contains(it.name.lowercase()) }
    }
}
