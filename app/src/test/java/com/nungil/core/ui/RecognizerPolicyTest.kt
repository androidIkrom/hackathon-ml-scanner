package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizerPolicyTest {
    private val policy = RecognizerPolicy()

    @Test fun delaysFromTheGuide() {
        assertEquals(1_500L, RecognizerPolicy.DELAY_AFTER_ENABLE_MS)
        assertEquals(2_500L, policy.afterResult())
        assertEquals(700L, policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).delayMs)
    }

    @Test fun rebuildsAfterThreeErrorsInARow() {
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_SPEECH_TIMEOUT, Lang.EN).rebuild)
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).rebuild)
        assertTrue(policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN).rebuild)
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).rebuild)
    }

    @Test fun aResultResetsTheErrorCount() {
        policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN)
        policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN)
        policy.afterResult()
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).rebuild)
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

    @Test fun waitsForTheAppToFinishSpeakingButNotForever() {
        assertTrue(policy.shouldWait(speaking = true, waitedMs = 0))
        assertFalse(policy.shouldWait(speaking = false, waitedMs = 0))
        assertFalse(policy.shouldWait(speaking = true, waitedMs = RecognizerPolicy.MAX_WAIT_FOR_SPEECH_MS))
    }

    @Test fun permissionOutcomes() {
        assertEquals(PermissionOutcome.GRANTED, PermissionOutcome.of(granted = true, showRationale = false))
        assertEquals(PermissionOutcome.DENIED, PermissionOutcome.of(granted = false, showRationale = true))
        assertEquals(PermissionOutcome.BLOCKED, PermissionOutcome.of(granted = false, showRationale = false))
    }
}
