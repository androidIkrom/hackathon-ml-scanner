# Shared Recognition (stage 1 of the shared scanner modules)

## Why

Every camera screen recognises saved things, but items are recognised by two different code paths:

| Screen | Items | People |
|---|---|---|
| Look around, Live (ScanFragment) | `ItemTagger` (NameTagger) | `FaceTagger` → `FaceIdentifier` |
| Walk, Go (WalkVision) | `ItemTagger` (NameTagger) | `FaceTagger` → `FaceIdentifier` |
| Find (SearchCameraFragment) | `ItemTargetMatcher` (TargetMatcher) | `PersonTargetMatcher` → `FaceIdentifier` |

A fix made in one path does not reach the other. Find was taught to search the whole frame in squares, to keep a
found item at a lower score and to check the thing alone; the live scan kept a 0.75 threshold on tight detector
boxes and named almost nothing until it was patched by hand on 2026-10-04 (branch `fix/live-scan-voice`).

Goal: one engine per kind of saved thing. Every screen gets its saved things from it, so a change to how things are
recognised reaches every screen at once.

This is stage 1 of 4 agreed with the user (option A): recognition, then common camera voice commands, then the
announcing rules, then the frame source. Each stage has its own spec, plan and PR.

## Decisions (agreed)

1. One interface, two implementations: `SavedFinder`, implemented by `ItemFinder` and `PersonFinder`.
2. Screens keep their current interfaces. `NameTagger` (Scan, Walk) and `TargetMatcher` (Find) become thin adapters
   over a `SavedFinder`, so screen code barely changes.
3. One algorithm and one set of thresholds everywhere: find at `FIND_THRESHOLD` 0.55, keep at `KEEP_THRESHOLD`
   0.45, look closer from `LOOK_CLOSER` 0.35, thing alone from `ALONE_MIN` 0.60.
4. Screens differ only by a speed setting (`Reach`):
   - `WHOLE_FRAME` — Look around, Live, Find: detector boxes, near search, whole-frame squares, closer look, thing alone.
   - `BOXES_AND_NEAR` — Walk, Go: detector boxes and the near search only. Walk's worker thread also produces hazard
     alerts; a ~1 s whole-frame search per frame would delay them. Items the detector does not know are not found in
     Walk (as today).
5. Confirmation over frames stays on the screen side: `StickyNames` and `FoundPlaces` are about showing and
   announcing, not recognising.

## Interface

```kotlin
// contract/app/SavedFinder.kt
interface SavedFinder : Closeable {
    /** The saved things in [frame]. [only]: one saved id to look for (Find); null: all saved things. */
    fun find(frame: VisionFrame, only: Long? = null): List<Found>
}

data class Found(
    val kind: TagKind,
    val id: Long,
    val name: String,
    /** Where it is: the thing's outline when one was cut out, else the square or the detector box. */
    val box: Box,
    /** The detector box that is this thing, if there is one. */
    val detectionIndex: Int?,
    val score: Float,
)
```

`NameTag` keeps its optional `box` (added on 2026-10-04) for finds without a detector box.

## Items: the one algorithm

Pure Kotlin in `core/items/ItemSearch.kt`, so it can be unit tested with fake scores. Android parts
(MobileNet embedder, Room samples, segmenter) are in `items/ItemFinder.kt` and are handed to `ItemSearch` as three
functions:

- `match(square): Match?` — the saved item a square looks most like, and its score (`ItemRecognizer.identify(v, 0f)`).
- `thing(square): Thing?` — the thing in the middle of a square, cut out: its outline, its own learned square, and
  the best saved match of it alone (segmenter + `embedAlone`). Null without a segmenter or a usable mask.
- the frame's detections and size.

A target is a group of saved ids that share a name (`ItemMatcher.sameName`); a square counts for a target only when
its best match is in the group (as Find does today). With `only` set there is one target; with null every saved
item is a target.

Per call, for the targets not yet found in this call:

1. **Boxes.** Up to 3 non-person detector boxes (`ItemCrop.candidates`), each embedded as its learned square
   (`ItemWindows.square`). A target is found on a box at `FIND_THRESHOLD`.
