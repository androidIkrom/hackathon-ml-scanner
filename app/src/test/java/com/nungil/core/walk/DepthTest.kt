package com.nungil.core.walk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

class DepthTest {
    private val w = 90
    private val h = 160

    private fun grid(value: (x: Int, y: Int) -> Float) = FloatArray(w * h) { value(it % w, it / w) }

    // ---- walls ahead -----------------------------------------------------------------------------
    @Test fun openSpaceIsNotBlocked() {
        val r = DepthObstacles.read(grid { _, _ -> 6f }, w, h)
        assertTrue(r.none { it.blocked })
        assertTrue(r.none { it.unknown })
    }

    @Test fun wallAheadIsBlockedAtItsLowerQuartile() {
        val r = DepthObstacles.read(grid { x, y -> if (x in 30 until 60) 1.5f + (y % 4) * 0.1f else 6f }, w, h)
        val ahead = r.first { it.zone == Zone.AHEAD }
        assertTrue(ahead.blocked)
        assertEquals(1.5f, ahead.distanceM!!, 0.11f)
        assertFalse(r.first { it.zone == Zone.LEFT }.blocked)
    }

    @Test fun fortyPercentCloseIsEnough() {
        val r = DepthObstacles.read(grid { x, _ -> if (x in 30 until 60 && x % 5 < 2) 2f else if (x in 30 until 60) 7f else 7f }, w, h)
        assertTrue(r.first { it.zone == Zone.AHEAD }.blocked)
    }

    @Test fun readingsOutsideTheBandAreIgnored() {
        val r = DepthObstacles.read(grid { _, y -> if (y < 40 || y > 110) 0.8f else 6f }, w, h)
        assertTrue(r.none { it.blocked })
    }

    @Test fun missingDepthIsUnknownNotClear() {
        val r = DepthObstacles.read(grid { x, _ -> if (x in 30 until 60) Float.NaN else 6f }, w, h)
        val ahead = r.first { it.zone == Zone.AHEAD }
        assertTrue(ahead.unknown)
        assertFalse(ahead.blocked)
    }

    @Test fun bandLimitsFromTheGuide() {
        assertEquals(0.30f, DepthObstacles.BAND_TOP)
        assertEquals(0.62f, DepthObstacles.BAND_BOTTOM)
        assertEquals(3f, DepthObstacles.NEAR_M)
        assertEquals(0.4f, DepthObstacles.BLOCKED_SHARE)
    }

    // ---- frames that are not a measurement (a recorded walk: 193 of 229 frames) ---------------------
    @Test fun anEmptyOrOneValueDepthImageIsNotAMeasurement() {
        assertFalse(DepthGrid.measured(grid { _, _ -> 0f }))
        // ARCore handed out "33.11 m" or "1.43 m" in every pixel, with the phone tilted down
        assertFalse(DepthGrid.measured(grid { _, _ -> 33.11f }))
        assertFalse(DepthGrid.measured(grid { x, _ -> 1.43f + (x % 3) * 0.001f }))
    }

    @Test fun aRealSceneIsAMeasurement() {
        assertTrue(DepthGrid.measured(scene(-0.4f) { 0f }))
        // right in front of a wall the readings are close together, but not one value (recorded: 0.31-0.41 m)
        assertTrue(DepthGrid.measured(grid { x, y -> 0.31f + ((x * 7 + y * 13) % 10) * 0.01f }))
    }

    @Test fun fartherThanTheRangeIsMeasuredAndClear() {
        // A long corridor: beyond 8 m is a reading, not a missing one.
        val ahead = DepthObstacles.read(grid { _, _ -> 15f }, w, h).first { it.zone == Zone.AHEAD }
        assertFalse(ahead.unknown)
        assertFalse(ahead.blocked)
    }

