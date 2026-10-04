package com.nungil.core.items

import com.nungil.core.walk.RoutePhrases

/** What the user answers when the name that was heard, or a name that is already saved, is read back. */
enum class ItemNameAnswer { YES, NO, REPLACE, OTHER }

/**
 * The name of a new item, said aloud. What was heard is read back and the item is learned only after a yes: a
 * name misheard here cannot be found by the name the user says later ("My green chair" was saved as "My green
 * tear", the logs). The recognizer's other guesses are offered before the user has to say it again, and a name
 * that is already saved is replaced only when the user says so.
 */
object ItemNames {
    /** The most names offered before the user is asked to say it again. */
    const val MAX_CANDIDATES = 3

    private val REPLACE = setOf(
        "replace", "replace it", "overwrite", "overwrite it", "learn it again", "learn again",
        "바꿔", "바꿔 줘", "바꿔줘", "바꾸기", "교체", "교체해 줘", "덮어쓰기", "다시 배워",
    )

    /**
     * The names to offer, [heard] first, then the recognizer's other [guesses] for the same phrase, without
     * those that differ from an earlier one only in case, spaces or signs. Guesses that do not hold [heard] are
     * for some other phrase (the name was typed, or given with the command) and are left out.
     */
    fun candidates(heard: String, guesses: List<String>): List<String> {
        val first = heard.trim()
        if (first.isEmpty()) return emptyList()
        val others = if (guesses.any { key(it) == key(first) }) guesses.map { it.trim() } else emptyList()
        return (listOf(first) + others).filter { key(it).isNotEmpty() }.distinctBy { key(it) }.take(MAX_CANDIDATES)
    }

    /** Whether [a] and [b] are one name: the case and the spaces around it do not count. */
    fun same(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

    /** Whether an item is already saved under [name]. */
    fun taken(name: String, saved: Collection<String>): Boolean = name.isNotBlank() && saved.any { same(it, name) }

    /** A whole answer only: "replace it" is REPLACE, "my replacement key" is a name. */
    fun answer(text: String): ItemNameAnswer = when {
        text.lowercase().trim().trim('.', '!', '?', ',').trim() in REPLACE -> ItemNameAnswer.REPLACE
        RoutePhrases.isYes(text) -> ItemNameAnswer.YES
        RoutePhrases.isNo(text) -> ItemNameAnswer.NO
        else -> ItemNameAnswer.OTHER
    }

    /** Letters and digits only, lower case: "My Black Box", "my blackbox" and "my black box/" are one. */
    private fun key(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }
}
