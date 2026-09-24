package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenGuideTest {
    @Test fun positionsOnAThreeByThreeGrid() {
        assertEquals("top left", ScreenGuide.position(10f, 10f, 300f, 600f, Lang.EN))
        assertEquals("middle", ScreenGuide.position(150f, 300f, 300f, 600f, Lang.EN))
        assertEquals("bottom right", ScreenGuide.position(290f, 590f, 300f, 600f, Lang.EN))
        assertEquals("아래", ScreenGuide.position(150f, 590f, 300f, 600f, Lang.KO))
        assertEquals("왼쪽 위", ScreenGuide.position(10f, 10f, 300f, 600f, Lang.KO))
    }

    @Test fun describesControls() {
        assertEquals("Look around, button", ScreenGuide.describeControl("Look around", ControlKind.BUTTON, null, true, Lang.EN))
        assertEquals("High contrast, switch, on", ScreenGuide.describeControl("High contrast", ControlKind.SWITCH, true, true, Lang.EN))
        assertEquals("고대비 화면, 스위치, 꺼짐", ScreenGuide.describeControl("고대비 화면", ControlKind.SWITCH, false, true, Lang.KO))
        assertEquals("GPU, button, selected", ScreenGuide.describeControl("GPU", ControlKind.BUTTON, true, true, Lang.EN))
        assertEquals("Start, button, unavailable", ScreenGuide.describeControl("Start", ControlKind.BUTTON, null, false, Lang.EN))
        assertEquals("시작, 버튼, 사용할 수 없음", ScreenGuide.describeControl("시작", ControlKind.BUTTON, null, false, Lang.KO))
    }

    @Test fun summaryReadsAtMostTwelveControls() {
        val items = (1..15).map { GuideItem("Item $it", ControlKind.BUTTON, null, true, 100f, it * 10f) }
        val text = ScreenGuide.summary(items, 300f, 600f, Lang.EN)
        assertTrue(text.startsWith("This screen has 15 controls."))
        assertTrue(text.contains("Item 12, button"))
        assertTrue(!text.contains("Item 13,"))
        assertTrue(text.endsWith("And 3 more."))
    }

    @Test fun koreanSummary() {
        val items = listOf(GuideItem("시작", ControlKind.BUTTON, null, true, 150f, 590f))
        assertEquals("이 화면에는 항목이 1개 있어요. 시작, 버튼, 아래.", ScreenGuide.summary(items, 300f, 600f, Lang.KO))
    }

    @Test fun emptyScreen() = assertEquals("Nothing to press on this screen.", ScreenGuide.summary(emptyList(), 300f, 600f, Lang.EN))
}
