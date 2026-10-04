package com.nungil.core.walk

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa
import com.nungil.core.lang.KoNumbers
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One walking instruction. [maneuver] is where it is carried out: the step's first way point, which
 * is also the last way point of the step before (openrouteservice lists "Turn right" as the step that
 * starts at the turn). [pointIndex] is that way point's index in the route line.
 */
data class RouteStep(
    val instruction: String,
    val type: Int,
    val distanceM: Float,
    val maneuver: LatLon,
    val pointIndex: Int,
)

data class Route(
    val steps: List<RouteStep>,
    val line: List<LatLon>,
    val totalM: Float,
    val destination: Place,
)

/** openrouteservice JSON: the request body and the answers we read (spec §1). */
object OrsJson {
    const val ARRIVE_TYPE = 10
    const val DEPART_TYPE = 11

    /** Coordinates are longitude first. */
    fun routeRequest(from: LatLon, to: LatLon): String = JSONObject()
        .put("coordinates", JSONArray().put(JSONArray().put(from.lon).put(from.lat)).put(JSONArray().put(to.lon).put(to.lat)))
        .put("instructions", true)
        .put("instructions_format", "text")
        .put("language", "en")
        .put("units", "m")
        .put("preference", "shortest")
        .toString()

    /** Null when there is no feature or no line. */
    fun parseRoute(json: String, destination: Place): Route? = runCatching {
        val features = JSONObject(json).optJSONArray("features") ?: return null
        if (features.length() == 0) return null
        val feature = features.getJSONObject(0)
        val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
        val line = (0 until coords.length()).map { i ->
            val c = coords.getJSONArray(i)
            LatLon(c.getDouble(1), c.getDouble(0))
        }
        if (line.size < 2) return null
        val props = feature.getJSONObject("properties")
        val total = props.optJSONObject("summary")?.optDouble("distance", 0.0) ?: 0.0
        val steps = ArrayList<RouteStep>()
        val segments = props.optJSONArray("segments") ?: JSONArray()
        for (s in 0 until segments.length()) {
            val arr = segments.getJSONObject(s).optJSONArray("steps") ?: continue
            for (k in 0 until arr.length()) {
                val st = arr.getJSONObject(k)
                val wp = st.getJSONArray("way_points")
                val index = wp.getInt(0).coerceIn(0, line.lastIndex)
                steps.add(
                    RouteStep(
                        instruction = st.optString("instruction", ""),
                        type = st.optInt("type", -1),
                        distanceM = st.optDouble("distance", 0.0).toFloat(),
                        maneuver = line[index],
                        pointIndex = index,
                    ),
                )
            }
        }
        Route(steps, line, total.toFloat(), destination)
    }.getOrNull()

    /**
     * Candidates with their short name ("서울역"), not the long label ("South Korea Seoul Yongsan 서울역"),
     * because the name is what gets spoken. With [near], anything farther than [NEAR_KM] is dropped:
     * nobody walks to a Starbucks in Portugal.
     */
    fun parseGeocode(json: String, near: LatLon? = null): List<Place> = runCatching {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            if (c.length() < 2) return@mapNotNull null
            val props = f.optJSONObject("properties")
            val name = props?.optString("name", "").orEmpty().ifBlank { props?.optString("label", "").orEmpty() }
            val point = LatLon(c.getDouble(1), c.getDouble(0))
            when {
                name.isBlank() -> null
                near != null && Beacon.distanceMetres(near, point) > NEAR_KM * 1000 -> null
                else -> Place(name, point)
            }
        }
    }.getOrDefault(emptyList())

    /** Place search stays within walking reach of the user. */
    const val NEAR_KM = 20

    /** An empty body or a 403 "Quota exceeded" (the deprecated host) both count as failure. */
    fun isQuotaError(code: Int, body: String): Boolean = code == 403 && body.contains("Quota exceeded")
}

sealed interface Announcement {
    val text: String

    data class Prepare(override val text: String) : Announcement
    data class Turn(override val text: String) : Announcement
    data class Arrived(override val text: String) : Announcement
    data class OffRoute(override val text: String) : Announcement
    data class WrongWay(override val text: String) : Announcement
    data class Approach(override val text: String) : Announcement
}

