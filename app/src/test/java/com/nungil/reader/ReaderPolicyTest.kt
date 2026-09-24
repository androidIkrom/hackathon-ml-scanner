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

    @Test fun longestLineOfAtLeastThreeCharacters() {
        assertEquals("EXIT ONLY", ReaderPolicy.longestLine(listOf("A", " EXIT ONLY ", "EXIT")))
        assertNull(ReaderPolicy.longestLine(listOf("A", "BC", "  ")))
    }

    @Test fun noRepeatWithinTenSeconds() {
        val p = ReaderPolicy()
        assertTrue(p.shouldSpeak("EXIT", 0))
        assertFalse(p.shouldSpeak("EXIT", 9_999))
        assertTrue(p.shouldSpeak("PUSH", 5_000))
        assertTrue(p.shouldSpeak("EXIT", 10_000))
    }
}
