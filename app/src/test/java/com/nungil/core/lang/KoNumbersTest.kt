package com.nungil.core.lang

import org.junit.Assert.assertEquals
import org.junit.Test

class KoNumbersTest {
    @Test fun nativeNumbers() {
        assertEquals("한 개", KoNumbers.count(1, "개"))
        assertEquals("두 명", KoNumbers.count(2, "명"))
        assertEquals("세 대", KoNumbers.count(3, "대"))
        assertEquals("열한 권", KoNumbers.count(11, "권"))
        assertEquals("스무 개", KoNumbers.count(20, "개"))
    }

    @Test fun digitsAboveTwenty() = assertEquals("21개", KoNumbers.count(21, "개"))

    @Test(expected = IllegalArgumentException::class)
    fun zeroIsRejected() {
        KoNumbers.count(0, "개")
    }
}