/**
 * Turn-by-turn guidance from GPS fixes (spec §4): prepare at 25 m, turn at 5 m, arrive within 15 m,
 * off route after three fixes more than 40 m from the line. Progress is measured along the route, so
 * a long GPS gap skips passed turns silently and a U-shaped street never confuses the next turn.
 */
class Navigator(private val route: Route, private val stepLengthM: Float?, private val lang: Lang) {
    private val origin = route.line.first()
    private val xy = route.line.map { project(it) }
    private val along = FloatArray(xy.size).also { a ->
        for (i in 1 until xy.size) a[i] = a[i - 1] + dist(xy[i - 1], xy[i])
    }
    private val guided = route.steps.filter { it.type != OrsJson.DEPART_TYPE && it.type != OrsJson.ARRIVE_TYPE && it.pointIndex > 0 }
    private var current = 0
    private var prepared = -1
    private var turned = -1
    private var farFixes = 0
    private val saidAt = HashMap<String, Long>()

    /** From the end of the line to the destination: the route ends on the street, the place may be off it. */
    private val endGap = Beacon.distanceMetres(route.line.last(), route.destination.point).toFloat()

    /** The farthest along the line the walker has been, and whether "walking away" was said since. */
    private var farthest = -1f
    private var wrongWaySaid = false
    private val approach = GoApproach().also { it.next(along.last() + endGap) }

    var finished = false
        private set

    /** Progress along the route at the last fix on it, in metres (for the walker's speed). */
    var progressM = 0f
        private set

    /** Which side of the street the destination is on: against the direction of the route's last segment. */
    val side: Side? = if (route.line.size >= 2) GoMath.side(route.line[route.line.size - 2], route.line.last(), route.destination.point) else null

    fun update(nowMs: Long, at: LatLon): Announcement? {
        if (finished) return null
        if (Beacon.distanceMetres(at, route.destination.point) <= ARRIVED_M) {
            finished = true
            return Announcement.Arrived(RoutePhrases.arrived(route.destination.name, lang, side))
        }
        val p = project(at)
        val (offLine, myAlong) = nearest(p)
        if (offLine > OFF_ROUTE_M) {
            farFixes++
            if (farFixes >= OFF_ROUTE_FIXES) {
                farFixes = 0
                return Announcement.OffRoute(RoutePhrases.offRoute(lang))
            }
            return null
        }
        farFixes = 0
        progressM = myAlong
        // Walking back along the route, still on it: said once, and again only after the farthest point is regained.
        if (myAlong >= farthest) {
            farthest = myAlong
            wrongWaySaid = false
        } else if (!wrongWaySaid && farthest - myAlong >= WRONG_WAY_M) {
            wrongWaySaid = true
            return Announcement.WrongWay(RoutePhrases.wrongWay(lang))
        }
        // Skip turns already behind the walker (a long GPS gap), silently.
        while (current < guided.size && along[guided[current].pointIndex] < myAlong - TURN_M) current++
        turn(nowMs, myAlong)?.let { return it }
        // After the last turn too: the destination on the way in.
        val remaining = max(0f, along.last() - myAlong) + endGap
        if (approach.next(remaining) != null) {
            return Announcement.Approach(RoutePhrases.approach(route.destination.name, remaining, side, lang))
        }
        return null
    }

    /** The next turn's announcement at [myAlong], if one is due and was not said in the last [REPEAT_MS]. */
    private fun turn(nowMs: Long, myAlong: Float): Announcement? {
        if (current >= guided.size) return null
        val step = guided[current]
        val ahead = max(0f, along[step.pointIndex] - myAlong)
        val announcement = when {
            ahead <= TURN_M && turned != current -> {
                turned = current
                current++
                Announcement.Turn(RoutePhrases.turn(step, lang))
            }
            ahead <= PREPARE_M && ahead > TURN_M && prepared != current -> {
                prepared = current
                Announcement.Prepare(RoutePhrases.prepare(step, ahead, stepLengthM, lang))
            }
            else -> null
        } ?: return null
        val last = saidAt[announcement.text]
        if (last != null && nowMs - last < REPEAT_MS) return null
        saidAt[announcement.text] = nowMs
        return announcement
    }

