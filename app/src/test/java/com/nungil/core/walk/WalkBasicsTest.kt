package com.nungil.core.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkBasicsTest {
    /** What MainActivity passes: a word the screen parser knows is a screen, not a place. */
    private val appScreens: (String) -> Boolean = { com.nungil.core.voice.VoiceCommandParser.parse(it) !is com.nungil.contract.VoiceCommand.Unknown }
    private val seoulStation = LatLon(37.5547, 126.9707)
    private val cityHall = LatLon(37.5663, 126.9779)

    // ---- beacon ----------------------------------------------------------------------------------
    @Test fun distanceBetweenSeoulStationAndCityHall() =
        assertEquals(1_430.0, Beacon.distanceMetres(seoulStation, cityHall), 40.0)

    @Test fun bearingNorthAndEast() {
        assertEquals(0.0, Beacon.bearingDeg(LatLon(37.0, 127.0), LatLon(37.01, 127.0)), 0.5)
        assertEquals(90.0, Beacon.bearingDeg(LatLon(37.0, 127.0), LatLon(37.0, 127.01)), 0.5)
    }

    @Test fun clockDirections() {
        assertEquals(12, Beacon.clock(0.0))
        assertEquals(12, Beacon.clock(10.0))
        assertEquals(3, Beacon.clock(90.0))
        assertEquals(6, Beacon.clock(180.0))
        assertEquals(9, Beacon.clock(-90.0))
        assertEquals(12, Beacon.clock(355.0))
    }

    // ---- steps and beeps -------------------------------------------------------------------------
    @Test fun stepLengthFromBodyHeight() = assertEquals(0.7055f, StepLength.metres(1.70f), 0.001f)

    @Test fun stepsAreAtLeastOne() {
        assertEquals(1, StepLength.steps(0.1f, 0.7f))
        assertEquals(3, StepLength.steps(2.1f, 0.7f))
    }

    @Test fun beepIntervalFromTheGuide() {
        assertEquals(1_000L, WalkBeep.intervalMs(4f))
        assertEquals(150L, WalkBeep.intervalMs(0.5f))
        assertEquals(150L, WalkBeep.intervalMs(0.2f))
        assertEquals(575L, WalkBeep.intervalMs(2.25f))
        assertEquals(0L, WalkBeep.intervalMs(4.5f))
        assertEquals(0L, WalkBeep.intervalMs(null))
    }

    @Test fun vibrateUnderOneMetre() {
        assertTrue(WalkBeep.vibrate(0.9f))
        assertFalse(WalkBeep.vibrate(1f))
        assertFalse(WalkBeep.vibrate(null))
    }

    // ---- hazards ---------------------------------------------------------------------------------
    @Test fun hazardNeedsListAndScore() {
        assertTrue(HazardPolicy.isHazard("person", 0.7f))
        assertFalse(HazardPolicy.isHazard("person", 0.69f))
        assertFalse(HazardPolicy.isHazard("book", 0.99f))
    }

    @Test fun hazardConfirmedInThreeOfFive() {
        val c = HazardConfirmer()
        assertTrue(c.update(setOf("car")).isEmpty())
        assertTrue(c.update(setOf("car")).isEmpty())
        assertEquals(setOf("car"), c.update(setOf("car")))
        c.update(emptySet())
        c.update(emptySet())
        assertTrue(c.update(emptySet()).isEmpty())
    }

    @Test fun obstacleNames() {
        assertEquals("door" to "문", ObstacleName.of("sliding door", 0.6f))
        assertEquals("fence" to "울타리", ObstacleName.of("picket fence", 0.5f))
        assertEquals("obstacle" to "장애물", ObstacleName.of("refrigerator", 0.7f))
        assertEquals("refrigerator" to "장애물", ObstacleName.of("refrigerator", 0.9f))
        assertEquals("obstacle" to "장애물", ObstacleName.of("sliding door", 0.4f))
    }

    // ---- traffic lights and ground ---------------------------------------------------------------
    private fun pixels(color: Int, n: Int, fill: Int = 0xFF202020.toInt(), total: Int = 100) =
        IntArray(total) { if (it < n) color else fill }

    @Test fun redAndGreenLights() {
        assertEquals(LightColor.RED, TrafficLightColor.classify(pixels(0xFFFF2020.toInt(), 10)))
        assertEquals(LightColor.GREEN, TrafficLightColor.classify(pixels(0xFF20FFB0.toInt(), 10)))
        assertNull(TrafficLightColor.classify(pixels(0xFFFF2020.toInt(), 2)))
        assertNull(TrafficLightColor.classify(IntArray(0)))
    }

    @Test fun groundOnlyWithSkyAndConfidence() {
        assertTrue(GroundRule.speakable(200, lastSkySeenAtMs = 1_000, nowMs = 30_000))
        assertFalse(GroundRule.speakable(199, lastSkySeenAtMs = 1_000, nowMs = 30_000))
        assertFalse(GroundRule.speakable(255, lastSkySeenAtMs = null, nowMs = 30_000))
        assertFalse(GroundRule.speakable(255, lastSkySeenAtMs = 1_000, nowMs = 62_000))
    }

    // ---- places ----------------------------------------------------------------------------------
    @Test fun placesRoundTrip() {
        val places = listOf(Place("home", seoulStation), Place("학교", cityHall))
        assertEquals(places, PlacesCodec.decode(PlacesCodec.encode(places)))
        assertTrue(PlacesCodec.decode(null).isEmpty())
        assertTrue(PlacesCodec.decode("broken line").isEmpty())
    }

    @Test fun savingAgainReplaces() {
        val list = PlacesCodec.put(listOf(Place("Home", seoulStation)), Place("home", cityHall))
        assertEquals(listOf(Place("home", cityHall)), list)
    }

    @Test fun findingPlaces() {
        val list = listOf(Place("my home", seoulStation), Place("학교", cityHall))
        assertEquals("my home", PlacesCodec.find(list, "home")?.name)
        assertEquals("학교", PlacesCodec.find(list, " 학교 ")?.name)
        assertNull(PlacesCodec.find(list, "office"))
        assertNull(PlacesCodec.find(list, ""))
    }

    // ---- commands --------------------------------------------------------------------------------
    @Test fun englishCommands() {
        assertEquals(WalkCommand.SavePlace("home"), WalkCommands.parse("Save this place as home."))
        assertEquals(WalkCommand.SavePlace("office"), WalkCommands.parse("remember here as office"))
        assertEquals(WalkCommand.GoTo("Seoul Station"), WalkCommands.parse("take me to Seoul Station"))
        assertEquals(WalkCommand.GoTo("home"), WalkCommands.parse("Take me home"))
        assertEquals(WalkCommand.GoTo("home"), WalkCommands.parse("navigate to my home"))
        assertNull(WalkCommands.parse("go to settings", appScreens))
        assertNull(WalkCommands.parse("full scan"))
    }

    @Test fun koreanCommands() {
        assertEquals(WalkCommand.SavePlace("집"), WalkCommands.parse("여기를 집으로 저장해 줘"))
        assertEquals(WalkCommand.SavePlace("학교"), WalkCommands.parse("이 장소를 학교로 저장"))
        assertEquals(WalkCommand.GoTo("서울역"), WalkCommands.parse("서울역까지 안내해 줘"))
        assertEquals(WalkCommand.GoTo("집"), WalkCommands.parse("집으로 가는 길 알려 줘"))
        assertEquals(WalkCommand.GoTo("집"), WalkCommands.parse("집으로 가자"))
    }

    @Test fun saveWithoutANameAsksForOne() {
        assertEquals(WalkCommand.SavePlace(null), WalkCommands.parse("save this place"))
        assertEquals(WalkCommand.SavePlace(null), WalkCommands.parse("Remember here."))
        assertEquals(WalkCommand.SavePlace(null), WalkCommands.parse("여기 저장해 줘"))
        assertEquals(WalkCommand.SavePlace("집"), WalkCommands.parse("여기를 집으로 저장해 줘"))
    }

    @Test fun goModeCommands() {
        for (p in listOf("go mode", "Navigation", "길 안내", "길찾기")) assertEquals(p, WalkCommand.GoMode, WalkCommands.parse(p))
        assertEquals(WalkCommand.GoTo("home"), WalkCommands.parse("take me home"))
    }

    @Test fun goToAPlaceIsAPlace() {
        assertEquals(WalkCommand.GoTo("piano"), WalkCommands.parse("Go to piano", appScreens))
        assertEquals(WalkCommand.GoTo("Seoul Station"), WalkCommands.parse("let's go to Seoul Station", appScreens))
        assertEquals(WalkCommand.GoTo("park"), WalkCommands.parse("go to the park", appScreens))
        for (screen in listOf("settings", "history", "saved")) assertNull(screen, WalkCommands.parse("go to $screen", appScreens))
    }

    /** Heard during live scan: "go Seoul" used to become Start and "That does not work on this screen". */
    @Test fun goWithoutToIsAPlace() {
        assertEquals(WalkCommand.GoTo("Seoul"), WalkCommands.parse("go Seoul", appScreens))
        assertEquals(WalkCommand.GoTo("Seoul Station"), WalkCommands.parse("let's go Seoul Station", appScreens))
        assertEquals(WalkCommand.GoTo("서울"), WalkCommands.parse("서울 가자", appScreens))
        for (p in listOf("go back", "go home", "go ahead", "go on", "go again", "go live scan", "go settings")) {
            assertNull(p, WalkCommands.parse(p, appScreens))
        }
        assertNull(WalkCommands.parse("그만 가자", appScreens))
    }

    @Test fun moreWaysToAskForARoute() {
        for (p in listOf("route", "guide me", "take me somewhere", "길 알려 줘", "내비", "네비게이션")) {
            assertEquals(p, WalkCommand.GoMode, WalkCommands.parse(p, appScreens))
        }
        for (p in listOf("bring me to Seoul Station", "head to Seoul Station", "show me the way to Seoul Station", "how do I get to Seoul Station")) {
            assertEquals(p, WalkCommand.GoTo("Seoul Station"), WalkCommands.parse(p, appScreens))
        }
        assertEquals(WalkCommand.GoTo("서울역"), WalkCommands.parse("서울역에 어떻게 가", appScreens))
    }

    @Test fun letsGoToAScreenIsNotAPlace() =
        assertNull(WalkCommands.parse("설정으로 가자") { it == "설정" })
}
