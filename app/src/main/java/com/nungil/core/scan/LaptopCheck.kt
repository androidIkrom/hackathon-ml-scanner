package com.nungil.core.scan

/**
 * What a "keyboard" box really is. The detector calls an open laptop a keyboard: a laptop on the desk was said as
 * "a black keyboard" and never as a laptop (Live sees, 2026-10-05). The 1000-class classifier looks at the box and
 * knows "laptop" and "notebook" apart from "computer keyboard".
 */
object LaptopCheck {
    const val LAPTOP = "laptop"
    const val KEYBOARD = "keyboard"

    /** Below this the classifier's laptop guess is noise. */
    const val MIN_SCORE = 0.15f

    private val LAPTOP_WORDS = setOf("laptop", "notebook")
    private val KEYBOARD_WORDS = setOf("computer keyboard", "typewriter keyboard")

    /** [guesses]: the classifier's top names and scores for the box. */
    fun label(guesses: List<Pair<String, Float>>): String {
        val laptop = guesses.filter { it.first.lowercase() in LAPTOP_WORDS }.maxOfOrNull { it.second } ?: 0f
        val keyboard = guesses.filter { it.first.lowercase() in KEYBOARD_WORDS }.maxOfOrNull { it.second } ?: 0f
        return if (laptop >= MIN_SCORE && laptop > keyboard) LAPTOP else KEYBOARD
    }
}
