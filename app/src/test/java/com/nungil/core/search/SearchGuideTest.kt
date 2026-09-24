package com.nungil.core.search

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchGuideTest {
    @Test fun zoneBoundaries() {
        assertEquals(Zone.FAR_LEFT, SearchGuide.zone(0.19f))
        assertEquals(Zone.LEFT, SearchGuide.zone(0.2f))
        assertEquals(Zone.LEFT, SearchGuide.zone(0.39f))
        assertEquals(Zone.AHEAD, SearchGuide.zone(0.4f))
        assertEquals(Zone.AHEAD, SearchGuide.zone(0.6f))
        assertEquals(Zone.RIGHT, SearchGuide.zone(0.61f))
        assertEquals(Zone.RIGHT, SearchGuide.zone(0.8f))
        assertEquals(Zone.FAR_RIGHT, SearchGuide.zone(0.81f))
    }

    @Test fun beepIntervalIsLinear() {
        assertEquals(1_000L, SearchGuide.beepIntervalMs(0f))
        assertEquals(1_000L, SearchGuide.beepIntervalMs(1f))
        assertEquals(150L, SearchGuide.beepIntervalMs(0.5f))
        assertEquals(575L, SearchGuide.beepIntervalMs(0.25f))
        assertEquals(1_000L, SearchGuide.beepIntervalMs(-0.3f))
    }

    @Test fun centred() {
        assertTrue(SearchGuide.isCentered(0.5f))
        assertFalse(SearchGuide.isCentered(0.3f))
    }

    @Test fun frontCameraSwapsSides() {
        assertEquals(0.8f, SearchGuide.userX(0.2f, Facing.FRONT), 1e-6f)
        assertEquals(0.2f, SearchGuide.userX(0.2f, Facing.BACK), 1e-6f)
    }

    @Test fun zoneWords() {
        assertEquals("far left", SearchGuide.zoneWord(Zone.FAR_LEFT, Lang.EN))
        assertEquals("ahead", SearchGuide.zoneWord(Zone.AHEAD, Lang.EN))
        assertEquals("왼쪽 끝", SearchGuide.zoneWord(Zone.FAR_LEFT, Lang.KO))
        assertEquals("정면", SearchGuide.zoneWord(Zone.AHEAD, Lang.KO))
        assertEquals("오른쪽 끝", SearchGuide.zoneWord(Zone.FAR_RIGHT, Lang.KO))
    }
}
