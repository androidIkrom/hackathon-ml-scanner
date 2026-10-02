package com.nungil.core.walk

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa
import com.nungil.core.lang.KoNumbers
import com.nungil.core.lang.LabelNames
import kotlin.math.roundToInt

/**
 * Everything walk mode says, in English and Korean. It reports what it sees and never promises that
 * the way is clear: no sentence here contains "safe" or "안전".
 */
object WalkPhrases {
    const val CODE_MAX_CHARS = 80

    /** Closer than this is "very close"; from here up it is at least "1 step" / "1 metre". */
    const val VERY_CLOSE_M = 0.5f

    private fun ko(lang: Lang) = lang == Lang.KO

    /** "3 steps" / "세 걸음" when a step length is known, else "2 metres" / "2미터"; under 0.5 m "very close". */
    fun distance(metres: Float, stepM: Float?, lang: Lang): String {
        if (metres < VERY_CLOSE_M) return if (ko(lang)) "아주 가까워요" else "very close"
        if (stepM != null) {
            val n = StepLength.steps(metres, stepM)
            return if (ko(lang)) KoNumbers.count(n, "걸음") else if (n == 1) "1 step" else "$n steps"
        }
        val m = maxOf(1, metres.roundToInt())
        return if (ko(lang)) "${m}미터" else if (m == 1) "1 metre" else "$m metres"
    }

    /** The number [distance] would say: 0 for "very close", else steps or whole metres. */
    fun distanceLevel(metres: Float, stepM: Float?): Int = when {
        metres < VERY_CLOSE_M -> 0
        stepM != null -> StepLength.steps(metres, stepM)
        else -> maxOf(1, metres.roundToInt())
    }

    private fun zoneEn(zone: Zone) = when (zone) {
        Zone.AHEAD -> "ahead"
        Zone.LEFT -> "on your left"
        Zone.RIGHT -> "on your right"
    }

    private fun zoneKo(zone: Zone) = when (zone) {
        Zone.AHEAD -> "앞에"
        Zone.LEFT -> "왼쪽에"
        Zone.RIGHT -> "오른쪽에"
    }

    fun wall(zone: Zone, metres: Float, stepM: Float?, lang: Lang): String {
        val d = distance(metres, stepM, lang)
        return if (ko(lang)) {
            if (zone == Zone.AHEAD) "앞에 벽이 있어요, $d." else "${zoneKo(zone)} 장애물이 있어요, $d."
        } else {
            if (zone == Zone.AHEAD) "Wall ahead, $d." else "Obstacle ${zoneEn(zone)}, $d."
        }
    }

    /**
     * "Stairs going down in 5 steps." The distance is said as "in N steps", so it is never mistaken for
     * the number of stairs.
     */
    fun floor(change: FloorChange, metres: Float, stepM: Float?, lang: Lang): String {
        val veryClose = metres < VERY_CLOSE_M
        return if (ko(lang)) {
            val where = if (veryClose) "바로 앞에" else "${distance(metres, stepM, lang)} 앞에"
            when (change) {
                FloorChange.STEP_UP -> "$where 올라가는 턱이 있어요."
                FloorChange.DROP -> "$where 내려가는 곳이 있어요."
                FloorChange.STAIRS -> "$where 올라가는 계단이 있어요."
                FloorChange.STAIRS_DOWN -> "$where 내려가는 계단이 있어요."
            }
        } else {
            val what = when (change) {
                FloorChange.STEP_UP -> "Step up"
                FloorChange.DROP -> "Going down"
                FloorChange.STAIRS -> "Stairs going up"
                FloorChange.STAIRS_DOWN -> "Stairs going down"
            }
            if (veryClose) "$what, very close." else "$what in ${distance(metres, stepM, lang)}."
        }
    }

    /** [label] is a COCO label; [metres] null when depth is not available. */
    fun hazard(label: String, zone: Zone, metres: Float?, stepM: Float?, lang: Lang): String {
        val d = metres?.let { distance(it, stepM, lang) }
        return if (ko(lang)) {
            val base = "${zoneKo(zone)} ${Josa.iGa(LabelNames.name(label, lang))} 있어요"
            if (d == null) "$base." else "$base, $d."
        } else {
            val name = label.replaceFirstChar { it.uppercase() }
            if (d == null) "$name ${zoneEn(zone)}." else "$name ${zoneEn(zone)}, $d."
        }
    }

    fun obstacle(nameEn: String, nameKo: String, lang: Lang): String =
        if (ko(lang)) "앞에 ${Josa.iGa(nameKo)} 있어요." else "${nameEn.replaceFirstChar { it.uppercase() }} ahead."

    fun ground(kind: GroundKind, lang: Lang): String = if (ko(lang)) {
        when (kind) {
            GroundKind.ROAD -> "발밑이 차도예요."
            GroundKind.SIDEWALK -> "인도예요."
            GroundKind.TERRAIN -> "흙길이에요."
        }
    } else {
        when (kind) {
            GroundKind.ROAD -> "Road under your feet."
            GroundKind.SIDEWALK -> "On the sidewalk."
            GroundKind.TERRAIN -> "Grass or dirt underfoot."
        }
    }

    fun light(color: LightColor, lang: Lang): String = when (color) {
        LightColor.RED -> if (ko(lang)) "빨간불이에요." else "Red light."
        LightColor.GREEN -> if (ko(lang)) "초록불이에요." else "Green light."
    }

