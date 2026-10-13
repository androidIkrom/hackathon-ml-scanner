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
2026-10-13 (user: save the frame first): TEMP `scan/BedDiagnostics.kt`, called from `ScanFragment.logSeen`, saves a
frame with every box drawn when the detector sees `bed` (at most 30, `adb pull
/sdcard/Android/data/com.nungil/files/beds`). Not committed. Next: the user points Live at that desk; pick the rule
from the frames; remove the diagnostics.
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
Done 2026-10-13 (not yet tried on the phone): held app sound comes back once no new word has come for 1.5 s
(`RecognizerPolicy.holdEnds`, `QUIET_WORDS_MS`; `VoiceInput.quietWords`, log "App sound back: no new words …"); a
button's 6 s talk window still holds; new words hold again. While a text is read aloud only a command holds at all
(reader, b8fe7f9). Live check: Live with a voice in the room (radio, video, people) and see that what it finds is
said within a second or two.

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

### Text reading and QR (in progress, not committed; break 2026-10-12)
- Done in the working tree, unit tests green (950), on the phone:
  - `ReaderPolicy.sameLine`: one line read twice by OCR agrees on 80 % of its letters (case and spaces ignored),
    and lines with other numbers are other lines. Before, a line had to come twice letter for letter, which OCR
    never does, so nothing was said (since d8bff72).
  - `ReaderPolicy.toSay` / `inOrder` / `sentence`: every clear line in view, top to bottom and left to right on a
    row; lines under 60 % of the biggest line's height are small print and left out; a line is said once seen in
    two reads in a row, not again within 10 s, at most 300 characters at once. Device: it read the chat top down.
  - `READ_EVERY_MS` 1.5 s -> 0.6 s (the user: "needs a bit more time to read"); English (Latin) model only (the
    Korean model was tried: slower, one 2 s timeout; the user: English first).
  - TEMP log "Reader sees N lines in X ms: unsure, small, readable (biggest), to read, saying" in `ReaderFragment`.
- Replay test (2026-10-12): `ReaderEngine` (one read: code, else text via ReaderPolicy) is used by the reader
  screen and by androidTest `ReaderReplayTest`, which draws frames on the phone (8-line page with hand shake,
  sign with small print, QR code, small text, blurred page) and reads them with the real ML Kit. All 6 pass in
  26 s: QR on the first frame (27 ms); sign at the 2nd read without its small print; pages read top down from the
  2nd read (0.6 s), every line once. It found `MIN_HEIGHT` 0.03 dropping normal page lines (0.028-0.033 high):
  now 0.015. Run it: see memory "on-device-tests-infinix" (am instrument; Hiber freezes background apps, so the
  test shows the debug-only `ReplayHostActivity`).
- Offer first, read on the user's word (user, 2026-10-12): text in view is announced ("Text found, titled 'X'" when
  1-3 lines are 1.4x the lower-middle height, else "beginning with" the first 6 words; "Say read to hear it"), and
  read in full (all readable lines, small print too, top down) on "read"/"yes"/a tap on the text; "no" drops it;
  no answer in 15 s drops it; the same text is not offered again for 30 s. Codes are still said at once. Replay:
  page offered at 0.6 s once, sign titled 'EMERGENCY EXIT'; voice answer and tap not covered by the replay.
- Left: one live check by the user at the end (also the voice answer "read"); real reader frames for `androidTest/assets/replay/<scene>/`.
- 2026-10-12/13, the text was read only in part (a demo to the professor, then in class):
  - `SpeechQueue` kept 3 phrases and dropped the rest, with 1.5 s between them: 31 lines queued, 3 said. Lines of a
    text (`Speaker.sayLines`) are now never dropped and follow after 0.15 s.
  - Other voices in the room held the app's sound (barge-in), and the hold dropped the whole text: 27 lines queued,
    none said. Now a hold pauses a text and it goes on from the line cut (`SpeechQueue.hold`); while a text is read
    only a command stops it (`VoiceInput.bargesIn`); "stop" / "continue" go on from `SpeechQueue.cutLine`.
  - A lecture went to commands ("Play function is…" -> Start, "Number of item…" -> Saved objects): a phrase of more
    than 8 words is no command (`VoiceCommandParser.MAX_COMMAND_WORDS`) and gets no "I did not understand"; reader
    answers are at most 4 words.
  - Device (volume 0): `SpeakLinesTest` (6 lines in order; a hold at line 3 and the text goes on) and
    `ReaderReplayTest` 6/6.
