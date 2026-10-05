package com.nungil.core.ui

import com.nungil.contract.Facing
import com.nungil.contract.VoiceCommand

/** What a camera screen does with a camera command (CameraCommandPolicy). */
sealed interface CameraAction {
    /** Turn to [to], or to the other camera when null, and say which is on. */
    data class Switch(val to: Facing?) : CameraAction

    /** The screen has the back camera only (Walk: ARCore owns it). */
    data object BackCameraOnly : CameraAction

    /** The camera is not open (permission panel, screen closing): nothing to switch and nothing to say about it. */
    data object NotReady : CameraAction

    /** Name the thing in the middle of the last frame. */
    data object DescribeCentre : CameraAction

    /** Name the face nearest the middle of the last frame. */
    data object NameFace : CameraAction

    /** Pause the screen's work and stay on the screen. */
    data object Pause : CameraAction

    /** The work is paused already: leave the screen. */
    data object Leave : CameraAction

    /** Start or resume the screen's work. */
    data object Resume : CameraAction
}

/**
 * The camera commands every camera screen takes the same way (spec 2026-10-05-shared-camera-commands): front or
 * back camera, what and who is this, stop and start. Each screen used to handle them in its own way: "front
 * camera" worked on two screens, and "what is this" in Walk or Find left the screen for Live (the logs). A stop
 * that silenced the app's speech never gets here (SpeechStop); a later stop pauses the work, and one more leaves.
 */
object CameraCommandPolicy {
    /**
     * What a camera screen does with [command]; null when it is not a camera command and the screen decides.
     * [backCameraOnly] is the screen's (Walk, item learning); [cameraReady] is whether its camera is open now.
     */
    fun decide(command: VoiceCommand, backCameraOnly: Boolean, cameraReady: Boolean, working: Boolean): CameraAction? = when (command) {
        is VoiceCommand.SwitchCamera -> when {
            backCameraOnly -> CameraAction.BackCameraOnly
            !cameraReady -> CameraAction.NotReady
            else -> CameraAction.Switch(command.to)
        }
        VoiceCommand.WhatIsThis -> CameraAction.DescribeCentre
        VoiceCommand.WhoIsThis -> CameraAction.NameFace
        VoiceCommand.Stop -> if (working) CameraAction.Pause else CameraAction.Leave
        VoiceCommand.Start -> CameraAction.Resume
        else -> null
    }
}
