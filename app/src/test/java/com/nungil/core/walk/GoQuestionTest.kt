package com.nungil.core.walk

import org.junit.Assert.assertEquals
import org.junit.Test

class GoQuestionTest {
    private fun q(vararg texts: String) = texts.map { GoQuestion.of(it) }

    @Test fun english() {
        assertEquals(List(6) { GoQuestion.HOW_FAR }, q("How far?", "how long", "How much further", "how many minutes", "When will I arrive?", "how far is it"))
        assertEquals(List(3) { GoQuestion.NEXT }, q("What's next?", "next turn", "next instruction"))
        assertEquals(List(3) { GoQuestion.REPEAT }, q("Repeat", "say again", "What did you say?"))
        assertEquals(List(3) { GoQuestion.WHICH_WAY }, q("Which way?", "where do I go", "direction"))
        // "Where to go" opened the Search screen in the middle of a route (the logs).
        assertEquals(List(3) { GoQuestion.WHICH_WAY }, q("Where to go?", "which way to go", "where do I go now"))
        assertEquals(List(3) { GoQuestion.WHERE_AM_I }, q("Where am I?", "what street is this", "my location"))
        assertEquals(List(4) { GoQuestion.QUIET }, q("quiet updates", "fewer updates", "updates off", "stop updates"))
        assertEquals(List(2) { GoQuestion.UPDATES_ON }, q("updates on", "more updates"))
        assertEquals(GoQuestion.HOW_FAR, GoQuestion.of("please, how far"))
    }

    @Test fun korean() {
        assertEquals(List(4) { GoQuestion.HOW_FAR }, q("얼마나 남았어", "몇 분 남았어요", "언제 도착해", "거리"))
        assertEquals(List(2) { GoQuestion.NEXT }, q("다음은", "다음 안내"))
        assertEquals(List(3) { GoQuestion.REPEAT }, q("다시", "반복", "뭐라고"))
        assertEquals(List(2) { GoQuestion.WHICH_WAY }, q("어느 쪽", "어디로 가"))
        assertEquals(List(3) { GoQuestion.WHERE_AM_I }, q("여기 어디야", "지금 어디야", "현재 위치"))
        assertEquals(List(2) { GoQuestion.QUIET }, q("안내 조용히", "업데이트 꺼"))
        assertEquals(List(2) { GoQuestion.UPDATES_ON }, q("업데이트 켜", "안내 다시 켜"))
    }

    @Test fun onlyWholeQuestions() {
        assertEquals(
            List(6) { null },
            q("Next Door Cafe", "repeat after me", "next time", "how far is Seoul Station from Busan", "Seoul Station", "다시 서울역"),
        )
    }
}
