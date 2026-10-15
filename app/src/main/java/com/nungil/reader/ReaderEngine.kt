package com.nungil.reader

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nungil.contract.Lang
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * One read of one frame: a code when there is one (said at once), else text in view, offered first and read in full
 * on the user's word ([ReaderPolicy]). The reader screen and the replay test (androidTest ReaderReplayTest) read the
 * same way. One read at a time, on a worker thread; [accept] and [decline] from any thread. [timeoutS]: how long one
 * read may take (the emulator, with no GPU, is many times slower than the phone).
 */
class ReaderEngine(private val timeoutS: Long = TIMEOUT_S) : Closeable {
    /**
     * [spoken]: a code to say now, or null; [offer]: text found, to offer ("Text found, …"); [lines]: what OCR saw;
     * [code]: a QR or barcode's value.
     */
    class Read(val spoken: String?, val offer: ReaderPolicy.Offer?, val lines: List<SeenLine>, val code: String?, val ocrMs: Long)

    private val scanner = BarcodeScanning.getClient()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val policy = ReaderPolicy()

    /**
     * Throws when ML Kit fails or takes over [timeoutS] seconds. [rotation]: the turn (0, 90, 180, 270) that sets
     * [bitmap] upright; ML Kit turns it itself, as fast as reading an upright one, where turning a 1920 x 2560 frame
     * first took 170-210 ms on the phone (2026-10-15). Lines come in the upright frame (ML Kit's corners do not: ReaderPolicy.uprightCorners). [nowMs]: the time of the frame.
     */
    fun read(bitmap: Bitmap, rotation: Int, nowMs: Long, lang: Lang): Read {
        val image = InputImage.fromBitmap(bitmap, rotation)
        val started = System.nanoTime()
        val code = Tasks.await(scanner.process(image), timeoutS, TimeUnit.SECONDS)
            .firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
        if (code != null) {
            // A code is exact: it is read at once.
            val spoken = ReaderPolicy.codePhrase(code, lang).takeIf { synchronized(policy) { policy.shouldSpeak(it, nowMs) } }
            return Read(spoken, null, emptyList(), code, (System.nanoTime() - started) / 1_000_000)
        }
        val text = Tasks.await(recognizer.process(image), timeoutS, TimeUnit.SECONDS)
        val ms = (System.nanoTime() - started) / 1_000_000
        val onSide = rotation % 180 != 0
        val w = (if (onSide) bitmap.height else bitmap.width).toFloat()
        val h = (if (onSide) bitmap.width else bitmap.height).toFloat()
        val raw = text.textBlocks.withIndex().flatMap { (b, block) ->
            block.lines.mapNotNull { line ->
                val corners = line.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
                val (xs, ys) = ReaderPolicy.uprightCorners(
                    FloatArray(4) { corners[it].x.toFloat() }, FloatArray(4) { corners[it].y.toFloat() },
                    rotation, bitmap.width.toFloat(), bitmap.height.toFloat(),
                )
                ReaderPolicy.RawLine(line.text, line.confidence, xs, ys, b)
            }
        }
        // A page held crooked is set straight, so its lines keep their order.
        val lines = ReaderPolicy.straighten(raw, w, h)
        val offer = synchronized(policy) { policy.found(lines, nowMs) }
        return Read(null, offer, lines, null, ms)
    }

    /** "Read" at [nowMs]: the offered text's lines, or null when nothing was offered. */
    fun accept(nowMs: Long): List<String>? = synchronized(policy) { policy.accept(nowMs) }

    /** [chars] of text are being read again from [nowMs]. */
    fun reading(nowMs: Long, chars: Int) = synchronized(policy) { policy.reading(nowMs, chars) }

    /** "Another text". */
    fun another() = synchronized(policy) { policy.another() }

    /** "No", or no answer in time. */
    fun decline() = synchronized(policy) { policy.decline() }

    override fun close() {
        scanner.close()
        recognizer.close()
    }

    companion object {
        /**
         * A read may take this long. It was 2 s: a full laptop screen in the 1440 x 1920 frame took longer on the
         * phone, and the read timed out twice before one came through (the logs, 2026-10-13).
         */
        const val TIMEOUT_S = 6L

        /**
         * The upright frame the reader asks the camera for. At 480 x 640 the text of a laptop screen was 2-4 px high
         * and none of it was read (replay "laptop screen", 2026-10-13).
         */
        const val FRAME_WIDTH = 1440
        const val FRAME_HEIGHT = 1920
    }
}
