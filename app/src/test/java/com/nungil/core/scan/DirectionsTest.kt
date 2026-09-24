package com.nungil.core.scan

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionsTest {
    @Test fun englishFourSectors() {
        assertEquals("in front", Directions.of(10f, Lang.EN))
        assertEquals("on your right", Directions.of(90f, Lang.EN))
        assertEquals("behind you", Directions.of(200f, Lang.EN))
        assertEquals("on your left", Directions.of(-80f, Lang.EN))
    }

    @Test fun koreanFourSectors() {
        assertEquals("앞", Directions.of(-30f, Lang.KO))
        assertEquals("오른쪽", Directions.of(60f, Lang.KO))
        assertEquals("뒤", Directions.of(180f, Lang.KO))
        assertEquals("왼쪽", Directions.of(270f, Lang.KO))
    }

    @Test fun eightSectorNames() {
        assertEquals("front right", Directions.sector8Name(1, Lang.EN))
        assertEquals("왼쪽 뒤", Directions.sector8Name(5, Lang.KO))
        assertEquals("front", Directions.sector8Name(8, Lang.EN))
    }
}
