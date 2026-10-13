package com.nungil.debug

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

/**
 * Debug builds only. A blank screen shown, over the lock screen too, while an on-device test runs (ReaderReplayTest):
 * the phone's power manager (Transsion "Hiber") freezes an app with no screen in front about 10 s after it went to
 * the background, and a frozen test never ends (2026-10-12).
 */
class ReplayHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
