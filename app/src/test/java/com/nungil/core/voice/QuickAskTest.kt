package com.nungil.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAskTest {
    @Test fun theTimeAsItWasAskedInTheLogs() {
        for (heard in listOf("What time", "I tell me time", "Hi tell me time", "Tell me time", "what time is it", "What's the time?", "time", "지금 몇 시야")) {
            assertEquals(heard, QuickAsk.TIME, QuickAsk.of(heard))
        }
    }

    @Test fun theWeatherAsItWasAskedInTheLogs() {
        for (heard in listOf("Tell me weather", "Hi tell me the weather", "I tell me weather", "weather", "what is the weather today", "오늘 날씨 어때")) {
            assertEquals(heard, QuickAsk.WEATHER, QuickAsk.of(heard))
        }
    }

    @Test fun theDate() {
        for (heard in listOf("what day is it", "what is the date", "today's date", "오늘 며칠이야")) {
            assertEquals(heard, QuickAsk.DATE, QuickAsk.of(heard))
        }
    }

    @Test fun otherWordsWithTimeInThemAreNotAQuestion() {
        for (heard in listOf("one more time", "next time", "Time Square", "Reel Time Theater", "Go Seoul", "stop", "")) {
            assertNull(heard, QuickAsk.of(heard))
        }
    }
}
