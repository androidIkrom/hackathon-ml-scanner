package com.nungil.core.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JosaTest {
    @Test fun hangulBatchim() {
        assertTrue(Josa.hasBatchim("책"))
        assertFalse(Josa.hasBatchim("의자"))
        assertTrue(Josa.hasBatchim("한 명"))
        assertFalse(Josa.hasBatchim("세 개"))
    }

    @Test fun digitsUseSinoKoreanReading() {
        assertTrue(Josa.hasBatchim("3"))
        assertFalse(Josa.hasBatchim("2"))
        assertTrue(Josa.hasBatchim("10"))
    }

    @Test fun latinHeuristic() {
        assertFalse(Josa.hasBatchim("Ali"))
        assertTrue(Josa.hasBatchim("Kim"))
        assertTrue(Josa.hasBatchim("Ann"))
    }

    @Test fun ignoresTrailingPunctuation() = assertTrue(Josa.hasBatchim("책."))

    @Test fun emptyWordHasNoBatchim() = assertFalse(Josa.hasBatchim(""))

    @Test fun particles() {
        assertEquals("의자가", Josa.iGa("의자"))
        assertEquals("책이", Josa.iGa("책"))
        assertEquals("가방을", Josa.eulReul("가방"))
        assertEquals("의자를", Josa.eulReul("의자"))
        assertEquals("책은", Josa.eunNeun("책"))
        assertEquals("의자는", Josa.eunNeun("의자"))
        assertEquals("세 개와", Josa.waGwa("세 개"))
        assertEquals("한 명과", Josa.waGwa("한 명"))
    }
}
