package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HelpAnswerTest {
    private fun of(vararg texts: String) = texts.map { HelpAnswer.of(it) }

    @Test fun yesInManyWords() {
        assertEquals(
            List(14) { true },
            of(
                "yes", "Yes please", "ok", "sure", "go ahead", "tell me", "please do", "of course", "alright",
                "all right", "I do", "explain", "read it", "yes, tell me",
            ),
        )
        assertEquals(
            List(10) { true },
            of("네", "응", "그래", "좋아요", "알려 줘", "알려 주세요", "부탁해", "설명해 줘", "네 알려 주세요", "응 알려줘"),
        )
    }

    @Test fun noInManyWords() {
        assertEquals(
            List(10) { false },
            of("no", "No thanks", "no thank you", "not now", "later", "never mind", "nevermind", "don't", "nope", "I'm fine"),
        )
        assertEquals(List(7) { false }, of("아니", "아니요", "괜찮아", "괜찮아요", "됐어", "나중에", "필요 없어"))
    }

    @Test fun anythingElseIsNotAnAnswer() {
        assertEquals(List(4) { null }, of("Seoul Station", "what time is it", "", "go to the park"))
    }
}
