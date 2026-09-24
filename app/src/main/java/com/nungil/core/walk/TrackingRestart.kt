package com.nungil.core.walk

import kotlin.math.min

/**
 * When walk mode may restart an ARCore session that stopped tracking. A restart pauses ARCore, and a pause
 * while its depth pipeline is busy crashed natively (Infinix X6880 in a dark room, restarting every 15 s).
 * So there is no restart when ARCore can recover by itself (too dark, too little texture, moving too fast),
 * and every restart that does not bring tracking back doubles the wait before the next one.
 */
class TrackingRestart {
    private var lastRestartAt = Long.MIN_VALUE
    private var restarts = 0

    /**
     * [lastTrackedAt]: last frame ARCore was tracking. [startedAt]: when the session was (re)started.
     * [selfRecovers]: the current failure reason is one ARCore gets over on its own.
     */
    fun shouldRestart(now: Long, lastTrackedAt: Long, startedAt: Long, selfRecovers: Boolean): Boolean {
        if (lastTrackedAt > lastRestartAt) restarts = 0
        if (selfRecovers || now - maxOf(lastTrackedAt, startedAt) < STALE_MS) return false
        if (restarts > 0 && now - lastRestartAt < GAP_MS shl min(restarts - 1, MAX_DOUBLINGS)) return false
        lastRestartAt = now
        restarts++
        return true
    }

    companion object {
        const val STALE_MS = 5_000L

        /** 15 s, 30 s, 60 s, then every 120 s. */
        const val GAP_MS = 15_000L
        const val MAX_DOUBLINGS = 3
    }
}
