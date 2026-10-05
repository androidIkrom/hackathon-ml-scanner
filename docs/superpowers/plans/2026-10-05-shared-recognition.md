# Shared Recognition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One engine per kind of saved thing (`ItemFinder`, `PersonFinder` behind `SavedFinder`), used by Look around, Live, Walk, Go and Find, so a recognition fix reaches every screen.

**Architecture:** The item algorithm moves into pure Kotlin (`core/items/ItemSearch.kt`), fed by two functions (best saved match of a square; the thing cut out of a square) so it is unit tested with fakes. `ItemFinder` / `PersonFinder` are thin Android wrappers. Screens keep `NameTagger` (Scan, Walk) and `TargetMatcher` (Find) through two adapters; the four old classes are deleted.

**Tech Stack:** Kotlin, MediaPipe tasks-vision (`ImageEmbedder`, `InteractiveSegmenterLegacy`), ML Kit faces, Room, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-05-shared-recognition-design.md`

## Global Constraints

- Thresholds unchanged and taken from `ItemMatcher`: `FIND_THRESHOLD` 0.55, `KEEP_THRESHOLD` 0.45, `LOOK_CLOSER` 0.35, `ALONE_MIN` 0.6, `OUTLINE_MIN` 0.5.
- Reach: `WHOLE_FRAME` for Scan (Look around, Live) and Find; `BOXES_AND_NEAR` for Walk/Go.
- With `only == null` the whole-frame search, closer look and thing-alone run for one target per call.
- A detector box is the found thing's box when its centre is in the found place and its area is at most 1.5× the place's.
- `StickyNames`, `FoundPlaces`, Room schema, samples and enrolment are not touched.
- Branch `shared-recognition` (already holds the spec). No AI co-author line in commits. Push and PR only on the user's word.
- Gradle in Bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`. Unit tests: `./gradlew --no-daemon -q testDebugUnitTest -PskipModels`; a quiet run with exit 0 is a pass. APK for the phone: `./gradlew --no-daemon -q assembleDebug` (never `-PskipModels`).
- Device: `adb -s adb-13192704AA003312-2aTWiz._adb-tls-connect._tcp`, never the emulator. Save `logcat -d` to the scratchpad before any `logcat -c`.
- Comments in the code's own style: plain sentences, measured numbers and "(the logs)" where a log decided it.

## Review Focus

1. Find on an item seen a moment ago that is now gone: near squares score low, the whole frame is searched again in the same call, nothing found, the remembered square is dropped — Task 1 test `aKeptItemNotThereAnyMoreIsSearchedForAgainAndForgotten`.
2. Two saved items under one name ("My new white bottle" ×2 on the phone): one target, one find, either id's squares count — Task 1 test `itemsSavedTwiceUnderOneNameAreOneTarget`.
3. All-items mode, one item on a detector box and another without: the boxed one is named from its box and the other may still be found by squares in the same call — Task 2 test `aBoxedItemDoesNotStopTheSearchForAnother`.
4. The segmenter is missing or gives no mask (`cut` returns null): squares decide alone, the square is the box — Task 1 test `withoutACutThingTheSquaresDecide`.
5. Find's camera runs without the detector (`detections` empty): no box step, search unchanged — Task 1 tests all pass an empty detection list.

---

### Task 1: `ItemSearch` — one target (Find's behaviour)

**Files:**
- Create: `app/src/main/java/com/nungil/core/items/ItemSearch.kt`
- Test: `app/src/test/java/com/nungil/core/items/ItemSearchTest.kt`

