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

    // ---- alerts ----------------------------------------------------------------------------------
    @Test fun mostUrgentNewAlertWins() {
        val a = WalkAlerts()
        val hazard = Alert(AlertKind.HAZARD, "person:ahead", "Person ahead.")
        val floor = Alert(AlertKind.FLOOR, "stairs", "Stairs ahead.")
        assertEquals(floor, a.choose(0, listOf(hazard, floor)))
        assertEquals(hazard, a.choose(100, listOf(hazard, floor)))
        assertNull(a.choose(200, listOf(hazard, floor)))
    }

    @Test fun aSituationThatReturnsIsSaidAgainButNotWithinSixSeconds() {
        val a = WalkAlerts()
        val wall = Alert(AlertKind.FLOOR, "drop", "Drop ahead.")
        assertEquals(wall, a.choose(0, listOf(wall)))
        assertNull(a.choose(1_000, emptyList()))
        assertNull(a.choose(2_000, listOf(wall)))
        assertNull(a.choose(3_000, emptyList()))
        assertEquals(wall, a.choose(6_500, listOf(wall)))
    }

    @Test fun silenceWhenNothingIsFound() = assertNull(WalkAlerts().choose(0, emptyList()))

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

    @Test fun cornerOfACorridorIsNotRepeated() {
        val a = WalkAlerts()
        val left = Alert(AlertKind.HAZARD, "wall:LEFT", "Obstacle on your left, 1 step.")
        val right = Alert(AlertKind.HAZARD, "wall:RIGHT", "Obstacle on your right, 1 step.")
        val ahead = Alert(AlertKind.HAZARD, "wall:ahead:2", "Wall ahead, 2 steps.")
        val said = (0L until 10_000L step 100L).mapNotNull { a.choose(it, listOf(left, right, ahead)) }
        assertEquals(3, said.size)
    }

    @Test fun aFlickeringKeyIsNotSaidAgain() {
        val a = WalkAlerts()
        val car = Alert(AlertKind.HAZARD, "hazard:car:AHEAD", "Car ahead.")
        val said = (0L until 20_000L step 100L).mapNotNull { t -> a.choose(t, if ((t / 100) % 4 == 3L) emptyList() else listOf(car)) }
        assertEquals(1, said.size)
    }

    @Test fun parkedCarsAreNotAnnouncedOverAndOver() {
        val a = WalkAlerts()
        fun car(zone: String, level: Int) = Alert(AlertKind.HAZARD, "hazard:car:$zone", "Car $zone, $level steps.", topic = "hazard:car", level = level)
        assertEquals("hazard:car:AHEAD", a.choose(0, listOf(car("AHEAD", 11)))?.key)
        // another parked car on the right, just as far: not within the topic window
        assertNull(a.choose(3_000, listOf(car("RIGHT", 11))))
        // one getting clearly closer is said
        assertEquals("hazard:car:LEFT", a.choose(4_000, listOf(car("LEFT", 6)))?.key)
        // and after the window any car may be said again
        assertEquals("hazard:car:RIGHT", a.choose(17_000, listOf(car("RIGHT", 11)))?.key)
    }

    @Test fun nearThingsCountEveryStep() {
        val a = WalkAlerts()
        fun floor(change: String, level: Int) = Alert(AlertKind.FLOOR, "floor:$change", "$change $level", topic = "floor", level = level)
        assertEquals("floor:STAIRS_DOWN", a.choose(0, listOf(floor("STAIRS_DOWN", 3)))?.key)
        // a contradicting reading just as far is noise
        assertNull(a.choose(1_000, listOf(floor("STEP_UP", 3))))
        assertEquals("floor:DROP", a.choose(2_000, listOf(floor("DROP", 2)))?.key)
    }

    @Test fun letsGoToAScreenIsNotAPlace() =
        assertNull(WalkCommands.parse("설정으로 가자") { it == "설정" })
}