- 2026-10-13, every size, family and weight read, in order (user). New replay scenes: mixed fonts (sizes 14-44 px,
  serif / sans / mono / italic, bold and normal in one line, a big word next to small ones), the same tilted 7° and
  -6°, soft and noisy, other families light on dark, two columns. Fixed:
  - a crooked page: lines measured along their slant and the page set straight (`ReaderPolicy.straighten`, from
    ML Kit's corner points); before, "Tuesday" came before "Monday" and the title was lost;
  - columns: blocks side by side are read column by column (`inOrder` with ML Kit's blocks); lines far apart, each a
    block of its own, are read row by row as a table;
  - the title is the first run of big lines only (not "Weekly Notes SALE all shoes");
  - small print in a noisy frame: `MIN_CONFIDENCE` 0.5, and a waiting offer gathers the lines of each reading
    (`merge`); the replay says "read" 2.4 s after the offer, as a user does.
  - Device: `ReaderReplayTest` 12/12 and `SpeakLinesTest` 2/2; unit tests 974.
  - Sizes changing inside a line (scene "paragraph, sizes inside the lines", straight and tilted 6°): ML Kit cuts a
    line round a big word, and reads that word in some frames only ("FIVE." / "FIVE dollars…" / "Prices start at
    FVE"). Pieces of one line are one sentence (`Offer.sentences`, `rows`, `joinPieces`: no full stop between them,
    a word read in both pieces said once); of one line read twice the fuller reading is kept (`merge`). A lone
    big "5" among small words never came from ML Kit in any frame: not fixable here. Device 14/14, unit 978.
  - Also seen: the offer said "beginning with 'Tuzatishe'" but the reading began higher up ("Saved Messages"): the
    waiting offer takes the fuller reading of the same text. Open.

### Later
- Done: "Find my charging" (1226347), "Delete <name>" / "Open <name>" by voice (1226347), Task 4 (PR #62 merged
  2026-10-13; main merged in through PR #63; the ruleset now takes changes to `speed-laptop` only by PR).
- "How sure before speaking" by voice (2026-10-13, branch `live-quality-2`): "more sure" / "less sure" / "say more"
  move it by 10, "sureness 50" / "confidence sixty" set it, inside the slider's 30-70 %; Korean "더 확실하게",
  "덜 확실하게", "확신 50". Said back ("Sureness 60 percent: more things, some may be wrong."; at the end: "Already
  the most sure: 70 percent."). An open camera takes it at the next frame (`SettingsStore.onMinScore`, listened to
  by `CameraSession`). Unit tests 982; androidTest `SettingsStoreTest` on the phone. Not yet said live.
- Stage 4: spec `docs/superpowers/specs/2026-10-13-shared-models-design.md`. 4a built 2026-10-13 (shared models,
  kept 30 s; `SharedModelsTest` on the phone: no crash in ten hand-overs, a screen 2.1 s cold and 0.55 s warm). Live
  check left. 4b after the numbers.
- Reader on a laptop screen (2026-10-13, c6b4875): only the big model name was read. The reader now asks the camera
  for 1440 x 1920 (screen text was 2-4 px at 640 x 480) and drops text under 8 px instead of a share of the frame.
  Replay "laptop screen": 0 of 10 lines before, 10 of 10 after. Live check left.
- Live, a second thing beside the first (2026-10-13): a blue bottle next to the white one joined its cluster
  (same label within 20°) and was never said. Now the announcer's key holds the cluster's steady count (seen in 2
  frames), so "2 bottles at 12 o'clock" is said when it grows; with more than one no colour is said (one vote for
  both), and a colour that is the thing's name is not said ("2 orange oranges"). Unit-tested; live check left.

- Reader on real photos, on the emulator (user, 2026-10-13: "not on the phone, on the emulator, real images from
  online"). `tools/reader-photos/make_frames.py` fetches free photos from Wikimedia Commons (a laptop with English
  Wikipedia, code on a screen, a drive-thru menu board, a 1969 newspaper held at a slant, a museum plaque), cuts what
  a phone in portrait sees (1440 x 1920, a little hand shake) and writes the phrases that must be read; frames and
  photos stay out of git. `ReaderReplayTest.realPhotos` (`-e scene <name>`, `-e lines all`) reads them. Found and
  fixed: a read could time out (2 s, now 6 s; the emulator needs `ReaderEngine(timeoutS = 120)`); a plaque line was
  never read because its ornaments came as "O", "6", "9" in turn (sameLine leaves out a lone character at either
  end, and O among digits is 0); two readings of one line on top of each other were joined as pieces of a line.
  Emulator: `Pixel_8_2`, headless, `-memory 1536`; Play Store, Google app and other heavy apps disabled on it
  (`pm disable-user`) or the test process was killed for memory.

- Task 5 on real desk photos, on the emulator (2026-10-13): `tools/desk-photos/make_frames.py` (14 Commons desk
  photos, three portrait views each, 480 x 640) and androidTest `DetectorPhotosTest` (Lite2 on CPU). No "bed" in 42
  frames, but a plain wall was "refrigerator 0.72" over 59 % of a frame. Rule (user: yes): bed, couch, refrigerator,
  dining table and oven filling half the view or more need a tenth more (0.8 at the default)
  (`DetectionFilter.minScoreFor(Detection, …)`). After it: the wall goes, the rest as before. The phone's
  BedDiagnostics frames, when the user takes Live to that desk again, will show whether the "gray bed" was one.

### YOLO26 (user, 2026-10-13; branch `yolo26`)
- Licence: Nungil is AGPL-3.0 from now (user's choice; `LICENSE`, README "Credits and licences"), as YOLO26 is.
- Export: `tools/yolo26/export.py` in WSL (`run-export.sh`, `setup-litert.sh`): LiteRT export runs on Linux or macOS
  only, needs `nms=False` for the end-to-end head ([1, 300, 6]: x1, y1, x2, y2 in input pixels, score, class), and
  `litert-torch` with the CPU torch (its default pulls CUDA wheels that fill WSL's /tmp). Input [1, 3, 640, 640].
  The model (9.5 MB) goes to `app/src/main/assets/yolo26n.tflite`, not in git and not downloaded by the build:
  without it, the YOLO choice falls back to EfficientDet-Lite2.
- App: `core/scan/YoloOutput` (letterbox fit, rows to detections, COCO names; unit-tested), `scan/YoloDetector`
  (TFLite interpreter, CPU, 4 threads), `SharedDetector` gives detections for both kinds, `ModelChoice.YOLO`
  (Settings "YOLO26", voice "model YOLO" / "욜로 모델").
- Emulator, 42 desk frames (`DetectorPhotosTest.yoloNextToLite2`): YOLO26n is surer (laptops 0.91-0.98 against
  0.77-0.86) and finds what Lite2 missed (hands, a phone and a laptop seen from above; a bottle on a shelf; two
  laptops), misses one laptop at the edge Lite2 had at 0.70; 118 ms a frame against 101 ms (emulator CPU).
- Phone (user: "phoneda boshlayvering"), the same 42 frames: on the CPU YOLO26 took 379 ms a frame against Lite2's
  234 ms on the GPU. Now exported 640 high and 480 wide (Live's frame as it is: no bars, a quarter less work) and on
  the TFLite GPU delegate (`tensorflow-lite-gpu` 2.17, a first frame proves it, else the CPU): 155 ms against
  Lite2's 135 ms on the GPU in the same run; YOLO26 would say laptop 20, bottle 7, cup 5, phone 4 against Lite2's
  18, 4, 7, 2.
- Left: a live check with "YOLO26" chosen in Settings; then whether it becomes the default; hosting the exported
  model (e.g. a GitHub release) so a build elsewhere gets it.
