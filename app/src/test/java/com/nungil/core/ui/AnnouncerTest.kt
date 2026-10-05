package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncerTest {
    private val hazard = 1
    private val floor = 0
    private val clearPriority = 10

    private val wall3 = Notice("wall:ahead:3", "Wall ahead, 3 steps.", hazard, ahead = true)
    private val wall1 = Notice("wall:ahead:1", "Wall ahead, 1 step.", hazard, ahead = true, urgent = true)
    private val right = Notice("wall:RIGHT", "Obstacle on your right, 2 steps.", hazard)
    private val clear = Notice("clear", "Nothing close ahead.", clearPriority, cutsIn = true)

    private fun walk() = Announcer(Announcer.WALK)
    private fun after(n: Notice, at: Long = 0) = at + Announcer.durationMs(n.text) + 1

    // ---- Walk's rules, as WalkAlerts and WalkPacing had them ----------------------------------------------

    @Test fun whatIsAheadIsSaidBeforeTheSides() {
        val a = walk()
        assertEquals(wall3, a.choose(0, listOf(right, wall3)))
        assertEquals(right, a.choose(after(wall3), listOf(right, wall3)))
    }

    @Test fun aSideIsNotSaidWhileSomethingIsBeingSaidAndIsNotLostEither() {
        // The logs: "Obstacle on your right" was heard 6 s late, behind three queued sentences.
        val a = walk()
        assertEquals(wall3, a.choose(0, listOf(wall3, right)))
        assertNull(a.choose(500, listOf(wall3, right)))
        assertEquals(right, a.choose(after(wall3), listOf(wall3, right)))
    }

    @Test fun theNewestAboutAheadCutsInAtOnce() {
        val a = walk()
        assertEquals(wall3, a.choose(0, listOf(wall3)))
        assertEquals(wall1, a.choose(100, listOf(wall1, right)))
        assertEquals(clear, a.choose(200, listOf(clear, right)))
        assertNull(a.choose(300, listOf(right)))
        assertEquals(right, a.choose(after(clear, 200), listOf(right)))
    }

    @Test fun koreanTakesLongerPerCharacter() =
        assertTrue(Announcer.durationMs("앞에 벽이 있어요, 두 걸음.") > Announcer.durationMs("Wall ahead, 2 st."))

    @Test fun mostUrgentNewNoticeWins() {
        val a = walk()
        val person = Notice("person:ahead", "Person ahead.", hazard)
        val stairs = Notice("stairs", "Stairs ahead.", floor)
        assertEquals(stairs, a.choose(0, listOf(person, stairs)))
        assertEquals(person, a.choose(after(stairs), listOf(person, stairs)))
        assertNull(a.choose(after(stairs) + after(person), listOf(person, stairs)))
    }

    @Test fun aSituationThatReturnsIsSaidAgainButNotWithinSixSeconds() {
        val a = walk()
        val drop = Notice("drop", "Drop ahead.", floor)
        assertEquals(drop, a.choose(0, listOf(drop)))
        assertNull(a.choose(1_000, emptyList()))
        assertNull(a.choose(2_000, listOf(drop)))
        assertNull(a.choose(3_000, emptyList()))
        assertEquals(drop, a.choose(6_500, listOf(drop)))
    }

    @Test fun aTopicAgainOnlyWhenClearlyCloser() {
        val a = walk()
        val car6 = Notice("car:6", "Car ahead, 6 steps.", hazard, topic = "car", level = 6)
        val car5 = Notice("car:5", "Car ahead, 5 steps.", hazard, topic = "car", level = 5)
        val car4 = Notice("car:4", "Car ahead, 4 steps.", hazard, topic = "car", level = 4)
        assertEquals(car6, a.choose(0, listOf(car6)))
        assertNull(a.choose(5_000, listOf(car5)))
        assertEquals(car4, a.choose(6_000, listOf(car4)))
    }

    @Test fun silenceWhenNothingIsFound() = assertNull(walk().choose(0, emptyList()))

    @Test fun cornerOfACorridorIsNotRepeated() {
        val a = walk()
        val left = Notice("wall:LEFT", "Obstacle on your left, 1 step.", hazard)
        val right = Notice("wall:RIGHT", "Obstacle on your right, 1 step.", hazard)
        val ahead = Notice("wall:ahead:2", "Wall ahead, 2 steps.", hazard)
        val said = (0L until 10_000L step 100L).mapNotNull { a.choose(it, listOf(left, right, ahead)) }
        assertEquals(3, said.size)
    }

    @Test fun aFlickeringKeyIsNotSaidAgain() {
        val a = walk()
        val car = Notice("hazard:car:AHEAD", "Car ahead.", hazard)
        val said = (0L until 20_000L step 100L).mapNotNull { t -> a.choose(t, if ((t / 100) % 4 == 3L) emptyList() else listOf(car)) }
        assertEquals(1, said.size)
    }

    @Test fun parkedCarsAreNotAnnouncedOverAndOver() {
        val a = walk()
        fun car(zone: String, level: Int) = Notice("hazard:car:$zone", "Car $zone, $level steps.", hazard, topic = "hazard:car", level = level)
        assertEquals("hazard:car:AHEAD", a.choose(0, listOf(car("AHEAD", 11)))?.key)
        // another parked car on the right, just as far: not within the topic window
        assertNull(a.choose(3_000, listOf(car("RIGHT", 11))))
        // one getting clearly closer is said
        assertEquals("hazard:car:LEFT", a.choose(4_000, listOf(car("LEFT", 6)))?.key)
        // and after the window any car may be said again
        assertEquals("hazard:car:RIGHT", a.choose(17_000, listOf(car("RIGHT", 11)))?.key)
    }

    @Test fun nearThingsCountEveryStep() {
        val a = walk()
        fun floor(change: String, level: Int) = Notice("floor:$change", "$change $level", floor, topic = "floor", level = level)
        assertEquals("floor:STAIRS_DOWN", a.choose(0, listOf(floor("STAIRS_DOWN", 3)))?.key)
        // a contradicting reading just as far is noise
        assertNull(a.choose(1_000, listOf(floor("STEP_UP", 3))))
        assertEquals("floor:DROP", a.choose(2_000, listOf(floor("DROP", 2)))?.key)
    }

    // ---- New with the shared announcer -------------------------------------------------------------------

    @Test fun aNoticeWaitingForSilenceIsSaidOnceQuiet() {
        val a = Announcer(Announcer.LIVE)
        val chair = Notice("obj:1", "a chair on your left", 0)
        val cup = Notice("obj:2", "a cup on your right", 0)
        assertEquals(chair, a.choose(0, listOf(chair, cup)))
        assertNull(a.choose(300, listOf(chair, cup)))
        assertEquals(cup, a.choose(after(chair), listOf(chair, cup)))
    }

    @Test fun objectsConfirmedTogetherAreSaidOneAfterAnother() {
        val a = Announcer(Announcer.LIVE)
        val things = (1..3).map { Notice("obj:$it", "thing $it on your left", 0) }
        val said = mutableListOf<Notice>()
        var t = 0L
        while (t < 20_000L) {
            a.choose(t, things)?.let { said += it }
            t += 250
        }
        assertEquals(things, said)
    }

    @Test fun liveRulesSayAnObjectAgainOnlyAfterTenSecondsAway() {
        val chair = Notice("obj:1", "a chair ahead", 0, ahead = true)
        val a = Announcer(Announcer.LIVE)
        assertEquals(chair, a.choose(0, listOf(chair)))
        assertNull(a.choose(500, listOf(chair)))
        assertNull(a.choose(1_000, listOf(chair)))
        assertNull(a.choose(10_900, listOf(chair)))
        val b = Announcer(Announcer.LIVE)
        b.choose(0, listOf(chair))
        b.choose(1_000, listOf(chair))
        assertEquals(chair, b.choose(11_100, listOf(chair)))
    }

    @Test fun aSentenceSaidOutsideHoldsWaitingNoticesBack() {
        val a = walk()
        a.said(0, "It is ten past three.")
        assertNull(a.choose(100, listOf(right)))
        assertEquals(wall3, a.choose(100, listOf(wall3)))
        assertEquals(right, a.choose(after(wall3, 100), listOf(right)))
    }

    @Test fun wordsChangingDoNotMakeANewNotice() {
        // A thing on the edge of "ahead" flickers between words; the key is the thing, so it is said once.
        val a = Announcer(Announcer.LIVE)
        val said = (0L until 8_000L step 250L).mapNotNull { t ->
            val text = if ((t / 250) % 2 == 0L) "a chair ahead" else "a chair slightly left"
            a.choose(t, listOf(Notice("obj:7", text, 0)))
        }
        assertEquals(1, said.size)
    }
}
