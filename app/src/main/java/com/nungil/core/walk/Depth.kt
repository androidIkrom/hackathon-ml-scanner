package com.nungil.core.walk

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

enum class Zone { LEFT, AHEAD, RIGHT }

/**
 * Per-row geometry of the screen-shaped depth grid. ARCore depth is the distance along the camera's
 * optical axis, so a row's ray is longer by 1 / cos(its angle from the axis). [alpha] is the ray's
 * angle below the horizon; [cameraHeightM] is estimated from the floor at the user's feet.
 */
class GridGeometry(val focalPx: Float, val pitchRad: Float, height: Int, val cameraHeightM: Float) {
    val alpha = FloatArray(height)
    private val rayScale = FloatArray(height)

    init {
        for (r in 0 until height) {
            val beta = atan(((r + 0.5f) - height / 2f) / focalPx)
            alpha[r] = beta - pitchRad
            rayScale[r] = 1f / cos(beta)
        }
    }

    /** Horizontal distance to the point seen in row [row] at axis depth [z]. */
    fun forward(row: Int, z: Float): Float = z * rayScale[row] * cos(alpha[row])

    /** Height of that point above the floor. */
    fun heightAt(row: Int, z: Float): Float = cameraHeightM - drop(row, z)

    /** How far below the camera the point is. */
    fun drop(row: Int, z: Float): Float = z * rayScale[row] * sin(alpha[row])

    companion object {
        const val FLOOR_NEAR_M = 0.6f
        const val FLOOR_FAR_M = 1.3f
        const val FLOOR_REACH_M = 3f
        const val FLOOR_SPAN_M = 0.7f
        const val MIN_FLOOR_SAMPLES = 20
        const val MIN_HEIGHT_M = 0.8f
        const val MAX_HEIGHT_M = 1.9f
        private const val MIN_ALPHA_RAD = 0.1f

        /** Share of samples below the floor estimate: the floor is the lowest thing near the feet. */
        const val FLOOR_PERCENTILE = 0.8f

        /**
         * This frame's guess of the phone's height above the floor, from points 0.6-1.3 m ahead in the
         * middle third. A phone held almost upright first sees the floor farther away than that (the logs
         * showed the height never measured in a corridor), so the window then starts where the floor
         * starts. A wall or a leg there sits ABOVE the floor (it is less far below the camera), so the
         * floor is a high percentile of "how far below the camera", never the median. Null when there
         * are too few points.
         */
        fun floorHeight(depthM: FloatArray, width: Int, height: Int, focalPx: Float, pitchRad: Float): Float? {
            val probe = GridGeometry(focalPx, pitchRad, height, 0f)
            val forwards = ArrayList<Float>()
            val drops = ArrayList<Float>()
            for (r in 0 until height) {
                if (probe.alpha[r] < MIN_ALPHA_RAD) continue
                for (x in width / 3 until width * 2 / 3) {
                    val z = depthM[r * width + x]
                    if (z.isNaN() || z < DepthObstacles.MIN_M || z > DepthObstacles.MAX_M) continue
                    val forward = probe.forward(r, z)
                    if (forward in FLOOR_NEAR_M..FLOOR_REACH_M) {
                        forwards.add(forward)
                        drops.add(probe.drop(r, z))
                    }
                }
            }
            if (forwards.size < MIN_FLOOR_SAMPLES) return null
            val start = forwards.sorted()[forwards.size / 20]
            val end = maxOf(FLOOR_FAR_M, start + FLOOR_SPAN_M)
            val samples = drops.filterIndexed { i, _ -> forwards[i] <= end }.sorted()
            if (samples.size < MIN_FLOOR_SAMPLES) return null
            return samples[((samples.size - 1) * FLOOR_PERCENTILE).toInt()]
        }

        /** One-frame geometry (tests, and the first frame): the floor height, or the default. */
        fun estimate(depthM: FloatArray, width: Int, height: Int, focalPx: Float, pitchRad: Float): GridGeometry {
            val h = floorHeight(depthM, width, height, focalPx, pitchRad)?.takeIf { it in MIN_HEIGHT_M..MAX_HEIGHT_M }
                ?: GroundProfile.DEFAULT_CAMERA_HEIGHT_M
            return GridGeometry(focalPx, pitchRad, height, h)
        }
    }
}