    @Test fun turningAwayFromAWallDropsItEvenWithoutDepth() {
        // The recorded walk: turned from a wall to a dark corridor with no depth, and the wall kept beeping for 10 s.
        val hold = CloseHold()
        hold.update(0, ahead(true, 1.1f), here = Standpoint(0f, 0f, 0f))
        assertEquals(1.1f, hold.update(500, ahead(false, null, valid = 0f), here = Standpoint(0f, 0f, 0.2f)))
        assertNull(hold.update(1_000, ahead(false, null, valid = 0f), here = Standpoint(0f, 0f, 1.2f)))
    }

    @Test fun aWallLostAtTwoStepsIsCountedDownByWalking() {
        // The recorded walk: depth stopped at 1.3 m from a plain wall; nothing was said down to touching it.
        val hold = CloseHold()
        assertEquals(1.3f, hold.update(0, ahead(true, 1.3f), headingDeg = 90f))
        assertEquals(0.6f, hold.update(1_000, ahead(false, null, valid = 0f), walkedM = 0.7f, headingDeg = 95f)!!, 0.001f)
        assertEquals(DepthObstacles.MIN_M, hold.update(2_000, ahead(false, null, valid = 0f), walkedM = 0.7f, headingDeg = 92f)!!, 0f)
    }

    @Test fun turningAwayByTheCompassDropsTheWallWhenARCoreIsLost() {
        val hold = CloseHold()
        hold.update(0, ahead(true, 1.3f), headingDeg = 350f)
        assertEquals(1.3f, hold.update(500, ahead(false, null, valid = 0f), headingDeg = 10f))
        assertNull(hold.update(1_000, ahead(false, null, valid = 0f), headingDeg = 40f))
    }

    @Test fun aWallTwoStepsAwayThatIsGoneIsBelievedGone() {
        // Only right in front of a wall depth invents a clear way; at two steps a person may just have left.
        val hold = CloseHold()
        val here = Standpoint(0f, 0f, 0f)
        hold.update(0, ahead(true, 1.8f), here = here)
        assertNull(hold.update(500, ahead(false, null), here = here))
    }

    @Test fun walkingOnTowardsTheWallKeepsIt() {
        val hold = CloseHold()
        hold.update(0, ahead(true, 1.1f), here = Standpoint(0f, 0f, 0f))
        assertEquals(0.5f, hold.update(500, ahead(false, null, valid = 0f), walkedM = 0.6f, here = Standpoint(0f, 0.6f, 0f))!!, 0.001f)
        assertEquals(0.5f, hold.update(1_000, ahead(false, null), here = Standpoint(0f, 0.6f, 0f))!!, 0.001f)
    }

    // ---- close hold ------------------------------------------------------------------------------
    private fun ahead(blocked: Boolean, d: Float?, valid: Float = 1f) = ZoneReading(Zone.AHEAD, blocked, d, valid)

    @Test fun closeWallIsHeldWhileDepthIsMissing() {
        val hold = CloseHold()
        assertEquals(1.0f, hold.update(0, ahead(true, 1.0f)))
        assertEquals(1.0f, hold.update(5_000, ahead(false, null, valid = 0.05f)))
        assertEquals(1.0f, hold.update(10_000, ahead(false, null, valid = 0.05f)))
        assertNull(hold.update(10_001, ahead(false, null, valid = 0.05f)))
    }

    @Test fun walkingOnWithoutDepthBringsTheWallCloser() {
        val hold = CloseHold()
        hold.update(0, ahead(true, 1.1f))
        assertEquals(0.4f, hold.update(1_000, ahead(false, null, valid = 0f), walkedM = 0.7f)!!, 0.001f)
        assertEquals(DepthObstacles.MIN_M, hold.update(2_000, ahead(false, null, valid = 0f), walkedM = 0.7f)!!, 0f)
    }

    @Test fun approachSpeedFromMeasurements() {
        val a = ApproachSpeed()
        a.measured(0, 2.0f)
        a.measured(500, 1.7f)
        a.measured(1_000, 1.4f)
        assertEquals(0.6f, a.speed(), 0.001f)
        assertEquals(0.3f, a.walked(1_000, 1_500), 0.001f)
        assertEquals(0f, a.walked(3_000, 3_500), 0f)
    }

