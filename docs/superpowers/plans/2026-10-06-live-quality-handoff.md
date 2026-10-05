# Live Quality — Handoff and Plan (continue in a new chat)

> Status: findings verified on the device 2026-10-05; tasks below are a proposal. Start the next chat by reading
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

### Task 3 — Old-style items (from finding 2)
- Data (no code): re-learn the old-style items with the current Add Object, delete duplicates
  (three glasses items; Toca = Chuckle, the same jar).
- Code options to discuss: Saved list marks items "learned the old way — learn again"; and/or a live scan names
  old-style items only on a detector box, never by look (they are the source of the false finds).

### Task 4 — Finish `speed-laptop`
After Tasks 1–3 (or before, as its own PR): push `speed-laptop`, open the PR with the device numbers above, merge.

### Later
- Stage 4 (frame source: one camera pipeline, one model instance per kind) — spec not written yet.
- "Delete <name>" by voice on the Saved list (said "That does not work on this screen").
- "How sure before speaking" by voice (min score; default 0.7 hides detections at 0.5–0.69).