/**
 * The phone's height above the floor, smoothed over frames. People hold the phone at a steady height,
 * so implausible single-frame guesses (a wall right in front, no floor in view) are ignored and the
 * last good value is kept.
 */
class CameraHeight {
    var value = GroundProfile.DEFAULT_CAMERA_HEIGHT_M
        private set

    /** False while [value] is still the default: steps and drops cannot be judged against a guess. */
    var measured = false
        private set

    fun update(frameGuess: Float?): Float {
        val g = frameGuess ?: return value
        if (g !in ACCEPT_MIN_M..ACCEPT_MAX_M) return value
        value = if (measured) value + SMOOTHING * (g - value) else g
        measured = true
        return value
    }

    companion object {
        const val ACCEPT_MIN_M = 0.9f
        const val ACCEPT_MAX_M = 1.8f
        const val SMOOTHING = 0.2f
    }
}

/**
 * One third of the screen at chest height. [distanceM] is the lower quartile of the close readings
 * when [blocked]. [validShare] is how much of the band had a usable reading.
 */
data class ZoneReading(val zone: Zone, val blocked: Boolean, val distanceM: Float?, val validShare: Float) {
    /** Depth-from-motion could not measure here (a close, plain wall looks like this). */
    val unknown: Boolean get() = validShare < DepthObstacles.UNKNOWN_SHARE
}

/**
 * Walls and doors from a screen-shaped depth grid in metres (row-major, 0 or NaN = no reading):
 * the band 30–62 % down the image, a third blocked when 40 % of its readings are within 3 m
 * (build guide §7). Readings beyond 8 m count as far.
 */
object DepthObstacles {
    /**
     * Walls within [BOXED_M] ahead, left and right: an elevator or a closet. The floor there is not
     * seen well enough to judge, and the logs showed "stairs going up" inside an elevator.
     */
    fun boxedIn(zones: List<ZoneReading>): Boolean =
        zones.size == 3 && zones.all { it.blocked && !it.unknown && (it.distanceM ?: Float.MAX_VALUE) <= BOXED_M }

    const val BOXED_M = 1.2f

    const val BAND_TOP = 0.30f
    const val BAND_BOTTOM = 0.62f
    const val MIN_M = 0.3f
    const val MAX_M = 8f
    const val NEAR_M = 3f
    const val BLOCKED_SHARE = 0.4f
    const val UNKNOWN_SHARE = 0.2f

    /** With geometry, points lower than this are floor, not obstacles (a phone tilted down sees the floor in the band). */
    const val OBSTACLE_MIN_HEIGHT_M = 0.3f

    /**
     * Readings closer than MIN_M count as "very close" at MIN_M: right in front of a wall depth gets
     * small, not invalid. With [geometry], floor points are ignored and distances are horizontal.
     */
    fun read(depthM: FloatArray, width: Int, height: Int, geometry: GridGeometry? = null): List<ZoneReading> {
        require(width > 0 && height > 0 && depthM.size == width * height) { "grid is $width x $height but has ${depthM.size} values" }
        val top = (height * BAND_TOP).toInt()
        val bottom = ceil(height * BAND_BOTTOM).toInt().coerceAtMost(height)
        return Zone.entries.mapIndexed { i, zone ->
            val x0 = width * i / 3
            val x1 = width * (i + 1) / 3
            var total = 0
            var valid = 0
            val near = ArrayList<Float>()
            for (y in top until bottom) for (x in x0 until x1) {
                total++
                val raw = depthM[y * width + x]
                if (raw.isNaN() || raw <= 0f) continue
                valid++
                // Farther than the range is a reading too: a long corridor is clear, not unknown.
                if (raw > MAX_M) continue
                val z = raw.coerceAtLeast(MIN_M)
                val distance = if (geometry != null) {
                    if (geometry.heightAt(y, z) < OBSTACLE_MIN_HEIGHT_M) continue
                    geometry.forward(y, z).coerceAtLeast(MIN_M)
                } else {
                    z
                }
                if (distance < NEAR_M) near.add(distance)
            }
            val blocked = valid > 0 && near.size >= valid * BLOCKED_SHARE
            val distance = if (blocked) near.sorted()[(near.size - 1) / 4] else null
            ZoneReading(zone, blocked, distance, if (total == 0) 0f else valid.toFloat() / total)
        }
    }
}

