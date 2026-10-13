package com.nungil.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** "More sure" said while the camera runs reaches it at once: CameraSession listens to the saved score. */
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @Test fun aNewScoreIsHeardByTheListener() {
        val store = SettingsStore(ApplicationProvider.getApplicationContext())
        val before = store.load()
        val heard = CountDownLatch(1)
        var score = 0f
        lateinit var stop: () -> Unit
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            stop = store.onMinScore { score = it; heard.countDown() }
        }
        val to = if (before.minScore > 0.5f) 0.5f else 0.6f
        try {
            store.save(before.copy(minScore = to))
            heard.await(3, TimeUnit.SECONDS)
            assertEquals(to, score, 1e-6f)
        } finally {
            stop()
            store.save(before)
        }
    }
}
