package com.nungil.items

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenterlegacy.InteractiveSegmenterLegacy
import com.nungil.debug.ReplayHostActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteOrder

/**
 * ItemSegmenter ran magic_touch through MediaPipe's InteractiveSegmenterLegacy until 2026-10-15 and straight through
 * LiteRT since (on the GPU when the phone takes it). The outlines must stay MediaPipe's: on desk photos pointed at
 * several places, the parts at 0.5 or more overlap by 0.9 at least on average (intersection over union). Also logs
 * the time of each.
 */
@RunWith(AndroidJUnit4::class)
class ItemSegmenterParityTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun outlinesAreMediaPipes() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val reference = InteractiveSegmenterLegacy.createFromOptions(
            context,
            InteractiveSegmenterLegacy.InteractiveSegmenterLegacyOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("magic_touch.tflite").build())
                .setOutputConfidenceMasks(true).setOutputCategoryMask(false).build(),
        )
        val segmenter = ItemSegmenter(context)
        val overlaps = mutableListOf<Double>()
        val ours = mutableListOf<Double>()
        val theirs = mutableListOf<Double>()
        try {
            for (name in assets.list("desks").orEmpty().sorted().take(10)) {
                val photo = assets.open("desks/$name").use { BitmapFactory.decodeStream(it) } ?: continue
                for ((x, y) in listOf(0.5f to 0.5f, 0.3f to 0.6f, 0.7f to 0.4f)) {
                    var t = SystemClock.elapsedRealtimeNanos()
                    val point = InteractiveSegmenterLegacy.RegionOfInterest.create(
                        NormalizedKeypoint.create(x * photo.width, y * photo.height),
                    )
                    val mask = reference.segment(BitmapImageBuilder(photo).build(), point).confidenceMasks().get().first()
                    val want = ByteBufferExtractor.extract(mask).order(ByteOrder.nativeOrder()).asFloatBuffer()
                    theirs += (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                    t = SystemClock.elapsedRealtimeNanos()
                    val got = segmenter.at(photo, x, y)!!
                    ours += (SystemClock.elapsedRealtimeNanos() - t) / 1e6
                    assertTrue(got.width == mask.width && got.height == mask.height)
                    var both = 0
                    var either = 0
                    for (i in got.values.indices) {
                        val a = want.get(i) >= 0.5f
                        val b = got.values[i] >= 0.5f
                        if (a && b) both++
                        if (a || b) either++
                    }
                    overlaps += if (either == 0) 1.0 else both.toDouble() / either
                    Log.i("NungilPerf", "Item segmenter parity: $name at $x,$y overlap ${"%.3f".format(overlaps.last())}")
                }
            }
        } finally {
            segmenter.close()
            reference.close()
        }
        Log.i(
            "NungilPerf",
            "Item segmenter: overlap with MediaPipe min ${"%.3f".format(overlaps.min())} mean ${"%.3f".format(overlaps.average())} " +
                "over ${overlaps.size}; ${"%.1f".format(ours.sorted()[ours.size / 2])} ms against MediaPipe's " +
                "${"%.1f".format(theirs.sorted()[theirs.size / 2])} ms",
        )
        assertTrue("overlap ${overlaps.average()}", overlaps.size >= 20 && overlaps.average() >= 0.9)
    }

    /** Ours alone, again and again: the time a cut takes with nothing else running. */
    @Test fun timeAlone() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val photo = assets.open("desks/" + assets.list("desks")!!.sorted().first()).use { BitmapFactory.decodeStream(it) }
        ItemSegmenter(context).use { segmenter ->
            val times = (0 until 40).map {
                val t = SystemClock.elapsedRealtimeNanos()
                segmenter.at(photo, 0.5f, 0.5f)
                (SystemClock.elapsedRealtimeNanos() - t) / 1e6
            }.drop(5).sorted()
            Log.i("NungilPerf", "Item segmenter alone: median ${"%.1f".format(times[times.size / 2])} ms")
        }
    }
}
