package com.nungil.people

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nungil.debug.ReplayHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/** FaceNet runs on the app's TFLite runtime (LiteRT since 2026-10-15) and gives a unit vector, the same for one crop. */
@RunWith(AndroidJUnit4::class)
class FaceEmbedderSmokeTest {
    /** Keeps the app in front: in the background the phone freezes it and the test never ends (ReplayHostActivity). */
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)

    @Test fun aCropGivesTheSameUnitVectorTwice() {
        val crop = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 160) for (x in 0 until 160) setPixel(x, y, Color.rgb(x, y, (x + y) / 2))
        }
        FaceEmbedder(InstrumentationRegistry.getInstrumentation().targetContext).use { embedder ->
            val a = embedder.embedCrop(crop)
            val b = embedder.embedCrop(crop)
            assertEquals(512, a.size)
            assertEquals(1.0, sqrt(a.sumOf { it.toDouble() * it }), 1e-3)
            assertTrue(a.contentEquals(b))
        }
    }
}
