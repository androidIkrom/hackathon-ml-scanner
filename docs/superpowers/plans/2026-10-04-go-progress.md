# Go Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Go mode says the distance and time left every minute and on request, answers what next / repeat / which way / where am I, warns of the wrong way and weak GPS, and announces the destination with its side.

**Architecture:** Small pure units in `core/walk` (`GoPace`, `GoProgress`, `GoQuestion`, `GoApproach`, `GpsSignal`, additions to `Navigator`, `GoMath`, `RoutePhrases`, `OrsJson`), each unit-tested; `walk/WalkFragment` only wires them to the GPS fixes, the 200 ms Go ticker, `takesWords` and the existing navigation slot (`queueNavigation`).

**Tech Stack:** Kotlin, Android Views, JUnit 4 (`org.json` available in unit tests), openrouteservice / Pelias over `HttpURLConnection`.

**Spec:** `docs/superpowers/specs/2026-10-04-go-progress-design.md`

## Global Constraints

- Every sentence in English and Korean (`Lang.EN`, `Lang.KO`); distances through `WalkPhrases.far(metres: Double, lang)`.
- Numbers from the spec: update every 60 s; put off when the next turn is within 25 m, a navigation sentence was said in the last 15 s, or a route is being fetched; pace window 120 s, moving at ≥ 0.3 m/s, needs ≥ 20 s and ≥ 10 m, clamp 0.5–2.0 m/s, default 1.0 m/s; wrong way at 15 m behind the farthest point; weak GPS worse than 30 m for 10 s, good again at 20 m; approach at 50 m and 20 m; side when > 3 m off the last segment's line; "near" within 300 m; time under 45 s is "less than a minute".
- Branch `go-progress`. Commit messages in the repo's plain-sentence style, no AI co-author line. Do not commit `docs/superpowers/plans/2026-10-04-item-alone-samples.md`.
- Before Gradle: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. Unit tests: `./gradlew --no-daemon -q testDebugUnitTest -PskipModels` (quiet + exit 0 = pass; count in `app/build/test-results/testDebugUnitTest/*.xml`). Build: `./gradlew --no-daemon -q assembleDebug`.
- Device only, never the emulator: `adb -s adb-13192704AA003312-2aTWiz._adb-tls-connect._tcp`. Log tag `Nungil`.
- Comments in the code's own style: plain sentences naming the measured or agreed numbers.

## Review Focus

1. Standing still with GPS jitter (±8 m along the route) must not say "walking away" and must not raise the speed — Task 1 test `standingJitterDoesNotCountAsWalking`, Task 4 test `jitterIsNotTheWrongWay`.
2. A route shorter than 50 m must not start with "in 50 metres" — Task 4 test `aShortRouteDoesNotAnnounceAThresholdItStartedInside`.
3. A place name on "Where to?" that holds question words ("Next Door Cafe", "repeat after me") is not a question — Task 3 test `onlyWholeQuestions`.
4. A reroute mid-walk keeps the measured speed — Task 1 test `aNewRouteKeepsTheSpeed`.
5. Unknown GPS accuracy (0) is never weak — Task 5 test `unknownAccuracyIsNotWeak`.

---

### Task 1: Pace and the time sentence

**Files:**
- Create: `app/src/main/java/com/nungil/core/walk/GoPace.kt`
- Modify: `app/src/main/java/com/nungil/core/walk/Route.kt` (`RoutePhrases`)
- Test: `app/src/test/java/com/nungil/core/walk/GoPaceTest.kt`