/** Where the camera is and where it looks, on the ground plane (ARCore's pose): it looks along (sin yaw, cos yaw). */
data class Standpoint(val x: Float, val z: Float, val yawRad: Float) {
    /**
     * True once the camera has turned away from where it looked at [other], or stepped back from it.
     * Walking on towards what was ahead there, or along it, is not "away".
     */
    fun awayFrom(other: Standpoint): Boolean {
        val turned = abs(atan2(sin(yawRad - other.yawRad), cos(yawRad - other.yawRad)))
        val back = -((x - other.x) * sin(other.yawRad) + (z - other.z) * cos(other.yawRad))
        return turned >= TURNED_RAD || back >= BACK_M
    }

    companion object {
        const val BACK_M = 0.4f
        const val TURNED_RAD = 0.5f
    }
}

/**
 * A close wall stays close when depth disappears. Right in front of a plain wall depth-from-motion
 * has nothing to measure; reading that as "clear" would silence the warning exactly when it matters.
 * The last reading under [HOLD_M] is held for [HOLD_MS] while the way ahead is unknown (a recorded walk
 * lost a plain wall at 1.3 m and said nothing down to touching it), until a measured clear way cancels
 * it. Right in front of a wall depth also came back as "7 m, clear" (the logs: walk mode then said
 * "Nothing close ahead"), so under [CLOSE_M] a clear way is believed only once the camera has turned
 * away or stepped back from where the wall was measured. Turning away also ends the hold at once.
 */
class CloseHold {
    private var heldM: Float? = null
    private var heldAtMs = 0L
    private var heldFrom: Standpoint? = null
    private var heldHeadingDeg: Float? = null

    /**
     * Distance to warn about ahead, or null. [walkedM] is how far the user walked since the last call
     * (from the step detector): while depth is gone, the held wall gets that much closer. [here] is
     * null when ARCore does not know where the camera is; [headingDeg] is the compass, which still
     * knows about turns then.
     */
    fun update(nowMs: Long, ahead: ZoneReading, walkedM: Float = 0f, here: Standpoint? = null, headingDeg: Float? = null): Float? {
        val held = heldM
        val from = heldFrom
        val was = heldHeadingDeg
        val away = (from != null && here != null && here.awayFrom(from)) ||
            (was != null && headingDeg != null && abs(((headingDeg - was + 540f) % 360f) - 180f) >= TURNED_DEG)
        val knowsWhere = (from != null && here != null) || (was != null && headingDeg != null)
        val measuredClose = ahead.blocked && !ahead.unknown && (ahead.distanceM ?: Float.MAX_VALUE) < CLOSE_M
        if (!ahead.unknown && !measuredClose && held != null && held < CLOSE_M && knowsWhere && !away) {
            heldAtMs = nowMs
            return held
        }
        if (ahead.unknown) {
            val held = heldM ?: return null
            // Turned or stepped away: whatever is ahead now, it is not that wall.
            if (nowMs - heldAtMs > HOLD_MS || away) {
                heldM = null
                return null
            }
            val closer = (held - walkedM).coerceAtLeast(DepthObstacles.MIN_M)
            heldM = closer
            return closer
        }
        if (ahead.blocked) {
            val d = ahead.distanceM
            if (d != null && d < HOLD_M) {
                heldM = d
                heldAtMs = nowMs
                heldFrom = here
                heldHeadingDeg = headingDeg
            } else {
                heldM = null
            }
            return d
        }
        heldM = null
        return null
    }

    fun clear() {
        heldM = null
    }

    companion object {
        const val CLOSE_M = 1.2f
        const val HOLD_M = 2f
        const val TURNED_DEG = 30f
        const val HOLD_MS = 10_000L
    }
}

/**
 * How fast the user is closing in on what is ahead, from the last measured distances. Used for up to
 * [MAX_EXTRAPOLATE_MS] after depth is lost, because the step detector can lag by a step or two.
 */
class ApproachSpeed {
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var lastMeasuredAt = 0L

