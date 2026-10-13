package com.nungil.core.saved

import com.nungil.contract.Lang
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
            ?.takeIf { it.type != TargetType.LABEL } ?: return emptyList()
        val found = saved.firstOrNull { it.id == target.id && it.type == target.type } ?: return emptyList()
        return saved.filter { it.type == found.type && it.name.equals(found.name, ignoreCase = true) }
    }
}
