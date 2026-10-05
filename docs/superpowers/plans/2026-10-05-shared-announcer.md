# Shared Announcer and Directions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Live, Find and Walk say what they see through one announcer (no stale queue, priorities, no repeats) and with one set of direction words, optionally clock hours.

**Architecture:** Two pure units in `core/ui`: `Bearings` (degrees → words or clock hour) and `Announcer` (Walk's `WalkAlerts` + `WalkPacing`, with per-screen rules). Each screen keeps deciding what could be said and hands candidates (`Notice`s) to its own `Announcer`; the chosen one is said with `sayNow`. Walk keeps its `Alert` type and maps it to `Notice` in one place (ruling against the spec's "Alert is replaced": 17 call sites for no behaviour change; the spec is updated in Task 0).

**Tech Stack:** Kotlin, JUnit 4, Android SharedPreferences, Material switch.

**Spec:** `docs/superpowers/specs/2026-10-05-shared-announcer-design.md`

## Global Constraints

- Bearing buckets: |deg| ≤ 7 ahead; ≤ 20 slightly left / right; ≤ 135 on your left / right; else behind you. KO: 앞에, 조금 왼쪽에 / 조금 오른쪽에, 왼쪽에 / 오른쪽에, 뒤에. Clock: hour = round(deg / 30) mod 12, 0 → 12; EN "at N o'clock", KO "N시 방향에".
- Zone bearings: Find FAR_LEFT −26, LEFT −13, AHEAD 0, RIGHT 13, FAR_RIGHT 26; Walk LEFT −22, AHEAD 0, RIGHT 22.
- Announcer rules: Walk goneMs 2 000 / repeatMs 6 000 / topicRepeatMs 12 000; Live 10 000 / 6 000 / 12 000; Find 2 000 / 2 000 / 12 000. Duration estimate as `WalkPacing` today (300 ms + 70 ms per Latin char, 140 per Hangul).
- Setting: `AppPrefs.clockDirections`, key `clock_directions`, default false; label "Clock directions" / "시계 방향으로 말하기".
- Branch `shared-announcer` (holds the spec). No AI co-author line. Push and PR only on the user's word.
- Gradle: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`; unit tests `./gradlew --no-daemon -q testDebugUnitTest -PskipModels`; phone APK `./gradlew --no-daemon -q assembleDebug` (never `-PskipModels`). Device `adb -s adb-13192704AA003312-2aTWiz._adb-tls-connect._tcp`; save the log before `logcat -c`.

## Review Focus

1. Live with several objects confirmed in the same frame: one is said, the others stay candidates and are said in later frames while in view — Task 5 test `objectsConfirmedTogetherAreSaidOneAfterAnother` (Announcer level) and the device check.
2. The Clock directions switch changed while a screen is open: the next sentence uses the new style (style read per sentence, not cached at screen start) — Task 3 reads `services.directionStyle` at each phrase; Task 7 checks it on the device.
3. Walk's question answers (`pacing.said(...)` today) still hold back waiting notices — Task 2 test `aSentenceSaidOutsideHoldsWaitingNoticesBack`.
4. A Live object that sits exactly on a bucket edge flickering 6°/8° does not get re-announced as a new thing (key is the cluster, not the words) — Task 5 test `wordsChangingDoNotMakeANewNotice`.
5. Korean sentences keep their grammar with the new words (particle after the direction) — Task 4 tests assert full KO sentences.

---

### Task 0: Spec note

- [ ] Edit the spec's last Walk bullet under "Announcer": "Walk keeps `Alert` as its own type and maps it to `Notice` in `WalkFragment`." Commit with the plan file already committed: `Spec: Walk keeps Alert, mapped to Notice`.

### Task 1: `Bearings`

**Files:** Create `app/src/main/java/com/nungil/core/ui/Bearings.kt`; Test `app/src/test/java/com/nungil/core/ui/BearingsTest.kt`.

**Interfaces — Produces:** `enum class DirectionStyle { WORDS, CLOCK }`; `object Bearings { fun say(deg: Float, style: DirectionStyle, lang: Lang): String; fun clockHour(deg: Float): Int }`.

- [ ] **Step 1: Failing tests:** `wordsEnglish` (0 → "ahead", −7 → "ahead", −8 → "slightly left", 20 → "slightly right", 21 → "on your right", −135 → "on your left", 136 → "behind you", −180 → "behind you"); `wordsKorean` (same bearings → 앞에, 조금 왼쪽에, 조금 오른쪽에, 오른쪽에, 왼쪽에, 뒤에); `clockHours` (0 → 12, −30 → 11, 90 → 3, 180 → 6, −180 → 6, 14 → 12, 16 → 1); `clockSentences` (−30 EN "at 11 o'clock", KO "11시 방향에").
- [ ] **Step 2:** Run `--tests "com.nungil.core.ui.BearingsTest"` — compile failure.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the class, then the suite — exit 0.
- [ ] **Step 5:** Commit `Bearings: one way to say a direction, in words or clock hours`.

### Task 2: `Announcer`

**Files:** Create `app/src/main/java/com/nungil/core/ui/Announcer.kt`; Test `app/src/test/java/com/nungil/core/ui/AnnouncerTest.kt`; Modify `app/src/main/java/com/nungil/walk/WalkFragment.kt` (use it); Delete `WalkAlerts` and `WalkPacing` from `core/walk/WalkAlerts.kt` (keep `AlertKind`, `Alert`, `PlacesCodec` there); move their tests from `WalkPacingTest.kt` and `WalkBasicsTest.kt` into `AnnouncerTest`.

**Interfaces — Produces:** `Notice` and `Announcer` exactly as in the spec (`Rules(goneMs, repeatMs, topicRepeatMs)`, `choose(nowMs, candidates): Notice?`, `said(nowMs, text)`, `reset()`), plus `companion object { val WALK; val LIVE; val FIND }` with the Global Constraints' values and `fun durationMs(text: String): Long`. `fun Alert.toNotice(): Notice` (priority = `kind.ordinal`) in `core/walk/WalkAlerts.kt`.

`choose` = today's `WalkAlerts.choose` with `allowed = { now >= busyUntil || it.ahead || it.priority == CLEAR priority }`… — the CLEAR exception becomes a `Notice.cutsIn` flag set by `toNotice()` for `AlertKind.CLEAR`; after a pick, `busyUntil = now + durationMs(text)`.

- [ ] **Step 1: Failing tests:** every existing `WalkAlerts` / `WalkPacing` test rewritten against `Announcer(Announcer.WALK)` with `Notice`s, unchanged in meaning; new `aNoticeWaitingForSilenceIsSaidOnceQuiet`; `liveRulesSayAnObjectAgainOnlyAfterTenSecondsAway` (in view 0–1 s, away, back at 10.9 s → not said; back at 11.1 s → said); `aSentenceSaidOutsideHoldsWaitingNoticesBack`; `objectsConfirmedTogetherAreSaidOneAfterAnother` (two non-ahead notices every frame: first at t0, second after the first's duration).
- [ ] **Step 2:** Run `--tests "com.nungil.core.ui.AnnouncerTest"` — compile failure.
- [ ] **Step 3:** Implement `Announcer`; switch `WalkFragment` (`alerts.choose { pacing.allows }` → `announcer.choose(now, candidates.map { it.toNotice() })`, keeping the `chosen === pendingNav` check by key; `pacing.said(now, Alert(INFO, …))` → `announcer.said(now, text)`); delete the old classes and tests.
- [ ] **Step 4:** Run the suite and `assembleDebug -PskipModels` — exit 0.
- [ ] **Step 5:** Commit `Announcer: Walk's rules for every screen that describes the scene`.

