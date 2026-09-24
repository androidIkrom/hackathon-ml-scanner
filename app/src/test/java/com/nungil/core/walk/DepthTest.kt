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
