# Hackathon Brief: On-Device Scene Assistant for Blind Users (Android)

This brief holds lessons from a previous prototype (ML Scanner, Sept 2026, 82 commits). It contains **ideas, decisions, numbers and pitfalls only, with no code**. The hackathon project must be written from zero. Use this brief to skip research and avoid known dead ends.

Target device used so far: Infinix X6880, Android 15 (API 35), arm64-v8a, 8 GB RAM, gyroscope + compass + accelerometer. Laptop: i5-11400H, 16 GB RAM, RTX 2050. Drive C: is almost full, so keep SDK caches, models and builds on D:.

---

## 1. The idea in one paragraph

A blind or low-vision user holds the phone upright and turns around (or walks). Everything runs on the phone with no network. The app detects objects, gives each one a direction (from the rotation sensor) and a color, removes duplicates, and speaks a short summary: *"Around you: 3 blue chairs in front; a black laptop on your right."* On top of that it can recognize saved people and saved objects by name, find a named thing with beeps and speech, take voice commands, and warn about obstacles while walking.

Pitch angle: accessibility, fully on-device (privacy, works offline), with real sensor fusion (compass + camera FOV + depth). No open-source repo was found that combines detection with compass heading and 360° scanning, so that part is original.

---

## 2. Stack that worked

| Need | Choice | Notes |
|---|---|---|
| Base | MediaPipe object detection Android sample (`google-ai-edge/mediapipe-samples`, `examples/object_detection/android`, Apache-2.0) | Gives CameraX, a detector helper and a box overlay. Starting from it saved 1–2 hours of camera wiring. Starting from the public sample is fine, since it is not your own prior code. |
| Language / UI | Kotlin, Fragments + XML, Navigation component, Material Components 1.12 (Material 3 theme) | Kept the sample's toolkit and did not migrate to Compose. |
| Camera | CameraX | ARCore owns the camera in walk mode instead (see §6). |
| Object detection | `com.google.mediapipe:tasks-vision` ObjectDetector, COCO 80 classes | Models are listed in §3. |
| Heading | `SensorManager` `TYPE_ROTATION_VECTOR`, `remapCoordinateSystem(AXIS_X, AXIS_Z)` for upright phone, low-pass filtered | Lock the app to portrait, because the math assumes it. |
| Speech out | Android `TextToSpeech`, `Locale.US` | |
| Speech in | Android `SpeechRecognizer`, en-US, prefer offline | See the microphone pitfalls in §7. |
| Faces | ML Kit Face Detection 16.1.7 (bundled) + FaceNet-512 TFLite (`shubham0204/FaceRecognition_With_FaceNet_Android`, Apache-2.0) on `org.tensorflow:tensorflow-lite` 2.17.0 | |
| Saved objects | MediaPipe ImageEmbedder with `mobilenet_v3_small` (about 4 MB, L2-normalized) | |
| Outlines | MediaPipe interactive segmenter | Optional; mostly eye candy. |
| Codes / text | ML Kit barcode-scanning 17.3.0, ML Kit text-recognition v2 (bundled, offline) | |
| Depth | ARCore `com.google.ar:core` 1.49.0, `DepthMode.AUTOMATIC` | |
| Storage | Room with kapt (the sample already applies kapt), SharedPreferences for settings | |
| Tests | JUnit 4 on a pure-Kotlin `core/` package with no Android imports | |

**Not chosen, with reasons:**
- ML Kit Object Detection only has 5 coarse categories and at most 5 objects per frame.
- ML Kit Image Labeling has no boxes.
- Ultralytics apps are AGPL-3.0.
- Assistive apps found on GitHub (EyeVis, A-EYE and others) are stale or unlicensed, so do not copy from them.
- The Palette library was dropped for color (see §5).

**Fine-tuning datasets** (stretch goal, never used): Roboflow Universe has Classroom Dataset (307 images: bag, chair, desk), classroom-count-det (195), Whiteboards Detection (270) and ClassRoom (yh). None has whiteboard, projector, desk and chair together. Stock COCO already covers chair, laptop, tv, book, backpack, person, bottle, clock, keyboard, mouse and cell phone. **Verdict: fine-tuning was not worth it in a short timeframe.**

