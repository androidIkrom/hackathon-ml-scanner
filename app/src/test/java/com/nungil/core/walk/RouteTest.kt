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
