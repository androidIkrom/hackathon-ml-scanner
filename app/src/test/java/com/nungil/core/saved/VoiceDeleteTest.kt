package com.nungil.core.saved

import com.nungil.contract.Lang
import com.nungil.core.search.SavedName
import com.nungil.core.search.TargetType
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceDeleteTest {
    private val charger = listOf(SavedName(23, "My charger test one", TargetType.ITEM, "object"))
    private val ali = listOf(SavedName(3, "Ali", TargetType.PERSON, "person"))
    private val delete = VoiceDelete()

    @Test fun theSecondDeleteMayComeAFewSecondsLater() {
        // The logs, 2026-10-13: "Delete my charger test one", then "Delete" 6.6 s later was "not found": the question
        // takes 3 s to say, and in a room the recognizer gives the word seconds after it was said.
        assertEquals(15_000L, VoiceDelete.WINDOW_MS)
        delete.heard(charger, 0L)
        assertEquals(VoiceDelete.Result.Delete(charger), delete.heard(emptyList(), 6_600L))
    }

    @Test fun theFirstDeleteAsksTheSecondDeletes() {
        assertEquals(VoiceDelete.Result.Ask(charger), delete.heard(charger, 0L))
        assertEquals(VoiceDelete.Result.Delete(charger), delete.heard(emptyList(), 2_000L))
    }

    @Test fun theNameMaySayItAgain() {
        delete.heard(charger, 0L)
        assertEquals(VoiceDelete.Result.Delete(charger), delete.heard(charger, 2_000L))
    }

    @Test fun tooLateItAsksAgain() {
        delete.heard(charger, 0L)
        assertEquals(VoiceDelete.Result.Ask(charger), delete.heard(charger, VoiceDelete.WINDOW_MS + 1))
    }

    @Test fun tooLateWithoutANameIsNotFound() {
        delete.heard(charger, 0L)
        assertEquals(VoiceDelete.Result.NotFound, delete.heard(emptyList(), VoiceDelete.WINDOW_MS + 1))
    }

    @Test fun anotherNameAsksForThatOne() {
        delete.heard(charger, 0L)
        assertEquals(VoiceDelete.Result.Ask(ali), delete.heard(ali, 1_000L))
        assertEquals(VoiceDelete.Result.Delete(ali), delete.heard(emptyList(), 2_000L))
    }

    @Test fun anotherCommandInBetweenCancels() {
        delete.heard(charger, 0L)
        delete.cancel()
        assertEquals(VoiceDelete.Result.NotFound, delete.heard(emptyList(), 1_000L))
    }

    @Test fun nothingNamedNothingAsked() = assertEquals(VoiceDelete.Result.NotFound, delete.heard(emptyList(), 0L))

    @Test fun tunedNumbers() = assertEquals(15_000L, VoiceDelete.WINDOW_MS)

    @Test fun phrases() {
        assertEquals("Delete My charger test one? Say delete again.", SavedVoicePhrases.ask(charger, Lang.EN))
        assertEquals("My charger test one를 삭제할까요? 한 번 더 삭제라고 말해 주세요.", SavedVoicePhrases.ask(charger, Lang.KO))
        val two = charger + SavedName(24, "My charger test one", TargetType.ITEM, "object")
        assertEquals("There are 2 called My charger test one. Say delete again to delete them all.", SavedVoicePhrases.ask(two, Lang.EN))
        assertEquals("My charger test one 이름이 2개 있어요. 모두 지우려면 한 번 더 삭제라고 말해 주세요.", SavedVoicePhrases.ask(two, Lang.KO))
        assertEquals("My charger test one deleted.", SavedVoicePhrases.deleted(charger, Lang.EN))
        assertEquals("Ali를 삭제했어요.", SavedVoicePhrases.deleted(ali, Lang.KO))
        assertEquals("I could not find that name in Saved.", SavedVoicePhrases.notFound(Lang.EN))
        assertEquals("저장된 목록에서 그 이름을 찾지 못했어요.", SavedVoicePhrases.notFound(Lang.KO))
    }
}