### Task 3: Clock directions setting

**Files:** Modify `shell/AppPrefs.kt` (`var clockDirections`), `contract/app/AppServices.kt` (`val directionStyle: DirectionStyle`), `shell/MainActivity.kt` (implements it from prefs), `shell/SettingsFragment.kt` + its layout (a switch under the voice guide switch) + `strings` EN / KO (exact labels above).

**Interfaces — Produces:** `services.directionStyle` read at the moment a phrase is built.

- [ ] **Step 1:** Make the changes.
- [ ] **Step 2:** Run the suite and `assembleDebug -PskipModels` — exit 0.
- [ ] **Step 3:** Commit `Settings: say directions as clock hours`.

### Task 4: Phrases use `Bearings`

**Files:** Modify `core/walk/WalkPhrases.kt` (`wall`, `hazard`, `saved` take `style: DirectionStyle`; zone words through `Bearings` with the Walk zone bearings), `core/search/SearchPhrases.kt` (`where(name, zone, style, lang)` with the Find zone bearings; `SearchGuide.zoneWord` unchanged — it is the on-screen status), `core/scan/SummaryBuilder.kt` (`livePhrase(o, style, lang, colorsOn)` with the object's bearing; `fullSummary` sector words = `Bearings` at 0 / 90 / 180 / −90); callers in `walk/WalkVision.kt` (style from a `() -> DirectionStyle` like its `lang`), `search/SearchCameraFragment.kt`, `scan/ScanFragment.kt` / `core/scan/ScanSession.kt` (style passed in with `lang`). Delete `core/scan/Directions.kt` if nothing else uses it.
Tests: `WalkPhrasesTest`, `SearchPhrasesTest`, `SummaryBuilderTest`, `ScanSessionTest` updated.

- [ ] **Step 1: Failing tests** (update the expected strings first): Find `where("Cup", LEFT, WORDS, EN)` = "Cup slightly left.", `FAR_LEFT` = "Cup on your left.", `FAR_RIGHT, CLOCK` = "Cup at 1 o'clock."; KO "컵이 조금 왼쪽에 있어요."; Walk hazard on LEFT stays "… on your left …", in CLOCK "… at 11 o'clock …"; live phrase of an object at 0° = "a blue chair ahead" / "앞에 파란 의자"; full summary sector 0 = "… ahead", sector 2 = "… behind you".
- [ ] **Step 2:** Run the four classes — the updated assertions fail.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Suite and `assembleDebug -PskipModels` — exit 0.
- [ ] **Step 5:** Commit `Every screen says directions the same way`.

### Task 5: Live through the announcer

**Files:** Modify `core/scan/ObjectClusterer.kt` (a stable `id` per cluster; `fun inView(): List<Pair<Int, ObjectSummary>>` = confirmed clusters seen in the last `addFrame`), `core/scan/ScanSession.kt` (`Step.inView: List<LiveObject>`, `data class LiveObject(val key: Int, val summary: ObjectSummary, val bearing: Float)` replacing `news`), `scan/ScanFragment.kt` (an `Announcer(Announcer.LIVE)`; each frame `inView` → `Notice("obj:<key>", livePhrase, priority 0, ahead = |bearing| ≤ 7)`; chosen → `sayNow` + log `Live: "<text>"`), `contract/app/AppServices.kt` (remove `sayLive`), `speech/TtsSpeaker.kt`, `core/ui/SpeechQueue.kt` (remove `addLive`, `LIVE_GAP_MS`, `MAX_LIVE_PARTS`, the live entry flag) and their tests.

- [ ] **Step 1: Failing tests:** `ScanSessionTest.confirmedObjectsInViewAreReportedEveryFrame` (same key across frames, bearing from heading); `wordsChangingDoNotMakeANewNotice` (in `AnnouncerTest`: same key, text alternating "ahead" / "slightly left" each frame → said once); remove the `SpeechQueue` live tests.
- [ ] **Step 2:** Run — new tests fail.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Suite and `assembleDebug -PskipModels` — exit 0.
- [ ] **Step 5:** Commit `Live says what is in view through the announcer, not a queue`.

### Task 6: Find through the announcer

**Files:** Modify `search/SearchCameraFragment.kt`: an `Announcer(Announcer.FIND)`; `SearchTracker`'s `Where(zone)` → `Notice("find:<zone>", where(...), priority 0, ahead = zone == AHEAD)`, `Lost` → `Notice("find:lost", lost, priority 0)`; the chosen one `sayNow`. The tracker and beeps unchanged.

- [ ] **Step 1:** Make the change.
- [ ] **Step 2:** Suite and `assembleDebug` (with models) — exit 0.
- [ ] **Step 3:** Commit `Find says where the target is through the announcer`.

### Task 7: Device check

- [ ] Save the log, install, clear. The user: Live in a room (objects said on time; one said again after 10 s away; not repeated while in view); Find a saved item (words), then Settings → Clock directions on, Find again (hours), Walk briefly (hazard words / hours); switch it off again. Read `Live:`, `Walk said`, `Saying` lines; write the results into the PR body. On a regression stop and report.
