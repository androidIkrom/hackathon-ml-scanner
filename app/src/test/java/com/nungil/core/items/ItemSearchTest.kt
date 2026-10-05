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

    // ---- All saved items -------------------------------------------------------------------------------------

    private val bottle = 3L
    private val towel = 5L
    private val glass = 6L
    private val all = mapOf(bottle to "My bottle", towel to "My towel", glass to "My glass", other to "Pen")
    private val left = Box(0.1f, 0.2f, 0.3f, 0.6f)
    private val right = Box(0.55f, 0.3f, 0.9f, 0.7f)

    private fun centreIn(box: Box, area: Box) = box.centerX in area.left..area.right && box.centerY in area.top..area.bottom

    @Test fun aDetectorBoxNamesTheItemOnIt() {
        val scene = Scene { if (centreIn(it, left)) ItemMatcher.Match(bottle, 0.6f) else ItemMatcher.Match(other, 0.2f) }
        val hits = search(finder(all), scene, only = null, detections = listOf(Detection("bottle", 0.8f, left))).hits
        assertEquals(1, hits.size)
        assertEquals(bottle, hits[0].id)
        assertEquals(0, hits[0].detectionIndex)
        assertEquals(left, hits[0].box)
    }

    @Test fun aBoxedItemDoesNotStopTheSearchForAnother() {
        val scene = Scene {
            when {
                centreIn(it, left) -> ItemMatcher.Match(bottle, 0.6f)
                centreIn(it, right) -> ItemMatcher.Match(towel, 0.6f)
                else -> ItemMatcher.Match(other, 0.2f)
            }
        }
        val hits = search(finder(all), scene, only = null, detections = listOf(Detection("bottle", 0.8f, left))).hits
        assertEquals(setOf(bottle, towel), hits.map { it.id }.toSet())
        assertNull(hits.single { it.id == towel }.detectionIndex)
    }

    @Test fun onlyOneWholeFrameTargetPerCall() {
        val scene = Scene {
            when {
                centreIn(it, left) -> ItemMatcher.Match(towel, 0.6f)
                centreIn(it, right) -> ItemMatcher.Match(glass, 0.65f)
                else -> ItemMatcher.Match(other, 0.2f)
            }
        }
        val hits = search(finder(all), scene, only = null).hits
        assertEquals(listOf(glass), hits.map { it.id })
    }

    @Test fun nothingInAFrameOfOtherThings() {
        val hits = search(finder(all), Scene { ItemMatcher.Match(bottle, 0.29f) }, only = null,
            detections = listOf(Detection("cup", 0.7f, left))).hits
        assertTrue(hits.isEmpty())
    }

    @Test fun boxesAndNearNeverSearchTheWholeFrame() {
        val scene = cupAt(0.9f)
        assertTrue(search(finder(reach = Reach.BOXES_AND_NEAR), scene, only = null).hits.isEmpty())
        assertEquals(0, scene.calls)
    }

    @Test fun aPlaceWithADetectorBoxInItsMiddleTakesThatBox() {
        // The detector's boxes are not the cup by their own squares (a 480 px square, a 74 px one); the grid finds it.
        val scene = Scene {
            when {
                it.width * W < 150f || abs(it.width * W - 480f) < 1f -> ItemMatcher.Match(other, 0.2f)
                inPlace(it) -> ItemMatcher.Match(cup, 0.6f)
                else -> ItemMatcher.Match(other, 0.2f)
            }
        }
        val small = Detection("vase", 0.6f, Box(0.45f, 0.45f, 0.55f, 0.55f))
        val big = Detection("tv", 0.6f, Box(0.05f, 0.05f, 0.95f, 0.95f))
        assertEquals(0, search(finder(), scene, only = null, detections = listOf(small)).hits.single().detectionIndex)
        assertNull(search(finder(), scene, only = null, detections = listOf(big)).hits.single().detectionIndex)
    }

    @Test fun aPersonBoxIsNeverAnItem() {
        val person = Detection("person", 0.9f, left)
        val square = ItemWindows.square(left, W, H)
        val scene = Scene { if (it == square) ItemMatcher.Match(bottle, 0.9f) else ItemMatcher.Match(other, 0.2f) }
        assertTrue(search(finder(all), scene, only = null, detections = listOf(person)).hits.isEmpty())
    }

    // ---- Walk: boxes and near only -----------------------------------------------------------------------------

    private val leftSquare = ItemWindows.square(left, W, H)
    private fun nearLeft(box: Box) = centreIn(box, leftSquare)

    @Test fun boxesAndNearKeepsABoxedItemNearItsSquare() {
        val s = finder(all, Reach.BOXES_AND_NEAR)
        val first = Scene { if (it == leftSquare) ItemMatcher.Match(bottle, 0.6f) else ItemMatcher.Match(other, 0.2f) }
        assertEquals(1, search(s, first, only = null, detections = listOf(Detection("bottle", 0.8f, left))).hits.size)
        // The detector lost the box: the bottle is kept near its square at 0.45, without the whole frame.
        val next = Scene { if (nearLeft(it)) ItemMatcher.Match(bottle, 0.47f) else ItemMatcher.Match(other, 0.2f) }
        assertEquals(listOf(bottle), search(s, next, only = null).hits.map { it.id })
        assertTrue("near squares only: ${next.calls}", next.calls <= 9)
    }

    @Test fun boxesAndNearNeverCutsAThing() {
        // Walk's worker thread also gives the hazard alerts: no segmenter there.
        val s = finder(all, Reach.BOXES_AND_NEAR)
        val first = Scene { if (it == leftSquare) ItemMatcher.Match(bottle, 0.6f) else ItemMatcher.Match(other, 0.2f) }
        search(s, first, only = null, detections = listOf(Detection("bottle", 0.8f, left)))
        var cuts = 0
        val thing = CutThing(left, leftSquare, ItemMatcher.Match(bottle, 0.9f))
        val next = Scene { if (nearLeft(it)) ItemMatcher.Match(bottle, 0.4f) else ItemMatcher.Match(other, 0.2f) }
        val hits = search(s, next, only = null, cut = { cuts++; thing }).hits
        assertTrue(hits.isEmpty())
        assertEquals(0, cuts)
    }

    @Test fun twoRememberedItemsAreBothLookedForNearby() {
        val s = finder(all)
        val both = Scene {
            when {
                centreIn(it, left) -> ItemMatcher.Match(bottle, 0.6f)
                centreIn(it, right) -> ItemMatcher.Match(towel, 0.6f)
                else -> ItemMatcher.Match(other, 0.2f)
            }
        }
        assertEquals(2, search(s, both, only = null, detections = listOf(Detection("bottle", 0.8f, left))).hits.size)
        val kept = Scene {
            when {
                centreIn(it, left) -> ItemMatcher.Match(bottle, 0.5f)
                centreIn(it, right) -> ItemMatcher.Match(towel, 0.5f)
                else -> ItemMatcher.Match(other, 0.2f)
            }
        }
        assertEquals(setOf(bottle, towel), search(s, kept, only = null).hits.map { it.id }.toSet())
    }

    private companion object {
        const val W = 640
        const val H = 480
    }
}
