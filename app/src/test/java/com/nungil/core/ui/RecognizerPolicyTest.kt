package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizerPolicyTest {
    private val policy = RecognizerPolicy()

    @Test fun listensAgainAlmostAtOnce() {
        assertEquals(400L, RecognizerPolicy.DELAY_AFTER_ENABLE_MS)
        assertEquals(250L, policy.afterResult())
        assertEquals(100L, policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).delayMs)
        assertEquals(100L, policy.afterError(RecognizerPolicy.ERROR_SPEECH_TIMEOUT, Lang.EN).delayMs)
        assertEquals(700L, policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN).delayMs)
    }

    @Test fun aLaterGuessThatIsACommandBeatsAMisheardFirstGuess() {
        val known = setOf("live scan", "stop")
        assertEquals("live scan", RecognizerPolicy.choose(listOf("light skim", " live scan ", "stop")) { it in known })
        assertEquals("light skim", RecognizerPolicy.choose(listOf("light skim", "like skim")) { it in known })
        assertEquals("", RecognizerPolicy.choose(listOf(" ", "")) { true })
    }

    @Test fun silenceIsNotAnError() {
        repeat(10) {
            assertFalse(policy.afterError(RecognizerPolicy.ERROR_SPEECH_TIMEOUT, Lang.EN).rebuild)
            assertFalse(policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).rebuild)
        }
    }

    @Test fun rebuildsAfterThreeRealErrorsInARow() {
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN).rebuild)
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_RECOGNIZER_BUSY, Lang.EN).rebuild)
        assertTrue(policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN).rebuild)
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN).rebuild)
    }

    @Test fun aResultResetsTheErrorCount() {
        policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN)
        policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN)
        policy.afterResult()
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN).rebuild)
    }

    @Test fun missingPermissionStopsTheLoop() =
        assertEquals(ListenAction.STOP_NO_PERMISSION, policy.afterError(RecognizerPolicy.ERROR_INSUFFICIENT_PERMISSIONS, Lang.EN).action)

    @Test fun missingKoreanFallsBackToEnglishOnce() {
        val first = policy.afterError(RecognizerPolicy.ERROR_LANGUAGE_UNAVAILABLE, Lang.KO)
        assertEquals(ListenAction.FALL_BACK_TO_ENGLISH, first.action)
        assertTrue(first.rebuild)
        assertEquals(Lang.EN, policy.language(Lang.KO))
        assertEquals(ListenAction.RETRY, policy.afterError(RecognizerPolicy.ERROR_LANGUAGE_NOT_SUPPORTED, Lang.KO).action)
    }

    @Test fun englishNeverFallsBack() {
        assertEquals(ListenAction.RETRY, policy.afterError(RecognizerPolicy.ERROR_LANGUAGE_NOT_SUPPORTED, Lang.EN).action)
        assertEquals(Lang.KO, RecognizerPolicy().language(Lang.KO))
    }

    @Test fun safetyLimits() {
        assertEquals(10_000L, RecognizerPolicy.HOLD_SAFETY_MS)
        assertEquals(300L, RecognizerPolicy.MUTE_TAIL_MS)
        assertEquals(2_000L, RecognizerPolicy.MUTE_MAX_MS)
        assertEquals(10_000L, RecognizerPolicy.NOT_UNDERSTOOD_GAP_MS)
        assertEquals(6_000L, RecognizerPolicy.TALK_WINDOW_MS)
        assertTrue(RecognizerPolicy.TALK_WINDOW_MS < RecognizerPolicy.HOLD_SAFETY_MS)
    }

    @Test fun permissionOutcomes() {
        assertEquals(PermissionOutcome.GRANTED, PermissionOutcome.of(granted = true, showRationale = false))
        assertEquals(PermissionOutcome.DENIED, PermissionOutcome.of(granted = false, showRationale = true))
        assertEquals(PermissionOutcome.BLOCKED, PermissionOutcome.of(granted = false, showRationale = false))
    }
}