2. **Near.** A target found in the previous call is looked for only in the nine squares around its last square
   (`ItemWindows.near`) and kept at `KEEP_THRESHOLD`; its best near square goes to the verdict (step 5).
3. **Whole frame** (`WHOLE_FRAME` only). Squares all over the frame (`ItemWindows.grid`); for each target its best
   square and the merged place of all its squares at the threshold (`ItemWindows.locate`).
4. **Closer.** The best place at `LOOK_CLOSER` or more is looked at again in `ItemWindows.around`.
5. **Verdict.** `ItemMatcher.seen(squareScore, needs, alone)`: the squares decide; from `LOOK_CLOSER` up the thing
   alone at `ALONE_MIN` can add a find, never remove one. The box returned is the thing's outline when
   `ItemMatcher.outlined` says so, else the square.
6. A found target whose place contains a detector box's centre, that box not much bigger than the place
   (1.5× the area), is reported with that `detectionIndex`.

Cost control: with `only == null`, steps 3–5 run for the single best target of the whole frame per call (as the
2026-10-04 live patch does); steps 1–2 cover the others. Each target remembers its last square for step 2 until it
is not found in a call.

Logging: one line per call at most once a second, with every score that decided:
`Item search: <n> squares in <ms> ms, <target> near/grid/closer scores (needs ..), alone .. (needs ..), seen ..`.
Find keeps its current wording so old and new logs compare.

## People

`PersonFinder` wraps `FaceIdentifier` and `FaceBoxes`. All saved people → every face hit assigned to a person box
(`FaceBoxes.assign`, today's `FaceTagger`); one person → the best hit for that id and its person box (today's
`PersonTargetMatcher`). `FaceTagger`'s every-3rd-frame throttle stays in the `NameTagger` adapter, because Find
runs every frame it can.

## Screens after the change

- `createNameTaggers(context, reach)` returns adapters over `PersonFinder` and `ItemFinder(reach)`.
  ScanFragment passes `WHOLE_FRAME`; WalkVision passes `BOXES_AND_NEAR`.
- `TargetMatchers.create` returns adapters over the same finders (`WHOLE_FRAME`, `only = id`); Find shows the
  returned box, as today.
- WalkVision uses `tag.box` when `detectionIndex` is -1 to pick the zone (today such tags are dropped).
- `ItemTagger`, `ItemTargetMatcher`, `FaceTagger` and `PersonTargetMatcher` are removed; nothing else uses them.

## Errors

- No saved items or people: the finder returns nothing (no model load for an empty kind, as now).
- Segmenter fails to load: the thing-alone check is skipped; squares decide alone (as Find today).
- An embedding fails for one square: that square scores nothing; the rest still count.
- Model files missing (`-PskipModels` builds): `tryCreate` leaves the finder out, the screen works without names.

## Testing

Unit tests (JUnit, pure `ItemSearch` with fake `match` / `thing`):

1. Find behaviour preserved: found at 0.55, kept at 0.45 near the last square, lost below; the closer look moves the
   place; the thing alone adds a find from 0.35 and never removes one; a square that looks more like another saved
   item does not count; two items with one name are one target.
2. All-items mode: a detector box names the item on it; an item without a box is found by squares and reported
   with its place; at most one whole-frame target per call; nothing is found in a frame of other things.
3. `BOXES_AND_NEAR` never scores the whole-frame grid.
4. A found place with a detector box in its middle reports that box's index; a much bigger box is not taken.

Device check (the physical phone, `adb -s adb-13192704AA003312-2aTWiz._adb-tls-connect._tcp`):

- Find a saved item (a bottle, then a towel or box): `Item search` lines, found and kept as before.
- Live scan with the same items: named, including one the detector does not know.
- Walk: a saved bottle on a detector box is still named; no slow-down of alerts (frame time in the log).

## Out of scope

Voice commands, announcing rules and the frame source (stages 2–4). No change to thresholds, samples, enrolment,
`StickyNames`, `FoundPlaces` or the Room schema.
