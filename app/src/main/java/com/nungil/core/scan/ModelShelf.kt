package com.nungil.core.scan

import java.io.Closeable

/**
 * One loaded instance per model kind and key, shared by the screens (stage 4a, spec 2026-10-13-shared-models-design).
 * Each screen built and closed its own models, so every screen switch loaded a model next to running work: MediaPipe
 * crashed (Task 7), and opening Live after Find loaded the same files again.
 *
 * - [take] hands out the model for a kind and key, built once; the [Lease] gives it back when closed.
 * - A model given back by every user stays [keepMs], so a screen opened again soon finds it loaded; [sweep] closes
 *   the ones kept longer.
 * - Taking another key of a kind (another detector file or processor) first closes the kind's idle models: two
 *   detectors are never loaded only because a setting changed.
 *
 * Thread-safe. Models are built and closed outside the shelf's own lock, so giving one back never waits for a load.
 */
class ModelShelf(private val clock: () -> Long, private val keepMs: Long = KEEP_MS) {
    private class Slot(val kind: String, val key: String, val model: Closeable) {
        var users = 0
        var idleSince = 0L
    }

    /** One user's hold on a model; closing it gives the model back (once). */
    inner class Lease<M : Closeable> internal constructor(val model: M) : Closeable {
        private var open = true

        override fun close() {
            synchronized(this@ModelShelf) {
                if (!open) return
                open = false
                val slot = slots.firstOrNull { it.model === model } ?: return
                slot.users--
                if (slot.users == 0) slot.idleSince = clock()
            }
        }
    }

    private val slots = mutableListOf<Slot>()

    /** One lock per kind and key while it is built: a second screen waits for the first build instead of building. */
    private val building = HashMap<String, Any>()

    /**
     * The model of [kind] and [key]: the loaded one, else [build] (outside the shelf's lock; a second taker of the
     * same model waits for that build and gets it: the detector warmed up at start and Live opened at once).
     */
    fun <M : Closeable> take(kind: String, key: String, build: () -> M): Lease<M> {
        val lock = synchronized(this) { building.getOrPut("$kind|$key") { Any() } }
        return synchronized(lock) { takeNow(kind, key, build) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <M : Closeable> takeNow(kind: String, key: String, build: () -> M): Lease<M> {
        val stale: List<Slot>
        synchronized(this) {
            slots.firstOrNull { it.kind == kind && it.key == key }?.let { slot ->
                slot.users++
                return Lease(slot.model as M)
            }
            stale = slots.filter { it.kind == kind && it.users == 0 }
            slots.removeAll(stale)
        }
        stale.forEach { closeQuietly(it.model) }
        val made = build()
        synchronized(this) {
            // Another screen may have built it meanwhile: theirs is kept.
            slots.firstOrNull { it.kind == kind && it.key == key }?.let { slot ->
                slot.users++
                closeQuietly(made)
                return Lease(slot.model as M)
            }
            slots += Slot(kind, key, made).also { it.users = 1 }
            return Lease(made)
        }
    }

    /** Closes the models nobody has used for [keepMs]. */
    fun sweep() {
        val old: List<Slot>
        synchronized(this) {
            val now = clock()
            old = slots.filter { it.users == 0 && now - it.idleSince >= keepMs }
            slots.removeAll(old)
        }
        old.forEach { closeQuietly(it.model) }
    }

    /** Models loaded, in use or kept. */
    val loaded: Int get() = synchronized(this) { slots.size }

    /** The app is going away: every model is closed, in use or not. */
    fun closeAll() {
        val all: List<Slot>
        synchronized(this) {
            all = slots.toList()
            slots.clear()
        }
        all.forEach { closeQuietly(it.model) }
    }

    private fun closeQuietly(model: Closeable) {
        try {
            model.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        /** A screen opened again within this finds its models loaded (user's choice, 2026-10-13). */
        const val KEEP_MS = 30_000L
    }
}
