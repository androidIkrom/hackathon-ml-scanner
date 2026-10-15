package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.nungil.debug.ReplayHostActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Items were saved with MediaPipe's ImageEmbedder; ItemEmbedder runs MobileNet straight through LiteRT since
 * 2026-10-15 (MediaPipe took 26 ms a square, the model 6). The vectors must stay the saved ones' kind: a cosine of
 * 0.99 at least on crops of the desk photos. Also logs the time of each.
 */
@RunWith(AndroidJUnit4::class)
class ItemEmbedderParityTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun vectorsAreMediaPipes() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val reference = ImageEmbedder.createFromOptions(
            context,
            ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("mobilenet_v3_small.tflite").build())
                .setRunningMode(RunningMode.IMAGE).setL2Normalize(true).setQuantize(false).build(),
        )
        val embedder = ItemEmbedder(context)
        val cosines = mutableListOf<Double>()
        val ours = mutableListOf<Double>()
        val theirs = mutableListOf<Double>()
        try {
            for (name in assets.list("desks").orEmpty().sorted().take(12)) {
                val photo = assets.open("desks/$name").use { BitmapFactory.decodeStream(it) } ?: continue
                for ((x, y, w, h) in listOf(listOf(0, 0, 200, 200), listOf(100, 150, 160, 260), listOf(200, 300, 280, 180))) {
                    if (x + w > photo.width || y + h > photo.height) continue
                    val crop = Bitmap.createBitmap(photo, x, y, w, h)
                    var t = SystemClock.elapsedRealtimeNanos()
                    val want = reference.embed(BitmapImageBuilder(crop).build()).embeddingResult().embeddings()[0].floatEmbedding()
                    theirs += (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                    t = SystemClock.elapsedRealtimeNanos()
                    val got = embedder.embed(crop)!!
                    ours += (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                    cosines += want.indices.sumOf { want[it].toDouble() * got[it] }
                }
            }
        } finally {
            embedder.close()
            reference.close()
        }
        Log.i(
            "NungilPerf",
            "Item embedder: cosine to MediaPipe min ${"%.4f".format(cosines.min())} mean ${"%.4f".format(cosines.average())} " +
                "over ${cosines.size}; ${"%.1f".format(ours.sorted()[ours.size / 2])} ms against MediaPipe's " +
                "${"%.1f".format(theirs.sorted()[theirs.size / 2])} ms",
        )
        assertTrue("cosine ${cosines.min()}", cosines.size >= 30 && cosines.min() >= 0.99)
    }
}