    @Test fun standingStillIsNoSpeed() {
        val a = ApproachSpeed()
        a.measured(0, 1.4f)
        a.measured(1_000, 1.5f)
        assertEquals(0f, a.speed(), 0f)
    }

    @Test fun measuredClearCancelsTheHold() {
        val hold = CloseHold()
        hold.update(0, ahead(true, 1.0f))
        assertNull(hold.update(1_000, ahead(false, null)))
        assertNull(hold.update(2_000, ahead(false, null, valid = 0.05f)))
    }

    @Test fun farWallsAreNotHeld() {
        val hold = CloseHold()
        assertEquals(2.5f, hold.update(0, ahead(true, 2.5f)))
        assertNull(hold.update(1_000, ahead(false, null, valid = 0.05f)))
    }

    // ---- geometry and ground profile -------------------------------------------------------------
    private val focal = 120f

    /**
     * ARCore-style depth (along the optical axis) of a scene seen from [camH] with [pitch], whose
     * surface height at forward distance f is [ground](f).
     */
    private fun scene(pitch: Float, camH: Float = 1.2f, ground: (Float) -> Float): FloatArray = grid { _, y ->
        val beta = atan(((y + 0.5f) - h / 2f) / focal)
        val alpha = beta - pitch
        if (alpha <= 0.01f) 0f else {
            var d = 0.3f
            var hit = 0f
            while (d < 8f) {
                val f = d * cos(alpha)
                if (camH - d * sin(alpha) <= ground(f)) {
                    hit = d * cos(beta)
                    break
                }
                d += 0.01f
            }
            hit
        }
    }

    private fun geometry(depth: FloatArray, pitch: Float) = GridGeometry.estimate(depth, w, h, focal, pitch)

    @Test fun cameraHeightComesFromTheFloorAtTheFeet() {
        val depth = scene(-0.35f, camH = 1.45f) { 0f }
        assertEquals(1.45f, geometry(depth, -0.35f).cameraHeightM, 0.05f)
    }

    @Test fun anUprightPhoneStillMeasuresItsHeight() {
        // Held almost upright at 1.45 m the phone first sees the floor 1.4 m ahead, past the 0.6-1.3 m window.
        val depth = scene(-0.23f, camH = 1.45f) { 0f }
        assertEquals(1.45f, GridGeometry.floorHeight(depth, w, h, focal, -0.23f)!!, 0.05f)
    }

    @Test fun aPhoneHeldHigherIsNotADrop() {
        val depth = scene(-0.35f, camH = 1.45f) { 0f }
        assertNull(GroundProfile.analyse(GroundProfile.rows(depth, w, h, geometry(depth, -0.35f))))
    }

    @Test fun flatFloorHasNoChange() {
        val depth = scene(-0.3f) { 0f }
        val rows = GroundProfile.rows(depth, w, h, geometry(depth, -0.3f))
        assertTrue(rows.size > 5)
        assertNull(GroundProfile.analyse(rows))
    }

    @Test fun kerbUpIsAStep() {
        val depth = scene(-0.3f) { f -> if (f > 2f) 0.15f else 0f }
        val r = GroundProfile.analyse(GroundProfile.rows(depth, w, h, geometry(depth, -0.3f)))!!
        assertEquals(FloorChange.STEP_UP, r.change)
        assertEquals(2f, r.distanceM, 0.3f)
    }

    @Test fun floorSeenInTheBandIsNotAWall() {
        // Tilted down, the chest-height band sees the floor 2-3 m ahead: open space, not a wall.
        val depth = scene(-0.6f) { 0f }
        val r = DepthObstacles.read(depth, w, h, geometry(depth, -0.6f))
        assertFalse(r.first { it.zone == Zone.AHEAD }.blocked)
    }

    @Test fun aRealWallIsStillAWallWithGeometry() {
        val depth = scene(-0.3f) { f -> if (f > 1.5f) 3f else 0f }
        val ahead = DepthObstacles.read(depth, w, h, geometry(depth, -0.3f)).first { it.zone == Zone.AHEAD }
        assertTrue(ahead.blocked)
        assertEquals(1.5f, ahead.distanceM!!, 0.2f)
    }

