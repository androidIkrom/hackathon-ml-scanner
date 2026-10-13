package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ModelLockTest {
    private val lock = ModelLock()

    @Test fun aModelLoadsOnlyAfterTheCallThatIsRunning() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val running = CountDownLatch(1)
        val old = Thread {
            lock.run {
                running.countDown()
                Thread.sleep(200)
                order += "old call done"
            }
        }
        old.start()
        assertTrue(running.await(2, TimeUnit.SECONDS))
        lock.load { order += "new model loaded" }
        old.join()
        assertEquals(listOf("old call done", "new model loaded"), order)
    }

    @Test fun modelsRunTogether() {
        val both = CountDownLatch(2)
        val threads = List(2) {
            Thread { lock.run { both.countDown(); both.await(2, TimeUnit.SECONDS) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(0L, both.count)
    }

    @Test fun aLoadMayRunItsModelOnce() = assertEquals(7, lock.load { lock.run { 7 } })

    @Test fun theValueComesBack() = assertEquals("x", lock.run { "x" })
}