    fun measured(nowMs: Long, distanceM: Float) {
        samples.addLast(nowMs to distanceM)
        while (samples.size > WINDOW) samples.removeFirst()
        lastMeasuredAt = nowMs
    }

    fun clear() = samples.clear()

    /** Metres per second towards the obstacle; 0 when not approaching or not known. */
    fun speed(): Float {
        if (samples.size < 2) return 0f
        val (t0, d0) = samples.first()
        val (t1, d1) = samples.last()
        val dt = (t1 - t0) / 1000f
        if (dt < MIN_SPAN_S) return 0f
        return ((d0 - d1) / dt).coerceIn(0f, MAX_SPEED_MPS)
    }

    /** Distance covered between [fromMs] and [toMs] at the last speed, only within the extrapolation window. */
    fun walked(fromMs: Long, toMs: Long): Float {
        val end = minOf(toMs, lastMeasuredAt + MAX_EXTRAPOLATE_MS)
        if (end <= fromMs) return 0f
        return speed() * (end - fromMs) / 1000f
    }

    companion object {
        const val WINDOW = 6
        const val MIN_SPAN_S = 0.4f
        const val MAX_SPEED_MPS = 1.5f
        const val MAX_EXTRAPOLATE_MS = 2_000L
    }
}

enum class FloorChange { STEP_UP, DROP, STAIRS, STAIRS_DOWN }

data class FloorReading(val change: FloorChange, val distanceM: Float)

/**
 * Steps, kerbs, drops and stairs from depth geometry; no model can detect them (build guide §7).
 * For each image row: ray angle below the horizon, then height above the floor and forward distance
 * of every sample in the middle third, 0.6–4 m ahead.
 */
object GroundProfile {
    const val FLOOR_M = 0.09f
    const val STEP_MAX_M = 0.8f
    const val NEAR_M = 0.6f
    const val FAR_M = 4f
    const val MIN_SAMPLES = 3
    const val WALL_TALL_M = 0.8f
    const val WALL_NEAR_M = 0.4f
    const val BIN_M = 0.1f
    const val STAIR_RISE_M = 0.05f
    const val MIN_RISES = 2
    const val NOISE_M = 0.02f
    /** Depth on a plain floor wobbles by 10-20 cm: a 10 cm lower path read as 30 cm, which is not stairs yet. */
    const val DEEP_DROP_M = 0.4f
    const val MIN_DROP_ROWS = 3
    const val LEVEL_BIN_M = 0.25f
    const val MIN_BEFORE_BINS = 2
    const val MIN_AFTER_BINS = 2
    const val SUSTAIN_M = 0.75f
    const val MAX_CLIMB_M = 0.22f

    /** Stairs climb about 0.6 m per metre; between 0.35 m and 0.55 m high they move at least this far away. */
    const val LOW_PART_M = 0.35f
    const val HIGH_PART_M = 0.55f
    const val VERTICAL_RUN_M = 0.5f
    const val DEFAULT_CAMERA_HEIGHT_M = 1.25f
    private const val MIN_BELOW_HORIZON_RAD = 0.05f

    data class Row(val forwardM: Float, val heightM: Float)

    /** Rows of the middle third, 0.6-4 m ahead, as (forward, height above floor), nearest first. */
    fun rows(depthM: FloatArray, width: Int, height: Int, geometry: GridGeometry): List<Row> {
        require(depthM.size == width * height)
        val out = ArrayList<Row>()
        val x0 = width / 3
        val x1 = width * 2 / 3
        for (r in 0 until height) {
            if (geometry.alpha[r] < MIN_BELOW_HORIZON_RAD) continue
            val heights = ArrayList<Float>()
            val forwards = ArrayList<Float>()
            for (x in x0 until x1) {
                val d = depthM[r * width + x]
                if (d.isNaN() || d < DepthObstacles.MIN_M || d > DepthObstacles.MAX_M) continue
                val forward = geometry.forward(r, d)
                if (forward < NEAR_M || forward > FAR_M) continue
                heights.add(geometry.heightAt(r, d))
                forwards.add(forward)
            }
            if (heights.size >= MIN_SAMPLES) out.add(Row(median(forwards), median(heights)))
        }
        return out.sortedBy { it.forwardM }
    }