**Interfaces:**
- Consumes: `ItemMatcher` (`Match`, `seen`, `outlined`, `sameName`, thresholds), `ItemWindows` (`grid`, `near`, `around`, `locate`, `square`), `ItemCrop.candidates`.
- Produces:
  ```kotlin
  enum class Reach { WHOLE_FRAME, BOXES_AND_NEAR }

  /** The thing in the middle of a square, cut out: its outline, the square it is learned in, its best saved match alone (null: embedding failed). */
  class CutThing(val outline: Box, val square: Box, val match: ItemMatcher.Match?)

  class ItemSearch(names: Map<Long, String>, learnedAlone: Set<Long>, reach: Reach) {
      data class Hit(val id: Long, val box: Box, val detectionIndex: Int?, val score: Float, val seen: ItemMatcher.Seen)
      class Result(val hits: List<Hit>, val looked: Int, val log: String)
      fun search(
          detections: List<Detection>, width: Int, height: Int, only: Long?,
          match: (Box) -> ItemMatcher.Match?, cut: (Box) -> CutThing?,
      ): Result
  }
  ```
  `Hit.id` is the target group's smallest id; `Hit.box` is the shown box (outline or square); `Result.log` is the part of the Find log line after "N squares in M ms, " (e.g. `near 0.52, grid 0.31, closer 0.00 (needs 0.45), alone - (needs 0.60), seen by squares, outlined`).

