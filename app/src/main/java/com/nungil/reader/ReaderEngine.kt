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
 * same way. One read at a time, on a worker thread; [accept] and [decline] from any thread.
 */
class ReaderEngine : Closeable {
    /**
     * [spoken]: a code to say now, or null; [offer]: text found, to offer ("Text found, …"); [lines]: what OCR saw;
     * [code]: a QR or barcode's value.
     */
    class Read(val spoken: String?, val offer: ReaderPolicy.Offer?, val lines: List<SeenLine>, val code: String?, val ocrMs: Long)

    private val scanner = BarcodeScanning.getClient()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val policy = ReaderPolicy()

    /** Throws when ML Kit fails or takes over [TIMEOUT_S] seconds. [nowMs]: the time of the frame. */
    fun read(bitmap: Bitmap, nowMs: Long, lang: Lang): Read {
        val image = InputImage.fromBitmap(bitmap, 0)
        val started = System.nanoTime()
        val code = Tasks.await(scanner.process(image), TIMEOUT_S, TimeUnit.SECONDS)
            .firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
        if (code != null) {
            // A code is exact: it is read at once.
            val spoken = ReaderPolicy.codePhrase(code, lang).takeIf { synchronized(policy) { policy.shouldSpeak(it, nowMs) } }
            return Read(spoken, null, emptyList(), code, (System.nanoTime() - started) / 1_000_000)
        }
        val text = Tasks.await(recognizer.process(image), TIMEOUT_S, TimeUnit.SECONDS)
        val ms = (System.nanoTime() - started) / 1_000_000
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val lines = text.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            SeenLine(line.text, line.confidence, box.height() / h, box.exactCenterX() / w, box.exactCenterY() / h)
        }
        val offer = synchronized(policy) { policy.found(lines, nowMs) }
        return Read(null, offer, lines, null, ms)
    }

    /** "Read": the offered text in full, or null when nothing was offered. */
    fun accept(): String? = synchronized(policy) { policy.accept() }

    /** "No", or no answer in time. */
    fun decline() = synchronized(policy) { policy.decline() }

    override fun close() {
        scanner.close()
        recognizer.close()
    }

    private companion object {
        const val TIMEOUT_S = 2L
    }
}
