package com.nungil.core.weather

import com.nungil.contract.Lang
import org.json.JSONObject
import kotlin.math.roundToInt

/** Today's weather at the phone's place. Temperatures in °C, [rainChance] in percent (null when not given). */
data class Today(val nowC: Int, val code: Int, val highC: Int, val lowC: Int, val rainChance: Int?)

/** The WMO weather codes Open-Meteo returns, grouped into what is worth saying. */
enum class Sky { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

object Weather {
    /** A stored report older than this is fetched again (when the phone is online). */
    const val REFRESH_AFTER_MS = 30 * 60_000L

    /** A report older than this is spoken with its age, so an offline answer is not taken for today's. */
    const val SAY_AGE_AFTER_MS = 60 * 60_000L

    fun sky(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY_CLOUDY
        45, 48 -> Sky.FOG
        in 51..57 -> Sky.DRIZZLE
        in 61..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun needsRefresh(nowMs: Long, fetchedAtMs: Long?): Boolean = fetchedAtMs == null || nowMs - fetchedAtMs >= REFRESH_AFTER_MS

    /** The Open-Meteo forecast request for today at one place (no key needed). */
    fun url(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(java.util.Locale.US, lat, lon) +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=1"

    /** Parses the answer to [url]; throws on anything unexpected. */
    fun parse(json: String): Today {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val rain = daily.optJSONArray("precipitation_probability_max")
        return Today(
            nowC = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
            highC = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            lowC = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            rainChance = rain?.takeUnless { it.isNull(0) }?.getInt(0),
        )
    }
}

/** What the small weather label shows and what it says when tapped, in both languages. */
object WeatherPhrases {
    fun skyWord(sky: Sky, lang: Lang): String = when (lang) {
        Lang.EN -> when (sky) {
            Sky.CLEAR -> "Clear"
            Sky.PARTLY_CLOUDY -> "Partly cloudy"
            Sky.CLOUDY -> "Cloudy"
            Sky.FOG -> "Fog"
            Sky.DRIZZLE -> "Drizzle"
            Sky.RAIN -> "Rain"
            Sky.SNOW -> "Snow"
            Sky.STORM -> "Storm"
        }
        Lang.KO -> when (sky) {
            Sky.CLEAR -> "맑음"
            Sky.PARTLY_CLOUDY -> "구름 조금"
            Sky.CLOUDY -> "흐림"
            Sky.FOG -> "안개"
            Sky.DRIZZLE -> "이슬비"
            Sky.RAIN -> "비"
            Sky.SNOW -> "눈"
            Sky.STORM -> "뇌우"
        }
    }

    /** The label: "18° Cloudy" / "18° 흐림". */
    fun label(today: Today, lang: Lang): String = "${today.nowC}° ${skyWord(Weather.sky(today.code), lang)}"

    /** The spoken report; [ageMs] adds how old it is once that is more than an hour. */
    fun spoken(today: Today, lang: Lang, ageMs: Long = 0): String {
        val sky = skyWord(Weather.sky(today.code), lang)
        val hours = (ageMs / 3_600_000L).toInt()
        val old = ageMs >= Weather.SAY_AGE_AFTER_MS
        return when (lang) {
            Lang.EN -> buildString {
                append("Today: now ${today.nowC} degrees, ${sky.lowercase()}. High ${today.highC}, low ${today.lowC}.")
                today.rainChance?.let { append(" Chance of rain $it percent.") }
                if (old) append(if (hours == 1) " This is from an hour ago." else " This is from $hours hours ago.")
            }
            Lang.KO -> buildString {
                append("오늘 날씨: 지금 ${today.nowC}도, $sky. 최고 ${today.highC}도, 최저 ${today.lowC}도.")
                today.rainChance?.let { append(" 비 올 확률은 ${it}퍼센트예요.") }
                if (old) append(" ${hours}시간 전 정보예요.")
            }
        }
    }
}
