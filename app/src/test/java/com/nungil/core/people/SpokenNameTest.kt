package com.nungil.core.people

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenNameTest {
    @Test fun aBareNameIsTheName() = assertEquals("Myself", SpokenName.of("Myself"))

    @Test fun wordsBeforeTheNameAreDropped() {
        assertEquals("Ali", SpokenName.of("his name is Ali"))
        assertEquals("Ali", SpokenName.of("name Ali"))
        assertEquals("Ali", SpokenName.of("call her Ali."))
        assertEquals("Ali", SpokenName.of("new name Ali"))
        assertEquals("알리", SpokenName.of("이름은 알리"))
    }

    @Test fun theLeadingWordsAloneAreNoName() = assertEquals("", SpokenName.of("name is"))

    @Test fun aNameThatStartsLikeALeadIsKept() = assertEquals("Namik", SpokenName.of("Namik"))
}