    @Test fun readingsCloserThanTheMinimumCountAsVeryClose() {
        val r = DepthObstacles.read(grid { x, _ -> if (x in 30 until 60) 0.1f else 6f }, w, h)
        val ahead = r.first { it.zone == Zone.AHEAD }
        assertTrue(ahead.blocked)
        assertEquals(DepthObstacles.MIN_M, ahead.distanceM!!, 0f)
    }

    @Test fun stairsGoingDown() {
        val rows = (0 until 12).map { i -> GroundProfile.Row(1f + i * 0.1f, if (i == 0) 0f else -0.17f * ((i + 2) / 3)) }
        assertEquals(FloorChange.STAIRS_DOWN, GroundProfile.analyse(rows)!!.change)
    }

    @Test fun topOfAStaircaseIsStairsDown() {
        // Only edges show: noisy, but deep and getting lower.
        val rows = listOf(
            GroundProfile.Row(1f, 0f), GroundProfile.Row(1.6f, -0.18f), GroundProfile.Row(1.9f, -0.12f),
            GroundProfile.Row(2.3f, -0.4f), GroundProfile.Row(2.7f, -0.5f),
        )
        assertEquals(FloorChange.STAIRS_DOWN, GroundProfile.analyse(rows)!!.change)
    }

    @Test fun aKerbDownIsNotStairs() {
        val rows = listOf(GroundProfile.Row(1f, 0f), GroundProfile.Row(1.6f, -0.15f), GroundProfile.Row(1.9f, -0.15f), GroundProfile.Row(2.2f, -0.15f))
        assertEquals(FloorChange.DROP, GroundProfile.analyse(rows)!!.change)
    }

    @Test fun oneNoisyLowRowIsNotADrop() {
        val rows = listOf(GroundProfile.Row(1f, 0f), GroundProfile.Row(1.5f, -0.3f), GroundProfile.Row(1.8f, 0f), GroundProfile.Row(2.1f, 0f))
        assertNull(GroundProfile.analyse(rows))
    }

    @Test fun aWallInFrontDoesNotFoolTheCameraHeight() {
        // Floor, then a wall 1.1 m ahead: wall points are less far below the camera than the floor.
        val depth = scene(-0.5f, camH = 1.4f) { f -> if (f > 1.1f) 3f else 0f }
        assertEquals(1.4f, GridGeometry.floorHeight(depth, w, h, focal, -0.5f)!!, 0.08f)
    }

    @Test fun aWallCloseAheadIsStillAWallAndTheFloorIsNotADrop() {
        val depth = scene(-0.5f, camH = 1.4f) { f -> if (f > 1.1f) 3f else 0f }
        val g = GridGeometry(focal, -0.5f, h, GridGeometry.floorHeight(depth, w, h, focal, -0.5f)!!)
        assertTrue(DepthObstacles.read(depth, w, h, g).first { it.zone == Zone.AHEAD }.blocked)
        val floor = GroundProfile.analyse(GroundProfile.rows(depth, w, h, g))
        assertTrue(floor == null || floor.change != FloorChange.DROP && floor.change != FloorChange.STAIRS_DOWN)
    }

    @Test fun cameraHeightIsSmoothedAndIgnoresNonsense() {
        val c = CameraHeight()
        assertEquals(1.4f, c.update(1.4f), 0f)
        assertEquals(1.4f, c.update(0.5f), 0f)
        assertEquals(1.4f, c.update(null), 0f)
        assertEquals(1.42f, c.update(1.5f), 0.001f)
    }

