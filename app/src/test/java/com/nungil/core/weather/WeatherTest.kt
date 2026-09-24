package com.nungil.core.weather

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val sample = """
        {"latitude":37.56,"longitude":126.98,
         "current":{"time":"2026-09-25T14:00","temperature_2m":18.4,"weather_code":3},
         "daily":{"time":["2026-09-25"],"temperature_2m_max":[22.6],"temperature_2m_min":[13.5],
                  "precipitation_probability_max":[60]}}
    """.trimIndent()

    @Test fun parsesOpenMeteo() {
        assertEquals(Today(nowC = 18, code = 3, highC = 23, lowC = 14, rainChance = 60), Weather.parse(sample))
        val noRain = sample.replace("[60]", "[null]")
        assertNull(Weather.parse(noRain).rainChance)
    }

    @Test fun skyFromWmoCodes() {
        assertEquals(Sky.CLEAR, Weather.sky(0))
        assertEquals(Sky.PARTLY_CLOUDY, Weather.sky(2))
        assertEquals(Sky.CLOUDY, Weather.sky(3))
        assertEquals(Sky.FOG, Weather.sky(45))
        assertEquals(Sky.DRIZZLE, Weather.sky(53))
        assertEquals(Sky.RAIN, Weather.sky(63))
        assertEquals(Sky.RAIN, Weather.sky(81))
        assertEquals(Sky.SNOW, Weather.sky(75))
        assertEquals(Sky.STORM, Weather.sky(95))
    }

    @Test fun refreshAfterThirtyMinutes() {
        assertTrue(Weather.needsRefresh(0, null))
        assertFalse(Weather.needsRefresh(29 * 60_000L, 0))
        assertTrue(Weather.needsRefresh(30 * 60_000L, 0))
    }

    @Test fun urlAsksForTodayOnly() {
        val url = Weather.url(37.5665, 126.978)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=37.567&longitude=126.978"))
        assertTrue(url.contains("forecast_days=1"))
    }

    @Test fun labelAndSpeech() {
        val t = Today(18, 3, 23, 14, 60)
        assertEquals("18° Cloudy", WeatherPhrases.label(t, Lang.EN))
        assertEquals("18° 흐림", WeatherPhrases.label(t, Lang.KO))
        assertEquals(
            "Today: now 18 degrees, cloudy. High 23, low 14. Chance of rain 60 percent.",
            WeatherPhrases.spoken(t, Lang.EN),
        )
        assertEquals(
            "오늘 날씨: 지금 18도, 흐림. 최고 23도, 최저 14도. 비 올 확률은 60퍼센트예요.",
            WeatherPhrases.spoken(t, Lang.KO),
        )
        assertEquals(
            "Today: now 18 degrees, cloudy. High 23, low 14. This is from 3 hours ago.",
            WeatherPhrases.spoken(t.copy(rainChance = null), Lang.EN, ageMs = 3 * 3_600_000L),
        )
        assertTrue(WeatherPhrases.spoken(t, Lang.KO, ageMs = 2 * 3_600_000L).endsWith("2시간 전 정보예요."))
    }
}