---

## 3. Models and measured decisions

| Role | Model | Verdict |
|---|---|---|
| Accurate | EfficientDet-Lite2 (448 px input) | Became the default. Finds far and half-hidden people much better than Lite0. |
| Fast | EfficientDet-Lite0 | Fine for close objects. |
| Light | SSD MobileNet V2 | Added late; the cheapest option. |
| Walking | EfficientDet-Lite0 **int8** | A quarter of the size and faster on CPU. Good for the walking loop. |
| Fallback namer | EfficientNet-Lite0 classifier (1000 ImageNet classes, minimum score 0.35) | When COCO finds nothing, it names the middle 50% of the frame. |

- **Delegate:** GPU by default, with an automatic fallback to CPU when GPU fails to initialize. The fallback must recreate the detector only if the old one is closed and must survive an already-shut executor, or the app crashes.
- **Detector threshold:** run the detector itself at 0.3, then filter per label. The final defaults were:
  - Everything else needs **0.7** (0.5 caused false chairs and books).
  - People are accepted 0.2 lower, but never under 0.3. Far and half-hidden people score low.
- **Edge-clipped boxes:** do not count a box that touches exactly one side edge (2% margin). This removed half-object double counts.
- Log inference time every 30 frames to decide CPU vs GPU. No hard numbers were recorded, so measure early at the hackathon.

---

## 4. Core algorithms (keep these; they are the "AI Engineering" talking points)

**Direction of an object**
- Object angle = relative heading + (box center X − 0.5) × horizontal FOV.
- Compute the FOV from `CameraCharacteristics` as 2·atan(sensorWidth / (2·focal)), using the sensor side that maps to the portrait width. Fall back to 65°.
- Front camera: add 180° to the base direction. The analyzed frame is not mirrored, but the preview is, so mirror the overlay only.
- Directions: 8 sectors of 45°, or 4 sectors (front, right, behind, left) for speech.

**Coverage**
- 36 bins of 10°. A full scan auto-stops when all bins are covered, or after a 60 s timeout, in which case the summary is prefixed with "I scanned N percent of the room."

**Deduplication (most important)**
- A detection joins a cluster when the label matches and the angle is within 20° (circular mean).
- **Count = the maximum number seen in a single frame. Never sum across frames**, or 3 chairs over 100 frames become 300.
- A cluster is confirmed after 3 frames. Unconfirmed clusters are never spoken.
- Color is a majority vote inside the cluster, not part of the cluster key, so color flicker cannot split one object into two.

**Summary wording**
- Singular and plural ("a chair", "3 chairs").
- Same-label objects in one direction are grouped: "3 blue chairs" when they share a color, otherwise "4 chairs in blue, red and gray".
- The last two groups are joined with "and".
- People never get a color. Saved names get no article or color ("Ali in front").
- A plain "person" cluster within 20° of a named person is dropped.
- Empty result: "No objects found. Try better lighting and turn slowly."

**Sticky names:** once a tracked object has been recognized as a saved name, it keeps that name across frames instead of flickering back to "person".

**Speech queue:** leave at least 1.5 s between announcements and drop stale ones when more than 3 are pending. Flush before the summary, let the summary finish (15 s safety limit), and warn "Slow down" when turning faster than 60°/s (at most once every 5 s).

---

## 5. Color naming (hard-won)

- **What failed:** Palette's dominant swatch. Shadows and warm lamps turned everything gray, black or brown.
- **What works:**
  1. Sample 24×24 pixels from the center 50% of the box.
  2. Apply gray-world white balance.
  3. Let every pixel vote for one of 11 basic names using HSV rules.
  4. A colorful name wins once colorful pixels make up at least 30% of the named pixels.
  5. A cluster reports a color only with at least 2 votes and a clear majority.
- **HSV rules:**
  - v < 0.2 is black.
  - s < 0.15 is white (if v > 0.8) or gray.
  - Hue boundaries: red < 15 or ≥ 345, orange 15–45 (brown if v < 0.6), yellow 45–70, green 70–170, blue 170–260, purple 260–290, pink 290–345.
  - A light red (s < 0.5, v > 0.7) is pink.