    /**
     * The floor change ahead, judged against the floor nearest to the user in this frame, not against the
     * phone's height: that height drifts (it follows a lower path, or a wall), and the logs showed the
     * flat floor the user stood on announced as a step up. Quarter-metre medians, nearest first:
     * - the floor "before" is where they stay level, and it has to be at least half a metre long;
     * - what comes after has to stay higher (or lower) for [SUSTAIN_M], on at least half a metre of floor,
     *   and may not climb more than [MAX_CLIMB_M] per quarter metre.
     * Anything less is depth wobble, a wall, or the phone pointing at the ground: no change is reported.
     */
    fun read(rows: List<Row>): FloorReading? {
        val steps = rows.groupBy { floor(it.forwardM / LEVEL_BIN_M).toInt() }.toSortedMap().entries.toList()
        val levels = steps.map { (_, bin) -> median(bin.map { it.heightM }) }
        var before = 1
        while (before < levels.size && abs(levels[before] - median(levels.take(before))) <= FLOOR_M) before++
        if (before < MIN_BEFORE_BINS || before == levels.size) return null
        val level = median(levels.take(before))
        val edgeM = steps[before].key * LEVEL_BIN_M
        val reading = analyse(rows.map { Row(it.forwardM, if (it.forwardM < edgeM) 0f else it.heightM - level) }) ?: return null
        val up = reading.change == FloorChange.STEP_UP || reading.change == FloorChange.STAIRS
        val from = floor(reading.distanceM / LEVEL_BIN_M).toInt()
        val after = steps.indices.filter { steps[it].key >= from && steps[it].key * LEVEL_BIN_M < reading.distanceM + SUSTAIN_M }
            .map { levels[it] - level }
        if (after.size < MIN_AFTER_BINS) return null
        if (after.any { if (up) it <= FLOOR_M else it >= -FLOOR_M }) return null
        // Nothing to walk on climbs steeper than stairs: that is the foot of a wall or a door.
        if (up && after.zipWithNext().any { (a, b) -> b - a > MAX_CLIMB_M }) return null
        return reading
    }

    fun analyse(rows: List<Row>): FloorReading? {
        val sorted = rows.sortedBy { it.forwardM }
        val first = sorted.firstOrNull { abs(it.heightM) > FLOOR_M } ?: return null
        if (first.heightM < -FLOOR_M) {
            if (sorted.count { it.forwardM >= first.forwardM && it.heightM < -FLOOR_M } < MIN_DROP_ROWS) return null
            // Stairs down: the floor keeps getting lower, in steps, with distance.
            val down = bins(sorted.filter { it.forwardM >= first.forwardM && it.heightM < -FLOOR_M }, first.forwardM) { b -> b.minOf { it.heightM } }
            val descends = down.zipWithNext().all { (a, b) -> b <= a + NOISE_M }
            val drops = down.zipWithNext().count { (a, b) -> a - b >= STAIR_RISE_M }
            // From the top of a staircase only the edges of the lower steps show; a drop deeper than a kerb
            // that still goes lower further on is stairs too.
            val deepAndLower = down.size >= 2 && down.minOrNull()!! <= -DEEP_DROP_M && down.last() < down.first() - STAIR_RISE_M
            val change = if ((descends && drops >= MIN_RISES) || deepAndLower) FloorChange.STAIRS_DOWN else FloorChange.DROP
            return FloorReading(change, first.forwardM)
        }
        if (first.heightM > STEP_MAX_M) return null
        // A rise with something tall right next to it is the bottom of a wall or furniture, not a step.
        if (sorted.any { it.heightM > WALL_TALL_M && abs(it.forwardM - first.forwardM) <= WALL_NEAR_M }) return null
        // Walls are vertical, stairs climb: on stairs the higher surfaces are clearly farther away, on a wall
        // they are at the same distance. Medians of the low and the high part cancel the depth noise that
        // spreads a wall over several distances (the "wall that sounds like stairs").
        if (isVertical(sorted.filter { it.forwardM >= first.forwardM })) return null
        val bins = bins(sorted.filter { it.forwardM >= first.forwardM && it.heightM > FLOOR_M }, first.forwardM) { b -> b.maxOf { it.heightM } }
        val climbs = bins.zipWithNext().all { (a, b) -> b >= a - NOISE_M }
        val rises = bins.zipWithNext().count { (a, b) -> b - a >= STAIR_RISE_M }
        val change = if (climbs && rises >= MIN_RISES) FloorChange.STAIRS else FloorChange.STEP_UP
        return FloorReading(change, first.forwardM)
    }

