package com.nungil.contract.app

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.nungil.contract.Buzz
import com.nungil.contract.Dest
import com.nungil.contract.DirectionStyle
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand

/** Speech output. Implemented by I. Safe to call from any thread. */
interface Speaker {
    /** Queue [text]. Keeps a 1.5 s gap; drops the oldest pending items when more than 3 wait. */
    fun say(text: String)

    /** Drop everything queued and speak [text] immediately. */
    fun sayNow(text: String)

    /** Flush the queue, speak [text], then call [onDone] on the main thread (after 15 s at most). */
    fun sayFinal(text: String, onDone: () -> Unit = {})

    /** Stop speaking and clear the queue. */
    fun stop()
}

/** Vibration. Implemented by I. Safe to call from any thread. */
interface Haptics {
    fun buzz(kind: Buzz)
}

/** Short tones. Implemented by I. Safe to call from any thread. */
interface Beeper {
    /** One short beep. */
    fun beep()

    /** Repeat a beep every [intervalMs]; calling again changes the interval. 0 or less stops. */
    fun pulse(intervalMs: Long)

    fun stop()
}

/** Navigation to any screen. Implemented by I. Main thread only. */
interface AppNavigator {
    fun open(dest: Dest)
    fun back()
}

/**
 * Implemented by any screen (Fragment) that reacts to voice commands.
 * Return true when the screen handled [command]; false lets MainActivity handle it (usually navigation).
 */
fun interface VoiceHandler {
    fun onVoiceCommand(command: VoiceCommand): Boolean
}

/** Everything a screen needs from the app shell. MainActivity (I) implements it. */
interface AppServices {
    val lang: Lang
    val speaker: Speaker
    val haptics: Haptics
    val beeper: Beeper
    val navigator: AppNavigator

    /** How directions are said (Settings: clock directions). Read it for each sentence: it can change any time. */
    val directionStyle: DirectionStyle

    /**
     * The next words the user says (text that does not parse as a command) go to [onText], once, on the
     * main thread. If the always-on listener is off, a one-shot recognizer is opened instead.
     * The claim is cancelled automatically when [owner] is destroyed.
     */
    fun askForWords(owner: LifecycleOwner, onText: (String) -> Unit)
}

fun Fragment.services(): AppServices = requireActivity() as AppServices
