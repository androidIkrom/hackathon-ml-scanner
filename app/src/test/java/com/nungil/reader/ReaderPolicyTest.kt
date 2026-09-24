package com.nungil.reader

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPolicyTest {
    @Test fun codesAreCutAtEightyCharacters() {
        assertEquals(80, ReaderPolicy.truncateCode("x".repeat(200)).length)
        assertEquals("short", ReaderPolicy.truncateCode("short"))
        assertEquals("Code: abc", ReaderPolicy.codePhrase("abc", Lang.EN))
        assertEquals("코드: abc", ReaderPolicy.codePhrase("abc", Lang.KO))
    }

    private fun line(text: String, confidence: Float = 0.9f, height: Float = 0.1f, x: Float = 0.5f, y: Float = 0.5f) =
        SeenLine(text, confidence, height, x, y)

    @Test fun onlyClearBigRealTextIsReadable() {
        assertTrue(ReaderPolicy.readable(line(" EXIT ")))
        assertFalse(ReaderPolicy.readable(line("BC")))                     // under 3 characters
        assertFalse(ReaderPolicy.readable(line("EXIT", confidence = 0.5f))) // ML Kit unsure
        assertFalse(ReaderPolicy.readable(line("fine print", height = 0.02f)))
        assertFalse(ReaderPolicy.readable(line("|||~~ .")))                // not letters
        assertTrue(ReaderPolicy.readable(line("출구 EXIT")))
    }

    @Test fun theBiggestTextWinsNotTheLongest() {
        val sign = line("EXIT", height = 0.2f)
        val smallPrint = line("Fire door keep shut at all times", height = 0.05f)
        val noise = line("E X 1 T ||", confidence = 0.3f, height = 0.3f)
        assertEquals("EXIT", ReaderPolicy.pickLine(listOf(smallPrint, noise, sign)))
        assertNull(ReaderPolicy.pickLine(listOf(noise, line("ab"))))
    }

    @Test fun onATieTheMiddleWins() {
        val corner = line("LEFT", x = 0.1f, y = 0.1f)
        val middle = line("PUSH", x = 0.55f, y = 0.5f)
        assertEquals("PUSH", ReaderPolicy.pickLine(listOf(corner, middle)))
    }

    @Test fun textMustBeSeenInTwoReadsInARow() {
        val p = ReaderPolicy()
        assertFalse(p.steady("EXIT"))
        assertTrue(p.steady("EXIT"))
        assertFalse(p.steady("EX1T"))  // a misreading breaks the run
        assertFalse(p.steady(null))
        assertFalse(p.steady("EXIT"))
        assertTrue(p.steady("EXIT"))
    }

    @Test fun noRepeatWithinTenSeconds() {
        val p = ReaderPolicy()
        assertTrue(p.shouldSpeak("EXIT", 0))
        assertFalse(p.shouldSpeak("EXIT", 9_999))
        assertTrue(p.shouldSpeak("PUSH", 5_000))
        assertTrue(p.shouldSpeak("EXIT", 10_000))
    }
}