**Interfaces:**
- Produces: `class GoPace { fun add(nowMs: Long, progressM: Float); fun restart(); val speedMps: Float; fun secondsFor(metres: Float): Float }` with `companion object { const val DEFAULT_MPS = 1.0f; const val WINDOW_MS = 120_000L; const val CHUNK_MS = 10_000L; const val MOVING_MPS = 0.3f; const val MIN_MOVING_MS = 20_000L; const val MIN_MOVING_M = 10f; const val MIN_MPS = 0.5f; const val MAX_MPS = 2.0f }`.
- Produces: `RoutePhrases.timeLeft(seconds: Float, lang: Lang): String` and `RoutePhrases.progress(remainingM: Float, seconds: Float, straight: Boolean, lang: Lang): String`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class GoPaceTest {
    private fun walk(p: GoPace, fromS: Int, toS: Int, mps: Float, startM: Float = 0f) {
        for (s in fromS..toS) p.add(s * 1000L, startM + (s - fromS) * mps)
    }

    @Test fun oneMetreASecondUntilThereIsEnoughWalking() {
        val p = GoPace()
        walk(p, 0, 10, 1.4f)
        assertEquals(1.0f, p.speedMps, 0.001f)
        assertEquals(300f, p.secondsFor(300f), 0.5f)
    }

    @Test fun theWalkersOwnSpeed() {
        val p = GoPace()
        walk(p, 0, 60, 1.4f)
        assertEquals(1.4f, p.speedMps, 0.05f)
    }

    @Test fun stopsDoNotLowerIt() {
        val p = GoPace()
        walk(p, 0, 40, 1.2f)
        for (s in 41..100) p.add(s * 1000L, 48f)           // waiting at a crossing
        assertEquals(1.2f, p.speedMps, 0.05f)
    }

    @Test fun standingJitterDoesNotCountAsWalking() {
        val p = GoPace()
        for (s in 0..100) p.add(s * 1000L, if (s % 2 == 0) 0f else 8f)
        assertEquals(1.0f, p.speedMps, 0.001f)              // 8 m forward and back every second is not 10 m in 20 s of walking
    }

    @Test fun keptBetweenHalfAndTwoMetresASecond() {
        val slow = GoPace().also { walk(it, 0, 200, 0.35f) }
        assertEquals(0.5f, slow.speedMps, 0.001f)
        val fast = GoPace().also { walk(it, 0, 60, 3f) }
        assertEquals(2.0f, fast.speedMps, 0.001f)
    }

    @Test fun aNewRouteKeepsTheSpeed() {
        val p = GoPace()
        walk(p, 0, 60, 1.4f)
        p.restart()
        p.add(61_000, 0f)
        assertEquals(1.4f, p.speedMps, 0.05f)
    }

    @Test fun timeAndProgressSentences() {
        assertEquals("less than a minute", RoutePhrases.timeLeft(44f, Lang.EN))
        assertEquals("about 1 minute", RoutePhrases.timeLeft(80f, Lang.EN))
        assertEquals("about 6 minutes", RoutePhrases.timeLeft(350f, Lang.EN))
        assertEquals("about 1 hour 20 minutes", RoutePhrases.timeLeft(4800f, Lang.EN))
        assertEquals("1분도 안 걸려요", RoutePhrases.timeLeft(30f, Lang.KO))
        assertEquals("약 6분", RoutePhrases.timeLeft(350f, Lang.KO))
        assertEquals("약 1시간 20분", RoutePhrases.timeLeft(4800f, Lang.KO))
        assertEquals("350 metres left, about 6 minutes.", RoutePhrases.progress(350f, 350f, false, Lang.EN))
        assertEquals("350 metres in a straight line, about 6 minutes.", RoutePhrases.progress(350f, 350f, true, Lang.EN))
        assertEquals("350미터 남았어요, 약 6분.", RoutePhrases.progress(350f, 350f, false, Lang.KO))
        assertEquals("직선으로 350미터, 약 6분.", RoutePhrases.progress(350f, 350f, true, Lang.KO))
    }
}
```

- [ ] **Step 2: Run** `./gradlew --no-daemon -q testDebugUnitTest -PskipModels --tests 'com.nungil.core.walk.GoPaceTest'` — expected: compile error, `GoPace` unresolved.
- [ ] **Step 3: Implement `GoPace`** — keep `(timeMs, progressM)` samples, drop those older than `WINDOW_MS` before the newest. Walk the samples in chunks of at least `CHUNK_MS = 10_000L` (from one sample to the first one `CHUNK_MS` or more after it); a chunk is moving when its net progress / its time ≥ `MOVING_MPS`. Net, not the sum of steps: GPS jitter of ±8 m each second is several metres a second step by step and nothing over 10 s. With moving time ≥ `MIN_MOVING_MS` and moving distance ≥ `MIN_MOVING_M`, the kept speed becomes `(dist / time).coerceIn(MIN_MPS, MAX_MPS)`; else the last speed stands. `restart()` clears the samples only. `secondsFor(m) = m / speedMps`. KDoc names the spec's reasons (stops, walking back, reroute).
- [ ] **Step 4: Implement the two phrases** in `RoutePhrases`: minutes = `(seconds / 60).roundToInt().coerceAtLeast(1)`; from 60 minutes "about H hour(s) M minutes" (M omitted when 0; "1 hour" singular, "1 minute" singular); KO "약 H시간 M분"; distance from `WalkPhrases.far`.
- [ ] **Step 5: Run the test class** — expected: 7 tests, 0 failed.
- [ ] **Step 6: Commit** `GoPace.kt`, `Route.kt`, `GoPaceTest.kt`: `Go mode: the walker's own speed, and the time left`.

