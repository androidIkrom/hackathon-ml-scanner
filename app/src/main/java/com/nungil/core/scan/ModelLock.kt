package com.nungil.core.scan

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Every MediaPipe model call goes through here. Loading or closing a model ([load]) waits for the calls that
 * are running and lets no new one start; running models ([run]) go together. A model loaded while another one
 * ran on another thread crashed MediaPipe (SIGBUS right after "Created global reference to asset manager":
 * Live then Full scan, Live then Find; live-quality plan, Task 7).
 * Never [load] inside [run]: a running call cannot wait for itself.
 */
class ModelLock {
    // Fair: a waiting load is not kept out by the detector's next frame.
    private val lock = ReentrantReadWriteLock(true)

    fun <T> run(call: () -> T): T = lock.read(call)

    fun <T> load(change: () -> T): T = lock.write(change)

    companion object {
        /** The one every model uses. */
        val shared = ModelLock()
    }
}
