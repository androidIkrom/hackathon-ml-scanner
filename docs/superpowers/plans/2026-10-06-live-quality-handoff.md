# Live Quality — Handoff and Plan (continue in a new chat)

> Status: findings verified on the device 2026-10-05; tasks below are a proposal (Task 3's direction decided by the user). Start the next chat by reading
> this file, then run the brainstorming gate for Tasks 1–3 (bounded changes: short design in chat, user approval).

## Where things are

- `main` = b3f9564 (stages 1–3 merged: PR #58 recognition, #59 camera commands, #60 announcer).
- Branch `speed-laptop` (not pushed, no PR yet), on top of main:
  - square sizes in turn and one borderline cut in Live; `LaptopCheck` + `Relabels` (keyboard box → classifier → "laptop");
  - stop: "stop stop stop" stops the screen, a second stop counts for 20 s;
  - **reverted** 0168fe5 (Live 0.58 threshold + label-only overlay made things worse: lost the charger, no frames);
  - no colour for laptop / tv / cell phone (lit screens painted them blue);
  - Settings by voice: "model accurate/fast/light", "processor CPU/GPU", "speak results on/off", "colours on/off",
    "high contrast on/off", "read what I touch on/off" (works on the device: "Model: accurate.").
  - Unit tests green; APK on the phone is this branch.
- Worktree `D:\Ml-Scanner-old` at d3bb392 (main before the interfaces, PR #57) with `applicationId com.nungil.old`,
  installed on the phone as **"Nungil old"** with a copy of the saved items and people. Keep it for A/B checks;
  remove with `git worktree remove D:\Ml-Scanner-old` and `adb uninstall com.nungil.old` when done.
- Phone settings now: model ACCURATE, GPU, min score 0.7 (default), clock directions on.

## Findings (with evidence)

1. **The interfaces did not make recognition worse.** A/B on the same desk (logs `log-now.txt` 23:05 new,
   `log-ab.txt` 23:15 old, scratchpad of session e96273ab):
   - both say a cable is "My glass" at 0.60–0.66;
   - the new build named the charger, the white bottle and the laptop; the old one did not (charger once 0.63,
     never confirmed);
   - item search median ~0.9 s in both.
2. **Most false finds come from Add Object (enrolment), not from the live search.** Items saved before 2026-10-04
   (12 vectors: squares only, the item 19–41 % of the square, the rest desk) match things on the same desk:
   `My glass` (glasses on the desk) ↔ a cable; `Test5` (a whole desk) ↔ everything. Items saved since (24 vectors:
   squares + the item alone) were named rightly: charger, towel, new white bottle.
   Old-style items on the phone: Dopey, My remote, My glass, Toca, Chuckle, My white bottle, My spoon,
   My new white bottle (id 14), My black box, My new black glasses (id 22, 12 vectors: a step left early).
3. **"Fast" detector model (lite0) sees much less** than "Accurate" (lite2): ~50 % of live seconds "nothing" vs ~30 %.
   It is ~2× faster (155 vs 295 ms/frame). Keep Accurate.
4. **Frames for items found by look are wrong** because the box is the searched square, not the thing.
5. **Keyboard colour**: the laptop's keyboard was "a blue keyboard": its box takes the lit screen's light.

## Tasks (proposal; user decisions recorded)

### Task 1 — Frames that fit the thing (user: "frame of the object's own size, width and height")
For a place found by look (`FoundPlaces`), cut the thing out once when the place is confirmed (segmenter at the
place's centre, `ItemMask.of`, its `box`), keep that outline as the place's box, refresh it every N finds; draw it
as a normal frame. No outline (segmenter gives nothing usable) → draw nothing for that place (no square).
Cost to measure: one segmenter call (~300–500 ms) per newly confirmed place, not per frame.
Tests: `FoundPlaces` keeps an outline box and replaces it on refresh; a place without an outline is not drawn.

### Task 2 — Keyboard keeps a colour, the right one (user: "keyboard must have a colour")
Keep `keyboard` in `ColorPolicy` (it is). Find why it reads blue: log `ColorSampler` votes for keyboard boxes next to
a `laptop`/`tv` box; likely fixes: sample only the lower half of a keyboard box, or drop very bright / saturated
pixels (screen glow) before voting. Decide from the logged votes, then test with `ColorVote`/`ColorMapper` cases.

### Task 3 — The old enrolment method is not used any more (user's decision, 2026-10-05)
Items learned the old way (squares only: `ItemEnrollmentGuide.learnedAlone(vectors) == false`, 12 vectors) are the
source of the false finds and are no longer used for recognition anywhere (Live, Look around, Walk, Find):
- `ItemRecognizer` / `ItemFinder` leave them out of `ItemSearch` (no box, no square, no near search for them).
- The Saved list marks them "learned the old way — learn it again" and says so when one is opened; Find on such an
  item says it must be learned again instead of searching.
- Add Object already stores both kinds of samples; keep it that way, and make sure a new item can never be saved
  with square samples only (a step left early must still store the item-alone samples; `ItemEnrollmentGuide`
  saves a 12-vector list as 13 for exactly this — verify with a test).
- Data the user will clean: delete duplicates (three glasses items; Toca = Chuckle, the same jar) and re-learn the
  old-style items with the current Add Object.
Tests: recognizer ignores an item with square samples only; Saved/Find say "learn again" for it; a new enrolment
always yields `learnedAlone == true`.

### Task 4 — Finish `speed-laptop`
After Tasks 1–3 (or before, as its own PR): push `speed-laptop`, open the PR with the device numbers above, merge.

### Task 5 — Live says "bed" where there is none (user, 2026-10-06)
Device log `log-task1.txt` (scratchpad of session 736533c0), 22:09:59: "Live sees: bed 0.72", then 0.70, spoken as
"a gray bed at 12 o'clock" on a desk scene (laptop, charger, bottle nearby). The detector (lite2, ACCURATE) gives
`bed` at 0.70–0.72, above the 0.7 min score. First find what was in view (ask the user, or save a frame when `bed`
is detected), then decide: a stricter score for `bed` (and other big furniture labels seen from close up), a
`Relabels`/classifier check like `LaptopCheck`, or a size/shape rule (a bed fills most of the frame and is wide).
Tests: the chosen rule with the logged boxes and scores.

### Task 6 — Live speaks late while the microphone hears other voices (user, 2026-10-07)
Device log `log-task1b.txt` (scratchpad of session 736533c0): Live decided "My new white bottle at 12 o'clock" at
23:23:59.1 but it was said at 23:24:03.8. The recognizer had a partial ("Stop alarm": the user or a voice in the
room), `VoiceBargeIn` stopped all app sound (`beginHold`), and the hold lasted until the recognizer gave up after
8 s with no words. The room is noisy ("heard sound for 8–12 s but no words" all session), so this repeats.
Proposal: end the hold when no new partial word has come for ~1.5 s and no command was heard (the user who really
talks keeps producing partials); keep `HOLD_SAFETY_MS` as the outer limit. Tests: the hold-release rule as a pure
function (time since last new partial, final result or not).
Smaller delays seen: a place found by look needs two finds (~1.5–2.5 s from view to speech); a detector `bottle`
was said ("a blue bottle") 2 s before its saved name came.

### Task 7 — Crash when Live is opened again by voice (user, 2026-10-07)
Native crash in MediaPipe, `Fatal signal 7 (SIGBUS), code 2 (BUS_ADRERR)` in `libmediapipe_tasks_jni.so`, on a
MediaPipe thread ("Thread-19", "Thread-20"), 0.1–0.3 s after the scan screen is replaced. Seen three times
(scratchpad of session 736533c0):
- `log-task1b.txt` 2026-10-06 23:24:51: "Stop lights" → Leave, then Find ("Looking for My charger test one.");
- `log-cuts.txt` 2026-10-07 20:33:21: "Livescan" said while Live was open;
- `log-cuts2.txt` 2026-10-07 20:40:53: "Live skin" (heard as Live) said while Live was open.
Likely cause: the old screen closes its MediaPipe tasks (detector on the camera thread, item finder and segmenter
on the extras thread, `ScanFragment.onDestroyView`, `CameraSession.stop`) while the new screen creates its own on
other threads. First reproduce (say "Live scan" twice), then decide: a "Live" command while Live is open does
nothing (or says it is already on), and/or one shared model instance per kind (Stage 4), and/or the new screen
waits until the old one has closed its tasks.

### Paused 2026-10-11 (user: text reading and QR first)
- Done and committed: Task 2 colour + frame (1921f9c), Task 7 crash (1fc963c, ModelLock; 8 screen switches on the
  phone, no crash).
- Built, unit-tested, on the phone, **not tried on the phone, not committed**: "Delete <name>" / "Open <name>" on the
  Saved list (`core/saved/SavedByVoice.kt`, `VoiceDelete.kt`, `saved/SavedFragment.kt`), and `SearchResolver`
  preferring a real name in another guess over a filler-word name (fixes "Find my charging" -> "My", see Later).
  Try: Saved -> Objects, "Open my charger test one", "Delete my charger test one", "delete".
- Temporary, not committed: charger cut diagnostics (`items/CutDiagnostics.kt`, its call in `ItemFinder.cut`;
  images in /sdcard/Android/data/com.nungil/files/cuts). Finding: the cut is right, the saved item-alone samples
  show another side of the charger (no pins, no text, no cable): learn it again, then remove the diagnostics.
- Next after that: Task 6 (voice hold), Task 5 (bed), Task 4 (PR).

### Later
- "Find my charging" said "Looking for My.": the first guess matched the saved *person* "My" (a filler-word name)
  before the second guess "Find my charger" was tried. `SearchResolver.resolve(text, guesses, …)` should try the
  other guesses for a non-filler name before falling back to a filler-word name.
- Stage 4 (frame source: one camera pipeline, one model instance per kind) — spec not written yet.
- "Delete <name>" by voice on the Saved list (said "That does not work on this screen").
- "How sure before speaking" by voice (min score; default 0.7 hides detections at 0.5–0.69).
