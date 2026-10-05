# Shared Camera Commands Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Front/back camera, what is this, who is this, stop and start work the same way on every camera screen, from one place; "Find my charger" finds the charger.

**Architecture:** A pure `CameraCommandPolicy` decides what a camera command does from two facts (can the camera switch, is the work going on). `MainActivity` asks it before the screen's own handler whenever the current screen is a `CameraScreen` and carries the action out; what / who is this are answered by `FrameAnswers` from the screen's last frame. Each camera screen implements `CameraScreen` with the start / pause code it already has.

**Tech Stack:** Kotlin, CameraX (`CameraSession`), ML Kit faces (`FaceIdentifier`), TFLite classifier (`SceneClassifier`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-05-shared-camera-commands-design.md`

## Global Constraints

- Stop (option B): the speech-only first stop in `MainActivity.onHeard` (`SpeechStop`) is not changed; the policy sees only stops that get past it.
- Walk/Go and ItemEnroll have no switchable camera; Scan, Find, Reader and Enroll do.
- New sentences, EN / KO exactly: "I can't see anything yet." / "아직 아무것도 안 보여요."; "Only the back camera works here." / "여기서는 뒤 카메라만 쓸 수 있어요."; "Paused." / "잠시 멈췄어요.".
- Screens without a camera keep `Route.OpenAndSay(Live)` for what / who is this.
- Branch `shared-commands` (holds the spec). No AI co-author line in commits. Push and PR only on the user's word.
- Gradle in Bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. Unit tests: `./gradlew --no-daemon -q testDebugUnitTest -PskipModels` (quiet + exit 0 = pass). Phone APK: `./gradlew --no-daemon -q assembleDebug` (never `-PskipModels`).
- Device: `adb -s adb-13192704AA003312-2aTWiz._adb-tls-connect._tcp`; save `logcat -d` to the scratchpad before any `logcat -c`.
- Comments in the code's own style: plain sentences, "(the logs)" where a log decided it.

## Review Focus

1. A camera command arrives while the screen is closing (`_binding == null`, camera stopped): nothing is spoken about a camera that is gone, no crash — Task 4 makes every `CameraScreen` method a no-op without a view; checked by hand in Task 6.
2. "What is this" on Find for a COCO label (`keepBitmap` was false): the answer still comes from the detections, and "who is this" gets a bitmap — Task 4 sets `keepBitmap = true` for every Find target.
3. The answer to "what is this" arrives after the user left the screen: dropped — Task 3 `FrameAnswers` checks its owner is still alive before speaking.
4. Scan's auto-start is pending when "stop" is heard: it counts as working, so stop cancels it and stays on the screen (today's behaviour) instead of leaving — Task 4 `isWorking` includes the pending auto-start.
5. "my" alone with a person "My" saved still finds the person — Task 2 test `aFillerNameAloneIsStillFound`.

---

### Task 1: `CameraCommandPolicy`

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/CameraCommandPolicy.kt`
- Test: `app/src/test/java/com/nungil/core/ui/CameraCommandPolicyTest.kt`

**Interfaces:**
- Produces: `sealed interface CameraAction` with `Switch(to: Facing?)`, `BackCameraOnly`, `DescribeCentre`, `NameFace`, `Pause`, `Leave`, `Resume`; `object CameraCommandPolicy { fun decide(command: VoiceCommand, canSwitch: Boolean, working: Boolean): CameraAction? }`.

- [ ] **Step 1: Write the failing tests:** `switchCameraSwitchesWhenItCan` (`SwitchCamera(FRONT)`, canSwitch → `Switch(FRONT)`; `SwitchCamera()` → `Switch(null)`); `backCameraOnlyWhenItCannot`; `whatAndWhoIsThis` (→ `DescribeCentre`, `NameFace`, whatever canSwitch/working); `stopPausesWorkGoingOn` (working → `Pause`); `stopLeavesWhenAlreadyPaused` (not working → `Leave`); `startResumes`; `otherCommandsAreTheScreens` (`Back`, `Delete`, `ReadText`, `Unknown("x")`, `Go(..)` → null).
- [ ] **Step 2: Run** `./gradlew --no-daemon -q testDebugUnitTest -PskipModels --tests "com.nungil.core.ui.CameraCommandPolicyTest"` — expected: compile failure, `CameraCommandPolicy` unresolved.
- [ ] **Step 3: Implement** in `core/ui/CameraCommandPolicy.kt` per the spec's table.
- [ ] **Step 4: Run** the class, then the whole suite — exit 0.
- [ ] **Step 5: Commit** — `Camera commands: one decision for every camera screen`.

### Task 2: "Find my X"

**Files:**
- Modify: `app/src/main/java/com/nungil/core/search/SearchResolver.kt` (`resolve`, `matchSaved`)
- Test: `app/src/test/java/com/nungil/core/search/SearchResolverTest.kt`

**Interfaces:** `SearchResolver.resolve` signatures unchanged.

Containment (step 2 of `matchSaved`): collect every matching name instead of the first; prefer the one with the most of its words found in the query, then the longer name. A name whose every word is a filler (`QueryCleaner`'s fillers — it matches only through `cleaned.raw`) is kept aside: `resolve` returns it only when no other saved name matched and `matchLabel` found nothing.

- [ ] **Step 1: Write the failing tests** (saved names built the way the existing tests build them):
  - `theLongerNameBeatsAFillerName`: saved person "My", item "My charger test one"; `resolve("my charger")` → the item.
  - `aFillerNameAloneIsStillFound`: saved person "My"; `resolve("my")` → My; saved person "Me"; `resolve("find me")` → Me.
  - `aLabelBeatsAFillerName`: saved person "My"; `resolve("my bag")` → label `backpack`.
  - `mostWordsWin`: items "Black box" and "My black box"; `resolve("my black box")` → "My black box".
- [ ] **Step 2: Run** `--tests "com.nungil.core.search.SearchResolverTest"` — expected: `theLongerNameBeatsAFillerName` and `aLabelBeatsAFillerName` fail (they get "My"); the others may already pass.
- [ ] **Step 3: Implement** the change in `SearchResolver`.
- [ ] **Step 4: Run** the class (all old tests too), then the whole suite — exit 0.
- [ ] **Step 5: Commit** — `Find: a saved name of filler words never beats a longer name or a label`.

### Task 3: `CameraScreen`, `SwitchableCamera`, `FrameAnswers`

**Files:**
- Create: `app/src/main/java/com/nungil/contract/app/CameraScreen.kt` (`CameraScreen`, `SwitchableCamera` exactly as in the spec)
- Modify: `app/src/main/java/com/nungil/scan/CameraSession.kt` (implements `SwitchableCamera`; `facing` and `useCamera` exist)
- Modify: `app/src/main/java/com/nungil/people/FaceIdentifier.kt` (add `fun faces(bitmap: Bitmap): List<FaceSeen>`; `data class FaceSeen(val centerX: Float, val centerY: Float, val name: String?)`, name null for a usable face that matches nobody)
- Modify: `app/src/main/java/com/nungil/core/scan/ScanPhrases.kt` + `app/src/test/java/com/nungil/core/scan/ScanPhrasesTest.kt` (`nothingYet`, `backCameraOnly`, `paused`, with the Global Constraints' exact EN / KO)
- Create: `app/src/main/java/com/nungil/scan/FrameAnswers.kt`

**Interfaces:**
- Consumes: `SceneRules.centerDetection`, `SceneClassifier.nameCenter`, `ScanPhrases.looksLike/unknownThing/thisIs/unknownPerson/nobody`, `LabelNames.name`.
- Produces: `class FrameAnswers(context: Context, owner: LifecycleOwner, speak: (String) -> Unit) : Closeable { fun what(frame: VisionFrame?, lang: Lang); fun who(frame: VisionFrame?, lang: Lang) }` — work on its own single-thread executor, models made on first use, the answer posted to the main thread and spoken only while `owner` is at least STARTED; closed by its owner.

- [ ] **Step 1: Write the failing test** in `ScanPhrasesTest`: `newCameraSentences` asserting the three EN and three KO strings.
- [ ] **Step 2: Run** `--tests "com.nungil.core.scan.ScanPhrasesTest"` — expected: compile failure.
- [ ] **Step 3: Implement** the phrases, the two interfaces, `CameraSession : SwitchableCamera`, `FaceIdentifier.faces`, `FrameAnswers` (who: the face nearest the middle; what: centre detection, else classifier, else `unknownThing`; no frame or no bitmap where one is needed → `nothingYet`).
- [ ] **Step 4: Run** the unit suite and `assembleDebug -PskipModels` — both exit 0.
- [ ] **Step 5: Commit** — `CameraScreen and FrameAnswers: what and who is this from any camera screen's last frame`.

### Task 4: MainActivity, Scan and Find

**Files:**
- Modify: `app/src/main/java/com/nungil/shell/MainActivity.kt` (`handleCommand`)
- Modify: `app/src/main/java/com/nungil/scan/ScanFragment.kt`
- Modify: `app/src/main/java/com/nungil/search/SearchCameraFragment.kt`

**Interfaces:**
- Consumes: Tasks 1 and 3.
- Produces: the dispatch every later screen relies on: in `handleCommand`, when `currentScreen()` is a `CameraScreen` and `CameraCommandPolicy.decide(command, screen.switchable != null, screen.isWorking)` is not null, MainActivity carries out the action and returns; else the existing path.

Actions: `Switch` → `switchable.useCamera(to)` then `sayNow(ScanPhrases.cameraSwitched(switchable.facing))`; `BackCameraOnly` → `sayNow(backCameraOnly)`; `DescribeCentre` / `NameFace` → `FrameAnswers.what/who(screen.lastFrame(), lang)`; `Pause` → `screen.pause()`; `Leave` → `silenceAll(); back()`; `Resume` → `screen.resume()`. MainActivity owns one `FrameAnswers` at a time, made on the first what / who for a screen with that screen's `viewLifecycleOwner`, and closes it when that owner is destroyed (the contract package does not depend on `scan`).

Scan: `isWorking` = scanning or the auto-start pending; `pause()` = today's Stop branch; `resume()` = `startScan`; drop `whatIsThis` / `whoIsThis` (FrameAnswers does them); `classifyCenter` stays for `guessWhenEmpty` only; remove Start, Stop, SwitchCamera, WhatIsThis and WhoIsThis from `onVoiceCommand`.
Find: `keepBitmap = true` for every target; a `paused` flag: frames are not matched while paused, the beeps stop (`beeper.pulse(0)`), "Paused." is said; `resume()` clears it; `isWorking = !paused`; Stop and SwitchCamera leave its `onVoiceCommand`.

- [ ] **Step 1: Make the changes.**
- [ ] **Step 2: Run** the unit suite and `assembleDebug -PskipModels` — exit 0.
- [ ] **Step 3: Commit** — `Scan and Find take camera commands from the shared policy`.

### Task 5: Walk, Reader, Enroll, ItemEnroll

**Files:**
- Modify: `app/src/main/java/com/nungil/walk/WalkFragment.kt`, `app/src/main/java/com/nungil/walk/WalkVision.kt` (a `@Volatile var lastFrame: VisionFrame?` set in `analyse` from its bitmap and detections, also in `submitCameraFrame`)
- Modify: `app/src/main/java/com/nungil/reader/ReaderFragment.kt`, `app/src/main/java/com/nungil/people/EnrollFragment.kt`, `app/src/main/java/com/nungil/items/ItemEnrollFragment.kt`

**Interfaces:** consumes Tasks 3–4; each screen per the spec's "Screens" table.

Walk: `switchable = null`; `isWorking` = navigator, target, offer or running; `pause()` / `resume()` = today's Stop / Start branches; `onVoiceCommand` keeps only the Go-search `Unknown`. Reader, Enroll, ItemEnroll: `isWorking` = their running flag; `pause` / `resume` = their existing pause / start; `lastFrame` kept in `onFrame`; their Start / Stop / SwitchCamera leave `onVoiceCommand` (ItemEnroll keeps its `Unknown` answer).

- [ ] **Step 1: Make the changes.** `grep -n "VoiceCommand.Stop\|VoiceCommand.Start\|SwitchCamera\|WhatIsThis\|WhoIsThis" app/src/main/java/com/nungil/{scan,search,walk,reader}/*.kt app/src/main/java/com/nungil/people/EnrollFragment.kt app/src/main/java/com/nungil/items/ItemEnrollFragment.kt` must print nothing.
- [ ] **Step 2: Run** the unit suite and `assembleDebug` (with models) — exit 0.
- [ ] **Step 3: Commit** — `Walk, Reader and enrolment take camera commands from the shared policy`.

### Task 6: Device check

- [ ] **Step 1:** Save the log, install, clear the log.
- [ ] **Step 2:** The user runs the spec's device list: Find (what is this; stop → "Paused."; stop → leaves), Walk (front camera → back camera only; what is this stays in Walk), Reader (front camera switches and says so), Live (stop / start; who is this on a saved face), "Find my charger".
- [ ] **Step 3:** Expected lines: `Heard "..." -> ...` followed by the spoken answer (`Saying "..."`) and no screen change where none is expected; "Looking for My charger test one.". Write the results into the PR body.
- [ ] **Step 4:** On a regression stop and report with the log lines; otherwise push and open the PR on the user's word.