    fun saved(name: String, zone: Zone, lang: Lang): String =
        if (ko(lang)) "${zoneKo(zone)} ${Josa.iGa(name)} 있어요." else "$name ${zoneEn(zone)}."

    fun sign(text: String, lang: Lang): String = if (ko(lang)) "표지판: ${text.trim()}." else "Sign: ${text.trim()}."

    fun code(text: String, lang: Lang): String {
        val t = text.trim().take(CODE_MAX_CHARS)
        return if (ko(lang)) "코드: $t." else "Code: $t."
    }

    /** Beacon for a saved place: "Home, 300 metres, at 2 o'clock." */
    fun beacon(name: String, metres: Double, clock: Int, lang: Lang): String {
        val d = far(metres, lang)
        return if (ko(lang)) "${name}까지 $d, ${clock}시 방향이에요." else "$name, $d, at $clock o'clock."
    }

    /** 5 m steps under 100 m, 10 m steps under 1 km, then kilometres with one decimal. */
    fun far(metres: Double, lang: Lang): String {
        if (metres >= 1_000) {
            val km = (metres / 100).roundToInt() / 10.0
            return if (ko(lang)) "${km}킬로미터" else "$km kilometres"
        }
        val step = if (metres < 100) 5 else 10
        val m = maxOf(step, ((metres / step).roundToInt() * step))
        return if (ko(lang)) "${m}미터" else "$m metres"
    }

    /** Said once when the way ahead clears after a warning. Factual, never "safe". */
    fun nothingAhead(lang: Lang): String =
        if (ko(lang)) "가까운 장애물은 없어요." else "Nothing close ahead."

    /** Depth has been gone for a while: walls, steps and stairs are not being checked. */
    fun depthLost(lang: Lang): String =
        if (ko(lang)) "지금은 거리를 잴 수 없어요. 휴대폰을 천천히 움직여 주세요."
        else "I can't measure distance right now. Move the phone slowly."

    /** Not enough light for ARCore: nothing about walls or steps can be said, so say that plainly. */
    fun blankAhead(lang: Lang): String =
        if (ko(lang)) "앞이 보이지 않아요. 벽일 수 있으니 천천히 가세요."
        else "I can't see ahead. It may be a plain wall. Go slowly."

    fun tooDark(lang: Lang): String =
        if (ko(lang)) "너무 어두워서 거리를 잴 수 없어요. 벽과 계단을 알려 드릴 수 없으니 천천히 걸으세요."
        else "It's too dark to measure distance. I can't warn you about walls or steps, so walk slowly."

    fun depthBack(lang: Lang): String =
        if (ko(lang)) "다시 거리를 잴 수 있어요." else "I can measure distance again."

    fun askPlaceName(lang: Lang): String =
        if (ko(lang)) "이 장소 이름을 뭐라고 할까요?" else "What should I call this place?"

    fun alreadyAt(name: String, lang: Lang): String =
        if (ko(lang)) "지금 ${name}에 있어요." else "You are at $name."

    fun placeSaved(name: String, lang: Lang): String =
        if (ko(lang)) "이 장소를 ${euro(name)} 저장했어요." else "Saved this place as $name."

    fun unknownPlace(lang: Lang): String =
        if (ko(lang)) "그 장소를 찾을 수 없어요." else "I could not find that place."

    fun waitingForLocation(lang: Lang): String =
        if (ko(lang)) "위치를 확인하고 있어요." else "Waiting for your location."

    fun started(lang: Lang): String =
        if (ko(lang)) "걷기 모드예요. 장애물과 턱, 계단을 알려 드릴게요." else "Walk mode. I will tell you about obstacles, steps and stairs."

    fun noDepth(lang: Lang): String =
        if (ko(lang)) "이 휴대폰은 거리를 잴 수 없어서 사람과 차 같은 것만 알려 드려요."
        else "This phone cannot measure depth, so I only tell you about people, vehicles and other things I recognise."

    fun needArCore(lang: Lang): String =
        if (ko(lang)) "걷기 모드에는 AR용 Google Play 서비스가 필요해요." else "Walk mode needs Google Play Services for AR."

    fun needCamera(lang: Lang): String =
        if (ko(lang)) "걷기 모드는 카메라가 필요해요. 카메라를 허용해 주세요." else "Walk mode needs the camera. Please allow it."

    fun needLocation(lang: Lang): String =
        if (ko(lang)) "길 안내에는 위치 권한이 필요해요." else "Navigation needs your location. Please allow it."

    fun stopped(lang: Lang): String = if (ko(lang)) "걷기 모드를 멈췄어요." else "Walk mode stopped."

    /** 으로 after a consonant (except ㄹ), 로 after a vowel or ㄹ. */
    fun euro(word: String): String {
        val c = word.trimEnd().lastOrNull { it.isLetterOrDigit() }
        val hangul = c != null && c.code in 0xAC00..0xD7A3
        val jong = if (hangul) (c!!.code - 0xAC00) % 28 else -1
        val withEu = if (hangul) jong != 0 && jong != 8 else Josa.hasBatchim(word) && !word.endsWith("l", ignoreCase = true)
        return word + if (withEu) "으로" else "로"
    }
}
