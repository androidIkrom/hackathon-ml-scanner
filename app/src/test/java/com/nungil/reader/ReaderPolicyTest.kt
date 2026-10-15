package com.nungil.reader

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPolicyTest {
    /** ML Kit, told a frame's turn, gives a line's corners in the frame as it lies (the phone, 2026-10-15). */
    @Test fun cornersOfASidewaysFrameAreTurnedUpright() {
        // Upright 1920 x 2560: a line from (247, 862) to (557, 875); the camera's frame is 2560 x 1920, turn 90.
        val (xs, ys) = ReaderPolicy.uprightCorners(
            floatArrayOf(860f, 859f, 874f, 875f), floatArrayOf(1667f, 1361f, 1360f, 1666f), rotation = 90, width = 2560f, height = 1920f,
        )
        assertEquals(listOf(253f, 559f, 560f, 254f), xs.toList())
        assertEquals(listOf(860f, 859f, 874f, 875f), ys.toList())
        // Half a turn and three quarters: back where the corner was.
        val (x2, y2) = ReaderPolicy.uprightCorners(floatArrayOf(10f), floatArrayOf(20f), rotation = 180, width = 100f, height = 50f)
        assertEquals(90f to 30f, x2[0] to y2[0])
        val (x3, y3) = ReaderPolicy.uprightCorners(floatArrayOf(10f), floatArrayOf(20f), rotation = 270, width = 100f, height = 50f)
        assertEquals(20f to 90f, x3[0] to y3[0])
        val (x0, y0) = ReaderPolicy.uprightCorners(floatArrayOf(10f), floatArrayOf(20f), rotation = 0, width = 100f, height = 50f)
        assertEquals(10f to 20f, x0[0] to y0[0])
    }

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
        assertFalse(ReaderPolicy.readable(line("EXIT", confidence = 0.4f))) // ML Kit unsure
        // Small print in a noisy camera frame came at 0.57 and was left out (replay, 2026-10-13).
        assertTrue(ReaderPolicy.readable(line("Small print: offer ends on Sunday", confidence = 0.57f)))
        assertFalse(ReaderPolicy.readable(line("fine print", height = 0.01f)))
        // A laptop screen line, 10 px high in the reader's 1920 px frame (2026-10-13).
        assertTrue(ReaderPolicy.readable(SeenLine("Saved Messages", 0.9f, 10f / 1920, 0.5f, 0.5f, frameHeight = 1920)))
        assertFalse(ReaderPolicy.readable(SeenLine("Saved Messages", 0.9f, 6f / 1920, 0.5f, 0.5f, frameHeight = 1920)))
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
            p.accept(0)?.joinToString(" "),
        )
        assertNull(p.accept(0))
        // Read once, the same text is not offered again for a while; new text is.
        assertNull(p.found(page, 5_000))
        val other = listOf(line("A new page came into view", y = 0.5f))
        assertNull(p.found(other, 8_000))
        assertNotNull(p.found(other, 8_600))
        p.decline()
        assertNull(p.found(page, 600 + ReaderPolicy.OFFER_AGAIN_MS))
        assertNotNull(p.found(page, 1_200 + ReaderPolicy.OFFER_AGAIN_MS))
    }

    /**
     * One line cut into pieces differently in each frame (ML Kit on a frame told its turn, the phone, 2026-10-15):
     * whole, in two, in three. A piece of a line read whole the read before is seen twice, and the line is read whole.
     */
    @Test fun aLineCutDifferentlyEachReadIsStillReadWhole() {
        fun piece(text: String, x: Float, w: Float) = SeenLine(text, 0.9f, 0.034f, x, 0.26f, width = w)
        val top = SeenLine("Weekly Notes", 0.9f, 0.07f, 0.4f, 0.11f, width = 0.56f)
        val whole = listOf(top, piece("Monday: buy fresh bread and milk", 0.40f, 0.68f))
        val two = listOf(top, piece("Monday: buy fresh bread", 0.31f, 0.49f), piece("and milk", 0.66f, 0.16f))
        val three = listOf(top, piece("Monday: buy", 0.19f, 0.24f), piece("fresh bread", 0.44f, 0.23f), piece("and milk", 0.67f, 0.18f))
        val p = ReaderPolicy()
        listOf(whole, two, three, whole, two, three).forEachIndexed { i, frame -> p.found(frame, i * 600L) }
        assertEquals("Weekly Notes. Monday: buy fresh bread and milk.", p.accept(4_000)?.joinToString(" "))
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
        assertEquals("EMERGENCY. EXIT. Keep this door closed at all times. Fire door - do not obstruct.", p.accept(0)?.joinToString(" "))
    }

    @Test fun theTitleIsTheBigLinesAtTheTopNotABigLineFurtherDown() {
        // Replay, 2026-10-13: "titled 'Weekly Notes SALE all shoes half price'".
        val page = listOf(
            line("Weekly Notes", height = 0.061f, y = 0.1f),
            line("A short guide to the new reader", height = 0.034f, y = 0.17f),
            line("Monday: buy fresh bread and milk", height = 0.027f, y = 0.23f),
            line("Tuesday: call the doctor at ten", height = 0.028f, y = 0.29f),
            line("SALE all shoes half price", height = 0.047f, y = 0.4f),
            line("Thursday: pay the water bill", height = 0.027f, y = 0.47f),
        )
        assertEquals("Weekly Notes", ReaderPolicy.title(page))
    }

    /** A page line by line, its corners as ML Kit gives them (clockwise from top-left), turned by [degrees]. */
    private fun tilted(degrees: Double, vararg lines: Triple<String, Float, Float>): List<ReaderPolicy.RawLine> {
        val a = Math.toRadians(degrees)
        fun turn(x: Float, y: Float): Pair<Float, Float> {
            val dx = x - 240f
            val dy = y - 320f
            return (240f + (dx * Math.cos(a) - dy * Math.sin(a)).toFloat()) to (320f + (dx * Math.sin(a) + dy * Math.cos(a)).toFloat())
        }
        // Each line: its text, its left end and its width; 20 px high, 34 px apart.
        return lines.mapIndexed { i, (text, left, width) ->
            val top = 60f + i * 34f
            val corners = listOf(turn(left, top), turn(left + width, top), turn(left + width, top + 20f), turn(left, top + 20f))
            ReaderPolicy.RawLine(text, 0.9f, corners.map { it.first }.toFloatArray(), corners.map { it.second }.toFloatArray())
        }
    }

    @Test fun aCrookedPageIsReadInOrder() {
        // Replay, 2026-10-13: held 7° crooked, the lines' boxes grew taller, overlapped, and "Tuesday" came before
        // "Monday". Each line is measured along its own slant, and the page is set straight before ordering.
        for (degrees in listOf(8.0, -8.0)) {
            val raw = tilted(
                degrees,
                Triple("Monday: buy fresh bread and milk", 16f, 420f),
                Triple("Tuesday: call", 16f, 120f),
                Triple("Wednesday: meet Anna at the library today", 16f, 440f),
                Triple("Thursday", 300f, 100f),
            )
            val lines = ReaderPolicy.straighten(raw, 480f, 640f)
            assertEquals(listOf("Monday: buy fresh bread and milk", "Tuesday: call", "Wednesday: meet Anna at the library today", "Thursday"), ReaderPolicy.inOrder(lines).map { it.text })
            for (l in lines) assertEquals(20f / 640f, l.height, 0.001f)
        }
    }

    @Test fun anOfferGathersTheLinesOfEachReadingWhileItWaits() {
        // Small print read in one noisy frame and not the next was left out (replay, 2026-10-13).
        val a = line("Monday: buy bread", y = 0.2f)
        val b = line("Tuesday: call the doctor", y = 0.3f)
        val small = line("Small print: offer ends on Sunday", y = 0.5f, height = 0.03f)
        val p = ReaderPolicy()
        p.found(listOf(a, b), 0)
        assertNotNull(p.found(listOf(a, b), 600))
        p.found(listOf(a, b, small), 1_200)
        assertNull(p.found(listOf(a, b, small), 1_800))
        p.found(listOf(a, b), 2_400)
        assertEquals(listOf("Monday: buy bread.", "Tuesday: call the doctor.", "Small print: offer ends on Sunday."), p.accept(3_000))
    }

    @Test fun mergeKeepsTheOrderAndTheSurerReading() {
        val older = listOf(line("one two three", confidence = 0.6f), line("four five six"), line("seven eight nine"))
        val newer = listOf(line("one two thrce", confidence = 0.9f), line("seven eight nine"), line("ten eleven"))
        assertEquals(
            listOf("one two thrce", "four five six", "seven eight nine", "ten eleven"),
            ReaderPolicy.merge(older, newer).map { it.text },
        )
    }

    @Test fun twoColumnsAreReadOneAfterTheOther() {
        // Replay, 2026-10-13: "Left column one. Right column one. goes on in two. goes on in two. …".
        fun l(text: String, x: Float, y: Float, block: Int) = SeenLine(text, 0.9f, 0.03f, x, y, width = 0.35f, block = block)
        val page = listOf(
            l("goes on in two", 0.7f, 0.3f, 2), l("Left column one", 0.2f, 0.2f, 1), l("Right column one", 0.7f, 0.2f, 2),
            l("and ends in three", 0.2f, 0.4f, 1), l("goes on in two", 0.2f, 0.3f, 1), l("and ends in three", 0.7f, 0.4f, 2),
            SeenLine("Two Columns", 0.9f, 0.06f, 0.3f, 0.08f, width = 0.5f, block = 0),
            SeenLine("A footer under both", 0.9f, 0.03f, 0.5f, 0.6f, width = 0.8f, block = 3),
        )
        assertEquals(
            listOf("Two Columns", "Left column one", "goes on in two", "and ends in three", "Right column one", "goes on in two", "and ends in three", "A footer under both"),
            ReaderPolicy.inOrder(page).map { it.text },
        )
    }

    @Test fun piecesOfOneLineAreOneSentence() {
        // Replay, 2026-10-13: "Prices start at. dollars for a cup of tea." (a big word between them, cut in two).
        val lines = listOf(
            SeenLine("Prices start at", 0.9f, 0.03f, 0.16f, 0.51f, width = 0.25f, block = 3),
            SeenLine("dollars for a cup of tea.", 0.9f, 0.03f, 0.56f, 0.51f, width = 0.39f, block = 4),
            SeenLine("Thank you for coming", 0.9f, 0.027f, 0.23f, 0.59f, width = 0.39f, block = 5),
        )
        assertEquals(listOf("Prices start at dollars for a cup of tea.", "Thank you for coming."), ReaderPolicy.Offer(lines).sentences)
    }

    @Test fun ofOneLineReadTwiceTheFullerReadingIsKept() {
        // Replay, 2026-10-13, tilted: a big "FIVE" among small words came in some frames only, and the shorter,
        // surer reading was kept: "Prices start at dollars for a cup of tea".
        fun l(text: String, x: Float, c: Float = 0.9f, h: Float = 0.03f) = SeenLine(text, c, h, x, 0.51f, block = 1)
        val older = listOf(l("Prices start at", 0.16f, 0.91f), l("FIVE.", 0.42f, 0.72f, 0.058f), l("dollars for a cup of tea.", 0.69f, 0.87f))
        val newer = listOf(l("Prices start at", 0.16f, 0.91f), l("FIVE dollars for a cup of tea.", 0.6f, 0.78f, 0.04f))
        assertEquals(listOf("Prices start at", "FIVE dollars for a cup of tea."), ReaderPolicy.merge(older, newer).map { it.text })
        assertEquals(listOf("Prices start at", "FIVE dollars for a cup of tea."), ReaderPolicy.merge(newer, older).map { it.text })
    }

    @Test fun aWordReadInTwoPiecesIsSaidOnce() {
        assertEquals("Prices start at FIVE dollars", ReaderPolicy.joinPieces(listOf("Prices start at FVE", "FIVE dollars")))
        assertEquals("Prices start at FIVE dollars", ReaderPolicy.joinPieces(listOf("Prices start at", "FIVE dollars")))
        assertEquals("on the bus to the station", ReaderPolicy.joinPieces(listOf("on the bus", "to the station")))
    }

    @Test fun aShortLineOnARowOfItsOwnStays() {
        // "EXIT" is in "Fire exit route" below it, but is a line of its own.
        val exit = SeenLine("EXIT", 0.9f, 0.06f, 0.5f, 0.3f)
        val route = SeenLine("Fire exit route", 0.9f, 0.03f, 0.5f, 0.45f)
        assertEquals(listOf("EXIT", "Fire exit route"), ReaderPolicy.merge(listOf(exit, route), listOf(exit, route)).map { it.text })
    }

    @Test fun aTableRowIsReadAcross() {
        // Label and value in blocks of their own, row by row: each row is read label, value.
        fun l(text: String, x: Float, y: Float, block: Int) = SeenLine(text, 0.9f, 0.03f, x, y, width = 0.3f, block = block)
        val form = listOf(l("Name:", 0.2f, 0.2f, 0), l("Anna Kim", 0.7f, 0.2f, 1), l("Phone:", 0.2f, 0.3f, 2), l("010 1234", 0.7f, 0.3f, 3))
        assertEquals(listOf("Name:", "Anna Kim", "Phone:", "010 1234"), ReaderPolicy.inOrder(form).map { it.text })
    }

    @Test fun aLongBigLineIsNoTitle() {
        // The device log, 2026-10-12 18:34: a chat line a little taller than the rest was "titled '2. Keyin bir
        // marta qisqa jonli tekshiruv, Find something …'".
        val chat = listOf(
            line("2. Keyin bir marta qisqa jonli tekshiruv, Find something", height = 0.06f, y = 0.2f),
            line("Read text or a code ni oching.", height = 0.04f, y = 0.3f),
            line("Chatga qarating va read deng.", height = 0.04f, y = 0.4f),
        )
        val p = ReaderPolicy()
        p.found(chat, 0)
        assertEquals("Text found, beginning with '2. Keyin bir marta qisqa jonli…'. Say read to hear it.", p.found(chat, 600)!!.phrase(Lang.EN))
    }

    @Test fun anOfferWaitsForItsAnswerThenOtherTextMayBeOffered() {
        // The device log, 2026-10-12 18:34: five offers in a minute queued up behind each other.
        val p = ReaderPolicy()
        p.found(page, 0)
        assertNotNull(p.found(page, 600))
        val other = listOf(line("A new page came into view", y = 0.5f))
        p.found(other, 1_200)
        assertNull(p.found(other, 1_800))
        // Not answered: after a while it is dropped and the text now in view is offered.
        assertNotNull(p.found(other, 600 + ReaderPolicy.ANSWER_MS))
    }

    @Test fun nothingIsOfferedWhileTheTextIsBeingRead() {
        val p = ReaderPolicy()
        p.found(page, 0)
        p.found(page, 600)
        val lines = p.accept(1_000)!!
        val other = listOf(line("A new page came into view", y = 0.5f))
        val readingMs = lines.sumOf { it.length } * ReaderPolicy.MS_PER_CHAR
        p.found(other, 1_000 + readingMs - 600)
        assertNull(p.found(other, 1_000 + readingMs - 1))
        assertNotNull(p.found(other, 1_000 + readingMs + 600))
    }

    @Test fun aShortFirstLineIsSaidWhole() {
        val p = ReaderPolicy()
        val note = listOf(line("Milk and bread", y = 0.2f), line("Eggs and apples for the week", y = 0.3f))
        p.found(note, 0)
        assertEquals("글자를 찾았어요. 시작: 'Milk and bread'. 들으려면 '읽어'라고 말해 주세요.", p.found(note, 600)!!.phrase(Lang.KO))
    }

    @Test fun anotherTextDropsTheOfferAndTheReadingAndLooksAgain() {
        val p = ReaderPolicy()
        p.found(page, 0)
        p.found(page, 600)
        p.accept(1_000)
        p.another()
        // The text just read is not offered again; other text is, at once (not after it would have been read).
        val other = listOf(line("A new page came into view", y = 0.5f))
        assertNull(p.found(page, 1_600))
        p.found(other, 2_200)
        assertNotNull(p.found(other, 2_800))
    }

    @Test fun readingIsContinuedFromTheLineThatWasBeingSaid() {
        val lines = listOf("One.", "Two.", "Three.")
        assertEquals(1, ReaderPolicy.resumeAt(lines, "Two."))
        assertNull(ReaderPolicy.resumeAt(lines, null))
        assertNull(ReaderPolicy.resumeAt(lines, "Something else."))
    }

    @Test fun eachLineIsItsOwnSentence() {
        val p = ReaderPolicy()
        p.found(page, 0)
        p.found(page, 600)
        assertEquals(
            listOf("The reader reads every line in view.", "from the top of the screen to the bottom.", "Say stop to make it quiet."),
            p.accept(1_000),
        )
    }

    @Test fun anotherText() {
        for (t in listOf("another text", "next", "new text", "something else", "다른 글", "다음")) assertTrue(t, ReaderPolicy.wantsAnother(t))
        for (t in listOf("read", "yes", "stop", "no")) assertFalse(t, ReaderPolicy.wantsAnother(t))
        assertEquals("Looking for other text.", ReaderPolicy.lookingForAnother(Lang.EN))
        assertEquals("다른 글자를 찾을게요.", ReaderPolicy.lookingForAnother(Lang.KO))
    }

    @Test fun answers() {
        for (yes in listOf("read", "read it", "yes", "okay", "읽어", "네")) assertEquals(yes, true, ReaderPolicy.answer(yes))
        for (no in listOf("no", "skip", "아니")) assertEquals(no, false, ReaderPolicy.answer(no))
        assertNull(ReaderPolicy.answer("what is this"))
    }

    @Test fun aLongSentenceInTheRoomIsNoAnswer() {
        // A lecture in the room: "read" or "another" inside a long sentence is not the user's answer (2026-10-13).
        assertNull(ReaderPolicy.answer("you just read the documentation of the function before you use it"))
        assertNull(ReaderPolicy.answer("so yes the function performs some test and then gives an output"))
        assertFalse(ReaderPolicy.wantsAnother("you can call another function from inside this one"))
        assertEquals(true, ReaderPolicy.answer("yes read it please"))
        assertTrue(ReaderPolicy.wantsAnother("read another text please"))
    }

    @Test fun aLineReadAlittleDifferentlyIsStillTheSameLine() {
        assertTrue(ReaderPolicy.sameLine("SeenLine(Line.text,, line.confidence, box.height () / h", "SeenLine (lLine.text,, line.confidence, bex.height () / h"))
        assertTrue(ReaderPolicy.sameLine("Exit", " EXIT"))
        assertFalse(ReaderPolicy.sameLine("EXIT", "EX1T X"))
        assertFalse(ReaderPolicy.sameLine("SeenLine(Line.text, line.confidence", "TEMP diagnostics (text reading)"))
        assertFalse(ReaderPolicy.sameLine("Step 1: open the box", "Step 2: open the box"))
        // A plaque's ornaments read as "O", "6", "9" at the ends, and O for 0 (real photo, 2026-10-13).
        assertTrue(ReaderPolicy.sameLine("O FIELD MUSEUMO", "6 FIELD MUSEUM"))
        assertTrue(ReaderPolicy.sameLine("FIELD MUSEUM", "9 FIELD MUSEUMO"))
        assertTrue(ReaderPolicy.sameLine("100000 DOLLARS OR MORE", "10000O DOLLARS OR MORE"))
        assertTrue(ReaderPolicy.sameLine("100.000 DOLLARS OR MORE", "100000 DOLLARS OR MORE"))
        assertFalse(ReaderPolicy.sameLine("Room 12 is open", "Room 13 is open"))
    }

    @Test fun twoReadingsOnTopOfEachOtherAreNotPiecesOfOneLine() {
        val a = SeenLine("100000 DOLLARS OR MORE", 0.7f, 0.04f, 0.49f, 0.26f, width = 0.66f)
        val b = SeenLine("10000O DOLLARS OR MORE", 0.7f, 0.04f, 0.50f, 0.26f, width = 0.67f)
        assertEquals(2, ReaderPolicy.rows(listOf(a, b)).size)
    }

    @Test fun linesAreJoinedIntoSentences() =
        assertEquals("EXIT. Push the door. Is it open? A line seen twice, goes on.", ReaderPolicy.sentence(listOf("EXIT", "Push the door.", "Is it open?", "A line seen twice,", "goes on")))

    @Test fun marksAreSaidAsWords() {
        // The user, 2026-10-13: brackets, quotes, commas, dots and dashes were not read.
        assertEquals(
            "Exit open bracket push close bracket comma, quote Do not quote dash enter dot.",
            ReaderPolicy.spoken("Exit (push), \"Do not\" - enter.", Lang.EN),
        )
        assertEquals("Wait question mark. Go exclamation mark. Note colon, see dot dot dot.", ReaderPolicy.spoken("Wait? Go! Note: see...", Lang.EN))
        assertEquals("well dash known example dot com", ReaderPolicy.spoken("well-known example.com", Lang.EN))
        assertEquals("quote EXIT quote", ReaderPolicy.spoken("“EXIT”", Lang.EN))
    }

    @Test fun marksInsideNumbersAndWordsStay() {
        // The voice says these right: "3.5", "1,000", "10:30", "don't".
        assertEquals("3.5 kg comma, 1,000 at 10:30 don't", ReaderPolicy.spoken("3.5 kg, 1,000 at 10:30 don't", Lang.EN))
        assertEquals("Price 3.5 dot.", ReaderPolicy.spoken("Price 3.5.", Lang.EN))
    }

    @Test fun marksInKorean() =
        assertEquals("괄호 열고 출구 괄호 닫고 마침표.", ReaderPolicy.spoken("(출구).", Lang.KO))

    @Test fun aFrameIsMadeOnlyWhenItCanBeRead() {
        // The emulator, 2026-10-15: every 1440 x 1920 frame was made upright, 0.8-1.3 s each, though one in 600 ms
        // was read: the rest only took the processor from the reading.
        assertTrue(ReaderPolicy.wantsFrame(nowMs = 600, lastFrameMs = 0, reading = false))
        assertFalse(ReaderPolicy.wantsFrame(nowMs = 599, lastFrameMs = 0, reading = false))
        assertFalse(ReaderPolicy.wantsFrame(nowMs = 5_000, lastFrameMs = 0, reading = true))
    }

    @Test fun aCodeIsNotSaidAgainWithinTenSeconds() {
        val p = ReaderPolicy()
        assertTrue(p.shouldSpeak("Code: abc", 0))
        assertFalse(p.shouldSpeak("Code: abc", 9_999))
        assertTrue(p.shouldSpeak("Code: xyz", 5_000))
        assertTrue(p.shouldSpeak("Code: abc", 10_000))
    }
}