### Task 2: When the minute update is said

**Files:**
- Create: `app/src/main/java/com/nungil/core/walk/GoProgress.kt`
- Test: `app/src/test/java/com/nungil/core/walk/GoProgressTest.kt`

**Interfaces:**
- Produces: `class GoProgress(var quiet: Boolean = false) { fun start(nowMs: Long); fun stop(); fun check(nowMs: Long, nextTurnM: Float?, lastNavSaidMs: Long, rerouting: Boolean): Verdict; fun said(nowMs: Long); enum class Verdict { OFF, QUIET, NOT_YET, TURN_NEAR, JUST_SPOKE, REROUTING, SAY }; companion object { const val EVERY_MS = 60_000L; const val TURN_NEAR_M = 25f; const val QUIET_AFTER_NAV_MS = 15_000L } }`.
- Order of `check`: `OFF` (not started) → `QUIET` → `NOT_YET` (before due) → `REROUTING` → `TURN_NEAR` (`nextTurnM != null && nextTurnM <= 25`) → `JUST_SPOKE` (`nowMs - lastNavSaidMs < 15_000`) → `SAY`. `said(now)` sets the next due to `now + 60_000`; `start(now)` sets it to `now + 60_000`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class GoProgressTest {
    private val never = Long.MIN_VALUE / 2

    @Test fun everyMinuteAfterTheStart() {
        val g = GoProgress().also { it.start(0) }
        assertEquals(GoProgress.Verdict.NOT_YET, g.check(59_999, null, never, false))
        assertEquals(GoProgress.Verdict.SAY, g.check(60_000, null, never, false))
        g.said(60_000)
        assertEquals(GoProgress.Verdict.NOT_YET, g.check(119_000, null, never, false))
        assertEquals(GoProgress.Verdict.SAY, g.check(120_000, null, never, false))
    }

    @Test fun putOffNotDropped() {
        val g = GoProgress().also { it.start(0) }
        assertEquals(GoProgress.Verdict.TURN_NEAR, g.check(60_000, 25f, never, false))
        assertEquals(GoProgress.Verdict.JUST_SPOKE, g.check(61_000, 30f, 50_000, false))
        assertEquals(GoProgress.Verdict.REROUTING, g.check(66_000, 30f, never, true))
        assertEquals(GoProgress.Verdict.SAY, g.check(66_000, 30f, 50_000, false))
    }

    @Test fun anAnswerToHowFarCountsAsAnUpdate() {
        val g = GoProgress().also { it.start(0) }
        g.said(40_000)
        assertEquals(GoProgress.Verdict.NOT_YET, g.check(60_000, null, never, false))
        assertEquals(GoProgress.Verdict.SAY, g.check(100_000, null, never, false))
    }

    @Test fun quietAndOff() {
        assertEquals(GoProgress.Verdict.OFF, GoProgress().check(60_000, null, never, false))
        val g = GoProgress(quiet = true).also { it.start(0) }
        assertEquals(GoProgress.Verdict.QUIET, g.check(60_000, null, never, false))
        g.quiet = false
        assertEquals(GoProgress.Verdict.SAY, g.check(60_000, null, never, false))
        g.stop()
        assertEquals(GoProgress.Verdict.OFF, g.check(120_000, null, never, false))
    }
}
```

- [ ] **Step 2: Run** the class — expected: compile error, `GoProgress` unresolved.
- [ ] **Step 3: Implement `GoProgress`** as the Interfaces block says.
- [ ] **Step 4: Run** the class — expected: 4 tests, 0 failed.
- [ ] **Step 5: Commit** both files: `Go mode: when the minute update is said, and when it waits`.

### Task 3: Questions

**Files:**
- Create: `app/src/main/java/com/nungil/core/walk/GoQuestion.kt`
- Test: `app/src/test/java/com/nungil/core/walk/GoQuestionTest.kt`

**Interfaces:**
- Produces: `enum class GoQuestion { HOW_FAR, NEXT, REPEAT, WHICH_WAY, WHERE_AM_I, QUIET, UPDATES_ON; companion object { fun of(text: String): GoQuestion? } }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class GoQuestionTest {
    private fun q(vararg texts: String) = texts.map { GoQuestion.of(it) }

    @Test fun english() {
        assertEquals(List(6) { GoQuestion.HOW_FAR }, q("How far?", "how long", "How much further", "how many minutes", "When will I arrive?", "how far is it"))
        assertEquals(List(3) { GoQuestion.NEXT }, q("What's next?", "next turn", "next instruction"))
        assertEquals(List(3) { GoQuestion.REPEAT }, q("Repeat", "say again", "What did you say?"))
        assertEquals(List(3) { GoQuestion.WHICH_WAY }, q("Which way?", "where do I go", "direction"))
        assertEquals(List(3) { GoQuestion.WHERE_AM_I }, q("Where am I?", "what street is this", "my location"))
        assertEquals(List(4) { GoQuestion.QUIET }, q("quiet updates", "fewer updates", "updates off", "stop updates"))
        assertEquals(List(2) { GoQuestion.UPDATES_ON }, q("updates on", "more updates"))
        assertEquals(GoQuestion.HOW_FAR, GoQuestion.of("please, how far"))
    }

    @Test fun korean() {
        assertEquals(List(4) { GoQuestion.HOW_FAR }, q("얼마나 남았어", "몇 분 남았어요", "언제 도착해", "거리"))
        assertEquals(List(2) { GoQuestion.NEXT }, q("다음은", "다음 안내"))
        assertEquals(List(3) { GoQuestion.REPEAT }, q("다시", "반복", "뭐라고"))
        assertEquals(List(2) { GoQuestion.WHICH_WAY }, q("어느 쪽", "어디로 가"))
        assertEquals(List(3) { GoQuestion.WHERE_AM_I }, q("여기 어디야", "지금 어디야", "현재 위치"))
        assertEquals(List(2) { GoQuestion.QUIET }, q("안내 조용히", "업데이트 꺼"))
        assertEquals(List(2) { GoQuestion.UPDATES_ON }, q("업데이트 켜", "안내 다시 켜"))
    }

    @Test fun onlyWholeQuestions() {
        assertEquals(List(6) { null }, q("Next Door Cafe", "repeat after me", "next time", "how far is Seoul Station from Busan", "Seoul Station", "다시 서울역"))
    }
}
```

- [ ] **Step 2: Run** the class — expected: compile error, `GoQuestion` unresolved.
- [ ] **Step 3: Implement `of`** — normalise like `QuickAsk` (lowercase, apostrophes removed, split on non-letters), drop leading fillers `please, ok, okay, hey` and a trailing `please`, then match the whole remaining phrase against one exact set per question (EN joined with spaces; KO joined without spaces, e.g. `얼마나남았어`, `얼마나남았어요`). `UPDATES_ON` is matched before `REPEAT`, so "안내 다시 켜" is not "repeat". KDoc: only whole questions, as `QuickAsk`, so a place name on "Where to?" is never one.
- [ ] **Step 4: Run** the class — expected: 3 tests, 0 failed.
- [ ] **Step 5: Commit** both files: `Go mode: the questions a walker asks, in English and Korean`.

### Task 4: Navigator — progress, wrong way, approach, side

**Files:**
- Create: `app/src/main/java/com/nungil/core/walk/GoApproach.kt`
- Modify: `app/src/main/java/com/nungil/core/walk/Route.kt` (`Navigator`, `Announcement`, `GoState`, `GoMath`, `RoutePhrases`)
- Test: `app/src/test/java/com/nungil/core/walk/RouteTest.kt`

**Interfaces:**
- Produces: `enum class Side { LEFT, RIGHT }`; `GoMath.side(from: LatLon, to: LatLon, point: LatLon): Side?` (null within 3 m of the line through `from`→`to`; positive cross product of east/north vectors = LEFT).
- Produces: `class GoApproach { fun next(remainingM: Float): Int? }` — returns 50 or 20 the first time `remainingM` is at or under it; a threshold is armed only when the first `remainingM` it saw was more than threshold + 10 m.
- Produces in `Navigator`: `val progressM: Float` (progress along the line at the last on-line fix, 0 before), `val side: Side?` (`GoMath.side` of the line's last two points and the destination), new `Announcement.WrongWay(text)` and `Announcement.Approach(text)`. `update` order: Arrived → OffRoute → WrongWay → Turn → Prepare → Approach. Wrong way: farthest on-line progress kept; when `farthest - progress >= 15` once, re-armed when progress is back at farthest. `Arrived` text carries the side.
- Produces: `GoState(target, instruction, toTargetM, remainingM, final: Boolean = false)` (`final` when it points at the destination).
- Produces in `RoutePhrases`: `approach(name: String, metres: Float, side: Side?, lang: Lang)`, `arrived(name: String, lang: Lang, side: Side? = null)`, `wrongWay(lang)`, `next(state: GoState, name: String, lang: Lang)`, `whichWay(clock: Int, metres: Float, name: String?, lang: Lang)` (name null = the next turn), `noHeading(lang)`, `noRouteRunning(lang)`.

- [ ] **Step 1: Write the failing tests** (in `RouteTest`, with its `route()` and `at()` helpers: east 100 m, north 100 m, east 100 m; destination at the end)

```kotlin
@Test fun walkingBackIsTheWrongWay() {
    val n = Navigator(route(), null, Lang.EN)
    n.update(0, at(60.0, 0.0))
    assertEquals(60f, n.progressM, 1f)
    assertNull(n.update(1_000, at(50.0, 0.0)))
    assertEquals(Announcement.WrongWay("You are walking away from the route. Turn around."), n.update(2_000, at(44.0, 0.0)))
    assertNull(n.update(3_000, at(30.0, 0.0)))                                  // once
    n.update(4_000, at(61.0, 0.0))                                              // back past the farthest point
    assertTrue(n.update(5_000, at(45.0, 0.0)) is Announcement.WrongWay)
}

