package com.nungil.bench

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * An empty screen the benchmark stays behind. Without a visible activity the phone's power manager
 * (Transsion "Hiber") freezes the process a few seconds into the run.
 */
class BenchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply {
            text = "Benchmark running. Do not touch the phone."
            gravity = Gravity.CENTER
        })
    }
}
