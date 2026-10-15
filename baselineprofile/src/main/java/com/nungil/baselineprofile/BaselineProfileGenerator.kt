package com.nungil.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's start, as the user starts it, for its Baseline Profile: the classes it runs are compiled ahead of time in
 * the release build. On the emulator a release build already opened in 1.1 s against 1.7 s for the debug build
 * (2026-10-15).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun start() = rule.collect(packageName = "com.nungil", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}