@Test fun jitterIsNotTheWrongWay() {
    val n = Navigator(route(), null, Lang.EN)
    for (i in 0..20) assertFalse(n.update(i * 1_000L, at(if (i % 2 == 0) 40.0 else 32.0, 3.0)) is Announcement.WrongWay)
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
```

- [ ] **Step 2: Run** `RouteTest` — expected: compile errors (`Side`, `GoApproach`, `progressM`, ...).
- [ ] **Step 3: Implement** the Interfaces block. The approach in `Navigator` uses its own `GoApproach` on the remaining distance `peek` already computes (along the line plus the line's end to the destination), and is checked after the last turn too: today `update` returns early when no turn is left. Wrong way counts only on-line fixes (within 40 m). The existing `arrived(name, lang)` callers keep working through the default `side`.
- [ ] **Step 4: Run all unit tests** — expected: all pass, the existing `RouteTest` cases unchanged.
- [ ] **Step 5: Commit** the three files: `Go mode: the wrong way, the destination on the way in, and its side`.

### Task 5: Weak GPS, the street, and the quiet setting

**Files:**
- Create: `app/src/main/java/com/nungil/core/walk/GpsSignal.kt`
- Modify: `app/src/main/java/com/nungil/core/walk/Route.kt` (`OrsJson.parseReverse`, `RoutePhrases.gpsWeak/whereAmI/cannotLookUp/updatesOff/updatesOn`)
- Modify: `app/src/main/java/com/nungil/walk/WalkServices.kt` (`RouteSource.reverse`, `OrsRouteSource.reverse`, `GoSettings`)
- Test: `app/src/test/java/com/nungil/core/walk/GpsSignalTest.kt`, `RouteTest.kt`

**Interfaces:**
- Produces: `class GpsSignal { fun update(nowMs: Long, accuracyM: Float): Boolean }` — true once when the accuracy has been worse than 30 m for 10 s; re-armed at 20 m or better; accuracy 0 (unknown) resets the weak timer and never warns.
- Produces: `OrsJson.parseReverse(json: String): String?` — the first feature's `properties.street`, else its `name`; null when none or broken.
- Produces: `interface RouteSource { ...; fun reverse(at: LatLon): String? = null }`; `OrsRouteSource.reverse` calls `https://api.heigit.org/pelias/v1/reverse?point.lat=..&point.lon=..&size=1` with the same key header and fallback host pattern as `geocode` (`https://api.openrouteservice.org/geocode/reverse`).
- Produces: `class GoSettings(context: Context) { var quietUpdates: Boolean }` in shared preferences `"walk_go"`, key `"quiet_updates"`.
- Produces in `RoutePhrases`: `gpsWeak(lang)`, `whereAmI(street: String, near: String?, lang)`, `cannotLookUp(lang)`, `updatesOff(lang)`, `updatesOn(lang)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class GpsSignalTest {
    @Test fun weakForTenSecondsThenOnceUntilGoodAgain() {
        val g = GpsSignal()
        assertFalse(g.update(0, 35f))
        assertFalse(g.update(9_999, 40f))
        assertTrue(g.update(10_000, 40f))
        assertFalse(g.update(20_000, 50f))
        assertFalse(g.update(21_000, 25f))      // better, not yet good
        assertFalse(g.update(22_000, 18f))      // good: re-armed
        assertFalse(g.update(23_000, 45f))
        assertTrue(g.update(33_000, 45f))
    }

    @Test fun unknownAccuracyIsNotWeak() {
        val g = GpsSignal()
        for (s in 0..30) assertFalse(g.update(s * 1_000L, 0f))
    }
}
```

In `RouteTest`:

```kotlin
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
```

- [ ] **Step 2: Run** both classes — expected: compile errors.
- [ ] **Step 3: Implement** the Interfaces block (`GpsSignal`, `parseReverse`, the phrases, `reverse`, `GoSettings`).
- [ ] **Step 4: Run all unit tests and `assembleDebug`** — expected: all pass, no build output.
- [ ] **Step 5: Commit** the five files: `Go mode: weak GPS, the street the walker is on, and a quiet switch`.

### Task 6: Wiring in WalkFragment

**Files:**
- Modify: `app/src/main/java/com/nungil/walk/WalkFragment.kt`

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: State.** Fields: `pace = GoPace()`, `progress = GoProgress(goSettings.quietUpdates)`, `gps = GpsSignal()`, `beaconApproach = GoApproach()`, `beaconStartM: Float?`, `rerouting = false`, `lastNav: String?`, `lastNavAt = Long.MIN_VALUE / 2`, `lastPutOff: GoProgress.Verdict?` (to log a put-off once, not every tick).
- [ ] **Step 2: Navigation sentences.** `queueNavigation(text)` also sets `lastNav = text` and `lastNavAt = now`. `onRoute(Ok)`: `progress.start(now)` unless reroute; `pace.restart()`; `rerouting = false`. `requestRoute(reroute = true)` sets `rerouting = true`. `beaconTo`: `progress.start(now)` when not started, `beaconApproach = GoApproach()`, `beaconStartM = null`. `arrived` and `stopNavigation`: `progress.stop()`. `arrived(place)` says `RoutePhrases.arrived(place.name, lang, navigator?.side)` (read the side before `navigator = null`).
- [ ] **Step 3: Fixes (`onFix`).** With a navigator: after `update`, `pace.add(now, nav.progressM)`; `Announcement.WrongWay` and `Approach` go through `queueNavigation` like `Prepare` and log `Go wrong way` / nothing extra. By beacon: `metres` to the target; `beaconStartM` set on the first fix; `pace.add(now, beaconStartM - metres)`; `beaconApproach.next(metres)` → `queueNavigation(RoutePhrases.approach(name, metres, null, lang))`. With a destination: `gps.update(now, location.accuracyM)` → `queueNavigation(RoutePhrases.gpsWeak(lang))` and log `Go GPS weak: <accuracy> m`.
- [ ] **Step 4: The minute update (`goTicker`).** Once per tick while navigating: the remaining distance and `nextTurnM` from `navigator.peek(here)` (`state.toTargetM` when `!state.final`, else null), or the beacon distance with `nextTurnM = null`; `progress.check(now, nextTurnM, lastNavAt, rerouting)`; on `SAY` → `queueNavigation(RoutePhrases.progress(remaining, pace.secondsFor(remaining), straight = navigator == null, lang))`, `progress.said(now)`, log `Go update: <m> m left, <speed> m/s, <min> min`; on `TURN_NEAR`/`JUST_SPOKE`/`REROUTING` log `Go update put off: <verdict>` only when it differs from `lastPutOff`.
- [ ] **Step 5: Questions (`takesWords`).** After the `offer` block and before the "Where to?" place logic: `GoQuestion.of(t)` → `answer(question)`; return true, except `REPEAT` without a destination (return false: the global repeat). Every answer is said at once (`services.speaker.sayNow`, and shown in `walkingAnnouncement`). `answer`: `HOW_FAR` → the progress sentence, `say`, `progress.said(now)`; `NEXT` → `RoutePhrases.next(state, target.name, lang)`; `REPEAT` → `lastNav` else the `NEXT` sentence; `WHICH_WAY` → `RoutePhrases.whichWay(Beacon.clock(Beacon.bearingDeg(here, state.target) - heading), state.toTargetM, if (state.final) name else null, lang)`, or `noHeading` without a heading; first three without a destination → `noRouteRunning`; `WHERE_AM_I` → `withLocation { here -> network.execute { source?.reverse(here) } }` then on the main thread `whereAmI(street, near)` with `near` = the closest of the saved places and the target within 300 m, or `cannotLookUp` + the destination's `WalkPhrases.beacon(...)` (heading known) or its distance; stop the location afterwards when not navigating; `QUIET`/`UPDATES_ON` → set `progress.quiet` and `goSettings.quietUpdates`, say `updatesOff`/`updatesOn`. Each answer logs `Go asked: <question> -> "<answer>"`.
- [ ] **Step 6: Run all unit tests and `assembleDebug`** — expected: all pass, no build output.
- [ ] **Step 7: Commit** the file: `Go mode: say the progress every minute and answer the walker's questions`.

### Task 7: Device walk (the gate)

Install: `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk`, `logcat -c`, capture `-s Nungil:I`. The user walks outdoors; the log is read after.

| | The user does | Pass |
|---|---|---|
| A | "Take me to <place about 600–900 m away>" and walks | `Go update:` about every 60 s; never in the same second as a `Walk said [HAZARD` line; none within 25 m of a turn |
| B | Asks "how far", "what's next", "which way", "where am I", "repeat" on the way | one `Go asked:` line each, with the right answer |
| C | Turns and walks back 20 m | `Go wrong way` within about 20 m |
| D | Says "quiet updates", walks 2 minutes, "updates on" | no `Go update:` in between; turns still said |
| E | Arrives | approach at 50 m and 20 m with a side when the place is off the road; arrival with the same side |

- [ ] A–E pass, or each failure is reported with its log lines before any threshold is changed.
- [ ] Push and open the PR only when the user says so.