    /**
     * What the Go screen shows, without changing guidance state: the next turn point (or the destination
     * after the last turn), its instruction, the distance to it and the distance left along the route.
     */
    fun peek(at: LatLon): GoState {
        val (_, myAlong) = nearest(project(at))
        var i = current
        while (i < guided.size && along[guided[i].pointIndex] < myAlong - TURN_M) i++
        val remaining = max(0f, along.last() - myAlong) + Beacon.distanceMetres(route.line.last(), route.destination.point).toFloat()
        if (i >= guided.size) {
            val d = Beacon.distanceMetres(at, route.destination.point).toFloat()
            return GoState(route.destination.point, RoutePhrases.headTo(route.destination.name, lang), d, max(d, 0f), final = true)
        }
        val step = guided[i]
        return GoState(step.maneuver, RoutePhrases.display(step, lang), max(0f, along[step.pointIndex] - myAlong), remaining)
    }

    /** Distance to the line and progress along it, in metres. */
    private fun nearest(p: DoubleArray): Pair<Float, Float> {
        var best = Float.MAX_VALUE
        var bestAlong = 0f
        for (i in 0 until xy.size - 1) {
            val a = xy[i]
            val b = xy[i + 1]
            val dx = b[0] - a[0]
            val dy = b[1] - a[1]
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2).coerceIn(0.0, 1.0)
            val qx = a[0] + t * dx
            val qy = a[1] + t * dy
            val d = sqrt((p[0] - qx) * (p[0] - qx) + (p[1] - qy) * (p[1] - qy)).toFloat()
            if (d < best) {
                best = d
                bestAlong = along[i] + (t * sqrt(len2)).toFloat()
            }
        }
        return best to bestAlong
    }

    private fun project(p: LatLon): DoubleArray = doubleArrayOf(
        (p.lon - origin.lon) * cos(Math.toRadians(origin.lat)) * METRES_PER_DEG_LON,
        (p.lat - origin.lat) * METRES_PER_DEG_LAT,
    )

    private fun dist(a: DoubleArray, b: DoubleArray): Float =
        sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1])).toFloat()

    companion object {
        const val PREPARE_M = 25f
        const val TURN_M = 5f
        const val ARRIVED_M = 15.0
        const val OFF_ROUTE_M = 40f
        const val OFF_ROUTE_FIXES = 3
        const val REPEAT_MS = 10_000L

        /** This far back from the farthest point reached, still on the route, is walking the wrong way (spec §4). */
        const val WRONG_WAY_M = 15f
        private const val METRES_PER_DEG_LAT = 110_540.0
        private const val METRES_PER_DEG_LON = 111_320.0
    }
}

/**
 * One screenful of Go-mode direction: where the arrow points and what to show under it. [final]: the target is
 * the destination, no turn is left.
 */
data class GoState(val target: LatLon, val instruction: String, val toTargetM: Float, val remainingM: Float, val final: Boolean = false)

/** The side of the street a place is on, as the walker faces along the route. */
enum class Side { LEFT, RIGHT }

/** Arrow angle for the Go screen: the target's bearing relative to where the phone points, -180..180 (0 = straight ahead). */
object GoMath {
    fun arrowDeg(here: LatLon, target: LatLon, headingDeg: Float): Float {
        var rel = (Beacon.bearingDeg(here, target) - headingDeg).toFloat() % 360f
        if (rel > 180f) rel -= 360f
        if (rel <= -180f) rel += 360f
        return rel
    }

    /**
     * Which side of the line from [from] to [to] (walked that way) [point] is on; null within [SIDE_MIN_M] of the
     * line, where "left" or "right" would be a guess.
     */
    fun side(from: LatLon, to: LatLon, point: LatLon): Side? {
        val k = cos(Math.toRadians(to.lat)) * METRES_PER_DEG_LON
        val dx = (to.lon - from.lon) * k
        val dy = (to.lat - from.lat) * METRES_PER_DEG_LAT
        val vx = (point.lon - to.lon) * k
        val vy = (point.lat - to.lat) * METRES_PER_DEG_LAT
        val length = sqrt(dx * dx + dy * dy)
        if (length == 0.0) return null
        val cross = dx * vy - dy * vx
        if (kotlin.math.abs(cross) / length <= SIDE_MIN_M) return null
        return if (cross > 0) Side.LEFT else Side.RIGHT
    }

