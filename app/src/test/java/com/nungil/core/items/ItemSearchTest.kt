package com.nungil.core.items

import com.nungil.contract.Box
import com.nungil.contract.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ItemSearchTest {
    private val cup = 1L
    private val other = 99L
    private val names = mapOf(cup to "Cup", other to "Pen")
    private val place = Box(0.3f, 0.3f, 0.7f, 0.7f)

    /** The frame as the embedder would see it: [rule] gives a square's best saved match. Counts the squares asked about. */
    private class Scene(var rule: (Box) -> ItemMatcher.Match?) {
        var calls = 0
        fun match(box: Box): ItemMatcher.Match? {
            calls++
            return rule(box)
        }
    }

    private fun inPlace(box: Box) = box.centerX in place.left..place.right && box.centerY in place.top..place.bottom

    /** Squares with their middle on the cup score [score] for [id]; the rest look like the pen at 0.2. */
    private fun cupAt(score: Float, id: Long = cup) = Scene { if (inPlace(it)) ItemMatcher.Match(id, score) else ItemMatcher.Match(other, 0.2f) }

    private fun search(
        s: ItemSearch,
        scene: Scene,
        only: Long? = cup,
        cut: (Box) -> CutThing? = { null },
        detections: List<Detection> = emptyList(),
    ) = s.search(detections, W, H, only, scene::match, cut)

    private fun finder(n: Map<Long, String> = names, reach: Reach = Reach.WHOLE_FRAME) = ItemSearch(n, emptySet(), reach)

    @Test fun foundAtTheFindThreshold() {
        val hits = search(finder(), cupAt(0.56f)).hits
        assertEquals(1, hits.size)
        assertEquals(cup, hits[0].id)
        assertEquals(ItemMatcher.Seen.BY_SQUARES, hits[0].seen)
        assertTrue(search(finder(), cupAt(0.54f)).hits.isEmpty())
    }

    @Test fun keptAtTheKeepThresholdNearTheLastSquare() {
        val s = finder()
        assertEquals(1, search(s, cupAt(0.6f)).hits.size)
        val scene = cupAt(0.47f)
        val result = search(s, scene)
        assertEquals(1, result.hits.size)
        assertTrue("only the squares near the last one: ${scene.calls}", scene.calls <= 9)
    }

    @Test fun aKeptItemNotThereAnyMoreIsSearchedForAgainAndForgotten() {
        val s = finder()
        search(s, cupAt(0.6f))
        val gone = cupAt(0.2f).also { it.rule = { ItemMatcher.Match(other, 0.2f) } }
        assertTrue(search(s, gone).hits.isEmpty())
        assertTrue("the whole frame was searched: ${gone.calls}", gone.calls > 9)
        // Forgotten: 0.5 no longer keeps it, 0.55 is needed again.
        assertTrue(search(s, cupAt(0.5f)).hits.isEmpty())
    }

    @Test fun theCloserLookMovesThePlace() {
        // Three quarters of a big grid square (252 px of 640) fits the cup better than the grid squares.
        val scene = Scene {
            when {
                !inPlace(it) -> ItemMatcher.Match(other, 0.2f)
                abs(it.width * W - 252f) < 2f -> ItemMatcher.Match(cup, 0.6f)
                else -> ItemMatcher.Match(cup, 0.5f)
            }
        }
        val hits = search(finder(), scene).hits
        assertEquals(1, hits.size)
        assertEquals(252f, hits[0].box.width * W, 2f)
    }

    @Test fun theThingAloneAddsAFindFromLookCloser() {
        val outline = Box(0.45f, 0.4f, 0.55f, 0.6f)
        val thing = CutThing(outline, Box(0.35f, 0.3f, 0.65f, 0.7f), ItemMatcher.Match(cup, 0.65f))
        val hits = search(finder(), cupAt(0.4f), cut = { thing }).hits
        assertEquals(1, hits.size)
        assertEquals(ItemMatcher.Seen.BY_ITEM_ALONE, hits[0].seen)
        assertEquals(outline, hits[0].box)
        assertTrue(search(finder(), cupAt(0.3f), cut = { thing }).hits.isEmpty())
    }

    @Test fun theThingAloneNeverRemovesAFind() {
        val thing = CutThing(Box(0.45f, 0.4f, 0.55f, 0.6f), place, ItemMatcher.Match(cup, 0.1f))
        val hits = search(finder(), cupAt(0.6f), cut = { thing }).hits
        assertEquals(1, hits.size)
        assertEquals(ItemMatcher.Seen.BY_SQUARES, hits[0].seen)
    }

    @Test fun withoutACutThingTheSquaresDecide() {
        val hits = search(finder(), cupAt(0.56f)).hits
        assertEquals(1, hits.size)
        assertTrue("the square is the box", hits[0].box.width * W > 200f)
        assertNull(hits[0].detectionIndex)
        assertTrue(search(finder(), cupAt(0.5f)).hits.isEmpty())
    }

    @Test fun aSquareThatLooksMoreLikeAnotherItemDoesNotCount() =
        assertTrue(search(finder(), cupAt(0.9f, id = other)).hits.isEmpty())

    @Test fun itemsSavedTwiceUnderOneNameAreOneTarget() {
        val hits = search(finder(mapOf(1L to "Cup", 2L to "cup", other to "Pen")), cupAt(0.6f, id = 2L), only = 1L).hits
        assertEquals(1, hits.size)
        assertEquals(1L, hits[0].id)
    }

    private companion object {
        const val W = 640
        const val H = 480
    }
}
