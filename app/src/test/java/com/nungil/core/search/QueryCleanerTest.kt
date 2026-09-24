package com.nungil.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryCleanerTest {
    @Test fun stripsEnglishFillers() {
        val c = QueryCleaner.clean("Find my bag, please!")
        assertEquals(listOf("bag"), c.words)
        assertEquals(listOf("find", "my", "bag", "please"), c.raw)
    }

    @Test fun stripsWhereIsAndApostrophes() {
        assertEquals(listOf("keys"), QueryCleaner.clean("Where is the keys").words)
        assertEquals(listOf("phone"), QueryCleaner.clean("Where's my phone?").words)
        assertEquals(listOf("cup"), QueryCleaner.clean("search for a cup").words)
    }

    @Test fun stripsKoreanFillers() {
        assertEquals(listOf("가방을"), QueryCleaner.clean("내 가방을 찾아줘").words)
        assertEquals(listOf("휴대폰"), QueryCleaner.clean("휴대폰 어디 있어?").words)
        assertEquals(listOf("컵"), QueryCleaner.clean("컵 좀 찾아 주세요").words)
        assertEquals(listOf("의자"), QueryCleaner.clean("의자 어디에있어").words)
    }

    @Test fun rawKeepsNamesThatAreFillerWords() {
        val c = QueryCleaner.clean("find me")
        assertEquals(emptyList<String>(), c.words)
        assertEquals(listOf("find", "me"), c.raw)
    }

    @Test fun emptyInput() {
        val c = QueryCleaner.clean("  ?! ")
        assertEquals(emptyList<String>(), c.raw)
        assertEquals("", c.phrase)
    }

    @Test fun particles() {
        assertEquals(listOf("배낭"), QueryCleaner.withoutParticles("배낭을"))
        assertEquals(listOf("민준이", "민준"), QueryCleaner.withoutParticles("민준이를"))
        assertEquals(emptyList<String>(), QueryCleaner.withoutParticles("컵"))
        assertEquals(emptyList<String>(), QueryCleaner.withoutParticles("이"))
    }
}
