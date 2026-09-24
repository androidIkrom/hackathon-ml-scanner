package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ScreenHelpTest {
    private fun hasHangul(s: String) = s.any { it.code in 0xAC00..0xD7A3 }

    /** Every destination in the (frozen) navigation graph has help in both languages. */
    @Test fun everyNavDestinationHasHelp() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res-i/navigation/nav_graph.xml"))
        val nodes = doc.getElementsByTagName("fragment")
        val ids = (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:id").removePrefix("@+id/") }
        assertEquals(17, ids.size)
        for (id in ids) {
            assertTrue(id, ScreenHelp.hasScreen(id))
            assertTrue(id, !hasHangul(ScreenHelp.forScreen(id, Lang.EN)))
            assertTrue(id, hasHangul(ScreenHelp.forScreen(id, Lang.KO)))
        }
    }

    @Test fun unknownScreenGetsGeneralHelp() =
        assertEquals(ShellPhrases.text(Phrase.HELP_GENERAL, Lang.EN), ScreenHelp.forScreen("nowhere", Lang.EN))

    @Test fun topicsFromSpokenWords() {
        assertEquals("search", ScreenHelp.topicOf("search"))
        assertEquals("walk", ScreenHelp.topicOf("walk mode"))
        assertEquals("live", ScreenHelp.topicOf("live scan"))
        assertEquals("scan", ScreenHelp.topicOf("full scan"))
        assertEquals("walk", ScreenHelp.topicOf("걷기 모드"))
        assertEquals("search", ScreenHelp.topicOf("검색"))
        assertEquals("voice", ScreenHelp.topicOf("음성 명령"))
        assertNull(ScreenHelp.topicOf("banana"))
    }

    @Test fun topicTextInBothLanguages() {
        assertTrue(hasHangul(ScreenHelp.forTopic("walk mode", Lang.KO)!!))
        assertTrue(ScreenHelp.forTopic("language", Lang.EN)!!.contains("Korean"))
        assertNull(ScreenHelp.forTopic("banana", Lang.EN))
    }

    @Test fun introductionNamesTheApp() {
        assertTrue(OnboardingText.intro(Lang.EN).startsWith("Hello, I am Nungil."))
        assertTrue(OnboardingText.intro(Lang.KO).startsWith("안녕하세요, 눈길이에요."))
    }
}
