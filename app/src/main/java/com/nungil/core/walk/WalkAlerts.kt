package com.nungil.core.walk

import com.nungil.core.ui.Notice

/** What walk mode may say, most urgent first (build guide §7 plus navigation below the saved things). */
enum class AlertKind { FLOOR, HAZARD, GROUND, LIGHT, DEPTH, SAVED, SIGN, CODE, NAVIGATION, BEACON, CLEAR, INFO }

/**
 * One thing worth saying. [key] identifies the situation (e.g. "wall:ahead"): the same key is not
 * said again while it lasts, so only changes are spoken. Alerts about the same [topic] (e.g. "hazard:car")
 * are said once per topic window (Announcer) unless the thing got closer; [level] is how far away it
 * is (steps or metres as spoken, 0 = very close, [FAR] = unknown). [ahead]: it is about what is straight
 * ahead, which is said before anything to the left or right and cuts into a sentence.
 */
data class Alert(
    val kind: AlertKind,
    val key: String,
    val text: String,
    val urgent: Boolean = false,
    val topic: String? = null,
    val level: Int = FAR,
    val ahead: Boolean = false,
) {
    companion object {
        const val FAR = Int.MAX_VALUE
    }
}

/** This alert as the shared announcer's notice: its kind is its priority, "nothing close ahead" cuts in. */
fun Alert.toNotice(): Notice =
    Notice(key, text, kind.ordinal, ahead = ahead, urgent = urgent, topic = topic, level = level, cutsIn = kind == AlertKind.CLEAR)

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
