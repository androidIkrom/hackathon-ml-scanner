package com.nungil.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.nungil.contract.Compute
import com.nungil.contract.ModelChoice
import com.nungil.core.scan.DetectorPlan
import com.nungil.debug.ReplayHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Stage 4a on the phone: screens share one loaded model per kind. Two "screens" (two threads, as two cameras' analysis
 * threads) use one GPU detector at once and hand over to each other ten times, as Live, Find and Live again do.
 * Before, each built and closed its own, and a load next to running work crashed MediaPipe (Task 7).
 */
@RunWith(AndroidJUnit4::class)
class SharedModelsTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val frame = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }

    /** One screen: takes the models, runs them [frames] times, gives them back. */
    private fun screen(frames: Int): String {
        val detector = SharedModels.detector(context, DetectorPlan.modelFile(ModelChoice.ACCURATE), Compute.GPU)
        val classifier = SharedModels.classifier(context)
        val embedder = SharedModels.itemEmbedder(context)
        val segmenter = SharedModels.itemSegmenter(context)
        try {
            repeat(frames) {
                detector.model.detect(BitmapImageBuilder(frame).build())
                classifier.model.nameCenter(frame)
                embedder.model.embed(frame)
                segmenter.model.at(frame, 0.5f, 0.5f)
            }
            return "${System.identityHashCode(detector.model)}"
        } finally {
            detector.close()
            classifier.close()
            embedder.close()
            segmenter.close()
        }
    }

    @Test fun screensHandOverTheSameModelsWithoutACrash() {
        val started = SystemClock.elapsedRealtime()
        val first = screen(1)
        val coldMs = SystemClock.elapsedRealtime() - started
        val a = Executors.newSingleThreadExecutor()
        val b = Executors.newSingleThreadExecutor()
        val ids = mutableSetOf(first)
        repeat(10) {
            // The new screen starts while the old one still runs, on another thread.
            val old = a.submit<String> { screen(5) }
            val new = b.submit<String> { screen(5) }
            ids += old.get(60, TimeUnit.SECONDS)
            ids += new.get(60, TimeUnit.SECONDS)
        }
        val warmStarted = SystemClock.elapsedRealtime()
        screen(1)
        val warmMs = SystemClock.elapsedRealtime() - warmStarted
        a.shutdown()
        b.shutdown()
        Log.i("NungilModels", "First screen ${coldMs} ms (models loaded), a screen later ${warmMs} ms (models kept)")
        assertEquals("one detector for every screen", 1, ids.size)
        assertTrue("a screen with its models kept took $warmMs ms", warmMs < coldMs)
    }

    @Test fun theSameModelIsHandedToEveryScreen() {
        val one = SharedModels.classifier(context)
        val two = SharedModels.classifier(context)
        try {
            assertSame(one.model, two.model)
        } finally {
            one.close()
            two.close()
        }
    }
}
