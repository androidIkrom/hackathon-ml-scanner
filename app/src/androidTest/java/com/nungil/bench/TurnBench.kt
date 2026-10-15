package com.nungil.bench

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nungil.debug.ReplayHostActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long turning a camera frame upright takes (CameraSession.upright), with and without filtering, at Live's and the
 * reader's sizes (the phone gives the reader 1920 x 2560). Nothing is asserted; the times go to logcat tag NungilPerf.
 */
@RunWith(AndroidJUnit4::class)
class TurnBench {
    /** Keeps the app in front: in the background the phone freezes it and the test never ends (ReplayHostActivity). */
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)

    @Test
    fun turning() {
        for ((w, h) in listOf(640 to 480, 1920 to 1440, 2560 to 1920)) {
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF808080.toInt()) }
            val turn = Matrix().apply { postRotate(90f) }
            for (filter in listOf(true, false)) {
                val times = (0 until 15).map {
                    val t = SystemClock.elapsedRealtimeNanos()
                    Bitmap.createBitmap(frame, 0, 0, w, h, turn, filter).recycle()
                    (SystemClock.elapsedRealtimeNanos() - t) / 1_000_000.0
                }.drop(3).sorted()
                Log.i("NungilPerf", "Turn ${w}x$h filter=$filter: median ${"%.1f".format(times[times.size / 2])} ms")
            }
            val copies = (0 until 15).map {
                val t = SystemClock.elapsedRealtimeNanos()
                frame.copy(Bitmap.Config.ARGB_8888, false).recycle()
                (SystemClock.elapsedRealtimeNanos() - t) / 1_000_000.0
            }.drop(3).sorted()
            Log.i("NungilPerf", "Copy ${w}x$h: median ${"%.1f".format(copies[copies.size / 2])} ms")
        }
    }
}