    const val SIDE_MIN_M = 3.0
    private const val METRES_PER_DEG_LAT = 110_540.0
    private const val METRES_PER_DEG_LON = 111_320.0
}

/** At most one new route request every 30 s, and none for 60 s after a 429 (spec §4, §6). */
class RerouteGate {
    private var lastAt = Long.MIN_VALUE / 2
    private var blockedUntil = Long.MIN_VALUE / 2

    fun allow(nowMs: Long): Boolean {
        if (nowMs < blockedUntil || nowMs - lastAt < COOLDOWN_MS) return false
        lastAt = nowMs
        return true
    }

    fun tooManyRequests(nowMs: Long) {
        blockedUntil = nowMs + BACKOFF_MS
    }

    companion object {
        const val COOLDOWN_MS = 30_000L
        const val BACKOFF_MS = 60_000L
    }
}

/** What navigation says. English uses openrouteservice's own instruction; Korean uses the turn type. */
object RoutePhrases {
    const val ATTRIBUTION = "Routing by openrouteservice, map data from OpenStreetMap contributors."

    private val EN = mapOf(
        0 to "turn left", 1 to "turn right", 2 to "turn sharp left", 3 to "turn sharp right",
        4 to "bear left", 5 to "bear right", 6 to "go straight", 7 to "enter the roundabout",
        8 to "leave the roundabout", 9 to "turn around", 10 to "you have arrived", 11 to "start walking",
        12 to "keep left", 13 to "keep right",
    )
    private val KO = mapOf(
        0 to "왼쪽으로 도세요", 1 to "오른쪽으로 도세요", 2 to "왼쪽으로 크게 도세요", 3 to "오른쪽으로 크게 도세요",
        4 to "왼쪽으로 살짝 도세요", 5 to "오른쪽으로 살짝 도세요", 6 to "직진하세요", 7 to "로터리로 들어가세요",
        8 to "로터리에서 나가세요", 9 to "뒤로 돌아가세요", 10 to "도착했어요", 11 to "출발하세요",
        12 to "왼쪽 길로 가세요", 13 to "오른쪽 길로 가세요",
    )

    fun typePhrase(type: Int, lang: Lang): String =
        if (lang == Lang.KO) KO[type] ?: "계속 가세요" else EN[type] ?: "continue"

    /** English instruction, or the type phrase when the instruction is empty. */
    private fun what(step: RouteStep, lang: Lang): String =
        if (lang == Lang.EN && step.instruction.isNotBlank()) step.instruction.trim().trimEnd('.') else typePhrase(step.type, lang)

    /** Metres rounded to 5 m, never 0; or steps when the step length is known. */
    fun distance(metres: Float, stepLengthM: Float?, lang: Lang): String {
        if (stepLengthM != null) {
            val n = StepLength.steps(metres, stepLengthM)
            return if (lang == Lang.KO) KoNumbers.count(n, "걸음") else if (n == 1) "1 step" else "$n steps"
        }
        val m = max(5, (metres / 5f).roundToInt() * 5)
        return if (lang == Lang.KO) "${m}미터" else "$m metres"
    }

    fun prepare(step: RouteStep, metres: Float, stepLengthM: Float?, lang: Lang): String {
        val d = distance(metres, stepLengthM, lang)
        return if (lang == Lang.KO) "$d 앞에서 ${typePhrase(step.type, lang)}." else "In $d, ${what(step, lang).replaceFirstChar { it.lowercase() }}."
    }

    fun turn(step: RouteStep, lang: Lang): String =
        if (lang == Lang.KO) "지금 ${typePhrase(step.type, lang)}." else "${typePhrase(step.type, lang).replaceFirstChar { it.uppercase() }} now."

