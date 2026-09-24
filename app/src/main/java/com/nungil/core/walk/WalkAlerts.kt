package com.nungil.core.walk

/** What walk mode may say, most urgent first (build guide §7 plus navigation below the saved things). */
enum class AlertKind { FLOOR, HAZARD, GROUND, LIGHT, SAVED, SIGN, CODE, NAVIGATION, BEACON, CLEAR, INFO }

/**
 * One thing worth saying. [key] identifies the situation (e.g. "wall:ahead"): the same key is not
 * said again while it lasts, so only changes are spoken.
 */
data class Alert(val kind: AlertKind, val key: String, val text: String, val urgent: Boolean = false)

/**
 * Chooses at most one sentence per frame: the most urgent alert that is new. Never repeats the same
 * sentence inside [REPEAT_MS]. It never produces "all clear": when nothing is found it stays quiet.
 */
class WalkAlerts {
    private val lastKey = HashMap<AlertKind, String>()
    private val lastSaidAt = HashMap<String, Long>()

    fun choose(nowMs: Long, candidates: List<Alert>): Alert? {
        val present = candidates.map { it.kind }.toSet()
        // A situation that is gone may be announced again when it comes back.
        AlertKind.entries.filter { it !in present }.forEach { lastKey.remove(it) }
        val pick = candidates
            .sortedBy { it.kind.ordinal }
            .firstOrNull { lastKey[it.kind] != it.key && (lastSaidAt[it.text]?.let { t -> nowMs - t >= REPEAT_MS } ?: true) }
            ?: return null
        lastKey[pick.kind] = pick.key
        lastSaidAt[pick.text] = nowMs
        return pick
    }

    fun reset() {
        lastKey.clear()
        lastSaidAt.clear()
    }

    companion object {
        const val REPEAT_MS = 6_000L
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
