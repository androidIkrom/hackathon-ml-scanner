package com.nungil.core.walk

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WalkPhrasesTest {
    private val step = 0.7f

    @Test fun distances() {
        assertEquals("3 steps", WalkPhrases.distance(2.1f, step, Lang.EN))
        assertEquals("세 걸음", WalkPhrases.distance(2.1f, step, Lang.KO))
        assertEquals("2 metres", WalkPhrases.distance(2.2f, null, Lang.EN))
        assertEquals("1 metre", WalkPhrases.distance(1.2f, null, Lang.EN))
        assertEquals("2미터", WalkPhrases.distance(2.2f, null, Lang.KO))
        assertEquals("1 step", WalkPhrases.distance(0.6f, step, Lang.EN))
        assertEquals("1 metre", WalkPhrases.distance(0.6f, null, Lang.EN))
        assertEquals("very close", WalkPhrases.distance(0.4f, step, Lang.EN))
        assertEquals("아주 가까워요", WalkPhrases.distance(0.4f, null, Lang.KO))
    }

    @Test fun distanceLevelsMatchWhatIsSaid() {
        assertEquals(3, WalkPhrases.distanceLevel(2.1f, step))
        assertEquals(2, WalkPhrases.distanceLevel(2.2f, null))
        assertEquals(1, WalkPhrases.distanceLevel(0.8f, step))
        assertEquals(0, WalkPhrases.distanceLevel(0.4f, step))
    }

    @Test fun walls() {
        assertEquals("Wall ahead, 2 metres.", WalkPhrases.wall(Zone.AHEAD, 2f, null, Lang.EN))
        assertEquals("앞에 벽이 있어요, 2미터.", WalkPhrases.wall(Zone.AHEAD, 2f, null, Lang.KO))
        assertEquals("Obstacle on your left, 3 steps.", WalkPhrases.wall(Zone.LEFT, 2.1f, step, Lang.EN))
        assertEquals("오른쪽에 장애물이 있어요, 세 걸음.", WalkPhrases.wall(Zone.RIGHT, 2.1f, step, Lang.KO))
    }

    @Test fun floors() {
        assertEquals("Stairs going up in 3 steps.", WalkPhrases.floor(FloorChange.STAIRS, 2.1f, step, Lang.EN))
        assertEquals("세 걸음 앞에 올라가는 계단이 있어요.", WalkPhrases.floor(FloorChange.STAIRS, 2.1f, step, Lang.KO))
        assertEquals("Stairs going down in 2 metres.", WalkPhrases.floor(FloorChange.STAIRS_DOWN, 2f, null, Lang.EN))
        assertEquals("2미터 앞에 내려가는 계단이 있어요.", WalkPhrases.floor(FloorChange.STAIRS_DOWN, 2f, null, Lang.KO))
        assertEquals("Step up in 2 metres.", WalkPhrases.floor(FloorChange.STEP_UP, 2f, null, Lang.EN))
        assertEquals("Going down in 5 steps.", WalkPhrases.floor(FloorChange.DROP, 3.5f, step, Lang.EN))
        assertEquals("Going down, very close.", WalkPhrases.floor(FloorChange.DROP, 0.4f, step, Lang.EN))
        assertEquals("바로 앞에 내려가는 곳이 있어요.", WalkPhrases.floor(FloorChange.DROP, 0.4f, null, Lang.KO))
    }

    @Test fun hazards() {
        assertEquals("Person ahead, 3 steps.", WalkPhrases.hazard("person", Zone.AHEAD, 2.1f, step, Lang.EN))
        assertEquals("앞에 사람이 있어요, 세 걸음.", WalkPhrases.hazard("person", Zone.AHEAD, 2.1f, step, Lang.KO))
        assertEquals("Car on your right.", WalkPhrases.hazard("car", Zone.RIGHT, null, step, Lang.EN))
        assertEquals("왼쪽에 자전거가 있어요.", WalkPhrases.hazard("bicycle", Zone.LEFT, null, step, Lang.KO))
    }

    @Test fun obstaclesGroundAndLights() {
        assertEquals("Door ahead.", WalkPhrases.obstacle("door", "문", Lang.EN))
        assertEquals("앞에 문이 있어요.", WalkPhrases.obstacle("door", "문", Lang.KO))
        assertEquals("발밑이 차도예요.", WalkPhrases.ground(GroundKind.ROAD, Lang.KO))
        assertEquals("Red light.", WalkPhrases.light(LightColor.RED, Lang.EN))
        assertEquals("초록불이에요.", WalkPhrases.light(LightColor.GREEN, Lang.KO))
    }

    @Test fun savedThingsSignsAndCodes() {
        assertEquals("앞에 내 가방이 있어요.", WalkPhrases.saved("내 가방", Zone.AHEAD, Lang.KO))
        assertEquals("Sign: EXIT.", WalkPhrases.sign(" EXIT ", Lang.EN))
        assertEquals("Code: " + "x".repeat(80) + ".", WalkPhrases.code("x".repeat(120), Lang.EN))
    }

    @Test fun beaconDistances() {
        assertEquals("Home, 300 metres, at 2 o'clock.", WalkPhrases.beacon("Home", 304.0, 2, Lang.EN))
        assertEquals("집까지 1.2킬로미터, 12시 방향이에요.", WalkPhrases.beacon("집", 1_234.0, 12, Lang.KO))
        assertEquals("5 metres", WalkPhrases.far(2.0, Lang.EN))
        assertEquals("45 metres", WalkPhrases.far(44.0, Lang.EN))
    }

    @Test fun friendlyAndPlacePhrases() {
        assertEquals("Nothing close ahead.", WalkPhrases.nothingAhead(Lang.EN))
        assertEquals("가까운 장애물은 없어요.", WalkPhrases.nothingAhead(Lang.KO))
        assertEquals("You are at home.", WalkPhrases.alreadyAt("home", Lang.EN))
        assertEquals("지금 집에 있어요.", WalkPhrases.alreadyAt("집", Lang.KO))
    }

    @Test fun koreanDirectionParticle() {
        assertEquals("집으로", WalkPhrases.euro("집"))
        assertEquals("학교로", WalkPhrases.euro("학교"))
        assertEquals("서울로", WalkPhrases.euro("서울"))
        assertEquals("이 장소를 집으로 저장했어요.", WalkPhrases.placeSaved("집", Lang.KO))
    }

    @Test fun neverSaysSafe() {
        val all = listOf(Lang.EN, Lang.KO).flatMap { l ->
            listOf(
                WalkPhrases.started(l), WalkPhrases.noDepth(l), WalkPhrases.needArCore(l), WalkPhrases.stopped(l),
                WalkPhrases.waitingForLocation(l), WalkPhrases.unknownPlace(l), WalkPhrases.needCamera(l),
                WalkPhrases.needLocation(l), WalkPhrases.ground(GroundKind.SIDEWALK, l),
                WalkPhrases.nothingAhead(l), WalkPhrases.askPlaceName(l), WalkPhrases.alreadyAt("home", l),
                WalkPhrases.depthLost(l), WalkPhrases.tooDark(l), WalkPhrases.depthBack(l),
            )
        }
        all.forEach { assertFalse(it, it.contains("safe", ignoreCase = true) || it.contains("안전")) }
    }
}
