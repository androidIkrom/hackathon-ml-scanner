package com.nungil.reader

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private fun line(text: String, confidence: Float = 0.9f, height: Float = 0.06f, x: Float = 0.5f, y: Float = 0.5f) =
        SeenLine(text, confidence, height, x, y)

    @Test fun onlyClearRealTextIsReadable() {
        assertTrue(ReaderPolicy.readable(line(" EXIT ")))
        assertFalse(ReaderPolicy.readable(line("BC")))                     // under 3 characters
        assertFalse(ReaderPolicy.readable(line("EXIT", confidence = 0.5f))) // ML Kit unsure
        assertFalse(ReaderPolicy.readable(line("fine print", height = 0.01f)))
        assertTrue(ReaderPolicy.readable(line("A page at reading distance", height = 0.028f)))
        assertFalse(ReaderPolicy.readable(line("|||~~ .")))                // not letters
        assertTrue(ReaderPolicy.readable(line("출구 EXIT")))
    }

    @Test fun theTextIsReadFromTheTopDown() {
        // A screen of chat (the device log, 2026-10-12): every line, top to bottom, left to right on one row.
        val lines = listOf(
            line("bypass permissions on", y = 0.9f),
            line("The APK has the reader fix", y = 0.2f),
            line("right half", x = 0.8f, y = 0.5f),
            line("left half", x = 0.2f, y = 0.505f),
            line("|||~~ .", y = 0.3f),
        )
        assertEquals(
            listOf("The APK has the reader fix", "left half", "right half", "bypass permissions on"),
            ReaderPolicy.inOrder(lines).map { it.text },
        )
    }

    private val page = listOf(
        line("The reader reads every line in view", y = 0.1f),
        line("from the top of the screen to the bottom.", y = 0.2f),
        line("Say stop to make it quiet.", y = 0.3f),
    )

    @Test fun textSeenInTwoReadsIsOfferedOnceThenReadInFullOnYes() {
        val p = ReaderPolicy()
        assertNull(p.found(page, 0))
        val offer = p.found(page, 600)
        assertNotNull(offer)
        assertEquals("Text found, beginning with 'The reader reads every line in…'. Say read to hear it.", offer!!.phrase(Lang.EN))
        // While it waits for the answer, the same text is not offered again.
        assertNull(p.found(page, 1_200))
        assertEquals(
            "The reader reads every line in view. from the top of the screen to the bottom. Say stop to make it quiet.",
            p.accept(),
        )
        assertNull(p.accept())
        // Read once, the same text is not offered again for a while; new text is.
        assertNull(p.found(page, 5_000))
        val other = listOf(line("A new page came into view", y = 0.5f))
        assertNull(p.found(other, 5_600))
        assertNotNull(p.found(other, 6_200))
        p.decline()
        assertNull(p.found(page, 600 + ReaderPolicy.OFFER_AGAIN_MS))
        assertNotNull(p.found(page, 1_200 + ReaderPolicy.OFFER_AGAIN_MS))
    }

    @Test fun aLineMisreadOrMissingStillMakesTheSameText() {
        val p = ReaderPolicy()
        p.found(page, 0)
        p.found(page, 600)
        p.decline()
        val misread = listOf(line("The reader reads every lime in view", y = 0.1f), page[1])
        assertNull(p.found(misread, 1_200))
        assertNull(p.found(misread, 1_800))
    }

    @Test fun aSignIsOfferedByItsTitleAndReadWithItsSmallPrint() {
        val sign = listOf(
            line("EMERGENCY", height = 0.06f, y = 0.4f),
            line("EXIT", height = 0.058f, y = 0.5f),
            line("Keep this door closed at all times", height = 0.025f, y = 0.7f),
            line("Fire door - do not obstruct", height = 0.02f, y = 0.75f),
        )
        val p = ReaderPolicy()
        p.found(sign, 0)
        val offer = p.found(sign, 600)!!
        assertEquals("Text found, titled 'EMERGENCY EXIT'. Say read to hear it.", offer.phrase(Lang.EN))
        assertEquals("글자를 찾았어요. 제목: 'EMERGENCY EXIT'. 들으려면 '읽어'라고 말해 주세요.", offer.phrase(Lang.KO))
        assertEquals("EMERGENCY. EXIT. Keep this door closed at all times. Fire door - do not obstruct.", p.accept())
    }

    @Test fun aShortFirstLineIsSaidWhole() {
        val p = ReaderPolicy()
        val note = listOf(line("Milk and bread", y = 0.2f), line("Eggs and apples for the week", y = 0.3f))
        p.found(note, 0)
        assertEquals("글자를 찾았어요. 시작: 'Milk and bread'. 들으려면 '읽어'라고 말해 주세요.", p.found(note, 600)!!.phrase(Lang.KO))
    }

    @Test fun answers() {
        for (yes in listOf("read", "read it", "yes", "okay", "읽어", "네")) assertEquals(yes, true, ReaderPolicy.answer(yes))
        for (no in listOf("no", "skip", "아니")) assertEquals(no, false, ReaderPolicy.answer(no))
        assertNull(ReaderPolicy.answer("what is this"))
    }

    @Test fun aLineReadAlittleDifferentlyIsStillTheSameLine() {
        assertTrue(ReaderPolicy.sameLine("SeenLine(Line.text,, line.confidence, box.height () / h", "SeenLine (lLine.text,, line.confidence, bex.height () / h"))
        assertTrue(ReaderPolicy.sameLine("Exit", " EXIT"))
        assertFalse(ReaderPolicy.sameLine("EXIT", "EX1T X"))
        assertFalse(ReaderPolicy.sameLine("SeenLine(Line.text, line.confidence", "TEMP diagnostics (text reading)"))
        assertFalse(ReaderPolicy.sameLine("Step 1: open the box", "Step 2: open the box"))
    }

    @Test fun linesAreJoinedIntoSentences() =
        assertEquals("EXIT. Push the door. Is it open? A line seen twice, goes on.", ReaderPolicy.sentence(listOf("EXIT", "Push the door.", "Is it open?", "A line seen twice,", "goes on")))

    @Test fun aCodeIsNotSaidAgainWithinTenSeconds() {
        val p = ReaderPolicy()
        assertTrue(p.shouldSpeak("Code: abc", 0))
        assertFalse(p.shouldSpeak("Code: abc", 9_999))
        assertTrue(p.shouldSpeak("Code: xyz", 5_000))
        assertTrue(p.shouldSpeak("Code: abc", 10_000))
    }
}
