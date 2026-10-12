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
            for (frame in frames) {
                val read = engine.read(frame, t, Lang.EN)
                if (t == 0L) {
                    for (l in read.lines) {
                        Log.i(TAG, String.format(java.util.Locale.US, "  line h %.3f c %.2f %s \"%s\"", l.height, l.confidence, if (ReaderPolicy.readable(l)) "read" else "dropped", l.text))
                    }
                }
                val offer = read.offer?.phrase(Lang.EN)
                val full = if (offer != null) engine.accept() else null
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
        val heard = first.full!!
        val at = lines.map { line -> lines.indexOf(line) to found(heard, line) }
        assertTrue("not read: ${at.filter { it.second < 0 }.map { lines[it.first] }}", at.all { it.second >= 0 })
        assertEquals("order", at.sortedBy { it.second }.map { it.first }, at.map { it.first })
    }

    @Test fun aSignIsOfferedByItsTitleAndReadWithItsSmallPrint() {
        val reads = replay("sign with small print", List(6) { sign() })
        val offer = reads.single { it.offer != null }
        assertEquals("Text found, titled 'EMERGENCY EXIT'. Say read to hear it.", offer.offer)
        assertTrue("read: ${offer.full}", found(offer.full!!, "Keep this door closed at all times") >= 0)
    }

    @Test fun aQrCodeIsReadAtOnce() {
        val reads = replay("QR code", List(4) { qr("https://nungil.app/test") })
        assertEquals("https://nungil.app/test", reads.first().code)
        assertEquals("Code: https://nungil.app/test", reads.first().text)
        assertTrue("said again", reads.drop(1).all { it.text == null })
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

        /** All scenes' reads in one file, for adb pull. */
        @JvmStatic @AfterClass
        fun writeReport() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dir = context.getExternalFilesDir("replay") ?: return
            File(dir, "report.txt").writeText(REPORT.toString())
        }
    }
}