    fun arrived(name: String, lang: Lang, side: Side? = null): String = when {
        lang == Lang.KO && side != null -> "${name}에 도착했어요. ${sideKo(side)}에 있어요."
        lang == Lang.KO -> "${name}에 도착했어요."
        side != null -> "You have arrived at $name, on your ${sideEn(side)}."
        else -> "You have arrived at $name."
    }

    /** "Seoul Station in 50 metres, on your right." (spec §5) */
    fun approach(name: String, metres: Float, side: Side?, lang: Lang): String {
        val d = WalkPhrases.far(metres.toDouble(), lang)
        if (lang == Lang.KO) return "$d 앞${side?.let { " ${sideKo(it)}" } ?: ""}에 ${Josa.iGa(name)} 있어요."
        return "$name in $d" + (side?.let { ", on your ${sideEn(it)}" } ?: "") + "."
    }

    fun wrongWay(lang: Lang): String =
        if (lang == Lang.KO) "경로에서 멀어지고 있어요. 뒤로 돌아가세요." else "You are walking away from the route. Turn around."

    /** "Next, turn right onto Park Road in 80 metres." / after the last turn "Next, Seoul Station in 80 metres." */
    fun next(state: GoState, name: String, lang: Lang): String {
        val d = WalkPhrases.far(state.toTargetM.toDouble(), lang)
        if (lang == Lang.KO) return if (state.final) "다음은 $d 앞 ${name}이에요." else "다음은 $d 후 ${state.instruction}."
        val what = if (state.final) name else state.instruction.replaceFirstChar { it.lowercase() }
        return "Next, $what in $d."
    }

    /** [name] null: the next turn; else the destination (after the last turn, or by beacon). */
    fun whichWay(clock: Int, metres: Float, name: String?, lang: Lang): String {
        val d = WalkPhrases.far(metres.toDouble(), lang)
        if (lang == Lang.KO) return if (name == null) "다음 갈림길은 ${clock}시 방향, ${d}예요." else "${Josa.eunNeun(name)} ${clock}시 방향, ${d}예요."
        return if (name == null) "The next turn is at $clock o'clock, $d." else "$name is at $clock o'clock, $d."
    }

    fun noHeading(lang: Lang): String =
        if (lang == Lang.KO) "아직 방향을 알 수 없어요. 휴대폰을 세워 들고 다시 물어봐 주세요."
        else "I can't tell the direction yet. Hold the phone up and ask again."

    fun noRouteRunning(lang: Lang): String =
        if (lang == Lang.KO) "안내 중인 경로가 없어요. 어디로 가자고 말해 주세요." else "No route is running. Say go to, and a place."

    private fun sideEn(side: Side) = if (side == Side.RIGHT) "right" else "left"

    private fun sideKo(side: Side) = if (side == Side.RIGHT) "오른쪽" else "왼쪽"

    fun offRoute(lang: Lang): String =
        if (lang == Lang.KO) "경로를 벗어났어요. 새 경로를 찾을게요." else "Off the route, finding a new one."

    fun unavailable(lang: Lang): String =
        if (lang == Lang.KO) "경로를 찾을 수 없어서 방향으로 안내할게요." else "Route is not available, guiding you by direction."

    fun notSetUp(lang: Lang): String =
        if (lang == Lang.KO) "길 안내가 설정되지 않아서 방향으로 안내할게요." else "Navigation is not set up, guiding you by direction."

    fun noRoute(lang: Lang): String =
        if (lang == Lang.KO) "그곳으로 가는 걷는 길을 찾지 못했어요." else "I could not find a walking route there."

    fun started(name: String, totalM: Float, lang: Lang): String {
        val d = WalkPhrases.far(totalM.toDouble(), lang)
        return if (lang == Lang.KO) "${name}까지 $d 안내할게요." else "Guiding you to $name, $d."
    }

    /** The next instruction as shown on screen: openrouteservice's English text, or the turn in Korean. */
    fun display(step: RouteStep, lang: Lang): String =
        if (lang == Lang.EN && step.instruction.isNotBlank()) step.instruction.trim().trimEnd('.')
        else typePhrase(step.type, lang).replaceFirstChar { it.uppercase() }

