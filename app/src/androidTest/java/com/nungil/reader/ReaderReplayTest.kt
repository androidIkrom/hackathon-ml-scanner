package com.nungil.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.nungil.contract.Lang
import com.nungil.debug.ReplayHostActivity
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The reader on pictures instead of the camera: ML Kit text and code reading and ReaderPolicy, through ReaderEngine,
 * over frames drawn here (pages of text, a sign with small print, a QR code) and, when present, real frames in
 * androidTest assets `replay/<scene>/` (saved from the phone once). Each scene is read as the reader screen reads:
 * one frame per read, a read every ReaderPolicy.READ_EVERY_MS or as long as ML Kit takes. What would be said, and
 * when, goes to logcat (tag NungilReplay) and to files/replay/report.txt in the app's external files.
 *
 * Run: gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nungil.reader.ReaderReplayTest
 */
@RunWith(AndroidJUnit4::class)
class ReaderReplayTest {
    /** Keeps the app in front: in the background the phone freezes it and the test never ends (ReplayHostActivity). */
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)

    /** One read of a scene: when (ms from the first frame), what OCR saw, what was said. */
    /**
     * One read of a scene: when (ms from the first frame), what OCR saw, and what was said: a code ([text]), or an
     * offer ([offer], "Text found, …") and, the user saying "read" right away, the whole text ([full]).
     */
    private class Said(
        val atMs: Long, val text: String?, val lines: Int, val toRead: Int, val ocrMs: Long, val code: String?,
        val offer: String? = null, val full: String? = null,
    )

    private fun replay(scene: String, frames: List<Bitmap>): List<Said> {
        val out = mutableListOf<Said>()
        ReaderEngine().use { engine ->
            var t = 0L
            var offeredAt: Long? = null
            for (frame in frames) {
                val read = engine.read(frame, t, Lang.EN)
                if (t == 0L) {
                    for (l in read.lines) {
                        Log.i(TAG, String.format(java.util.Locale.US, "  line h %.3f c %.2f b %d x %.2f w %.2f y %.2f %s \"%s\"", l.height, l.confidence, l.block, l.centerX, l.width, l.centerY, if (ReaderPolicy.readable(l)) "read" else "dropped", l.text))
                    }
                }
                val offer = read.offer?.phrase(Lang.EN)
                if (offer != null && offeredAt == null) offeredAt = t
                // The user says "read" once the offer has been said, as on the phone: a few reads later.
                val full = offeredAt?.takeIf { t >= it + ANSWER_AFTER_MS }?.let {
                    offeredAt = Long.MAX_VALUE / 2
                    engine.accept(t)?.joinToString(" ")
                }
                out += Said(t, read.spoken, read.lines.size, ReaderPolicy.inOrder(read.lines).size, read.ocrMs, read.code, offer, full)
                t += maxOf(ReaderPolicy.READ_EVERY_MS, read.ocrMs)
                // As the reader screen does: one read every READ_EVERY_MS, not back to back.
                Thread.sleep(maxOf(0L, ReaderPolicy.READ_EVERY_MS - read.ocrMs))
            }
        }
        report(scene, out)
        return out
    }

    private fun report(scene: String, reads: List<Said>) {
        val text = buildString {
            append("== $scene: ${reads.size} reads, OCR ${reads.map { it.ocrMs }.sorted().let { it[it.size / 2] }} ms median\n")
            for (r in reads) {
                append("  ${r.atMs} ms: ${r.lines} lines, ${r.toRead} to read, ${r.ocrMs} ms")
                r.code?.let { append(", code") }
                r.text?.let { append(" -> \"$it\"") }
                r.offer?.let { append(" -> \"$it\"") }
                r.full?.let { append("\n      read: \"$it\"") }
                append('\n')
            }
        }
        text.lines().filter { it.isNotBlank() }.forEach { Log.i(TAG, it) }
        synchronized(REPORT) { REPORT.append(text) }
    }

    // ---- scenes ------------------------------------------------------------------------------------

    @Test fun aScreenOfTextIsOfferedSoonAndReadTopDown() {
        val lines = PAGE.take(8)
        val reads = replay("page, normal text", List(12) { page(lines, textPx = 22f, shiftPx = it % 3) })
        val offers = reads.filter { it.offer != null }
        assertEquals("offered once", 1, offers.size)
        val first = offers.single()
        assertTrue("offered after ${first.atMs} ms", first.atMs <= 2 * ReaderPolicy.READ_EVERY_MS)
        assertEquals("Text found, beginning with 'The reader reads every line in…'. Say read to hear it.", first.offer)
        // On "read", every line once, in order from the top.
        val heard = reads.firstNotNullOf { it.full }
        val at = lines.map { line -> lines.indexOf(line) to found(heard, line) }
        assertTrue("not read: ${at.filter { it.second < 0 }.map { lines[it.first] }}", at.all { it.second >= 0 })
        assertEquals("order", at.sortedBy { it.second }.map { it.first }, at.map { it.first })
    }

    @Test fun aSignIsOfferedByItsTitleAndReadWithItsSmallPrint() {
        val reads = replay("sign with small print", List(8) { sign() })
        val offer = reads.single { it.offer != null }
        assertEquals("Text found, titled 'EMERGENCY EXIT'. Say read to hear it.", offer.offer)
        val heard = reads.firstNotNullOf { it.full }
        assertTrue("read: $heard", found(heard, "Keep this door closed at all times") >= 0)
    }

    @Test fun aQrCodeIsReadAtOnce() {
        val reads = replay("QR code", List(4) { qr("https://nungil.app/test") })
        assertEquals("https://nungil.app/test", reads.first().code)
        assertEquals("Code: https://nungil.app/test", reads.first().text)
        assertTrue("said again", reads.drop(1).all { it.text == null })
    }

    /**
     * Sizes, font families and bold / normal mixed, as on a real page or a poster: every line read, in order from the
     * top. Bold next to normal on one line, and a big word next to small ones, are one line (the user, 2026-10-13).
     */
    @Test fun mixedFontsAreAllReadInOrder() = mixedFonts("page, mixed fonts") { styled(MIXED, shiftPx = it % 3) }

    /** The phone held a little crooked: the lines' boxes grow taller and overlap. */
    @Test fun mixedFontsTilted() = mixedFonts("page, mixed fonts, tilted 7°") { tilt(styled(MIXED, shiftPx = it % 3), 7f + it % 2) }

    @Test fun mixedFontsTiltedTheOtherWay() = mixedFonts("page, mixed fonts, tilted -6°") { tilt(styled(MIXED, shiftPx = it % 3), -6f) }

    /** A camera frame: soft focus and sensor noise. */
    @Test fun mixedFontsCameraLike() = mixedFonts("page, mixed fonts, soft and noisy") { noisy(blur(styled(MIXED, shiftPx = it % 3)), seed = it) }

    /** Other families (handwriting-like, light, condensed, serif monospace) on a dark slide, light letters. */
    @Test fun otherFamiliesOnADarkSlide() =
        mixedFonts("slide, other families, light on dark", MORE, "Lesson Three") { styled(MORE, shiftPx = it % 3, dark = true) }

    /**
     * Two columns of an article side by side, lines as close as in print: the left one top to bottom, then the right
     * one (ML Kit gives each column's paragraph as one block). Lines far apart are each a block of their own, and are
     * read row by row, as a table is.
     */
    @Test fun twoColumnsAreReadOneAfterTheOther() {
        val left = listOf("Most people who are blind", "use a phone every day", "to read and to find", "their way around town.")
        val right = listOf("The app runs on the phone", "and sends nothing away,", "so it works without", "a network at all.")
        val reads = replay("page, two columns", List(10) { shift ->
            val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.drawColor(Color.rgb(240, 238, 230))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 25, 25); textSize = 17f; typeface = Typeface.SERIF }
            c.drawText("Two Columns", 16f + shift % 3, 70f, Paint(paint).apply { textSize = 40f; typeface = Typeface.DEFAULT_BOLD })
            for (i in left.indices) {
                c.drawText(left[i], 12f + shift % 3, 140f + i * 22f, paint)
                c.drawText(right[i], 250f + shift % 3, 140f + i * 22f, paint)
            }
            b
        })
        val heard = reads.firstNotNullOf { it.full }
        val lines = listOf("Two Columns") + left + right
        val at = lines.map { found(heard, it) }
        assertTrue("not read: ${lines.filterIndexed { i, _ -> at[i] < 0 }} in \"$heard\"", at.all { it >= 0 })
        assertEquals("order in \"$heard\"", at.sorted(), at)
    }

    /**
     * Sizes that change inside a line, in the middle and at the end, line after line of one paragraph. ML Kit leaves
     * out a lone big "5" among small words (never in any frame, 2026-10-13); a big word it reads.
     */
    @Test fun sizesChangingInsideTheLines() =
        mixedFonts("paragraph, sizes inside the lines", INLINE, "Opening Hours") { styled(INLINE, shiftPx = it % 3) }

    @Test fun sizesChangingInsideTheLinesTilted() =
        mixedFonts("paragraph, sizes inside the lines, tilted 6°", INLINE, "Opening Hours") { tilt(styled(INLINE, shiftPx = it % 3), 6f) }

    /**
     * A laptop screen held in view (the user, 2026-10-13: the model name on the laptop was read, the text on its
     * screen was not): a 1920 x 1080 screen of chat and code lines, 16 px text, filling 85 % of the frame's width, a
     * little soft. Read at the frame size the reader asks the camera for.
     */
    @Test fun aLaptopScreen() {
        val (w, h) = ReaderEngine.FRAME_WIDTH to ReaderEngine.FRAME_HEIGHT
        val reads = replay("laptop screen, ${w}x$h", List(10) { laptop(w, h, shift = it % 3) })
        val heard = reads.firstNotNullOfOrNull { it.full }.orEmpty()
        val read = SCREEN.count { found(heard, it) >= 0 }
        Log.i(TAG, "laptop screen: $read of ${SCREEN.size} lines read")
        assertTrue("$read of ${SCREEN.size} lines read: \"$heard\"", read >= SCREEN.size * 8 / 10)
    }

    private fun laptop(w: Int, h: Int, shift: Int): Bitmap {
        val screen = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val c = Canvas(screen)
        c.drawColor(Color.rgb(30, 30, 36))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(225, 225, 225); textSize = 16f; typeface = Typeface.SANS_SERIF }
        SCREEN.forEachIndexed { i, line -> c.drawText(line, 120f, 120f + i * 30f, paint) }
        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val f = Canvas(frame)
        f.drawColor(Color.rgb(70, 60, 50))
        val sw = w * 0.85f
        val sh = sw * 1080f / 1920f
        val left = (w - sw) / 2 + shift * w / 480f
        val top = (h - sh) / 2
        f.drawBitmap(screen, null, android.graphics.RectF(left, top, left + sw, top + sh), Paint(Paint.FILTER_BITMAP_FLAG))
        // A camera a little soft: down by a third and back.
        val soft = Bitmap.createScaledBitmap(frame, w * 2 / 3, h * 2 / 3, true)
        return Bitmap.createScaledBitmap(soft, w, h, true)
    }

    private fun mixedFonts(scene: String, page: List<List<Run>> = MIXED, title: String = "Weekly Notes", frame: (Int) -> Bitmap) {
        val reads = replay(scene, List(10) { frame(it) })
        assertTrue("titled ${reads.firstNotNullOfOrNull { it.offer }}", reads.firstNotNullOfOrNull { it.offer }?.contains("'$title'") == true)
        val offer = reads.firstOrNull { it.offer != null }
        assertNotNull("never offered", offer)
        val heard = reads.firstNotNullOf { it.full }
        assertTrue("a full stop inside a line: \"$heard\"", !heard.contains("at. ") && !heard.contains("start at."))
        if (page === INLINE) assertTrue("a big word lost: \"$heard\"", heard.contains("start at FIVE dollars", ignoreCase = true))
        val lines = page.map { line -> line.joinToString("") { it.text } }
        val at = lines.map { found(heard, it) }
        assertTrue("not read: ${lines.filterIndexed { i, _ -> at[i] < 0 }} in \"$heard\"", at.all { it >= 0 })
        assertEquals("order in \"$heard\"", at.sorted(), at)
    }

    /** Text of 14 px on a 640 px frame is a page held at about 50 cm: measured, not asserted. */
    @Test fun smallTextFarAway() {
        replay("page, small text", List(6) { page(PAGE.take(10), textPx = 14f, shiftPx = it % 2) })
    }

    @Test fun aBlurredPage() {
        replay("page, blurred", List(6) { blur(page(PAGE.take(8), textPx = 22f, shiftPx = 0)) })
    }

    /** Real frames saved from the phone, one folder per scene; skipped when there are none. */
    @Test fun realFrames() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val scenes = assets.list("replay").orEmpty()
        for (scene in scenes) {
            val frames = assets.list("replay/$scene").orEmpty().sorted().mapNotNull { name ->
                assets.open("replay/$scene/$name").use { BitmapFactory.decodeStream(it) }
            }
            if (frames.isNotEmpty()) assertNotNull(replay("real: $scene", frames))
        }
    }

    // ---- frames ------------------------------------------------------------------------------------

    /** Lines of dark text on a light screen, as the camera's 480 x 640 frame sees it; [shiftPx] is a shaking hand. */
    private fun page(lines: List<String>, textPx: Float, shiftPx: Int): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(238, 238, 232))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 30); textSize = textPx; typeface = Typeface.SANS_SERIF }
        var y = 60f + shiftPx * 2
        for (line in lines) {
            c.drawText(line, 20f + shiftPx, y, paint)
            y += textPx * 1.9f
        }
        return b
    }

    /** One piece of a line in its own font. */
    private class Run(val text: String, val px: Float, val face: Typeface)

    /** Lines made of runs in other fonts, each line's runs on one baseline. */
    private fun styled(lines: List<List<Run>>, shiftPx: Int, dark: Boolean = false): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(if (dark) Color.rgb(28, 40, 70) else Color.rgb(240, 238, 230))
        var y = 40f + shiftPx * 2
        for (line in lines) {
            val tallest = line.maxOf { it.px }
            y += tallest
            var x = 16f + shiftPx
            for (run in line) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (dark) Color.rgb(245, 240, 220) else Color.rgb(25, 25, 25); textSize = run.px; typeface = run.face }
                c.drawText(run.text, x, y, paint)
                x += paint.measureText(run.text)
            }
            y += tallest * 0.7f
        }
        return b
    }

    private fun sign(): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(200, 200, 195))
        c.drawRect(30f, 200f, 450f, 380f, Paint().apply { color = Color.rgb(20, 120, 50) })
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 52f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("EMERGENCY", 60f, 275f, big)
        c.drawText("EXIT", 170f, 345f, big)
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 40); textSize = 18f }
        c.drawText("Keep this door closed at all times", 40f, 440f, small)
        c.drawText("Fire door - do not obstruct", 40f, 470f, small)
        return b
    }

    private fun qr(text: String): Bitmap {
        val size = 300
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.rgb(235, 235, 235))
        val left = (W - size) / 2
        val top = (H - size) / 2
        for (x in 0 until size) for (y in 0 until size) if (matrix[x, y]) b.setPixel(left + x, top + y, Color.BLACK)
        return b
    }

    private fun tilt(b: Bitmap, degrees: Float): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.rgb(120, 110, 100))
        c.rotate(degrees, W / 2f, H / 2f)
        c.drawBitmap(b, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun noisy(b: Bitmap, seed: Int): Bitmap {
        val random = java.util.Random(seed.toLong())
        val px = IntArray(W * H)
        b.getPixels(px, 0, W, 0, 0, W, H)
        for (i in px.indices) {
            val n = (random.nextGaussian() * 12).toInt()
            val p = px[i]
            px[i] = Color.rgb(((p shr 16 and 255) + n).coerceIn(0, 255), ((p shr 8 and 255) + n).coerceIn(0, 255), ((p and 255) + n).coerceIn(0, 255))
        }
        return Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888)
    }

    /** A camera a little out of focus: half size and back. */
    private fun blur(b: Bitmap): Bitmap =
        Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(b, W / 2, H / 2, true), W, H, true)

    /** Where [line] was said in [heard] (OCR may change a letter or two), or -1. */
    private fun found(heard: String, line: String): Int {
        val words = line.split(' ')
        val head = words.take(3).joinToString(" ")
        val i = heard.indexOf(head, ignoreCase = true)
        if (i >= 0) return i
        // A misread word: look for any window of the same length that is the same line.
        val n = line.length
        for (start in 0..maxOf(0, heard.length - n)) {
            if (ReaderPolicy.sameLine(heard.substring(start, minOf(heard.length, start + n)), line)) return start
        }
        return -1
    }

    companion object {
        private const val TAG = "NungilReplay"

        /** "Text found, …. Say read to hear it." takes about 3 s to say. */
        private const val ANSWER_AFTER_MS = 2_400L
        private const val W = 480
        private const val H = 640
        private val REPORT = StringBuilder()

        private val PAGE = listOf(
            "The reader reads every line in view",
            "from the top of the screen to the bottom.",
            "Small print next to big text is left out.",
            "A line is said once it was seen twice,",
            "and it is not said again for ten seconds.",
            "Codes are read at once, text a moment later.",
            "Point the phone at a page and hold it still.",
            "The voice pauses between the lines.",
            "Nothing is sent anywhere: it all runs here.",
            "Say stop to make it quiet.",
        )

        private val SERIF: Typeface = Typeface.SERIF
        private val SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        private val SANS: Typeface = Typeface.SANS_SERIF
        private val SANS_BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        private val ITALIC: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        private val MONO: Typeface = Typeface.MONOSPACE

        private val MIXED = listOf(
            listOf(Run("Weekly Notes", 44f, SERIF_BOLD)),
            listOf(Run("A short guide to the new reader", 24f, ITALIC)),
            listOf(Run("Monday: buy fresh bread and milk", 20f, SERIF)),
            listOf(Run("Tuesday:", 20f, SANS_BOLD), Run(" call the doctor at ten", 20f, SANS)),
            listOf(Run("Wednesday: meet Anna", 20f, MONO)),
            listOf(Run("SALE", 36f, SANS_BOLD), Run(" all shoes half price", 18f, SANS)),
            listOf(Run("Thursday: pay the water bill", 18f, SANS_BOLD)),
            listOf(Run("Friday: the ", 20f, SERIF), Run("big", 20f, SERIF_BOLD), Run(" match starts at six", 20f, SERIF)),
            listOf(Run("Small print: offer ends on Sunday", 14f, SERIF)),
        )

        private val SCREEN = listOf(
            "Reader offers one line from the laptop screen",
            "The camera frame is small for screen text",
            "Each line here is sixteen pixels high",
            "Point the phone at the middle of the screen",
            "Saved Messages and the chat list on the left",
            "The text should be read from the top down",
            "Live scan found a laptop and a white bottle",
            "Say read to hear the whole screen",
            "Nothing is sent anywhere: it all runs here",
            "This is the last line of the laptop screen",
        )

        private val INLINE = listOf(
            listOf(Run("Opening Hours", 40f, SANS_BOLD)),
            listOf(Run("The shop opens at ", 18f, SANS), Run("NINE", 32f, SANS_BOLD), Run(" every morning", 18f, SANS)),
            listOf(Run("and closes at ", 18f, SERIF), Run("six in the evening", 26f, SERIF_BOLD)),
            listOf(Run("On ", 18f, SANS), Run("Sunday", 24f, ITALIC), Run(" it is ", 18f, SANS), Run("closed", 30f, SANS_BOLD), Run(" all day.", 14f, SANS)),
            listOf(Run("Call ", 16f, MONO), Run("010 5555 1234", 22f, SANS_BOLD), Run(" to book a table", 16f, MONO)),
            listOf(Run("Prices start at ", 18f, SERIF), Run("FIVE", 40f, SERIF_BOLD), Run(" dollars", 14f, SERIF), Run(" for a cup of tea.", 18f, SERIF)),
            listOf(Run("Thank you for coming", 18f, ITALIC)),
        )

        private val MORE = listOf(
            listOf(Run("Lesson Three", 42f, Typeface.create("sans-serif-condensed", Typeface.BOLD))),
            listOf(Run("Functions take input", 24f, Typeface.create("cursive", Typeface.NORMAL))),
            listOf(Run("and give back an output.", 22f, Typeface.create("sans-serif-light", Typeface.NORMAL))),
            listOf(Run("Step 1: ", 22f, Typeface.create("serif-monospace", Typeface.BOLD)), Run("name the function", 22f, Typeface.create("serif-monospace", Typeface.NORMAL))),
            listOf(Run("Step 2: write what it needs", 22f, Typeface.create("casual", Typeface.NORMAL))),
            listOf(Run("Step 3: ", 22f, Typeface.create("sans-serif-medium", Typeface.BOLD)), Run("return the answer", 22f, Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC))),
            listOf(Run("Homework is due on Monday", 16f, Typeface.create("sans-serif-condensed", Typeface.ITALIC))),
        )

        /** All scenes' reads in one file, for adb pull. */
        @JvmStatic @AfterClass
        fun writeReport() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dir = context.getExternalFilesDir("replay") ?: return
            File(dir, "report.txt").writeText(REPORT.toString())
        }
    }
}