    /** True when the low part and the high part of what rises ahead stand at nearly the same distance. */
    fun isVertical(rising: List<Row>): Boolean {
        val low = rising.filter { it.heightM > FLOOR_M && it.heightM <= LOW_PART_M }.map { it.forwardM }
        val high = rising.filter { it.heightM >= HIGH_PART_M }.map { it.forwardM }
        if (low.isEmpty() || high.isEmpty()) return false
        return median(high) - median(low) < VERTICAL_RUN_M
    }

    /** Hand-wide distance bins from [start], each reduced to one height. */
    private fun bins(rows: List<Row>, start: Float, pick: (List<Row>) -> Float): List<Float> =
        rows.groupBy { floor((it.forwardM - start) / BIN_M).toInt() }.toSortedMap().values.map(pick)

    private fun median(v: List<Float>): Float = v.sorted()[v.size / 2]
}

/**
 * A floor change is reported once it is seen in [need] of the last [window] frames (depth noise flickers)
 * and it stays where it is while the user walks. What depth invents travels along with the camera: the
 * logs showed "stairs going down" 2.7 m ahead for five seconds of walking down a corridor. A real step
 * comes closer by what was walked. Within [TRUST_M] there is no time left to wait for that.
 */
class FloorConfirmer(private val window: Int = WINDOW, private val need: Int = NEED) {
    /** [placeM] is how far the user had walked when the change was seen, plus its distance. */
    private class Sighting(val walkedM: Float, val placeM: Float)

    private val history = ArrayDeque<FloorReading?>()
    private val sightings = HashMap<FloorChange, ArrayDeque<Sighting>>()
    private var walkedM = 0f

    /** [advancedM]: how far the camera moved towards where it looks since the last call. */
    fun update(reading: FloorReading?, advancedM: Float = 0f): FloorReading? {
        walkedM += advancedM
        history.addLast(reading)
        while (history.size > window) history.removeFirst()
        if (reading != null) remember(reading)
        val counts = history.filterNotNull().groupingBy { it.change }.eachCount()
        val change = counts.entries.filter { it.value >= need }.maxByOrNull { it.value }?.key ?: return null
        val latest = history.lastOrNull { it?.change == change } ?: return null
        return latest.takeIf { it.distanceM <= TRUST_M || staysPut(change) }
    }

    private fun remember(reading: FloorReading) {
        val seen = sightings.getOrPut(reading.change) { ArrayDeque() }
        seen.addLast(Sighting(walkedM, walkedM + reading.distanceM))
        while (seen.size > MAX_SIGHTINGS || abs(walkedM - seen.first().walkedM) > KEEP_M) seen.removeFirst()
    }

    /**
     * How far the place moved per metre walked, over at least [MIN_TRAVEL_M] of walking: 0 for a real
     * step, 1 for something that keeps its distance from the camera.
     */
    private fun staysPut(change: FloorChange): Boolean {
        val seen = sightings[change] ?: return false
        if (seen.last().walkedM - seen.first().walkedM < MIN_TRAVEL_M) return false
        val meanWalked = seen.sumOf { it.walkedM.toDouble() } / seen.size
        val meanPlace = seen.sumOf { it.placeM.toDouble() } / seen.size
        val spread = seen.sumOf { (it.walkedM - meanWalked) * (it.walkedM - meanWalked) }
        if (spread <= 0.0) return false
        val follow = seen.sumOf { (it.walkedM - meanWalked) * (it.placeM - meanPlace) } / spread
        return abs(follow) <= MAX_FOLLOW
    }

    companion object {
        const val WINDOW = 3
        const val NEED = 2
        const val TRUST_M = 2f
        const val MIN_TRAVEL_M = 1f
        const val MAX_FOLLOW = 0.3
        const val KEEP_M = 2.5f
        const val MAX_SIGHTINGS = 64
    }
}