    /** "In 40 metres · 240 metres left" / "40미터 후 · 240미터 남았어요" */
    fun goDistance(toTargetM: Float, remainingM: Float, lang: Lang): String {
        val next = WalkPhrases.far(toTargetM.toDouble(), lang)
        val left = WalkPhrases.far(remainingM.toDouble(), lang)
        return if (lang == Lang.KO) "$next 후 · $left 남았어요" else "In $next · $left left"
    }

    /** "less than a minute" under 45 s, "about 6 minutes", "about 1 hour 20 minutes" (spec §1). */
    fun timeLeft(seconds: Float, lang: Lang): String {
        if (seconds < LESS_THAN_A_MINUTE_S) return if (lang == Lang.KO) "1분도 안 걸려요" else "less than a minute"
        val minutes = (seconds / 60f).roundToInt().coerceAtLeast(1)
        val h = minutes / 60
        val m = minutes % 60
        if (lang == Lang.KO) return if (h == 0) "약 ${m}분" else if (m == 0) "약 ${h}시간" else "약 ${h}시간 ${m}분"
        fun unit(n: Int, one: String) = if (n == 1) "1 $one" else "$n ${one}s"
        return "about " + when {
            h == 0 -> unit(m, "minute")
            m == 0 -> unit(h, "hour")
            else -> unit(h, "hour") + " " + unit(m, "minute")
        }
    }

    /** The minute update: "350 metres left, about 6 minutes." By beacon the distance is a straight line. */
    fun progress(remainingM: Float, seconds: Float, straight: Boolean, lang: Lang): String {
        val d = WalkPhrases.far(remainingM.toDouble(), lang)
        val t = timeLeft(seconds, lang)
        return when {
            lang == Lang.KO && straight -> "직선으로 $d, $t."
            lang == Lang.KO -> "$d 남았어요, $t."
            straight -> "$d in a straight line, $t."
            else -> "$d left, $t."
        }
    }

    private const val LESS_THAN_A_MINUTE_S = 45f

    fun whereTo(lang: Lang): String = if (lang == Lang.KO) "어디로 갈까요?" else "Where to?"

    fun headTo(name: String, lang: Lang): String =
        if (lang == Lang.KO) "${WalkPhrases.euro(name)} 가세요" else "Head to $name"

    fun navigationStopped(lang: Lang): String =
        if (lang == Lang.KO) "길 안내를 멈췄어요." else "Navigation stopped."

    fun attribution(lang: Lang): String =
        if (lang == Lang.KO) "경로 제공 openrouteservice, 지도 데이터 OpenStreetMap 기여자." else ATTRIBUTION

    /** Geocode confirmation: "Seoul Station, 400 metres away. Say yes to go." With [more] places, also how to hear the next. */
    fun confirm(label: String, metres: Double, lang: Lang, more: Boolean = false): String {
        val d = WalkPhrases.far(metres, lang)
        return if (lang == Lang.KO) {
            if (more) "$label, $d 거리예요. 가려면 네, 다른 곳은 다음이라고 말해 주세요." else "$label, $d 거리예요. 가려면 네라고 말해 주세요."
        } else {
            if (more) "$label, $d away. Say yes to go, or next for another place." else "$label, $d away. Say yes to go."
        }
    }

    /** A place that exists but is out of walking reach: say so instead of "I could not find that place". */
    fun tooFar(name: String, metres: Double, lang: Lang): String {
        val d = WalkPhrases.far(metres, lang)
        return if (lang == Lang.KO) "${name}까지 ${d}예요. 걸어가기에는 너무 멀어요." else "$name is $d away. That is too far to walk."
    }

    /** The place search did not answer (no network, or the day's quota is used up): not "no such place". */
    fun searchUnavailable(lang: Lang): String =
        if (lang == Lang.KO) "지금은 장소를 검색할 수 없어요. 잠시 후에 다시 해 주세요." else "Place search is not available right now. Try again later."

    fun noSavedHome(lang: Lang): String =
        if (lang == Lang.KO) "아직 집으로 저장된 곳이 없어요. 집에 있을 때 '여기를 집으로 저장해 줘'라고 말해 주세요."
        else "No place is saved as home yet. When you are there, say: save this place as home."

