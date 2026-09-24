package com.nungil.core.lang

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelNamesTest {
    @Test fun hasAllEightyCocoLabels() = assertEquals(80, LabelNames.labels.size)

    @Test fun koreanNamesAreUnique() =
        assertEquals(80, LabelNames.labels.map { LabelNames.name(it, Lang.KO) }.toSet().size)

    @Test fun englishIsTheLabelItself() = assertEquals("cell phone", LabelNames.name("cell phone", Lang.EN))

    @Test fun koreanName() = assertEquals("휴대폰", LabelNames.name("cell phone", Lang.KO))

    @Test fun unknownLabelIsReturnedUnchanged() = assertEquals("Ali", LabelNames.name("Ali", Lang.KO))

    @Test fun counters() {
        assertEquals("명", LabelNames.counterKo("person"))
        assertEquals("마리", LabelNames.counterKo("dog"))
        assertEquals("대", LabelNames.counterKo("laptop"))
        assertEquals("권", LabelNames.counterKo("book"))
        assertEquals("개", LabelNames.counterKo("chair"))
        assertEquals("개", LabelNames.counterKo("not a label"))
    }

    @Test fun reverseLookup() {
        assertEquals("chair", LabelNames.labelForKorean("의자"))
        assertEquals("suitcase", LabelNames.labelForKorean(" 여행 가방 "))
        assertNull(LabelNames.labelForKorean("우주선"))
    }

    @Test fun knowsLabels() = assertTrue(LabelNames.isKnown("dining table"))
}
