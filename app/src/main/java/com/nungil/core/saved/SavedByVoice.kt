package com.nungil.core.saved

import com.nungil.contract.Lang
import com.nungil.core.scan.YoloOutput
import com.nungil.core.search.SavedName
import com.nungil.core.search.SearchResolver
import com.nungil.core.search.TargetType

/** "Delete <name>" and "Open <name>" on the Saved list: the saved people and items the words name. */
object SavedByVoice {
    /**
     * The saved thing named by the first of [guesses] (the phrase as heard) or, when that names nothing, by one
     * of the others ("test on" for "test one", the logs), with every other saved thing of the same kind and
     * name. A detector label ("bottle") is not a saved name. Empty when nothing saved is named.
     */
    fun named(guesses: List<String>, saved: List<SavedName>, lang: Lang): List<SavedName> {
        val heard = guesses.firstOrNull() ?: return emptyList()
        val target = SearchResolver.resolve(heard, guesses.drop(1), saved, lang)
            ?.takeIf { it.type != TargetType.LABEL }
        val found = target?.let { t -> saved.firstOrNull { it.id == t.id && it.type == t.type } }
            ?: guesses.firstNotNullOfOrNull { partOfOneName(it, saved) }
            ?: return emptyList()
        return saved.filter { it.type == found.type && it.name.equals(found.name, ignoreCase = true) }
    }

    /** Words of the command, and words of filler, that do not name anything ("delete", "my"). */
    private val NOT_NAME = setOf("delete", "remove", "erase", "open", "show", "the", "my", "a", "an", "삭제", "지워", "열어")

    /**
     * The one saved name holding every word said after the command, when the name was said only in part: "Delete
     * charger test one" for "My charger test one" was "not found" (the logs, 2026-10-13). Never for a detector's
     * label alone ("bottle"), nor when the words are in two different names.
     */
    private fun partOfOneName(heard: String, saved: List<SavedName>): SavedName? {
        val words = heard.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in NOT_NAME }
        if (words.isEmpty() || (words.size == 1 && words[0] in YoloOutput.COCO)) return null
        val holding = saved.filter { s ->
            val name = s.name.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            words.all { it in name }
        }
        return holding.takeIf { h -> h.isNotEmpty() && h.all { it.name.equals(h[0].name, ignoreCase = true) } }?.first()
    }
}
