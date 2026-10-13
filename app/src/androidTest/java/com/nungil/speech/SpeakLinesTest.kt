package com.nungil.speech

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nungil.contract.Lang
import com.nungil.debug.ReplayHostActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * The real speech engine says every line of a text read aloud (sayLines), in order: the reader queued 31 lines and
 * 3 were said, the queue keeping only the last 3 (2026-10-12). Speaks six short lines out loud on the phone.
 */
@RunWith(AndroidJUnit4::class)
class SpeakLinesTest {
    /** Keeps the app in front: in the background the phone freezes it (ReplayHostActivity). */
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)

    @Test fun everyLineIsSaidInOrder() {
        val lines = (1..6).map { "Line $it." }
        val said = Collections.synchronizedList(mutableListOf<String>())
        lateinit var speaker: TtsSpeaker
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            speaker = TtsSpeaker(ApplicationProvider.getApplicationContext(), { Lang.EN }, { said += it }, {})
            speaker.sayLines(lines)
        }
        val started = System.currentTimeMillis()
        while (said.size < lines.size && System.currentTimeMillis() - started < 40_000) Thread.sleep(200)
        val tookMs = System.currentTimeMillis() - started
        InstrumentationRegistry.getInstrumentation().runOnMainSync { speaker.shutdown() }
        assertEquals(lines, said.toList())
        // Six short lines with short pauses, not 1.5 s between each (engine start included).
        assertTrue("took $tookMs ms", tookMs < 15_000)
    }

    /** Someone talks in the middle of a text: the voice stops, then the text goes on from the line cut (2026-10-13). */
    @Test fun aVoiceInTheRoomPausesTheTextAndItGoesOn() {
        val lines = (1..6).map { "Line $it." }
        val said = Collections.synchronizedList(mutableListOf<String>())
        lateinit var speaker: TtsSpeaker
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            speaker = TtsSpeaker(ApplicationProvider.getApplicationContext(), { Lang.EN }, { said += it }, {})
            speaker.sayLines(lines)
        }
        val started = System.currentTimeMillis()
        while ("Line 3." !in said && System.currentTimeMillis() - started < 20_000) Thread.sleep(50)
        instrumentation.runOnMainSync { speaker.holdForUser() }
        Thread.sleep(2_000)
        instrumentation.runOnMainSync { speaker.resumeAfterUser() }
        while (said.lastOrNull() != "Line 6." && System.currentTimeMillis() - started < 40_000) Thread.sleep(200)
        Thread.sleep(500)
        instrumentation.runOnMainSync { speaker.shutdown() }
        // "Line 3." is said again after the hold; nothing is lost.
        assertEquals(lines, said.toList().distinct())
        assertEquals(listOf("Line 1.", "Line 2.", "Line 3.", "Line 3.", "Line 4.", "Line 5.", "Line 6."), said.toList())
    }
}