- **Dark frames:** give no color.

---

## 6. Feature notes, including what each one cost

| Feature | Value for demo | Cost / risk | Notes |
|---|---|---|---|
| Full 360° scan + summary | Core, must have | Low | The heart of the pitch. |
| Live scan (announce as found) | High | Low | Also show the announcements on screen. |
| History (Room) + replay | Medium | Low | Save on a process-wide scope, or the write is lost when the screen closes. |
| Settings (camera, CPU/GPU, model, confidence, speech, colors) | Medium | Low | Remember the last choices. |
| Saved people (face recognition) | **Very high wow** | Medium-high | Needed one serious fix round (below). |
| Saved objects (embeddings) | High | Medium | |
| Search ("find my bag") with beeps | **Very high wow** | Medium | Very demoable. |
| Voice commands | High | Medium | The microphone is fragile. |
| Voice guide / learner mode | High for accessibility judges | Low | |
| Object outlines (segmentation) | Visual wow only | Medium | Skip unless there is time. |
| QR / barcode reading | Medium | Low | Cheap with ML Kit. |
| Walk mode (ARCore depth, hazards, signs, traffic lights, GPS beacon) | High, but risky live | **Very high** | Took about 12 commits including many perf and crash fixes. |

**Face recognition fixes (strangers were matched as saved people):**
- The match threshold went from cosine 0.3 (the reference repo value) to **0.5**, and the winner must also beat the runner-up by **0.08**.
- A person's score is the average of their **3 best samples**, not one lucky sample.
- Faces are leveled by eye angle when tilted 3° or more, and embedded twice (normal and mirrored, averaged).
- Skip faces smaller than 64 px, or turned more than 35° left/right or 25° up/down.
- Enrollment covers 5 poses (straight, left, right, up, down) × 4 samples. Pose gates on ML Kit angles: straight |yaw| and |pitch| < 10; left/right |yaw| > 20; up/down |pitch| > 12.
- FaceNet preprocessing: resize to 160×160, RGB floats, per-image standardization.
- Run face recognition every 3rd frame that has a person box. A face inside a person box renames that box.

**Saved objects:**
- Matching threshold: cosine **0.75** between embeddings.
- Enrollment takes 12 samples: hold still, move left, move right.
- Enroll at confidence 0.3 and use the **most common** label instead of locking onto the first one seen.
- The first box must cover at least 5% of the frame.
- Embed at most 3 crops per frame.
- Search accepts any non-person class, because items are matched by how they look.

**Search guidance:**
- Five zones by box center: far left < 0.2, left < 0.4, ahead 0.4–0.6, right ≤ 0.8, far right.
- The beep interval shrinks linearly from 1000 ms at the edge to 150 ms at the center.
- Vibrate 60 ms when the target enters the center.
- Say "Lost it" after 3 s without a match.
- Announce a direction change at most every 2 s.
- The search detector runs at confidence 0.4.

**Search text resolution:**
- Normalize the query: lowercase, strip filler words (find, where is, my, the, and so on).
- Match saved names first: exact, then containment, then Levenshtein distance ≤ 2 for names of 4+ characters.
- Then match COCO labels, with plurals stripped and synonyms: phone → cell phone, desk/table → dining table, sofa → couch, monitor/screen → tv, bag → backpack, bike → bicycle, computer/notebook → laptop.
- Pitfall: a saved name that is also a command word ("Me") got stripped. Match names against the raw words too.

**Walk mode:**
- **Frame conversion:**
  - Converting through JPEG cost more than the detector itself. Convert YUV directly with integer math and downsample.
  - Better still, draw the ARCore camera texture into an offscreen buffer and read it back on the GPU: "a few ms instead of tens".
  - Keep the GL thread to bulk copies into reused buffers, and do the RGB conversion on a worker thread.
- **Crash on close:** ARCore crashed natively when the session closed while the GL thread was running. Pause the GL view first and use a closing flag.
- **Hazards:**
  - Confidence 0.7, and the object must appear in 3 of the last 5 frames.
  - Use a short class list: people, vehicles, dogs, bench, chair, plant and street furniture.
