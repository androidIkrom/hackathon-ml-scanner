package com.nungil.bench

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nungil.contract.Compute
import com.nungil.contract.Dest
import com.nungil.contract.ScanMode
import com.nungil.data.SettingsStore
import com.nungil.shell.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the camera screens one after another in the real app, as a user going from one to the next, so the
 * "Perf" lines of CameraSession (frames a second, where a frame's time goes, the first frame) and the samples of
 * `tools/perf/tour.sh` (memory, CPU, heat) can be read per screen. Nothing is asserted. Run it with `am instrument`
 * and `-e secs 30` for a longer stay on each screen, `-e compute CPU` to run the detector on the CPU for this tour only.
 */
@RunWith(AndroidJUnit4::class)
class PerfTour {
    @Test
    fun tour() {
        val args = InstrumentationRegistry.getArguments()
        val secs = args.getString("secs")?.toLong() ?: 25L
        val store = SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val before = store.load()
        args.getString("compute")?.let { store.save(before.copy(compute = Compute.valueOf(it))) }
        val stops = listOf(
            "live" to Dest.Scan(ScanMode.LIVE),
            "home" to Dest.Home,
            "reader" to Dest.Reader,
            "home" to Dest.Home,
            "find" to Dest.Search("bottle"),
            "home" to Dest.Home,
            "walk" to Dest.Walk,
            "home" to Dest.Home,
            // Live again: its models kept on the shelf, or loaded again.
            "live-again" to Dest.Scan(ScanMode.LIVE),
            "home" to Dest.Home,
        )
        ActivityScenario.launch(MainActivity::class.java).use { app ->
            Thread.sleep(START_MS)
            for ((name, dest) in stops) {
                Log.i(TAG, "Tour: $name")
                app.onActivity { it.open(dest) }
                Thread.sleep(if (dest == Dest.Home) HOME_MS else secs * 1000)
            }
            Log.i(TAG, "Tour: done")
        }
        store.save(before)
    }

    private companion object {
        const val TAG = "NungilPerf"
        const val START_MS = 6_000L
        const val HOME_MS = 4_000L
    }
}
