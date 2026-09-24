package com.nungil.core.weather

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test fun dates() {
        assertEquals("Thu, Sep 25", Clock.dateLabel(9, 25, 4, Lang.EN))
        assertEquals("9월 25일 (목)", Clock.dateLabel(9, 25, 4, Lang.KO))
        assertEquals("Sunday, January 1.", Clock.dateSpoken(1, 1, 7, Lang.EN))
        assertEquals("12월 31일 월요일이에요.", Clock.dateSpoken(12, 31, 1, Lang.KO))
    }

    @Test fun timeLabels() {
        assertEquals("14:05", Clock.timeLabel(14, 5, true, Lang.EN))
        assertEquals("09:00", Clock.timeLabel(9, 0, true, Lang.KO))
        assertEquals("2:05 PM", Clock.timeLabel(14, 5, false, Lang.EN))
        assertEquals("12:30 AM", Clock.timeLabel(0, 30, false, Lang.EN))
        assertEquals("오후 12:00", Clock.timeLabel(12, 0, false, Lang.KO))
    }

    @Test fun spokenTime() {
        assertEquals("It is 2:05 PM.", Clock.timeSpoken(14, 5, Lang.EN))
        assertEquals("지금 오후 2시 5분이에요.", Clock.timeSpoken(14, 5, Lang.KO))
        assertEquals("지금 오전 9시예요.", Clock.timeSpoken(9, 0, Lang.KO))
    }
}