- **Walls and doors from depth:** check the chest-height band (30–62% of the image) for readings 0.3–8 m away. A zone is blocked when 40% of its readings are within 3 m.
- **Steps, kerbs and drop-offs from depth geometry** (no model exists): look 0.6–4 m ahead in the middle third. Heights within 0.09 m count as floor, up to 0.8 m as a step, and 3 or more rises mean stairs.
- **Quiet policy:** speak only when something changes, never repeat within 6 s, and never say "safe".
- **Analysis:** every 150 ms; detector input 480×360.

---

## 7. Pitfalls to avoid from minute one

1. **Microphone dies after silence.** Rebuild the `SpeechRecognizer` after 3 errors in a row, retry after about 700 ms, listen at least 8 s, and end after 2 s of silence. Stop TTS before listening.
2. **Voice parsing:** match keywords, not whole sentences. Check "listening"/"microphone" before "stop" so they do not end a scan.
3. **Detector shutdown:** wait at most 2 s, never forever.
4. **Frame errors:** a detector frame error must not disable the Stop button.
5. **Hard-coded models:** keep one shared detector-options place so every screen follows the settings.
6. **Heavy work on the analysis thread** (segmentation, embeddings, OCR): move it to a single background thread with a busy flag, and keep drawing the last result.
7. **Portrait lock** from the start, because the heading and FOV math depend on it.
8. **Room migrations:** plan the schema early. Adding tables later needed migrations 1→2→3.
9. Put **model files in assets**, fetched by a Gradle download task, and keep them on D:.

---

## 8. UI: what was done and how to make it better

**Done:**
- Teal Material 3 light theme.
- Home with large cards (Full scan, Search, Saved) and a floating mic button.
- A scan hub (Full, Live, History, Walk).
- A settings screen before scanning.
- The scanner shows a 360° coverage ring, an elevated controls card, a large Start/Stop button that turns red, and a "View text" log dialog.
- Saved screen with tabs (People, Cars, Objects).
- Guided enrollment with a progress bar and spoken percent.
- A voice guide: tapping a control speaks its name, tapping empty space reads the screen.

**Improve for the hackathon:**
- **Voice-first single flow.** Many screens (16 destinations) are hard for a blind user. Aim for one main screen where everything is reachable by one big button + voice, with a small secondary menu for sighted helpers.
- **TalkBack compatibility.** The custom voice guide can fight TalkBack. Set proper content descriptions, headings and focus order, and test with TalkBack on.
- **High contrast and huge touch targets.** Add a dark / high-contrast theme and at least 64 dp buttons. Put the main action at the bottom within thumb reach.
- **Haptics as a language:** distinct vibration patterns for found, lost, obstacle and done.
- **Onboarding in 20 seconds:** spoken tutorial on first launch, and an optional calibration prompt (figure 8) when compass accuracy is low.
- **Demo view for judges:** a big readable caption of what is spoken, plus boxes and the coverage ring. Mirror with `scrcpy` over USB.
- **Visual polish:** filled outlines were liked. Use them only on the target in search mode, not on everything.

---

## 9. Suggested hackathon plan (scope first)

**MVP, in this order:**
1. Sample import, portrait lock, detector on GPU with CPU fallback.
2. Pure `core/` with tests: angle math, coverage, deduplication clusterer, color mapper, summary builder.
3. Full 360° scan + live scan + TTS + coverage ring.
4. Search "find X" for COCO labels with beeps and vibration.
5. Voice commands (with the microphone fixes from day one).

**Wow layer (pick 1–2):**
- Saved people (use the tuned face thresholds right away).
- Saved objects.
- QR / sign reading.

**Only if everything above is solid:** walk mode. It is impressive but the riskiest part for a live demo.

**Skip:** fine-tuning, outlines on every object, history (unless there is time; it is cheap), and the GPS beacon.

**Demo script (3–4 min):**
1. Live scan in the room.
2. Full scan: the ring fills and the summary is spoken.
3. "Find my bag": beeps get faster as the phone points at it.
4. A saved person is recognized by name.
5. One architecture slide.

**Talking points:** on-device inference, max-in-single-frame deduplication, heading + FOV fusion, tuned face matching (threshold + margin + best-3 average), and the accessibility use case.
