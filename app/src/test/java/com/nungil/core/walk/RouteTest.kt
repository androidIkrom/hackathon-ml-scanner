package com.nungil.core.walk

import com.nungil.contract.Lang
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class RouteTest {
    private val o = LatLon(37.5, 127.0)

    /** A point [east] and [north] metres from [o]. */
    private fun at(east: Double, north: Double) =
        LatLon(o.lat + north / 110_540.0, o.lon + east / (111_320.0 * cos(Math.toRadians(o.lat))))

    private fun step(type: Int, index: Int, line: List<LatLon>, instruction: String = "") =
        RouteStep(instruction, type, 0f, line[index], index)

    /** East 100 m, left, north 100 m, right, east 100 m. */
    private fun route(destination: LatLon? = null): Route {
        val line = listOf(at(0.0, 0.0), at(100.0, 0.0), at(100.0, 100.0), at(200.0, 100.0))
        val steps = listOf(
            step(OrsJson.DEPART_TYPE, 0, line, "Head east on First Street"),
            step(0, 1, line, "Turn left onto Main Street"),
            step(1, 2, line, "Turn right onto Park Road"),
            step(OrsJson.ARRIVE_TYPE, 3, line, "Arrive at Park Road"),
        )
        return Route(steps, line, 300f, Place("home", destination ?: line.last()))
    }

    // ---- request and response --------------------------------------------------------------------
    @Test fun requestPutsLongitudeFirst() {
        val body = JSONObject(OrsJson.routeRequest(LatLon(37.55, 126.97), LatLon(37.56, 126.98)))
        val first = body.getJSONArray("coordinates").getJSONArray(0)
        assertEquals(126.97, first.getDouble(0), 1e-9)
        assertEquals(37.55, first.getDouble(1), 1e-9)
        assertTrue(body.getBoolean("instructions"))
        assertEquals("shortest", body.getString("preference"))
    }

    private val sample = """
        {"features":[{"geometry":{"coordinates":[[127.0,37.5],[127.001,37.5],[127.001,37.501],[127.002,37.501]]},
         "properties":{"summary":{"distance":290.5,"duration":209.2},
          "segments":[{"steps":[
            {"distance":88.4,"duration":63.6,"type":11,"instruction":"Head east","name":"-","way_points":[0,1]},
            {"distance":111.1,"duration":80.0,"type":0,"instruction":"Turn left onto Sejong-daero","name":"Sejong-daero","way_points":[1,2]},
            {"distance":91.0,"duration":65.5,"type":1,"instruction":"","name":"-","way_points":[2,3]},
            {"distance":0.0,"duration":0.0,"type":10,"instruction":"Arrive at your destination","name":"-","way_points":[3,3]}]}]}}]}
    """.trimIndent()

    @Test fun parsesTheSampleResponse() {
        val r = OrsJson.parseRoute(sample, Place("home", LatLon(37.501, 127.002)))!!
        assertEquals(4, r.line.size)
        assertEquals(LatLon(37.5, 127.0), r.line[0])
        assertEquals(290.5f, r.totalM, 0.01f)
        assertEquals(4, r.steps.size)
        assertEquals("Turn left onto Sejong-daero", r.steps[1].instruction)
    }

    @Test fun maneuverIsWhereTheStepStartsWhichIsThePreviousStepsLastWayPoint() {
        val r = OrsJson.parseRoute(sample, Place("home", LatLon(37.501, 127.002)))!!
        assertEquals(1, r.steps[1].pointIndex)
        assertEquals(LatLon(37.5, 127.001), r.steps[1].maneuver)
    }

    @Test fun emptyOrBrokenResponsesAreNull() {
        assertNull(OrsJson.parseRoute("""{"features":[]}""", Place("x", o)))
        assertNull(OrsJson.parseRoute("not json", Place("x", o)))
    }

    @Test fun geocodeCandidates() {
        val json = """{"features":[{"geometry":{"coordinates":[126.9707,37.5547]},"properties":{"label":"Seoul Station, Seoul"}}]}"""
        assertEquals(listOf(Place("Seoul Station, Seoul", LatLon(37.5547, 126.9707))), OrsJson.parseGeocode(json))
        assertTrue(OrsJson.parseGeocode("{}").isEmpty())
    }

    @Test fun geocodeSpeaksTheShortNameAndDropsFarPlaces() {
        val json = """{"features":[
            {"geometry":{"coordinates":[126.9707,37.5550]},"properties":{"name":"서울역","label":"South Korea Seoul Yongsan 서울역"}},
            {"geometry":{"coordinates":[-8.61,41.15]},"properties":{"name":"Starbucks","label":"Starbucks, Porto, Portugal"}}]}"""
        val seoul = LatLon(37.566, 126.978)
        assertEquals(listOf(Place("서울역", LatLon(37.5550, 126.9707))), OrsJson.parseGeocode(json, near = seoul))
    }

    @Test fun deprecatedHostQuotaError() {
        assertTrue(OrsJson.isQuotaError(403, """{"error":"Quota exceeded"}"""))
        assertFalse(OrsJson.isQuotaError(429, "Too many"))
    }

    // ---- guidance --------------------------------------------------------------------------------
    @Test fun prepareOnceAt25MetresThenTurnOnceAt5() {
        val n = Navigator(route(), null, Lang.EN)
        assertNull(n.update(0, at(70.0, 0.0)))
        assertEquals(Announcement.Prepare("In 25 metres, turn left onto Main Street."), n.update(1_000, at(76.0, 0.0)))
        assertNull(n.update(2_000, at(80.0, 0.0)))
        assertEquals(Announcement.Turn("Turn left now."), n.update(3_000, at(96.0, 0.0)))
        assertNull(n.update(4_000, at(98.0, 0.0)))
    }

    @Test fun gpsJitterDoesNotRepeat() {
        val n = Navigator(route(), null, Lang.EN)
        assertTrue(n.update(0, at(78.0, 0.0)) is Announcement.Prepare)
        assertNull(n.update(1_000, at(74.0, 0.0)))
        assertNull(n.update(2_000, at(78.0, 0.0)))
    }

    @Test fun aLongGapSkipsTheStaleTurnSilently() {
        val n = Navigator(route(), null, Lang.EN)
        assertNull(n.update(0, at(10.0, 0.0)))
        val next = n.update(1_000, at(100.0, 80.0))
        assertEquals(Announcement.Prepare("In 20 metres, turn right onto Park Road."), next)
    }

    @Test fun arrivesWithin15Metres() {
        val n = Navigator(route(), null, Lang.EN)
        assertEquals(Announcement.Arrived("You have arrived at home."), n.update(0, at(190.0, 100.0)))
        assertTrue(n.finished)
        assertNull(n.update(1_000, at(200.0, 100.0)))
    }

    @Test fun noArrivalWhenStepsRunOutFarFromTheDestination() {
        val n = Navigator(route(destination = at(200.0, 400.0)), null, Lang.EN)
        n.update(0, at(150.0, 100.0))
        assertFalse(n.update(1_000, at(199.0, 100.0)) is Announcement.Arrived)
        assertFalse(n.finished)
    }

    @Test fun offRouteAfterThreeFarFixes() {
        val n = Navigator(route(), null, Lang.EN)
        assertNull(n.update(0, at(50.0, 60.0)))
        assertNull(n.update(1_000, at(50.0, 60.0)))
        assertEquals(Announcement.OffRoute("Off the route, finding a new one."), n.update(2_000, at(50.0, 60.0)))
    }

    @Test fun aNearFixResetsTheOffRouteCounter() {
        val n = Navigator(route(), null, Lang.EN)
        n.update(0, at(50.0, 60.0))
        n.update(1_000, at(50.0, 60.0))
        n.update(2_000, at(50.0, 2.0))
        assertNull(n.update(3_000, at(50.0, 60.0)))
        assertNull(n.update(4_000, at(50.0, 60.0)))
    }

    @Test fun rerouteCooldownAndBackoff() {
        val g = RerouteGate()
        assertTrue(g.allow(0))
        assertFalse(g.allow(10_000))
        assertTrue(g.allow(30_000))
        g.tooManyRequests(40_000)
        assertFalse(g.allow(90_000))
        assertTrue(g.allow(100_001))
    }

    // ---- wording ---------------------------------------------------------------------------------
    @Test fun metresAreRoundedToFiveAndNeverZero() {
        assertEquals("20 metres", RoutePhrases.distance(22f, null, Lang.EN))
        assertEquals("25 metres", RoutePhrases.distance(23f, null, Lang.EN))
        assertEquals("5 metres", RoutePhrases.distance(1f, null, Lang.EN))
        assertEquals("20미터", RoutePhrases.distance(21f, null, Lang.KO))
    }

    @Test fun stepsWhenTheStepLengthIsKnown() {
        assertEquals("30 steps", RoutePhrases.distance(21f, 0.7f, Lang.EN))
        assertEquals("스무 걸음", RoutePhrases.distance(14f, 0.7f, Lang.KO))
    }

    @Test fun emptyInstructionFallsBackToTheTurnType() {
        val s = RouteStep("", 1, 0f, o, 1)
        assertEquals("In 20 metres, turn right.", RoutePhrases.prepare(s, 20f, null, Lang.EN))
    }

    @Test fun koreanGuidance() {
        val s = RouteStep("Turn left onto Sejong-daero", 0, 0f, o, 1)
        assertEquals("20미터 앞에서 왼쪽으로 도세요.", RoutePhrases.prepare(s, 20f, null, Lang.KO))
        assertEquals("지금 왼쪽으로 도세요.", RoutePhrases.turn(s, Lang.KO))
        assertEquals("집에 도착했어요.", RoutePhrases.arrived("집", Lang.KO))
        assertEquals("길 안내를 멈췄어요.", RoutePhrases.navigationStopped(Lang.KO))
    }

    @Test fun yesAndNo() {
        assertTrue(RoutePhrases.isYes("Yes."))
        assertTrue(RoutePhrases.isYes("네"))
        assertTrue(RoutePhrases.isNo("아니요"))
        assertFalse(RoutePhrases.isYes("maybe"))
    }

    @Test fun answersToAFoundPlace() {
        for (yes in listOf("Yes.", "yeah", "yes please", "ok go", "Okay", "sure", "yes that one", "네", "좋아", "가자")) {
            assertEquals(yes, GoAnswer.Yes, GoAnswer.of(yes))
        }
        for (next in listOf("No.", "next", "no thanks", "next one", "not that one", "another one", "아니요", "다음")) {
            assertEquals(next, GoAnswer.Next, GoAnswer.of(next))
        }
        assertEquals(GoAnswer.Pick(0), GoAnswer.of("the first one"))
        assertEquals(GoAnswer.Pick(1), GoAnswer.of("Second"))
        assertEquals(GoAnswer.Pick(1), GoAnswer.of("number two"))
        assertEquals(GoAnswer.Pick(2), GoAnswer.of("3"))
        assertEquals(GoAnswer.Pick(1), GoAnswer.of("두 번째"))
    }

    @Test fun askingForTheOptionsIsAnAnswerToo() {
        // The logs: "Show available options" opened Settings.
        for (ask in listOf("Show available options", "what are the options", "list them", "all of them", "목록")) {
            assertEquals(ask, GoAnswer.Options, GoAnswer.of(ask))
        }
        assertEquals(
            "1: Bus Terminal, 400 metres. 2: Bus Stop, 1.2 kilometres. Say the number.",
            RoutePhrases.options(listOf("Bus Terminal" to 400.0, "Bus Stop" to 1_200.0), Lang.EN),
        )
    }

    @Test fun aPlaceNameIsNotAnAnswer() {
        // "OK Mart" and "North Station" start like an answer but are places.
        for (place in listOf("OK Mart", "North Station", "Seoul Station", "Yes24 bookstore", "One Mount", "강남역")) {
            assertEquals(place, GoAnswer.Other(place), GoAnswer.of(place))
        }
    }

    @Test fun confirmSaysHowToHearTheNextPlace() {
        assertEquals("Seoul Station, 400 metres away. Say yes to go.", RoutePhrases.confirm("Seoul Station", 400.0, Lang.EN))
        assertEquals("Seoul Station, 400 metres away. Say yes to go, or next for another place.", RoutePhrases.confirm("Seoul Station", 400.0, Lang.EN, more = true))
    }

    @Test fun goToABusStopIsAPlaceAndGoToAloneIsNot() {
        val isScreen: (String) -> Boolean = { it == "settings" }
        assertEquals(WalkCommand.GoTo("bus stop"), WalkCommands.parse("Go to bus stop", isScreen))
        assertEquals(WalkCommand.GoTo("bus stop"), WalkCommands.parse("Go bus stop", isScreen))
        // The logs: "Go to" on its own searched for a place called "to".
        assertEquals(WalkCommand.GoMode, WalkCommands.parse("Go to", isScreen))
        assertNull(WalkCommands.parse("go to settings", isScreen))
    }

    @Test fun aFarPlaceIsNotAnUnknownPlace() {
        assertEquals("Seoul is 85.2 kilometres away. That is too far to walk.", RoutePhrases.tooFar("Seoul", 85_200.0, Lang.EN))
        assertTrue(RoutePhrases.noSavedHome(Lang.EN).contains("save this place as home"))
        assertTrue(RoutePhrases.searchUnavailable(Lang.EN).isNotBlank())
    }

    @Test fun goScreenShowsTheNextTurnAndWhatIsLeft() {
        val n = Navigator(route(), null, Lang.EN)
        val s = n.peek(at(60.0, 0.0))
        assertEquals(route().line[1], s.target)
        assertEquals("Turn left onto Main Street", s.instruction)
        assertEquals(40f, s.toTargetM, 1f)
        assertEquals(240f, s.remainingM, 2f)
    }

    @Test fun afterTheLastTurnTheGoScreenPointsAtTheDestination() {
        val n = Navigator(route(), null, Lang.KO)
        val s = n.peek(at(150.0, 100.0))
        assertEquals("home로 가세요", s.instruction)
        assertEquals(50f, s.toTargetM, 2f)
    }

    @Test fun arrowPointsRelativeToThePhone() {
        val here = at(0.0, 0.0)
        assertEquals(0f, GoMath.arrowDeg(here, at(0.0, 100.0), 0f), 1f)
        assertEquals(90f, GoMath.arrowDeg(here, at(100.0, 0.0), 0f), 1f)
        assertEquals(-90f, GoMath.arrowDeg(here, at(0.0, 100.0), 90f), 1f)
        assertEquals(180f, kotlin.math.abs(GoMath.arrowDeg(here, at(0.0, -100.0), 0f)), 1f)
    }

    @Test fun goPhrases() {
        assertEquals("Head to Seoul Station", RoutePhrases.headTo("Seoul Station", Lang.EN))
        assertEquals("서울역으로 가세요", RoutePhrases.headTo("서울역", Lang.KO))
        assertEquals("In 40 metres · 240 metres left", RoutePhrases.goDistance(40f, 240f, Lang.EN))
        assertEquals("40미터 후 · 240미터 남았어요", RoutePhrases.goDistance(40f, 240f, Lang.KO))
        assertEquals("어디로 갈까요?", RoutePhrases.whereTo(Lang.KO))
        assertEquals("Turn right", RoutePhrases.display(RouteStep("", 1, 0f, o, 1), Lang.EN))
        assertEquals("오른쪽으로 도세요", RoutePhrases.display(RouteStep("Turn right onto X", 1, 0f, o, 1), Lang.KO))
    }

    // ---- progress, wrong way, approach, side --------------------------------------------------------

    @Test fun walkingBackIsTheWrongWay() {
        val n = Navigator(route(), null, Lang.EN)
        n.update(0, at(60.0, 0.0))
        assertEquals(60f, n.progressM, 1f)
        assertNull(n.update(1_000, at(50.0, 0.0)))
        assertNull(n.update(2_000, at(44.0, 0.0)))
        assertNull(n.update(3_000, at(42.0, 0.0)))
        assertEquals(Announcement.WrongWay("You are walking away from the route. Turn around."), n.update(4_000, at(40.0, 0.0)))
        assertNull(n.update(5_000, at(30.0, 0.0))) // once
        n.update(6_000, at(61.0, 0.0)) // back past the farthest point
        n.update(7_000, at(45.0, 0.0))
        n.update(8_000, at(44.0, 0.0))
        assertTrue(n.update(9_000, at(43.0, 0.0)) is Announcement.WrongWay)
    }

    @Test fun jitterIsNotTheWrongWay() {
        val n = Navigator(route(), null, Lang.EN)
        for (i in 0..20) assertFalse(n.update(i * 1_000L, at(if (i % 2 == 0) 40.0 else 32.0, 3.0)) is Announcement.WrongWay)
    }

    @Test fun aSingleJumpIsNotTheWrongWay() {
        // ±8 m of jitter swings 16 m: one fix that far back, then on again, is the GPS, not the walker.
        val n = Navigator(route(), null, Lang.EN)
        n.update(0, at(48.0, 0.0))
        for (i in 1..10) assertFalse(n.update(i * 1_000L, at(if (i % 3 == 0) 32.0 else 46.0, 0.0)) is Announcement.WrongWay)
    }

    @Test fun aPoorFixNeedsTwiceItsAccuracyBack() {
        val n = Navigator(route(), null, Lang.EN)
        n.update(0, at(60.0, 0.0), accuracyM = 10f)
        for (i in 1..5) assertFalse(n.update(i * 1_000L, at(42.0, 0.0), accuracyM = 10f) is Announcement.WrongWay) // 18 m < 20
        n.update(6_000, at(39.0, 0.0), accuracyM = 10f)
        n.update(7_000, at(38.0, 0.0), accuracyM = 10f)
        assertTrue(n.update(8_000, at(37.0, 0.0), accuracyM = 10f) is Announcement.WrongWay) // 23 m, three fixes
    }

    @Test fun aSnapToAnEarlierPartOfTheRouteIsNotTheWrongWay() {
        // A route back along the other side of the street: the GPS puts the walker on its earlier part, far behind.
        val n = Navigator(route(), null, Lang.EN)
        n.update(0, at(100.0, 90.0))
        for (i in 1..5) assertFalse(n.update(i * 1_000L, at(70.0, 0.0)) is Announcement.WrongWay)
    }

    @Test fun theDestinationOnTheWayInWithItsSide() {
        // 3.5 m right of where the last segment (heading east) ends. Left along the line + 3.5 m: 48.5 at x 155,
        // 19.5 at x 184 (16.4 m from it in a straight line: not yet arrived).
        val dest = at(200.0, 96.5)
        val n = Navigator(route(destination = dest), null, Lang.EN)
        assertEquals(Side.RIGHT, n.side)
        n.update(0, at(100.0, 60.0))
        assertEquals(Announcement.Approach("home in 50 metres, on your right."), n.update(1_000, at(155.0, 100.0)))
        assertEquals(Announcement.Approach("home in 20 metres, on your right."), n.update(2_000, at(184.0, 100.0)))
        assertEquals(Announcement.Arrived("You have arrived at home, on your right."), n.update(3_000, at(195.0, 97.0)))
    }

    @Test fun aShortRouteDoesNotAnnounceAThresholdItStartedInside() {
        val a = GoApproach()
        assertNull(a.next(45f))
        assertEquals(20, a.next(20f))
        assertNull(a.next(10f))
        val b = GoApproach()
        assertNull(b.next(61f))
        assertEquals(50, b.next(50f))
    }

    @Test fun noSideOnTheLine() {
        assertNull(GoMath.side(at(0.0, 0.0), at(100.0, 0.0), at(110.0, 2.0)))
        assertEquals(Side.LEFT, GoMath.side(at(0.0, 0.0), at(100.0, 0.0), at(100.0, 5.0)))
    }

    @Test fun nextAndWhichWaySentences() {
        val turn = GoState(at(100.0, 100.0), "Turn right onto Park Road", 80f, 180f)
        val end = GoState(at(200.0, 100.0), "Head to home", 80f, 80f, final = true)
        assertEquals("Next, turn right onto Park Road in 80 metres.", RoutePhrases.next(turn, "home", Lang.EN))
        assertEquals("Next, home in 80 metres.", RoutePhrases.next(end, "home", Lang.EN))
        assertEquals("The next turn is at 2 o'clock, 80 metres.", RoutePhrases.whichWay(2, 80f, null, Lang.EN))
        assertEquals("Seoul Station is at 2 o'clock, 350 metres.", RoutePhrases.whichWay(2, 350f, "Seoul Station", Lang.EN))
        assertEquals("다음 갈림길은 2시 방향, 80미터예요.", RoutePhrases.whichWay(2, 80f, null, Lang.KO))
        assertEquals("50미터 앞 오른쪽에 서울역이 있어요.", RoutePhrases.approach("서울역", 50f, Side.RIGHT, Lang.KO))
        assertEquals("경로에서 멀어지고 있어요. 뒤로 돌아가세요.", RoutePhrases.wrongWay(Lang.KO))
        assertEquals("No route is running. Say go to, and a place.", RoutePhrases.noRouteRunning(Lang.EN))
        assertEquals("I can't tell the direction yet. Hold the phone up and ask again.", RoutePhrases.noHeading(Lang.EN))
    }

    @Test fun theStreetFromAReverseAnswer() {
        val json = """{"features":[{"properties":{"name":"12 Sejong-daero","street":"Sejong-daero","label":"12 Sejong-daero, Seoul"}}]}"""
        assertEquals("Sejong-daero", OrsJson.parseReverse(json))
        assertEquals("Seoul Station", OrsJson.parseReverse("""{"features":[{"properties":{"name":"Seoul Station"}}]}"""))
        assertNull(OrsJson.parseReverse("""{"features":[]}"""))
        assertNull(OrsJson.parseReverse("not json"))
    }

    @Test fun whereAmISentences() {
        assertEquals("You are on Sejong-daero, near Seoul Station.", RoutePhrases.whereAmI("Sejong-daero", "Seoul Station", Lang.EN))
        assertEquals("You are on Sejong-daero.", RoutePhrases.whereAmI("Sejong-daero", null, Lang.EN))
        assertEquals("지금 세종대로에 있어요, 서울역 근처예요.", RoutePhrases.whereAmI("세종대로", "서울역", Lang.KO))
        assertEquals("The GPS signal is weak, directions may be off.", RoutePhrases.gpsWeak(Lang.EN))
        assertEquals("Minute updates off. Ask how far any time.", RoutePhrases.updatesOff(Lang.EN))
    }

    // ---- the direction every 10 s ----------------------------------------------------------------------

    private fun assertNear(expected: LatLon, actual: LatLon) =
        assertTrue("$actual is not $expected", Beacon.distanceMetres(expected, actual) < 0.5)

    @Test fun theDirectionPointsTwentyMetresAheadOnTheRoute() {
        assertNear(at(50.0, 0.0), Navigator(route(), null, Lang.EN).aheadPoint(at(30.0, 0.0)))
    }

    @Test fun offTheRouteItPointsBackToIt() {
        assertNear(at(50.0, 0.0), Navigator(route(), null, Lang.EN).aheadPoint(at(30.0, 10.0)))
    }

    @Test fun itDoesNotCutTheCorner() {
        // 10 m before the left turn: the corner, not a point in the next street through the building.
        assertNear(at(100.0, 0.0), Navigator(route(), null, Lang.EN).aheadPoint(at(90.0, 0.0)))
    }

    @Test fun atTheTurnItLooksIntoTheNextStreet() {
        // "Turn left now" is said 5 m before the corner: from there the direction is the next street.
        assertNear(at(100.0, 17.0), Navigator(route(), null, Lang.EN).aheadPoint(at(97.0, 0.0)))
    }

    @Test fun nearTheEndItPointsAtThePlace() {
        val dest = at(200.0, 96.5)
        assertNear(dest, Navigator(route(destination = dest), null, Lang.EN).aheadPoint(at(190.0, 100.0)))
    }

    @Test fun directionSentences() {
        assertEquals("Go at 2 o'clock.", RoutePhrases.goClock(2, Lang.EN))
        assertEquals("Go straight ahead.", RoutePhrases.goClock(12, Lang.EN))
        assertEquals("2시 방향으로 가세요.", RoutePhrases.goClock(2, Lang.KO))
        assertEquals("앞으로 곧장 가세요.", RoutePhrases.goClock(12, Lang.KO))
    }

    @Test fun numbersFromTheSpec() {
        assertEquals(25f, Navigator.PREPARE_M)
        assertEquals(5f, Navigator.TURN_M)
        assertEquals(15.0, Navigator.ARRIVED_M, 0.0)
        assertEquals(40f, Navigator.OFF_ROUTE_M)
        assertEquals(3, Navigator.OFF_ROUTE_FIXES)
        assertEquals(30_000L, RerouteGate.COOLDOWN_MS)
        assertEquals(10_000L, Navigator.REPEAT_MS)
    }
}
