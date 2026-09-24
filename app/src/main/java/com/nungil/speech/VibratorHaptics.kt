package com.nungil.speech

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nungil.contract.Buzz
import com.nungil.contract.app.Haptics
import com.nungil.core.ui.HapticPatterns

/** Plays [HapticPatterns] on the phone's vibrator. Safe from any thread; silent on phones without one. */
class VibratorHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun buzz(kind: Buzz) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val timings = HapticPatterns.timings(kind)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, HapticPatterns.amplitudes(kind), -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }
}