The algorithm is `ItemTargetMatcher.locate` + `judge` (read it before writing), generalised to a target = `ItemMatcher.sameName(id, names)`; a square counts for a target only when its best match's id is in the group, else 0. `needs` per target is `KEEP_THRESHOLD` when the target has a remembered square from the previous call, else `FIND_THRESHOLD`. After the call a target remembers the hit's learned square (thing's own square when seen alone, else the square), and forgets it when not found. Each grid window is matched once per call, whatever the number of targets.

Fakes for the tests: a 640×480 frame; `match` returns `Match(id, s)` when the window's centre lies in a given place box (s may depend on window size to make "closer" win), else `Match(OTHER, 0.2f)` for an unrelated saved id; `cut` returns null or a fixed `CutThing`; count the calls to `match`.

- [ ] **Step 1: Write the failing tests** in `ItemSearchTest`:
  - `foundAtTheFindThreshold`: place scores 0.56 → one hit, `seen == BY_SQUARES`; at 0.54 → no hit.
  - `keptAtTheKeepThresholdNearTheLastSquare`: found at 0.6; next call the place scores 0.47 → hit, and `match` was called only for `ItemWindows.near(last).size` windows.
  - `aKeptItemNotThereAnyMoreIsSearchedForAgainAndForgotten`: found; next call everything scores 0.2 → no hit and the grid was searched; a third call at 0.5 → no hit (needs 0.55 again).
  - `theCloserLookMovesThePlace`: grid best 0.5, a window from `ItemWindows.around` 0.6 → hit whose box is that window.
  - `theThingAloneAddsAFindFromLookCloser`: square 0.4, `CutThing` match `(id, 0.65)` → hit `BY_ITEM_ALONE`, box = its outline; square 0.3 with the same thing → no hit.
  - `theThingAloneNeverRemovesAFind`: square 0.6, thing alone 0.1 → hit `BY_SQUARES`.
  - `withoutACutThingTheSquaresDecide`: `cut` returns null; 0.56 → hit with the square as box; 0.5 → none.
  - `aSquareThatLooksMoreLikeAnotherItemDoesNotCount`: the place's best match is another saved id at 0.9 → no hit for `only`.
  - `itemsSavedTwiceUnderOneNameAreOneTarget`: names {1:"Cup", 2:"cup"}, `only = 1`, squares match id 2 at 0.6 → one hit, `id == 1`.
- [ ] **Step 2: Run** `./gradlew --no-daemon -q testDebugUnitTest -PskipModels --tests "com.nungil.core.items.ItemSearchTest"` — expected: compile failure (`ItemSearch` unresolved).
- [ ] **Step 3: Implement** `ItemSearch`, `CutThing`, `Reach` in `core/items/ItemSearch.kt` (single-target path; `only == null` may throw `NotImplementedError` until Task 2).
- [ ] **Step 4: Run** the same command — expected: exit 0; then the whole suite — exit 0.
- [ ] **Step 5: Commit** `git add app/src/main/java/com/nungil/core/items/ItemSearch.kt app/src/test/java/com/nungil/core/items/ItemSearchTest.kt` — message `One item search for every screen: Find's algorithm as pure logic`.

### Task 2: `ItemSearch` — all saved items, detector boxes, reach

**Files:**
- Modify: `app/src/main/java/com/nungil/core/items/ItemSearch.kt`
- Test: `app/src/test/java/com/nungil/core/items/ItemSearchTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces: `search(..., only = null, ...)` working; `Hit.detectionIndex` set per the 1.5× rule.

Order per call (spec "Items: the one algorithm"): boxes (up to 3 `ItemCrop.candidates`, each matched as `ItemWindows.square(box)`, found at `FIND_THRESHOLD`, box = the detection's box, its learned square remembered), then near for remembered targets not yet found, then — `WHOLE_FRAME` only — grid, closer and verdict for the single target with the best grid score among those not found.

- [ ] **Step 1: Write the failing tests:**
  - `aDetectorBoxNamesTheItemOnIt`: detection "bottle" whose square scores 0.6 for id 3 → hit id 3, `detectionIndex == 0`, box = the detection's box.
  - `aBoxedItemDoesNotStopTheSearchForAnother`: id 3 on a box at 0.6, id 5 in squares elsewhere at 0.6 → two hits; id 5's `detectionIndex == null`.
  - `onlyOneWholeFrameTargetPerCall`: ids 5 and 6 each score 0.6 in different places, no boxes → exactly one hit (the higher one).
  - `nothingInAFrameOfOtherThings`: every square best-matches id 3 at 0.29 → no hits.
  - `boxesAndNearNeverSearchTheWholeFrame`: `Reach.BOXES_AND_NEAR`, no detections, nothing remembered → no hits and zero `match` calls.
  - `aPlaceWithADetectorBoxInItsMiddleTakesThatBox`: item found by squares; a detection whose centre is in the place and area ≤ 1.5× → that index; a detection 2× the area → `null`.
  - `aPersonBoxIsNeverAnItem`: a "person" detection scoring 0.9 → no hit.
- [ ] **Step 2: Run** `--tests "com.nungil.core.items.ItemSearchTest"` — expected: the new tests fail.
- [ ] **Step 3: Implement** the all-items path and the box step; `MAX_BOX_SHARE = 1.5f` in `ItemSearch`'s companion.
- [ ] **Step 4: Run** the class, then the whole suite — exit 0.
- [ ] **Step 5: Commit** — message `Item search: every saved item at once, detector boxes, Walk's shorter reach`.

### Task 3: `SavedFinder`, `ItemFinder`, `PersonFinder`

**Files:**
- Create: `app/src/main/java/com/nungil/contract/app/SavedFinder.kt` (`SavedFinder`, `Found` exactly as in the spec's "Interface")
- Create: `app/src/main/java/com/nungil/items/ItemFinder.kt`
- Create: `app/src/main/java/com/nungil/people/PersonFinder.kt`
- Modify: `app/src/main/java/com/nungil/items/ItemRecognizer.kt` (add `val allNames: Map<Long, String>` and `fun learnedAlone(): Set<Long>` = ids where `ItemEnrollmentGuide.learnedAlone(samples(id))`)

**Interfaces:**
- Consumes: `ItemSearch`, `Reach`, `CutThing` (Tasks 1–2); `ItemEmbedder.embed/embedAlone`, `ItemSegmenter.at`, `ItemMask.of`, `FaceIdentifier.identify`, `FaceBoxes.assign/personBoxFor`.
- Produces: `class ItemFinder(context: Context, reach: Reach) : SavedFinder`; `class PersonFinder(context: Context) : SavedFinder`.

`ItemFinder`: `match` = `embedder.embed(bitmap, box)?.let { recognizer.identify(it, 0f) }`; `cut` = segmenter at the square's centre → `ItemMask.of` → `ItemWindows.square(mask.box)` → `embedAlone` → `identify(.., 0f)` (the body of `ItemTargetMatcher.thingIn`); the segmenter is created on the first `cut` call and a failed creation is logged once and treated as null. Logs `Item search: ${result.looked} squares in <ms> ms, ${result.log}` at most once a second (with the target's name when `only == null`). Returns empty when `frame.bitmap` is null or `recognizer.isEmpty`.

`PersonFinder`: returns empty without a bitmap or a "person" detection; `only == null` → every `FaceBoxes.assign` pair as `Found(PERSON, hit.personId, hit.name, detections[index].box, index, hit.score)`; `only != null` → the best hit of that id on `personBoxFor`, or nothing (today's `PersonTargetMatcher`).

- [ ] **Step 1: Write** the three files.
- [ ] **Step 2: Run** `./gradlew --no-daemon -q assembleDebug -PskipModels` — expected: exit 0 (compiles; nothing calls them yet).
- [ ] **Step 3: Commit** — message `SavedFinder: one finder for saved items and one for saved people`.

### Task 4: Screens use the finders; old classes go

**Files:**
- Modify: `app/src/main/java/com/nungil/people/Taggers.kt` — add `class SavedTagger(finder: SavedFinder, everyNth: Int = 1, skip: (VisionFrame) -> Boolean = { false }) : NameTagger`; `createNameTaggers(context: Context, reach: Reach = Reach.BOXES_AND_NEAR)` returns `SavedTagger(PersonFinder, everyNth = 3, skip = no "person" detection)` and `SavedTagger(ItemFinder(reach))`.
- Modify: `app/src/main/java/com/nungil/search/TargetMatchers.kt` — add `class SavedTargetMatcher(finder: SavedFinder, id: Long) : TargetMatcher` (`slow = true`, `locate` = first found box); PERSON → `PersonFinder`, ITEM → `ItemFinder(WHOLE_FRAME)`.
- Modify: `app/src/main/java/com/nungil/scan/ScanFragment.kt` — `createNameTaggers(app, Reach.WHOLE_FRAME)`.
- Modify: `app/src/main/java/com/nungil/walk/WalkVision.kt:420-426` — a tag with `detectionIndex == -1` takes its zone from `tag.box`.
- Delete: `items/ItemTagger.kt`, `items/ItemTargetMatcher.kt`, `people/FaceTagger.kt`, `people/PersonTargetMatcher.kt`.

**Interfaces:**
- Consumes: Task 3.
- Produces: unchanged `NameTagger` / `TargetMatcher` behaviour for every screen.

`SavedTagger.tag` maps `Found` → `NameTag(detectionIndex ?: -1, name, kind, box = if (detectionIndex == null) box else null)`; `skip` frames are not counted by `EveryNth` (today's `FaceTagger` counts only frames with a person box).

- [ ] **Step 1: Make the changes and deletions.** `grep -rn "ItemTagger\|ItemTargetMatcher\|FaceTagger\|PersonTargetMatcher" app/src` must then print nothing.
- [ ] **Step 2: Run** the unit tests, then `assembleDebug` without `-PskipModels` — both exit 0.
- [ ] **Step 3: Commit** — message `Every camera screen gets saved things from the same finders`.

### Task 5: Device check

- [ ] **Step 1:** Save the phone's log, install the APK, clear the log.
- [ ] **Step 2:** The user runs, in order: Find "My new white bottle", then Find a towel or box; Live scan with the bottle and the towel/box; Walk past the bottle. Read the log.
- [ ] **Step 3:** Expected: Find — `Item search` lines with `seen` while the item is in view and `near` scores while kept, as before the change; Live — the bottle named from its box and the towel/box from squares (`Live: "My towel …"`); Walk — the bottle named, and Walk frame times no longer than in a log from before the change. Write the numbers into the PR body.
- [ ] **Step 4:** On a regression, stop and report with the log lines; otherwise push and open the PR on the user's word.
