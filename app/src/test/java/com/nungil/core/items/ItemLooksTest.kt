package com.nungil.core.items

import com.nungil.contract.Lang
import com.nungil.core.scan.ColorName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class ItemLooksTest {
    private val size = 300

    private fun mask(on: (x: Int, y: Int) -> Boolean) = BooleanArray(size * size) { on(it % size, it / size) }
    private fun rect(w: Int, h: Int) = mask { x, y -> abs(x - 150) * 2 < w && abs(y - 150) * 2 < h }
    private fun ellipse(w: Int, h: Int) = mask { x, y ->
        val dx = (x - 150) / (w / 2f)
        val dy = (y - 150) / (h / 2f)
        dx * dx + dy * dy < 1f
    }

    private fun shapeOf(mask: BooleanArray) = ItemLooks.shape(ItemLooks.outline(mask, size, size)!!)

    @Test fun shapes() {
        assertEquals(ItemShape.ROUND, shapeOf(ellipse(120, 120)))
        assertEquals(ItemShape.SQUARE, shapeOf(rect(120, 120)))
        assertEquals(ItemShape.OBLONG, shapeOf(ellipse(180, 110)))
        assertEquals(ItemShape.RECTANGULAR, shapeOf(rect(180, 110)))
        assertEquals(ItemShape.LONG, shapeOf(rect(240, 60)))
        assertEquals(ItemShape.LONG, shapeOf(ellipse(60, 240)))
    }

    @Test fun aShapeWithNoNameIsNotNamed() {
        // A square standing on its corner fills half of the box around it: not round, not square.
        assertNull(shapeOf(mask { x, y -> abs(x - 150) + abs(y - 150) < 80 }))
    }

    @Test fun lengthAndWidthAreMeasuredAlongTheItem() {
        // A 200 x 40 bar lying at 45 degrees.
        val bar = mask { x, y ->
            val u = (x - 150 + y - 150) / 1.41421f
            val v = (y - 150 - (x - 150)) / 1.41421f
            abs(u) < 100 && abs(v) < 20
        }
        val outline = ItemLooks.outline(bar, size, size)!!
        assertEquals(200f, outline.lengthPx, 4f)
        assertEquals(40f, outline.widthPx, 4f)
        assertEquals(ItemShape.LONG, ItemLooks.shape(outline))
    }

    @Test fun nothingToMeasure() = assertNull(ItemLooks.outline(BooleanArray(size * size), size, size))

    @Test fun pixelsBecomeCentimetres() {
        // 60 degrees across 480 px from half a metre: the frame is 57.7 cm wide.
        assertEquals(28.87f, ItemLooks.cm(240f, 0.5f, 60f, 480), 0.01f)
        assertEquals(14.43f, ItemLooks.cm(240f, 0.25f, 60f, 480), 0.01f)
    }

    @Test fun sizesAndDistancesAreRoundNumbers() {
        assertEquals(7, ItemLooks.roundCm(7.4f))
        assertEquals(10, ItemLooks.roundCm(12.4f))
        assertEquals(30, ItemLooks.roundCm(28.9f))
        assertEquals(60, ItemLooks.roundCm(63f))
        assertEquals(1, ItemLooks.roundCm(0.2f))
        assertEquals(30, ItemLooks.distanceCm(0.34f))
        assertEquals(40, ItemLooks.distanceCm(0.36f))
        assertEquals(100, ItemLooks.distanceCm(1.2f))
        assertEquals(150, ItemLooks.distanceCm(1.3f))
        // The lens cannot tell distances this small or this large.
        assertNull(ItemLooks.distanceCm(0.05f))
        assertNull(ItemLooks.distanceCm(3f))
        assertNull(ItemLooks.distanceCm(null))
    }

    @Test fun theLookOfAThing() {
        val bar = ItemLooks.outline(rect(240, 60), size, size)
        assertEquals(
            ItemLook(ColorName.WHITE, ItemShape.LONG, 30, 7, 50),
            ItemLooks.look(bar, ColorName.WHITE, 0.5f, 60f, 480),
        )
        // Round and square things have one size.
        val ball = ItemLooks.outline(ellipse(120, 120), size, size)
        assertEquals(ItemLook(null, ItemShape.ROUND, 15, null, 50), ItemLooks.look(ball, null, 0.5f, 60f, 480))
        // No distance, no size.
        assertEquals(ItemLook(ColorName.RED, ItemShape.ROUND, null, null, null), ItemLooks.look(ball, ColorName.RED, null, 60f, 480))
        assertEquals(ItemLook(null, null, null, null, null), ItemLooks.look(null, null, 5f, 60f, 480))
    }

    @Test fun severalLooksAgreeOnTheCommonOne() {
        // The phone wandered over the table for a frame: one orange, oblong look among black round ones.
        val looks = listOf(
            ItemLook(ColorName.BLACK, ItemShape.ROUND, 15, null, 40),
            ItemLook(ColorName.ORANGE, ItemShape.OBLONG, 45, 30, 40),
            ItemLook(ColorName.BLACK, ItemShape.ROUND, 20, null, 30),
            ItemLook(null, null, null, null, null),
            ItemLook(ColorName.BLACK, ItemShape.OBLONG, 15, 10, 40),
        )
        assertEquals(ItemLook(ColorName.BLACK, ItemShape.ROUND, 15, 10, 40), ItemLooks.agree(looks))
        assertEquals(ItemLook(null, null, null, null, null), ItemLooks.agree(emptyList()))
    }

    @Test fun englishWords() {
        assertEquals(
            "It is black and round, about 20 centimetres across, about 40 centimetres away.",
            ItemPhrases.look(ItemLook(ColorName.BLACK, ItemShape.ROUND, 20, null, 40), Lang.EN),
        )
        assertEquals(
            "It is white, long and narrow, about 20 by 5 centimetres, about 30 centimetres away.",
            ItemPhrases.look(ItemLook(ColorName.WHITE, ItemShape.LONG, 20, 5, 30), Lang.EN),
        )
        assertEquals("It is blue.", ItemPhrases.look(ItemLook(ColorName.BLUE, null, null, null, null), Lang.EN))
        assertEquals(
            "It is oblong, about 1.5 metres away.",
            ItemPhrases.look(ItemLook(null, ItemShape.OBLONG, null, null, 150), Lang.EN),
        )
        assertEquals(
            "It is about 10 centimetres across, about 1 metre away.",
            ItemPhrases.look(ItemLook(null, null, 10, null, 100), Lang.EN),
        )
        assertNull(ItemPhrases.look(ItemLook(null, null, null, null, null), Lang.EN))
    }

    @Test fun theQuestion() {
        val look = ItemLook(ColorName.BLACK, ItemShape.ROUND, 20, null, 40)
        assertEquals(
            "I see something. It is black and round, about 20 centimetres across, about 40 centimetres away. Is this it? Say yes or no.",
            ItemPhrases.ask(look, Lang.EN),
        )
        assertEquals(
            "물건이 보여요. 검은색이고 둥근 모양이에요. 크기는 약 20센티미터, 거리는 약 40센티미터예요. 이것인가요? 네 또는 아니요라고 말해 주세요.",
            ItemPhrases.ask(look, Lang.KO),
        )
        assertNull(ItemPhrases.ask(ItemLook(null, null, null, null, null), Lang.EN))
    }

    @Test fun koreanWords() {
        assertEquals(
            "검은색이고 둥근 모양이에요. 크기는 약 20센티미터, 거리는 약 40센티미터예요.",
            ItemPhrases.look(ItemLook(ColorName.BLACK, ItemShape.ROUND, 20, null, 40), Lang.KO),
        )
        assertEquals(
            "흰색이고 길쭉한 모양이에요. 길이 약 20센티미터, 폭 약 5센티미터, 거리는 약 30센티미터예요.",
            ItemPhrases.look(ItemLook(ColorName.WHITE, ItemShape.LONG, 20, 5, 30), Lang.KO),
        )
        assertEquals("파란색이에요.", ItemPhrases.look(ItemLook(ColorName.BLUE, null, null, null, null), Lang.KO))
        assertEquals(
            "약간 길쭉한 모양이에요. 거리는 약 1.5미터예요.",
            ItemPhrases.look(ItemLook(null, ItemShape.OBLONG, null, null, 150), Lang.KO),
        )
        assertEquals(
            "크기는 약 10센티미터, 거리는 약 1미터예요.",
            ItemPhrases.look(ItemLook(null, null, 10, null, 100), Lang.KO),
        )
        assertNull(ItemPhrases.look(ItemLook(null, null, null, null, null), Lang.KO))
    }
}
