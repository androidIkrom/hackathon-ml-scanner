package com.nungil.walk

import android.app.Activity
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException

/**
 * The ARCore session for walk mode. Walk mode needs metric depth, so ARCore owns the camera here
 * (build guide §7). Every capability is checked; walk mode keeps working without the missing one.
 * Main thread only.
 */
class ArCamera(private val activity: Activity) {

    enum class Status { READY, INSTALLING, UNSUPPORTED }

    var session: Session? = null
        private set
    var depthSupported = false
        private set
    var semanticsSupported = false
        private set

    /** Call from onResume. [userRequestedInstall] is true only the first time. */
    fun open(userRequestedInstall: Boolean): Status {
        if (session != null) return Status.READY
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> return Status.INSTALLING
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            val s = Session(activity)
            val config = Config(s)
            depthSupported = s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            if (depthSupported) config.depthMode = Config.DepthMode.AUTOMATIC
            // Scene semantics (road / sidewalk under the feet) stays off: with it on, pausing or restarting the
            // session crashed natively inside ARCore on an Infinix X6880, and it only adds an outdoor hint.
            semanticsSupported = false
            config.semanticMode = Config.SemanticMode.DISABLED
            config.focusMode = Config.FocusMode.AUTO
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            // Pure cost here: nothing uses planes or light.
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            s.configure(config)
            session = s
            Log.i(TAG, "ARCore ready: depth=$depthSupported semantics=$semanticsSupported")
            Status.READY
        } catch (e: UnavailableException) {
            Log.w(TAG, "ARCore unavailable: ${e.javaClass.simpleName}")
            Status.UNSUPPORTED
        } catch (e: RuntimeException) {
            Log.w(TAG, "ARCore failed", e)
            Status.UNSUPPORTED
        }
    }

    /** False when the camera is taken by another app. */
    fun resume(): Boolean = try {
        session?.let { s ->
            runCatching { s.configure(s.config.apply { pipelines(this, on = true) }) }
                .onFailure { Log.w(TAG, "ARCore reconfigure on resume failed", it) }
            Log.i(TAG, "ARCore resume: depth=${s.config.depthMode} semantics=${s.config.semanticMode}")
        }
        session?.resume()
        true
    } catch (e: CameraNotAvailableException) {
        Log.w(TAG, "ARCore camera not available", e)
        false
    }

    /**
     * Pausing while ARCore's depth pipeline is still working on a frame crashed natively inside ARCore
     * (seen on an Infinix X6880 when a system dialog paused the app next to a wall). Switch depth and
     * semantics off first, then pause.
     */
    fun pause() {
        val s = session ?: return
        runCatching { s.configure(s.config.apply { pipelines(this, on = false) }) }
        runCatching { s.pause() }
    }

    private fun pipelines(config: Config, on: Boolean) {
        config.depthMode = if (on && depthSupported) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
        config.semanticMode = if (on && semanticsSupported) Config.SemanticMode.ENABLED else Config.SemanticMode.DISABLED
    }

    /** Only after the GL view has been paused (see WalkFragment.onPause): closing under a running GL thread crashes natively. */
    fun close() {
        runCatching { session?.close() }
        session = null
    }

    private companion object {
        const val TAG = "Nungil"
    }
}
