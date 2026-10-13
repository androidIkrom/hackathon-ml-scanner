package com.nungil.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.nungil.contract.Compute
import com.nungil.contract.Detection
import com.nungil.contract.ModelChoice
import com.nungil.core.scan.DetectionFilter
import com.nungil.core.scan.DetectorPlan
import com.nungil.core.scan.FrameMath
import com.nungil.contract.ScanSettings
import com.nungil.debug.ReplayHostActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The detector Live uses (EfficientDet-Lite2) over real photos of desks (tools/desk-photos/make_frames.py, assets
 * `desks/`, not in git): what it finds, and what Live would say at the default sureness. Live said "a gray bed" at a
 * desk (live-quality plan, Task 5). Run on the emulator; logcat tag NungilDesks.
 */
@RunWith(AndroidJUnit4::class)
class DetectorPhotosTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun whatTheDetectorSeesOnDesks() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val names = assets.list("desks").orEmpty().sorted()
        // The emulator has no usable GPU: the CPU gives the same detections, slower.
        val lease = SharedModels.detector(context, DetectorPlan.modelFile(ModelChoice.ACCURATE), Compute.CPU)
        val said = HashMap<String, Int>()
        try {
            for (name in names) {
                val bitmap = assets.open("desks/$name").use { BitmapFactory.decodeStream(it) } ?: continue
                val raw = lease.model.detect(BitmapImageBuilder(bitmap).build()).detections().mapNotNull { d ->
                    val c = d.categories().firstOrNull() ?: return@mapNotNull null
                    val r = d.boundingBox()
                    Detection(c.categoryName(), c.score(), FrameMath.normalizeBox(r.left, r.top, r.right, r.bottom, bitmap.width, bitmap.height))
                }
                val kept = DetectionFilter.keep(raw, ScanSettings.DEFAULT_MIN_SCORE)
                kept.forEach { said[it.label] = (said[it.label] ?: 0) + 1 }
                Log.i(TAG, "$name: " + raw.joinToString(", ") { d ->
                    String.format(Locale.US, "%s%s %.2f [%.2f %.2f %.2f %.2f]", if (d in kept) "*" else "", d.label, d.score, d.box.left, d.box.top, d.box.right, d.box.bottom)
                })
                bitmap.recycle()
            }
        } finally {
            lease.close()
        }
        Log.i(TAG, "Live would say, over ${names.size} frames: " + said.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" })
    }

    private companion object {
        const val TAG = "NungilDesks"
    }
}