    @Test fun aNoisyWallIsNotStairs() {
        // A wall 1.5 m ahead: depth noise of ±0.45 m spreads its rows over 1.05-1.95 m, independent of height.
        var seed = 7L
        fun noise(): Float {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 33) % 1000).toFloat() / 1000f * 0.9f - 0.45f
        }
        val rows = listOf(GroundProfile.Row(0.8f, 0f), GroundProfile.Row(1.0f, 0f)) +
            (0 until 30).map { i -> GroundProfile.Row(1.5f + noise(), 0.12f + i * 0.04f) }
        assertNull(GroundProfile.analyse(rows))
    }

    @Test fun realStairsAreNotVertical() {
        val rows = (0 until 12).map { i -> GroundProfile.Row(1f + i * 0.1f, if (i == 0) 0f else 0.17f * ((i + 2) / 3)) }
        assertFalse(GroundProfile.isVertical(rows))
        assertEquals(FloorChange.STAIRS, GroundProfile.analyse(rows)!!.change)
    }

    @Test fun aWallSceneApproachedCloseIsNeverStairs() {
        for (wallAt in listOf(0.9f, 1.2f, 1.6f, 2.0f)) {
            val depth = scene(-0.45f, camH = 1.35f) { f -> if (f > wallAt) 3f else 0f }
            val g = GridGeometry(focal, -0.45f, h, 1.35f)
            val floor = GroundProfile.analyse(GroundProfile.rows(depth, w, h, g))
            assertTrue("wall at $wallAt gave $floor", floor == null || floor.change == FloorChange.STEP_UP)
        }
    }

    @Test fun anElevatorIsBoxedInAHallIsNot() {
        fun z(zone: Zone, d: Float?) = ZoneReading(zone, d != null, d, 1f)
        assertTrue(DepthObstacles.boxedIn(listOf(z(Zone.LEFT, 0.6f), z(Zone.AHEAD, 1.0f), z(Zone.RIGHT, 0.7f))))
        assertFalse(DepthObstacles.boxedIn(listOf(z(Zone.LEFT, 0.6f), z(Zone.AHEAD, null), z(Zone.RIGHT, 0.7f))))
        assertFalse(DepthObstacles.boxedIn(listOf(z(Zone.LEFT, 0.6f), z(Zone.AHEAD, 2.5f), z(Zone.RIGHT, 0.7f))))
        // a wall that could not be measured is not proof of a box
        assertFalse(DepthObstacles.boxedIn(listOf(z(Zone.LEFT, 0.6f), ZoneReading(Zone.AHEAD, true, 0.5f, 0f), z(Zone.RIGHT, 0.7f))))
    }

    @Test fun floorChangeNeedsTwoOfThreeFrames() {
        val c = FloorConfirmer()
        val stairs = FloorReading(FloorChange.STAIRS, 2f)
        assertNull(c.update(stairs))
        assertNull(c.update(null))
        assertEquals(stairs, c.update(stairs))
        assertEquals(stairs, c.update(stairs))
        assertEquals(stairs, c.update(null))
        assertNull(c.update(null))
    }

    @Test fun aFarChangeThatKeepsItsDistanceWhileWalkingIsNeverReported() {
        // The corridor from the logs: "stairs going down" stayed 2.7 m ahead however far the user walked.
        val c = FloorConfirmer()
        repeat(40) { assertNull(c.update(FloorReading(FloorChange.STAIRS_DOWN, 2.7f), advancedM = 0.15f)) }
    }

    @Test fun aFarChangeThatComesCloserIsReportedBeforeItIsNear() {
        val c = FloorConfirmer()
        var walked = 0f
        var reportedAt: Float? = null
        while (reportedAt == null && walked < 3f) {
            val d = 3.8f - walked
            reportedAt = c.update(FloorReading(FloorChange.STAIRS_DOWN, d), advancedM = 0.15f)?.distanceM
            walked += 0.15f
        }
        assertTrue("reported at $reportedAt", reportedAt != null && reportedAt > FloorConfirmer.TRUST_M)
    }

    @Test fun standingStillAFarChangeWaitsANearOneDoesNot() {
        val far = FloorConfirmer()
        repeat(10) { assertNull(far.update(FloorReading(FloorChange.DROP, 3f))) }
        val near = FloorConfirmer()
        near.update(FloorReading(FloorChange.DROP, 1.5f))
        assertEquals(1.5f, near.update(FloorReading(FloorChange.DROP, 1.5f))!!.distanceM)
    }

    @Test fun heightIsNotMeasuredUntilTheFloorIsSeen() {
        val c = CameraHeight()
        assertFalse(c.measured)
        c.update(null)
        c.update(2.4f)
        assertFalse(c.measured)
        c.update(1.4f)
        assertTrue(c.measured)
    }

    // ---- floor changes against the floor in view (profiles from the device logs) -------------------
    /** "1.25:+2 1.50:+22" as the log prints it: metres ahead, centimetres above the assumed floor. */
    private fun logged(profile: String): List<GroundProfile.Row> = profile.trim().split(' ').flatMap { bin ->
        val (at, cm) = bin.split(':')
        (0 until 4).map { GroundProfile.Row(at.toFloat() + 0.03f + it * 0.06f, cm.toFloat() / 100f) }
    }

    @Test fun flatFloorsFromTheLogsAreNoFloorChange() {
        val flat = listOf(
            // a corridor; the image's bottom rows read deeper than the rest
            "1.25:+2 1.50:+22 1.75:+22 2.00:+18 2.25:+19 2.50:+17 2.75:+15 3.00:+12 3.25:+10 3.50:+9 3.75:+6",
            "1.25:+9 1.50:+18 1.75:+19 2.00:+20 2.25:+20 2.50:+19 2.75:+17 3.00:+21 3.25:+21 3.50:+42 3.75:+71",
            // the phone's height was learnt next to a wall: the whole floor reads 30 cm low
            "1.75:-41 2.00:-36 2.25:-31 2.50:-29 2.75:-29 3.00:-29 3.25:-25 3.50:-11 3.75:+25",
            "2.75:-94 3.00:-82 3.25:-80 3.50:-81 3.75:-83",
            // depth wobble
            "1.25:+10 1.50:+15 1.75:+20 2.00:+26 2.25:+27 2.50:+16 2.75:+10 3.00:+13 3.25:+22 3.50:+69",
            "1.75:-11 2.00:+2 2.25:+5 2.50:+3 2.75:-2 3.00:-19 3.25:-25 3.50:-28 3.75:-34",
            // a wall or a door ahead
            "1.75:-8 2.00:+5 2.25:+81 2.50:+73 2.75:+39 3.00:+42 3.25:+44",
            "1.50:-13 1.75:-5 2.00:-9 2.25:+4 2.50:+48 2.75:+101",
            // the phone pointed steeply down
            "0.50:-17 0.75:-1 1.00:+18 1.25:+36 1.50:+50 1.75:+53 2.00:+52",
            "0.50:+27 0.75:+40 1.00:+55 1.25:+65",
            "0.50:+9 0.75:+17 1.00:+27",
            "0.50:-2 0.75:+7 1.00:+11",
        )
        for (profile in flat) assertNull(profile, GroundProfile.read(logged(profile)))
    }

    @Test fun theLowerPathFromTheLogsIsGoingDownNotStairs() {
        // The user stood above a path that continues about 10 cm lower.
        val r = GroundProfile.read(logged("1.25:+19 1.50:+28 1.75:+15 2.00:-7 2.25:-6 2.50:-2 2.75:+4 3.00:+55"))!!
        assertEquals(FloorChange.DROP, r.change)
        assertEquals(1.75f, r.distanceM, 0.3f)
    }

    private fun readScene(pitch: Float, camH: Float, assumedH: Float, ground: (Float) -> Float): FloorReading? =
        GroundProfile.read(GroundProfile.rows(scene(pitch, camH, ground), w, h, GridGeometry(focal, pitch, h, assumedH)))

    @Test fun realStepsAreFoundWhateverHeightIsAssumed() {
        for (assumed in listOf(1.0f, 1.3f, 1.6f)) {
            assertNull("flat, assumed $assumed", readScene(-0.3f, 1.3f, assumed) { 0f })
            assertEquals("kerb, assumed $assumed", FloorChange.STEP_UP, readScene(-0.3f, 1.3f, assumed) { f -> if (f > 2.2f) 0.15f else 0f }?.change)
            assertEquals("drop, assumed $assumed", FloorChange.DROP, readScene(-0.3f, 1.3f, assumed) { f -> if (f > 2.2f) -0.15f else 0f }?.change)
            val stairs = readScene(-0.3f, 1.3f, assumed) { f -> if (f > 2.2f) 0.17f * (1 + ((f - 2.2f) / 0.28f).toInt()) else 0f }
            assertEquals("stairs, assumed $assumed", FloorChange.STAIRS, stairs?.change)
        }
    }

    @Test fun aWallSceneIsNoFloorChange() {
        for (wallAt in listOf(1.2f, 1.6f, 2.0f, 2.6f)) {
            val r = readScene(-0.3f, 1.3f, 1.3f) { f -> if (f > wallAt) 3f else 0f }
            assertNull("wall at $wallAt gave $r", r)
        }
    }

    // ---- a close wall and garbage depth ------------------------------------------------------------
    @Test fun aCloseWallStaysUntilTheCameraMovesAway() {
        // The logs: touching a wall, depth came back as "7 m, clear" and walk mode said "Nothing close ahead".
        val hold = CloseHold()
        val atWall = Standpoint(0f, 0f, 0f)
        assertEquals(0.4f, hold.update(0, ahead(true, 0.4f), here = atWall))
        assertEquals(0.4f, hold.update(1_000, ahead(false, null), here = Standpoint(0.05f, 0f, 0.1f)))
        assertEquals(0.4f, hold.update(30_000, ahead(false, null), here = atWall))
        assertEquals(0.4f, hold.update(31_000, ahead(true, 2.8f), here = atWall))
        // turned away, or stepped back: now the clear way is believed
        assertNull(hold.update(32_000, ahead(false, null), here = Standpoint(0f, 0f, 0.8f)))
        hold.update(40_000, ahead(true, 0.4f), here = atWall)
        assertNull(hold.update(41_000, ahead(false, null), here = Standpoint(0f, -0.6f, 0f)))
    }

    @Test fun dropIsFound() {
        val r = GroundProfile.analyse(
            listOf(GroundProfile.Row(1f, 0f), GroundProfile.Row(1.5f, -0.2f), GroundProfile.Row(1.7f, -0.2f), GroundProfile.Row(1.9f, -0.2f)),
        )!!
        assertEquals(FloorChange.DROP, r.change)
        assertEquals(1.5f, r.distanceM)
    }

    @Test fun stairsClimbWithDistance() {
        val rows = (0 until 12).map { i -> GroundProfile.Row(1f + i * 0.1f, if (i == 0) 0f else 0.17f * ((i + 2) / 3)) }
        assertEquals(FloorChange.STAIRS, GroundProfile.analyse(rows)!!.change)
    }

    @Test fun aWallIsNotAStep() {
        val rows = listOf(GroundProfile.Row(1f, 0f), GroundProfile.Row(2f, 0.15f), GroundProfile.Row(2.1f, 1.2f))
        assertNull(GroundProfile.analyse(rows))
    }

    @Test fun noisyWallIsNotStairs() {
        val rows = listOf(
            GroundProfile.Row(1f, 0f), GroundProfile.Row(2f, 0.2f), GroundProfile.Row(2.1f, 0.5f),
            GroundProfile.Row(2.2f, 0.3f), GroundProfile.Row(2.3f, 0.6f),
        )
        // Low and high parts stand at the same distance: a wall, so no floor change at all.
        assertNull(GroundProfile.analyse(rows))
    }

    @Test fun numbersFromTheGuide() {
        assertEquals(0.09f, GroundProfile.FLOOR_M)
        assertEquals(0.8f, GroundProfile.STEP_MAX_M)
        assertEquals(0.6f, GroundProfile.NEAR_M)
        assertEquals(4f, GroundProfile.FAR_M)
        assertEquals(3, GroundProfile.MIN_SAMPLES)
    }
}
