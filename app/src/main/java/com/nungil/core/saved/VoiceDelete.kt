package com.nungil.core.saved

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa
import com.nungil.core.search.SavedName

/**
 * Deleting by voice on the Saved list: "Delete <name>" asks, and "delete" (with or without the name) within
 * [WINDOW_MS] deletes. Anything else said in between cancels ([cancel]). Not thread-safe: main thread.
 */
class VoiceDelete(private val windowMs: Long = WINDOW_MS) {
    sealed interface Result {
        /** Nothing is named, and nothing waits to be confirmed. */
        data object NotFound : Result
        data class Ask(val targets: List<SavedName>) : Result
        data class Delete(val targets: List<SavedName>) : Result
    }

    private var asked: List<SavedName> = emptyList()
    private var askedAt = 0L

    /** "delete" was heard, naming [targets] (SavedByVoice.named; empty when no name was said). */
    fun heard(targets: List<SavedName>, nowMs: Long): Result {
        val waiting = asked.isNotEmpty() && nowMs - askedAt <= windowMs
        if (waiting && (targets.isEmpty() || targets.map { it.id to it.type }.toSet() == asked.map { it.id to it.type }.toSet())) {
            return Result.Delete(asked).also { asked = emptyList() }
        }
        if (targets.isEmpty()) {
            asked = emptyList()
            return Result.NotFound
        }
        asked = targets
        askedAt = nowMs
        return Result.Ask(targets)
    }

    fun cancel() {
        asked = emptyList()
    }

    companion object {
        /**
         * The second "delete" may come this long after the first. It was 5 s: the question takes 3 s to say, and in a
         * room the recognizer gives the word seconds after it was said, so "delete" 6.6 s on was "not found" (the logs,
         * 2026-10-13).
         */
        const val WINDOW_MS = 15_000L
    }
}

/** What deleting by voice says, in both languages. */
object SavedVoicePhrases {
    fun ask(targets: List<SavedName>, lang: Lang): String {
        val name = targets.first().name
        return when {
            targets.size == 1 && lang == Lang.KO -> "${Josa.eulReul(name)} 삭제할까요? 한 번 더 삭제라고 말해 주세요."
            targets.size == 1 -> "Delete $name? Say delete again."
            lang == Lang.KO -> "$name 이름이 ${targets.size}개 있어요. 모두 지우려면 한 번 더 삭제라고 말해 주세요."
            else -> "There are ${targets.size} called $name. Say delete again to delete them all."
        }
    }

    fun deleted(targets: List<SavedName>, lang: Lang): String {
        val name = targets.first().name
        return if (lang == Lang.KO) "${Josa.eulReul(name)} 삭제했어요." else "$name deleted."
    }

    fun notFound(lang: Lang): String =
        if (lang == Lang.KO) "저장된 목록에서 그 이름을 찾지 못했어요." else "I could not find that name in Saved."
}