    /** Every place found, numbered: "1: Bus Terminal, 400 metres. 2: … Say the number." */
    fun options(places: List<Pair<String, Double>>, lang: Lang): String {
        val list = places.mapIndexed { i, (name, metres) ->
            if (lang == Lang.KO) "${i + 1}번, $name, ${WalkPhrases.far(metres, lang)}." else "${i + 1}: $name, ${WalkPhrases.far(metres, lang)}."
        }.joinToString(" ")
        return if (lang == Lang.KO) "$list 번호를 말해 주세요." else "$list Say the number."
    }

    fun askAgain(lang: Lang): String =
        if (lang == Lang.KO) "가려면 네, 다른 곳은 다음이라고 말해 주세요." else "Say yes to go, or next."

    fun noMorePlaces(lang: Lang): String =
        if (lang == Lang.KO) "더 찾은 곳이 없어요. 어디로 갈까요?" else "No more places. Where to?"

    fun isYes(text: String): Boolean = GoAnswer.of(text) == GoAnswer.Yes

    fun isNo(text: String): Boolean = GoAnswer.of(text) == GoAnswer.Next
}

/**
 * What the user answers when a found place is read out. Only a whole answer counts ("yes please",
 * "not that one", "the second one"): "OK Mart" or "North Station" is another place, not a yes or a no.
 */
sealed interface GoAnswer {
    data object Yes : GoAnswer

    /** "no", "next": the next place found. */
    data object Next : GoAnswer

    /** "the second one": [index] counts from 0. */
    data class Pick(val index: Int) : GoAnswer

    /** "what are the options", "list them": read every place found. */
    data object Options : GoAnswer

    /** Not an answer; most likely another place. */
    data class Other(val text: String) : GoAnswer

    companion object {
        private val YES = setOf(
            "yes", "yeah", "yep", "yup", "ya", "yah", "ok", "okay", "sure", "correct", "go", "start",
            "네", "예", "응", "그래", "좋아", "좋아요", "가자", "맞아", "맞아요",
        )
        private val NO = setOf(
            "no", "nope", "nah", "next", "another", "other", "skip", "wrong", "not",
            "아니", "아니요", "아니야", "아니오", "다음", "다른",
        )
        private val FILLERS = setOf(
            "please", "that", "this", "one", "it", "the", "there", "thanks", "thank", "you", "lets", "let", "s", "is", "do",
            "곳", "거", "걸로", "으로", "로",
        )
        private val OPTIONS = setOf("options", "option", "choices", "list", "results", "all", "목록", "전부", "모두")
        private val OPTION_FILLERS = setOf(
            "show", "me", "what", "are", "the", "available", "my", "them", "of", "read", "tell", "say", "which", "ones",
            "there", "is", "please", "보여", "줘", "알려",
        )
        private val ORDINALS = listOf(
            setOf("first", "1st", "첫번째", "첫째"),
            setOf("second", "2nd", "두번째", "둘째"),
            setOf("third", "3rd", "세번째", "셋째"),
        )
        private val NUMBERS = listOf(
            setOf("one", "1", "하나", "일번", "1번"),
            setOf("two", "2", "둘", "이번", "2번"),
            setOf("three", "3", "셋", "삼번", "3번"),
        )

        fun of(text: String): GoAnswer {
            val clean = text.trim().trimEnd('.', '!', '?', ',').trim()
            val words = clean.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return Other(clean)
            val joined = words.joinToString("")
            ORDINALS.forEachIndexed { i, names -> if (words.any { it in names } || names.any { it == joined }) return Pick(i) }
            // "two", "number two": a bare number, not a place that has one in its name ("One Mount").
            val counted = words.filter { it != "number" && it != "번" }
            if (counted.size == 1) NUMBERS.forEachIndexed { i, names -> if (counted[0] in names) return Pick(i) }
            if (words.any { it in OPTIONS } && words.all { it in OPTIONS || it in OPTION_FILLERS }) return Options
            val rest = words.filter { it !in FILLERS }
            if (rest.isNotEmpty() && rest.all { it in NO || it in YES } && rest.any { it in NO }) return Next
            if (rest.isNotEmpty() && rest.all { it in YES }) return Yes
            return Other(clean)
        }
    }
}
