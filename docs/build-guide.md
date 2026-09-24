# On-Device Scene Assistant for Blind Users — Build Guide

Everything needed to build the Android app end to end: the product, the stack with exact versions,
the architecture, the tuned numbers that make each feature work, the pitfalls that cost hours, the
build order, and the demo. Written so that a two-person team with one Android phone can follow it
top to bottom under hackathon time pressure.

Companion document: `docs/hackathon-brief.md` holds the measured decisions and rejected options
behind the choices made here.

**How to read this.** Sections 1–5 are setup and must be done first. Section 6 (threads) and
section 8 (the pure-Kotlin brain) are what you build next. Section 7 is reference material for walk
mode, which is built last; skip it until then. Sections 11–13 are the checklists to keep open while
coding, and 14–18 are what turn working code into a demo that wins.

| § | Contents |
|---|---|
| 1–2 | product, flows, target device and workstation |
| 3–5 | versions, models and their URLs, manifest and platform setup |
| 6 | threading model: which thread does what, and what it must never do |
| 7 | walk mode: ARCore, depth, geometry, every number (build last) |
| 8 | the `core/` package: every algorithm and tuned constant, with tests |
| 9 | the Android layer: screens, navigation graph, Room, recognition engines, speech |
| 10 | voice control: recognizer setup, the traps, the parser, mishearings |
| 11 | 25 pitfalls with the fix next to each |
| 12 | build order in gates, with a stop condition per gate |
| 13 | unit tests, the device QA checklist, useful commands |
| 14 | accessibility and UI practice |
| 15 | optional street navigation (South Korea) |
| 16–18 | risks and fallbacks, demo script and talking points, repository hygiene |

---

## 1. Product

**One sentence.** A blind or low-vision user holds the phone upright, turns around or walks, and the
phone tells them what is around: what the objects are, which direction each one is in, and what
colour it is — entirely on the device, with no network.

**Why it works as a project.** Accessibility is a real need, on-device inference is a real
engineering constraint (privacy, latency, offline), and the sensor fusion (camera field of view plus
compass heading plus depth) is the part no off-the-shelf library does for you.

**Core flows, in the order a user meets them:**

1. **Full 360° scan.** The user turns in place. The app detects objects, gives each a compass
   direction and a colour, removes duplicates, and speaks one summary:
   *"Around you: three blue chairs in front; a black laptop on your right."*
2. **Live scan.** Same detection, announced as things are found, no summary.
3. **Search.** *"Find my bag."* The phone beeps faster as the camera points at the target and
   vibrates when it is centred.
4. **Saved people and objects.** Enrolled faces and objects are called by name inside a scan.
5. **Voice control.** Every screen is reachable by speech; the microphone stays on until switched
   off.
6. **Walk mode.** While walking, the app warns about obstacles, steps and walls using depth.

**Non-goals.** No cloud inference, no account, no map of the room, no fine-tuned model. Each of
these was considered and dropped for time or for the offline promise.

---

## 2. Target device and workstation

- **Phone:** any Android 9+ (API 24 is the floor) with a gyroscope and compass. Reference device:
  Infinix X6880, Android 15 (API 35), arm64-v8a, 8 GB RAM. ARCore depth is needed only for walk
  mode, and it is depth-from-motion on this class of phone, not a time-of-flight sensor.
- **Sensors used:** rotation vector (heading), accelerometer (steps), camera, microphone, vibrator,
  GPS (only for saved places).
- **Workstation:** Android Studio with SDK 35, JDK 17, 16 GB RAM is comfortable. Keep the SDK,
  Gradle caches, models and build outputs on a drive with at least 20 GB free; the models alone are
  about 130 MB and the Gradle cache grows past 5 GB.
- **USB debugging** on the phone, plus `scrcpy` to mirror the screen for the demo.

---

## 3. Stack, with exact versions

These versions build together. Do not "upgrade to latest" during the event.

**Build tooling**

| Piece | Version |
|---|---|
| Gradle wrapper | 8.14.3 |
| Android Gradle Plugin | 8.11.0 |
| Kotlin | 2.1.0 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 |
| Java target | 1.8 |
| Annotation processing | `kotlin-kapt` (Room) |
| Navigation safe-args | 2.8.9 |
| Model downloads | `de.undercouch:gradle-download-task:4.1.2` |

**App dependencies**

| Area | Artifact | Version |
|---|---|---|
| Kotlin | `org.jetbrains.kotlin:kotlin-stdlib-jdk8` | 2.1.0 |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 |
| Core | `androidx.core:core-ktx` | 1.13.1 |
| UI | `androidx.appcompat:appcompat` | 1.7.0 |
| UI | `com.google.android.material:material` | 1.12.0 |
| UI | `androidx.constraintlayout:constraintlayout` | 2.0.4 |
| UI | `androidx.recyclerview:recyclerview` | 1.3.2 |
| UI | `androidx.fragment:fragment-ktx` | 1.8.5 |
| UI | `androidx.window:window` | 1.0.0-alpha09 |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 |
| Lifecycle | `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.8.7 |
| Navigation | `androidx.navigation:navigation-fragment-ktx` | 2.8.9 |
| Navigation | `androidx.navigation:navigation-ui-ktx` | 2.8.9 |
| Camera | `androidx.camera:camera-core` | 1.4.2 |
| Camera | `androidx.camera:camera-camera2` | 1.4.2 |
| Camera | `androidx.camera:camera-lifecycle` | 1.4.2 |
| Camera | `androidx.camera:camera-view` | 1.4.2 |
| Detection | `com.google.mediapipe:tasks-vision` | 1.0.0 |
| Faces | `com.google.mlkit:face-detection` | 16.1.7 |
| Face embeddings | `org.tensorflow:tensorflow-lite` | 2.17.0 |
| Text | `com.google.mlkit:text-recognition` | 16.0.1 |
| Codes | `com.google.mlkit:barcode-scanning` | 17.3.0 |
| Depth / AR | `com.google.ar:core` | 1.49.0 |
| Storage | `androidx.room:room-runtime`, `room-ktx`, `room-compiler` | 2.7.2 |
| Tests | `junit:junit` | 4.13.2 |
| Instrumented tests | `androidx.test.ext:junit` 1.2.1, `androidx.test:core` 1.6.1, `androidx.test:runner` 1.6.2 |

**Gradle settings that matter**

- `buildFeatures { viewBinding true; dataBinding true }` — view binding is used everywhere; data
  binding comes with the MediaPipe sample layouts.
- `androidResources { noCompress 'tflite', 'task' }` — without this, MediaPipe fails to memory-map
  the models from assets.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx1536m`, `android.useAndroidX=true`.
- Proguard is configured for release but the demo runs the debug build.

**Everything is free.** No paid API, no key, no billing account anywhere in the core app.

---

## 4. Models

All models are downloaded at build time into `app/src/main/assets/` by a Gradle task that `preBuild`
depends on, so a fresh clone builds without manual steps. Keep `overwrite = false` so repeat builds
do not re-download 130 MB.

| Role | File | Source URL | Size |
|---|---|---|---|
| Detector, accurate (default) | `efficientdet-lite2.tflite` | `storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite2/float32/1/efficientdet_lite2.tflite` | 23 MB |
| Detector, fast | `efficientdet-lite0.tflite` | `.../object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite` | 14 MB |
| Detector, lightest | `ssd-mobilenet-v2.tflite` | `.../object_detector/ssd_mobilenet_v2/float32/1/ssd_mobilenet_v2.tflite` | 11 MB |
| Detector for walking (CPU) | `efficientdet-lite0-int8.tflite` | `.../object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite` | 4.6 MB |
| Fallback namer, 1000 classes | `efficientnet-lite0.tflite` | `.../image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite` | 18 MB |
| Object embeddings | `mobilenet_v3_small.tflite` | `.../image_embedder/mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite` | 4 MB |
| Face embeddings | `facenet_512.tflite` | `raw.githubusercontent.com/shubham0204/FaceRecognition_With_FaceNet_Android/master/app/src/main/assets/facenet_512.tflite` | 24 MB |
| Object outlines (optional) | `interactive_segmentation.task` | `.../interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task` | 30 MB |

Licences: MediaPipe models are Apache-2.0 from Google; FaceNet-512 comes from an Apache-2.0
repository. Both are safe to ship and safe to name on a slide.

**Model choice, measured.** EfficientDet-Lite2 (448 px input) finds far and half-hidden people much
better than Lite0 and became the default for scanning. The int8 Lite0 is a quarter of the size and
the right choice for the walking loop on the CPU. **The int8 model cannot run on the GPU**: its input
tensor is `UINT8`, MediaPipe feeds the GPU `FLOAT32`, and the graph fails on the first frame with
`ToTensorConverter: input data size does not match expected size`. Use the float model on the GPU and
the int8 model on the CPU.

---

## 5. Manifest and platform setup

```
permissions: CAMERA, RECORD_AUDIO, VIBRATE,
             ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACTIVITY_RECOGNITION
uses-feature: android.hardware.camera
queries: android.intent.action.TTS_SERVICE, android.speech.RecognitionService
meta-data: com.google.ar.core = optional
activity: screenOrientation="portrait", exported="true", theme="@style/AppTheme"
```

- **`queries` is required on API 30+**, or `SpeechRecognizer.isRecognitionAvailable()` and the TTS
  engine lookup return nothing on some phones even though both are installed.
- **`com.google.ar.core = optional`**, never `required`: the app must install and run on phones
  without ARCore, with walk mode disabled.
- **Portrait lock from the first commit.** The heading maths and the field-of-view maths both assume
  an upright phone; allowing rotation silently breaks every direction the app speaks.
- Runtime permissions are requested per feature, not all at startup: camera before scanning,
  microphone before voice, location only when a place is saved.

---

## 6. Threading model

Get this right on day one; almost every crash and stutter in this kind of app is a thread mistake.

| Thread | Owns | Never does |
|---|---|---|
| Main | UI, TextToSpeech, SpeechRecognizer, navigation, vibration | inference, bitmap conversion |
| CameraX analysis executor (single) | detector calls on camera frames | speaking, UI updates |
| Extras executor (single) | face embeddings, object embeddings, OCR, barcodes, classifier | detector calls |
| GL thread (walk mode only) | ARCore `Frame.update()`, texture draw, bulk depth copy, offscreen readback | inference, allocation per frame |
| Room writes | a process-wide coroutine scope | the fragment scope |

Rules that follow from the table:

- One **busy flag** (`AtomicBoolean`) per worker. A frame that arrives while the worker is busy is
  dropped, and the overlay keeps drawing the last result. Queueing frames turns a 200 ms hiccup into
  a ten-second lag.
- The **GPU delegate must be created and used on the same thread**. Create the detector inside the
  analysis executor, not in `onViewCreated`.
- **Detector shutdown waits at most 2 s** (`awaitTermination`), never forever, or leaving a screen
  can hang the app.
- **Room writes run on a process-wide scope.** A write started in a fragment scope is cancelled when
  the screen closes, and the scan silently never appears in history.
- `TextToSpeech` and `SpeechRecognizer` are main-thread-only APIs. Post to the main looper from
  workers instead of touching them directly.

---

## 7. Walk mode (the highest-risk feature; build it last)

Walk mode replaces CameraX with ARCore, because it needs metric depth. Everything here was paid for
in crashes and wrong announcements.

**Session setup** (`ArCamera`):

```kotlin
config.depthMode = DepthMode.AUTOMATIC          // only if isDepthModeSupported
config.semanticMode = SemanticMode.ENABLED      // only if isSemanticModeSupported
config.focusMode = FocusMode.AUTO
config.updateMode = UpdateMode.LATEST_CAMERA_IMAGE
config.lightEstimationMode = LightEstimationMode.DISABLED
config.planeFindingMode = PlaneFindingMode.DISABLED
```

Plane finding and light estimation are pure cost here; turn them off. Check every capability with
`isXSupported` and keep working without it.

**Teardown order, or ARCore crashes natively:**

1. set a `closing` flag so `onDrawFrame` returns immediately;
2. `queueEvent { capture.release() }` to free GL objects on the GL thread;
3. `glView.onPause()` — the GL thread must be stopped **before** the session;
4. `arCamera.pause()`, then `close()` after the executors have drained.

**Frames off the GPU, not through JPEG.** Draw the ARCore camera texture into an offscreen
framebuffer and `glReadPixels` it back: a few milliseconds instead of tens. Two buffers are used: a
detector frame 360 px wide and a sign-reading frame 720 px wide. `glReadPixels` returns the image
bottom-up, so flip it once.

**The capture must have the screen's shape.** A fixed 4:3 buffer on a portrait phone stretches the
picture about three times sideways, and a stretched wall is labelled "file cabinet" or "refrigerator"
by the classifier. Size the offscreen buffers from the GL viewport: `width = 360`,
`height = 360 * viewHeight / viewWidth`.

**Depth must be rotated into screen space.** ARCore hands out depth (and semantics) in camera-sensor
orientation, sideways and wider than the screen shows. Read it as if it were the screen image and
"chest height" becomes a vertical stripe, "ahead" becomes somewhere else, and close walls are never
found. Build a lookup table once per display geometry change with
`frame.transformCoordinates2d(VIEW_NORMALIZED, grid, IMAGE_NORMALIZED, out)` and sample the depth
image through it into a screen-shaped grid 90 columns wide. Rebuild it when
`frame.hasDisplayGeometryChanged()` is true or the depth image size changes.

**Focal length must be scaled to the picture.** `camera.imageIntrinsics.focalLength` is in
camera-image pixels. The portrait screen shows the camera image's long side top to bottom, so
multiply by `captureHeight / max(imageDimensions)` before using it in any geometry.

**Walls and doors from depth.** Look at the band 30–62% down the image (chest height, not floor or
ceiling), accept readings 0.3–8 m, and call a third of the width blocked when at least 40% of its
valid readings are within 3 m. Report the lower quartile of the close readings, so one stray pixel
cannot shout.

**A close wall stays close when depth disappears.** Right in front of a plain wall, depth-from-motion
has nothing to measure and nothing to track, so readings vanish. Treating "could not measure" as
"clear" silences the warning exactly when the user is about to walk into the wall. Distinguish the
two: if under 20% of the band ahead has valid readings, the way ahead is *unknown*, and the last
reading closer than 1.2 m is held for up to 10 s, until a measured clear way cancels it.

**Steps, kerbs and stairs from geometry, not a model.** For each image row compute the ray angle
below the horizon, then for each sample the height above the floor:
`height = cameraHeight - distance * sin(alpha)` with `forward = distance * cos(alpha)`. Heights
within 0.09 m are floor, up to 0.8 m are a step, below −0.09 m are a drop. Look 0.6–4 m ahead in the
middle third and require at least three samples per row.

Two rules keep this honest:

- A rise with something **taller than 0.8 m standing within 0.4 m of it** is the bottom of a wall or
  a piece of furniture, not a step. Without this, every wall is announced as a step.
- **Stairs climb.** Group rising bands into hand-wide distance bins, and call it stairs only when the
  bins never get lower with distance and at least two of them rise by 0.05 m or more. Depth noise
  spreads one wall over several distances, which is what makes a wall sound like a flight of stairs.

**Ground class (road, sidewalk, terrain) is outdoor-only.** ARCore scene semantics guesses indoors,
so it says "road under you" in a classroom. Read the class at the screen's bottom-centre through the
same coordinate transform, require the confidence image to be at least 200 of 255, and only speak it
when sky has been seen (`getSemanticLabelFraction(SKY) >= 0.02`) within the last minute.

**Hazards from the detector.** Confidence 0.7, and the label must appear in 3 of the last 5 frames.
Keep the class list short: person, bicycle, car, motorcycle, bus, truck, dog, bench, chair, couch,
dining table, potted plant, stop sign, fire hydrant, suitcase. Draw boxes only for these classes;
drawing every COCO class puts "scissors" and "book" on walls and floors.

**What to say, and when.** Priority: floor change, then hazard, then ground class, then traffic
light, then a saved thing, then a sign, then a code. Speak only on change, never repeat inside 6 s,
and never say "safe". Distances are spoken in steps when the step counter is available
(`0.415 × body height`), otherwise in metres.

**Beeps instead of words for closeness.** The beep interval shrinks linearly from 1000 ms at 4 m to
150 ms at 0.5 m, plus a 60 ms vibration under 1 m. This is what makes the mode usable with the phone
held low, and it is the part demo audiences remember.

**Pacing.** Analyse every 150 ms; read signs every 2.5 s; name an unknown obstacle every 1.5 s; look
for saved objects every 900 ms and do not repeat the same one inside 30 s.

**Prove the delegate before trusting it.** Run one throwaway 64×64 detect after creating the
detector. A GPU that loads a model it cannot execute fails on the first real frame, and the failure
surfaces later as a crash while closing. On failure, close it and fall back to the CPU. Log which
delegate won.

---

## 8. The `core/` package: pure Kotlin, no Android imports

This is the heart of the project and the reason it can be tested at all. Every rule that decides what
the app says lives here as a plain Kotlin function with no Android dependency, so it runs on the JVM
in milliseconds. Twenty-four files, 243 unit tests, all green before a feature is wired to a screen.

**Build this first.** With `core/` done and tested, the Android layer becomes glue: read a frame,
call `core`, speak the answer.

### 8.1 Geometry and direction

**`AngleMath`** — `normalize(deg)`, `diff(a, b)`, `sector8(relDeg)`, `sector4(relDeg)`,
`weightedMean(mean, count, sample)`. The weighted mean is a circular mean, which keeps a cluster's
angle stable as new frames arrive and does not break across 0°/360°.

**`BoxGeometry`** — everything that turns a detector box into a direction:

- `horizontalCenter(...)` — box centre in 0..1 of the *upright* image, given the frame rotation.
- `toUpright(...)`, `uprightPointsToRaw(...)` — rotate boxes and outline points between the analysed
  frame and the screen.
- `objectAngle(relHeading, centerNorm, hfovDeg, facing)` — the whole sensor fusion in one line:
  `relHeading + (centerNorm - 0.5) * hfov`, plus 180° for the front camera.
- `touchesOneSideEdge(..., margin = 0.02f)` — a box touching exactly one side edge is a half-seen
  object. Dropping those removed most double counts.

**Field of view** comes from `CameraCharacteristics`: `2 * atan(sensorWidth / (2 * focal))`, using
the sensor side that maps to the portrait width, with a 65° fallback when the camera reports nothing
usable. The front camera's analysed frame is not mirrored while the preview is, so mirror the overlay
only, never the maths.

### 8.2 Coverage of a 360° turn

**`CoverageTracker(binCount = 36)`** — 36 bins of 10°. `mark(relHeading)` fills one bin,
`markArc(from, to)` fills everything between two headings, capped at `MAX_ARC_DEG = 45f` in
`STEP_DEG = 5f` steps, so a fast turn leaves no holes but a spin cannot claim the whole room.
`percent()` and `isComplete()` drive the on-screen ring and the auto-stop.

### 8.3 Deduplication, the single most important algorithm

**`ObjectClusterer(mergeDeg = 20f, confirmFrames = 3)`** over
`FrameDetection(label, angle, color, isName)`:

- A detection joins a cluster when **the label matches and the angle is within 20°**.
- **A cluster's count is the maximum seen in one frame, never a sum across frames.** Summing turns
  three chairs into three hundred over a hundred frames. This one rule separates a working demo from
  nonsense.
- A cluster is spoken only after it has been seen in **3 frames** (`confirmed()`).
- Colour is a **majority vote inside the cluster** (`MIN_COLOR_VOTES = 2`) and is deliberately *not*
  part of the cluster key, so colour flicker cannot split one chair into two.

**`StickyNames(confirmHits = 2, forgetAfterFrames = 15, minIou = 0.3f)`** — once a tracked box has
been recognised as a saved name it keeps that name, instead of flickering back to "person".

### 8.4 Colour naming

Three small files, because the obvious approach fails: a Palette dominant swatch turns everything
grey, black or brown under shadows and warm lamps.

- **`WhiteBalance`** — gray-world gains, clamped to `MIN_GAIN = 0.6f … MAX_GAIN = 1.6f`.
- **`ColorMapper.nameFromHsv(h, s, v, frameIsDark)`** — 11 basic names. `v < 0.2` is black;
  `s < 0.15` is white above `v = 0.8` and grey below; hues: red `< 15` or `>= 345`, orange `15–45`
  (brown below `v = 0.6`), yellow `45–70`, green `70–170`, blue `170–260`, purple `260–290`, pink
  `290–345`; a light red (`s < 0.5`, `v > 0.7`) is pink. A dark frame returns no colour at all.
- **`ColorVote`** — sample 24×24 pixels from the centre 50% of the box, white-balance them, let each
  pixel vote for a name, and let a colourful name win once colourful pixels reach
  `MIN_CHROMATIC_SHARE = 0.3f`.
- **`ColorPolicy.hasColor(label)`** — people never get a colour.

### 8.5 What the app says

**`SummaryBuilder`** over `ObjectSummary(label, count, color, angle, isName)`: `plural(label)`,
`describe(o)`, `livePhrase(o)`, `fullSummary(objects, coveragePercent)`. The wording rules:

- singular and plural ("a chair", "three chairs");
- same-label objects in one direction are grouped: "three blue chairs" when they share a colour,
  otherwise "four chairs in blue, red and gray";
- the last two groups join with "and";
- saved names get no article and no colour ("Ali in front");
- incomplete coverage is admitted: "I scanned 70 percent of the room";
- an empty room answers "No objects found. Try better lighting and turn slowly."

**`ScanSession(mode, startedAtMs, timeoutMs = 60_000L)`** owns the clusterer and the coverage
tracker, returns the phrases to speak for each frame, auto-stops on full coverage or after 60 s, and
`finish()` produces the `ScanResult` that history stores.

**`ScanLogState`** keeps the on-screen text log, so judges can read what was spoken.

### 8.6 Search

**`SearchResolver.resolve(text, saved)`** — normalise the query (lowercase, strip "find", "where
is", "my", "the"), then match **saved names first** (exact, then containment, then Levenshtein
`MAX_TYPOS = 2` for names of `MIN_FUZZY_LENGTH = 4`+ characters), then COCO labels with plurals
stripped and synonyms: phone → cell phone, desk/table → dining table, sofa → couch, monitor/screen →
tv, bag → backpack, bike → bicycle, computer/notebook → laptop. Pitfall: a saved name that is also a
filler word ("Me") gets stripped by the cleaner, so match names against the raw words too.

**`SearchGuide`** — five zones by box centre (far left `< 0.2`, left `< 0.4`, ahead `0.4–0.6`, right
`<= 0.8`, far right); `beepIntervalMs` shrinks linearly from `FARTHEST_MS = 1_000L` at the edge to
`NEAREST_MS = 150L` at the centre; `isCentered` triggers a 60 ms vibration.
**`SearchTracker(lostAfterMs = 3_000, repeatMs = 2_000)`** says "Lost it" after three quiet seconds
and repeats a direction at most every two.

### 8.7 Faces and saved objects

**`FaceMatcher`** — `cosine(a, b)` and `bestMatch(vector, known, threshold = THRESHOLD)`, with
`THRESHOLD = 0.5f` and the winner having to beat the runner-up by `MARGIN = 0.08f`. A person's score
is the average of their `TOP_SAMPLES = 3` best samples. The reference implementation's 0.3 with a
single sample matched strangers as saved people; these three changes together fixed it.

**`FaceQuality.usable(sizePx, yawDeg, pitchDeg)`** — skip faces under `MIN_SIZE_PX = 64`, or turned
more than `MAX_YAW_DEG = 35f` or `MAX_PITCH_DEG = 25f`.

**`FaceNetPreprocess.standardize`** — per-image standardisation of 160×160 RGB floats, which is what
FaceNet-512 expects.

**`EnrollmentGuide(samplesPerPose = 4)`** with `Pose.{STRAIGHT, LEFT, RIGHT, UP, DOWN}` — 20 samples
over five poses, each pose gated on ML Kit angles (straight: |yaw| and |pitch| < 10; left/right:
|yaw| > 20; up/down: |pitch| > 12), with `percent()` for spoken progress.

**`NamedPeople.dropShadowedPersons`** — a plain "person" cluster within 20° of a named person is
dropped, so nobody is announced twice.

**`ItemMatcher.THRESHOLD = 0.75f`** cosine between MobileNetV3-Small embeddings, with
`mostCommon(labels)` deciding an enrolled item's label instead of locking onto the first frame's
guess. **`ItemEnrollmentGuide(samplesPerStep = 4)`** walks the user through still / left / right.
**`CenterPick.pick(candidates, minArea)`** chooses which box to enrol and requires it to cover at
least 5% of the frame. **`ItemKind.allows(label)`** keeps cars and objects apart.

### 8.8 Settings, filtering, help, voice

**`ScanSettings`** — `camera` (BACK/FRONT), `compute` (CPU/GPU), `model` (LIGHT/FAST/ACCURATE),
`minScore`, `speechOn`, `colorsOn`, plus `normalized()`. `MIN_SCORE_LOW = 0.3f`,
`MIN_SCORE_HIGH = 0.7f`, `DEFAULT_MIN_SCORE = 0.7f`: at 0.5 the detector invents chairs and books.

**`DetectionFilter`** — run the detector itself at `DETECTOR_THRESHOLD = 0.3f` and filter per label
above that. People are accepted `PERSON_BONUS_TENTHS = 2` (0.2) lower, but never below 0.3, because
far and half-hidden people score low.

**`Tiling`** — optional accuracy trick for far objects: detect the whole frame plus one zoomed tile
per frame, rotating through four tiles (`TILE_SHARE = 0.6f`), then merge with `SAME_IOU = 0.5f`,
`INSIDE_SHARE = 0.7f` and `CUT_MARGIN = 0.02f` so a tile's half-object does not become a second
thing.

**`ScreenHelp`** — 22 topic keys with their spoken explanations and `topicOf(text)`, so "what is
search" answers from the same table the help command uses.

**`ScreenGuide`** — turns a tap into speech: `position(...)` ("top right"),
`describeControl(label, kind, state, enabled)`, and `summary(items, width, height)` reading up to
`MAX_ITEMS = 12` controls of the current screen.

**`VoiceCommandParser.parse(text)`** → one of 17 `VoiceCommand` types; see §10.

**`ObstacleName.of(label, score)`** — the 1000-class classifier's guess becomes a word worth saying
only when it is something met while walking (`WALKING_SCORE = 0.5f`: door, fence, wall, chair, table,
bench, window, pole, stair railing, turnstile, mailbox, sign, box) or the classifier is very sure
(`OTHER_SCORE = 0.85f`). Everything else becomes the plain word "obstacle". Without this filter a
blank wall is announced as "file" or "refrigerator", because those are the nearest ImageNet classes
to a flat pale surface.

**`WalkCore`** — `WalkZone`, `Urgency`, `Hazard`, `WalkGeometry`, `HazardPolicy`, `WalkPhrases`,
`WalkAlerts`, `DepthObstacles`, `GroundProfile`, `HazardConfirmer`, `CloseHold`, `Beacon`,
`TrafficLightColor`. The numbers behind them are in §7.

### 8.9 Test suite

One test file per core file, JUnit 4 on the JVM, run with `gradlew.bat testDebugUnitTest` in a few
seconds. 243 tests; the largest groups are colour (23), summary wording (17), walk geometry and
phrases (18), faces (18), boxes (13) and voice parsing (13).

Every tuned number above has a test that fails if it changes. That is what lets you refactor safely
at 3 a.m. **Write the failing test first for anything with a number in it** — the colour rules, the
deduplication count, the face threshold and the stairs rule were all found that way.

---

## 9. The Android layer

One activity, Navigation component, view binding, Material 3. No Compose: the heavy parts are a
`Canvas` overlay, a `GLSurfaceView` for ARCore and a CameraX `PreviewView`, which would all sit
inside `AndroidView` wrappers anyway.

### 9.1 Package layout

```
com.classroomscanner
├── MainActivity.kt              single activity: nav host, toolbar, TTS, always-on microphone
├── ObjectDetectorHelper.kt      MediaPipe ObjectDetector wrapper (models, delegates, tiling)
├── OverlayView.kt               boxes, labels and outlines on a Canvas
├── core/                        pure Kotlin, no Android imports (§8)
├── fragments/                   one file per screen
├── face/FaceEngine.kt           FaceFinder (ML Kit), FaceEmbedder (FaceNet), FaceRecognizer
├── items/ItemEngine.kt          ItemEmbedder (MediaPipe), ItemRecognizer
├── vision/                      SceneClassifier, CodeReader, ObjectOutliner
├── speech/                      SpeechAnnouncer (TTS queue), SpeechInput (recognizer)
├── guide/                       VoiceGuide, VoiceListener, LearnerMode
├── walk/                        ArCamera, BackgroundRenderer, OffscreenCapture, DepthBytes,
│                                WalkVision, WalkHelpers, Compass
└── db/                          AppDatabase, entities, DAOs, repositories
```

### 9.2 Screens

| Screen | Job |
|---|---|
| `HomeFragment` | three large cards (Scan, Search, Saved) plus a floating microphone button |
| `ScanHubFragment` | Full scan, Live scan, History, Walk |
| `SettingsFragment` | camera, CPU/GPU, model, confidence, speech, colours |
| `PermissionsFragment` | asks for the camera, then continues to the scanner |
| `CameraFragment` | full and live scan: CameraX, detector, heading, faces, items, speech, history |
| `HistoryFragment` | past scans, replayed through TTS |
| `SavedFragment` | tabs for people, cars and objects |
| `AddPersonFragment` → `EnrollFragment` | name, then the five-pose face enrolment |
| `AddItemFragment` → `ItemEnrollFragment` | name and kind, then the three-step object enrolment |
| `PersonFragment`, `ItemFragment` | detail, rename, delete |
| `SearchFragment` | query by voice or keyboard, resolved against saved names and COCO labels |
| `SearchCameraFragment` | hunts one target with beeps, vibration and speech |
| `WalkFragment` | ARCore walk mode |

Sixteen destinations is too many for a blind user to hold in their head. Keep the graph, but make
**every destination reachable by one spoken command** (§10) and make Home usable with one thumb.

### 9.3 Navigation graph

Destinations and their arguments:

```
home_fragment (start)
scan_hub_fragment
settings_fragment(mode: ScanMode)
permissions_fragment(mode: ScanMode)
camera_fragment(mode: ScanMode)
history_fragment
saved_fragment(tab: Int = -1)
person_fragment(personId: Long)
add_person_fragment(name: String? = null)
enroll_fragment(name: String, front: Boolean)
item_fragment(itemId: Long)
add_item_fragment(name: String? = null, kind: String)
item_enroll_fragment(name: String, kind: String, front: Boolean)
search_fragment(query: String? = null)
search_camera_fragment(targetKind, targetId, targetName, targetLabel, front)
walk_fragment
```

Every destination also gets a **global action** (`action_global_camera`, `action_global_saved`,
`action_global_search`, …) so a voice command can jump straight there from anywhere.
`action_global_camera` uses `popUpTo camera_fragment inclusive`, or saying "full scan" twice stacks
two camera screens and two detectors.

Use **safe-args** and pass the spoken name or query as an argument, so "add person Ali" or "find my
bag" arrives at the screen already filled in and needs no second interaction.

### 9.4 Storage (Room, version 3)

Database `classroom-scanner.db`, three DAOs, six entities:

| Entity | Fields |
|---|---|
| `ScanEntity` | `id`, `startedAt`, `mode`, `coveragePercent`, `summaryText` |
| `DetectedObjectEntity` | `id`, `scanId` (FK cascade), `label`, `count`, `colorName?`, `relAngleDeg`, `sector8` |
| `PersonEntity` | `id`, `name`, `photoPath`, `createdAt` |
| `FaceEmbeddingEntity` | `id`, `personId` (FK cascade), `vector: ByteArray` |
| `ItemEntity` | `id`, `name`, `kind`, `label`, `photoPath`, `createdAt` |
| `ItemEmbeddingEntity` | `id`, `itemId` (FK cascade), `vector: ByteArray` |

Practices that paid off:

- **Write parent and children in one `@Transaction`** (`insertScanWithObjects`,
  `insertPersonWithFaces`, `insertItemWithEmbeddings`). Half-saved people are worse than none.
- **Observe with `Flow`** (`observeScans`, `observePeople`, `observeItems(kind)`) so lists update
  themselves.
- **Store embeddings as `ByteArray`** (float array to bytes), not as text. Simple, compact, fast.
- **Plan the schema on day one.** Adding people and then items later cost two migrations
  (1→2, 2→3). If you know all three tables up front, ship version 1 with all of them.
- **Writes run on a process-wide scope**, never the fragment scope, or closing the screen cancels
  the insert and history stays empty.

`SettingsStore` keeps the scan settings in `SharedPreferences` (`scan_settings`): camera, compute,
model, `minScore` (default 0.7), speech on, colours on. `VoiceGuide` keeps its own flag in
`app_prefs`.

### 9.5 Camera and detection

- CameraX `Preview` + `ImageAnalysis`, `STRATEGY_KEEP_ONLY_LATEST`, one single-thread executor for
  analysis, `PreviewView` in `FILL_START`.
- Keep **one place that builds detector options** from `ScanSettings`, and let every screen use it.
  Hard-coded model or delegate choices in a second screen are how "the setting does nothing" bugs
  appear.
- `ObjectDetectorHelper(threshold, currentDelegate, currentModel, runningMode, context, listener,
  tiled)`; models 0–3 map to lite0, lite2, ssd-mobilenet-v2 and lite0-int8; `MAX_RESULTS_DEFAULT =
  10`; `ResultBundle` carries the detections, the inference time, the input size and rotation, and
  optionally the frame bitmap.
- **Log the inference time every 30 frames** and print which delegate is running. This is how you
  decide CPU versus GPU on the actual phone instead of guessing.
- A frame error must never disable the Stop button: catch it, speak nothing, keep the UI alive.

`OverlayView` maps image pixels to the view with one matrix: translate to centre, rotate by the frame
rotation, translate back (swapping width and height for 90°/270°), then scale — `min(...)` for still
images, `max(...)` for a live stream that fills the screen. The front camera flips only the drawing
(`mirrored = true`). Custom labels are passed in parallel with the detections, and a `null` label
means "filtered out, do not draw", which keeps the overlay honest about what was counted.

### 9.6 Recognition engines and their numbers

| Engine | Model | Numbers that matter |
|---|---|---|
| `FaceFinder` (ML Kit) | — | `PERFORMANCE_MODE_FAST`, all landmarks (eyes level the face), min face size 0.1 |
| `FaceEmbedder` | `facenet_512.tflite` | input 160×160, 4 threads, crop margin 0.1, aligned crop margin 0.35, level the face when tilted ≥ 3°, skip crops under 24 px |
| `FaceRecognizer` | — | `FaceMatcher` thresholds (§8.7); runs on every third frame that has a person box |
| `ItemEmbedder` | `mobilenet_v3_small.tflite` | L2-normalised embeddings, min crop 16×16, at most 3 crops per frame |
| `SceneClassifier` | `efficientnet-lite0.tflite` | `MIN_SCORE = 0.35f`, one result, names the middle 50% of the frame |
| `CodeReader` (ML Kit) | — | truncate a code at `MAX_CHARS = 80` before speaking it |
| `SignReader` (ML Kit text) | — | speak the longest line of at least 3 characters |

Face recognition renames the person box it sits inside; that is what turns "a person in front" into
"Ali in front".

### 9.7 Speech out

`SpeechAnnouncer` wraps `TextToSpeech` (`Locale.US`) as a queue, because speaking over yourself is
the fastest way to make a demo unintelligible:

- `GAP_MS = 1500` between announcements, polled every `POLL_MS = 250`;
- drop stale items when more than `MAX_PENDING = 3` are waiting;
- flush the queue before the final summary and let the summary finish, with a
  `SHUTDOWN_SAFETY_MS = 15000` ceiling so a stuck engine cannot hang the screen;
- warn "Slow down" when the phone turns faster than 60°/s, at most once every 5 s;
- honour the speech setting and mute instantly when the user turns it off.

`VoiceGuide` is the accessibility layer on top: tapping a control speaks its name and state
(`ScreenGuide.describeControl`), tapping empty space reads the whole screen
(`ScreenGuide.summary`), and entering a screen speaks its one-line help in learner mode.

---

## 10. Voice control: the fragile part, and exactly how to make it work

Voice is the feature judges try first and the one most likely to embarrass you. Everything below is
worth implementing on the first day, not the last.

### 10.1 Creating the recognizer

```kotlin
val recognizer =
    if (!SpeechRecognizer.isRecognitionAvailable(context) &&
        Build.VERSION.SDK_INT >= 33 &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    ) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    } else {
        SpeechRecognizer.createSpeechRecognizer(context)
    }
```

Some phones report no recognition *service* while still having Android's on-device recognizer.
Checking only `isRecognitionAvailable` tells those users "voice input unavailable" on a phone where
voice works perfectly.

The manifest needs the `queries` entries for `android.speech.RecognitionService` and
`android.intent.action.TTS_SERVICE` (§5), or these lookups come back empty on API 30+.

### 10.2 The intent, and the trap that silently kills every command

```kotlin
Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
```

**Do not set `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS`.** It looks like the right way to say "the
user may speak a few seconds from now", but on Google's on-device recognizer it switches the session
into continuous dictation (`applicationDomain: AMBIENT_CONTINUOUS`). Speech is then delivered in
segments and the final result arrives **empty**: the logs show `onResults empty final recognition
results`, `Result lattice is not set`, then `NO_SPEECH_DETECTED`. The app hears nothing at all while
the microphone appears to work. Symptom to remember: the system speech logs show `withSpeech: true`
but your own callback never fires.

Keep partial results and use the last non-empty partial as a fallback when the final result is
empty — some recognizers end a turn with nothing even in one-shot mode:

```kotlin
val text = results?.getStringArrayList(RESULTS_RECOGNITION)?.firstOrNull()?.trim()
    .orEmpty().ifEmpty { lastPartial }
```

### 10.3 Keeping the microphone alive

A recognizer is one-shot. For an always-on assistant, restart it after every result and every error,
with these delays:

| After | Delay | Why |
|---|---|---|
| switching voice on | 1500 ms | let the "voice commands on" announcement finish |
| a command | 2500 ms | let the app's own answer be spoken, so it is not heard as a command |
| an error or silence | 700 ms | fast retry, no busy loop |

Rebuild the recognizer object after **3 consecutive errors**: a recognizer that keeps failing is
stuck, and a fresh one always works. Stop TTS before opening the microphone. Microphone-permission
errors must stop the loop and say so, instead of retrying forever.

### 10.4 One microphone, one owner

Two recognizers fighting over the microphone is the second silent failure. It happens as soon as a
screen wants free text (a search query, a name for a new person) while the always-on listener is
running: both open a session, each cancels the other, and neither returns anything.

The rule: **while the global listener is on, screens do not open their own recognizer.** They ask for
the next words instead:

```kotlin
// MainActivity
fun takeNextWords(onText: (String) -> Unit) { dictation = onText }

// in the heard-text handler, before acting on a command
if (dictation != null && command == VoiceCommand.Unknown) { deliverToScreen(text); return }
```

Two details make this pleasant rather than surprising:

- Only text that parses as `Unknown` becomes dictation. Saying "start" or "add car" while a name is
  expected still runs the command; saying "Ali" or "water bottle" fills the field.
- The screen cancels its claim in `onDestroyView`, so a name request cannot capture words on the
  next screen.

When the global listener is off, the screen's own microphone button opens a one-shot recognizer as
usual.

### 10.5 Parsing: keywords, not sentences

People phrase the same command a dozen ways ("full scan", "open full scan", "begin the full scan").
Parse by the words that carry meaning:

1. Strip punctuation, collapse spaces, lowercase a copy, keep the original for names.
2. **Check the microphone words first** — "listening", "microphone", "mic off", "voice off" — so
   "stop listening" cannot stop a scan instead.
3. Then phrase-shaped commands with a payload: "save this place as X", "take me to X",
   "search for X" / "find X" / "where is X".
4. Then questions: "who is this" → identify a person, "what is this" → identify a thing, "what is
   search" → help topic, "help" / "what is this screen" → help for the current screen.
5. Then adding things: any of add / create / new / register / save / make plus person / car / object,
   with the words after the kind taken as the name ("add person called Ali" → "Ali").
6. Then screens and buttons, by keyword.

**Order matters** inside step 6. A destination word beats an action word, so put the verbs first if
you want "stop full scan" to stop rather than to navigate. Known trade-off; decide it deliberately.

**Plan for mishearings.** These are real, from an accented speaker on a Korean and an Uzbek phone:

| Heard | Meant | Handling |
|---|---|---|
| "save it", "safe", "saves" | saved | treat as the Saved screen |
| "working mode", "work mode" | walking mode | "mode" plus "work"/"working" → walk |
| "for scan", "fool scan", "fullscan" | full scan | any remaining word containing "scan" → full scan |
| "person", "car", "object" alone | that tab of Saved | a bare kind word opens its tab |
| "Corazon", "Google" | nothing | stay `Unknown`, answer "I did not understand" |

Accept single words as commands wherever it is unambiguous: a blind user says "saved", not "please
open the saved screen".

**Speak the failure.** `Unknown` answers "I did not understand", and a command a screen cannot handle
answers "not here". Silence after a spoken command is indistinguishable from a broken app.

### 10.6 Logging, because you cannot debug speech by listening

Log every heard phrase with its parse result on one line, at `Log.i` (some phones suppress `Log.d`
for third-party apps):

```
I/ClassroomScanner: Heard "Walk mode" -> Walk
I/ClassroomScanner: Heard "Open Working mode" -> Unknown
```

That single line tells you whether a failure was the recognizer's or the parser's, which are fixed in
completely different places. On a phone with a small log buffer, stream it to a file:
`adb logcat -v time -s ClassroomScanner:V > voice.log`.

### 10.7 Voice commands to support

Screens: full scan, live scan, scan menu, history, saved (with people / cars / objects tabs), search,
settings, home, back. Actions on the current screen: start (press the main button), stop, switch
camera, repeat, read text, delete. Questions: who is this, what is this, help, what is <topic>.
Modes: walk, learner mode on/off, stop listening. Places: save this place as X, take me to X.
Adding: add person / car / object, optionally with a name.

Each screen implements a small `onVoiceCommand(command): Boolean` and returns false for anything it
does not own; the activity then navigates. That keeps the command table in one place and lets "start"
mean the right thing on every screen.

---

## 11. Pitfalls, with the fix next to each

| # | Symptom | Cause | Fix |
|---|---|---|---|
| 1 | Voice commands never arrive, microphone looks alive | `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS` puts the recognizer in continuous mode; the final result is empty | drop that extra, keep partial results as a fallback (§10.2) |
| 2 | Microphone dies after silence | a recognizer is one-shot and gets stuck after repeated errors | restart after every turn, rebuild after 3 errors (§10.3) |
| 3 | A name or query is never captured | two recognizers fight for the microphone | one owner; screens take the next words from the global listener (§10.4) |
| 4 | "stop listening" ends the scan | "stop" matched before "listening" | check microphone words first (§10.5) |
| 5 | 3 chairs become 300 | counts summed across frames | count = max in one frame (§8.3) |
| 6 | One chair announced twice | colour flicker splits the cluster | colour is a vote, not part of the key (§8.3) |
| 7 | Half-seen objects double-counted | boxes clipped by the frame edge | drop boxes touching exactly one side edge, 2% margin (§8.1) |
| 8 | Everything is grey or brown | Palette dominant swatch under warm light | gray-world white balance plus HSV voting (§8.4) |
| 9 | Strangers greeted by a saved name | cosine 0.3 on one sample | 0.5 with a 0.08 margin over the runner-up and a best-3 average (§8.7) |
| 10 | Directions are wrong after rotating the phone | heading and FOV maths assume portrait | lock portrait in the manifest from the first commit |
| 11 | Overlay boxes drift from objects | rotation and scale applied in the wrong order | one matrix: centre, rotate, back, then scale; `max` for live, `min` for stills (§9.5) |
| 12 | GPU crashes on close | the int8 model cannot run on the GPU; the failure surfaces at `close()` | float model on GPU, int8 on CPU; prove the delegate with a 64×64 trial detect; catch on close (§4, §7) |
| 13 | ARCore crashes natively when leaving walk mode | the session closed while the GL thread was running | closing flag, `glView.onPause()`, then pause and close (§7) |
| 14 | Walk mode names a wall "file" or "refrigerator" | the capture was stretched 3× and the 1000-class model is forced to choose | screen-shaped capture plus an allow-list of walking things (§7, §8.8) |
| 15 | No warning right in front of a wall | depth-from-motion cannot measure a close, textureless wall, and "unmeasured" was read as "clear" | hold the last close reading while the way ahead is unknown (§7) |
| 16 | "Road under you" indoors | ARCore scene semantics is outdoor-only | require confidence ≥ 200 and sky seen in the last minute (§7) |
| 17 | A wall announced as stairs | depth noise spreads one wall over several distances | suppress rises next to tall things; stairs must climb with distance (§7) |
| 18 | History is empty although the scan finished | the insert ran in a fragment scope that was cancelled | write on a process-wide scope (§9.4) |
| 19 | The app hangs when leaving the scanner | waiting forever for the detector executor | `awaitTermination(2, SECONDS)` |
| 20 | Stop button dead after a bad frame | frame error handler disabled the UI | never disable controls from a frame error (§9.5) |
| 21 | Preview stutters, lag grows | heavy work queued per frame | one worker per job with a busy flag, drop frames, keep the last result (§6) |
| 22 | A setting changes nothing | a second screen hard-codes the model or delegate | one shared options builder (§9.5) |
| 23 | Your own log lines are missing on the phone | vendor builds suppress `Log.d` for apps | log at `Log.i`, and stream to a file when the buffer is small (§10.6) |
| 24 | Adding a table breaks installs | Room schema grew twice | design all tables before version 1 (§9.4) |
| 25 | Build fails on a clean clone | models missing from assets | Gradle download tasks wired to `preBuild`, `noCompress 'tflite','task'` (§3, §4) |

---

## 12. Build order

Two people, one phone. Person A owns `core/` and tests; person B owns the camera, the overlay and the
screens. Merge at each gate and keep the app runnable at all times.

### Gate 0 — setup (first hour, do it together)

1. New project from the MediaPipe object-detection Android sample (Apache-2.0), package renamed.
2. Portrait lock, `queries` entries, permissions, ARCore `optional` meta-data.
3. Model download tasks wired to `preBuild`, `noCompress 'tflite','task'`.
4. `gradlew installDebug` runs the sample on the phone, boxes appear.
5. Git repository, one branch per feature, `testDebugUnitTest` in the loop from the start.

**Stop condition:** the stock sample detects objects on the phone. Do not start feature work before
this works, and do not spend more than 90 minutes here.

### Gate 1 — the brain (`core/`)

`AngleMath`, `BoxGeometry`, `CoverageTracker`, `ObjectClusterer`, `WhiteBalance`, `ColorMapper`,
`ColorVote`, `SummaryBuilder`, `ScanSession`, `DetectionFilter`, `ScanSettings` — each with its tests
written first. Everything above is pure logic and needs no phone, so it can be written while the
phone is busy elsewhere.

**Stop condition:** `testDebugUnitTest` green with the deduplication, colour and wording rules
covered.

### Gate 2 — the core demo

Heading provider, camera field of view, full 360° scan with the coverage ring, live scan, TTS queue,
the spoken summary, the text log dialog, settings, history. This is the pitch; it must be flawless.

**Stop condition:** a 360° turn in a real room produces one correct sentence, twice in a row.

### Gate 3 — search

`SearchResolver` and `SearchGuide` are already tested from Gate 1's pattern; add the search screen,
the hunting camera screen, beeps and the vibration. Big demo value for little code.

**Stop condition:** "find my bag" beeps faster as the phone points at the bag.

### Gate 4 — voice

Recognizer with every rule from §10, the parser with its mishearing table, per-screen command
handling, the voice guide and learner mode.

**Stop condition:** ten different spoken commands in a row, all logged with a correct parse.

### Gate 5 — one wow feature (pick one, not both)

- **Saved people:** enrolment, embeddings, matching with the tuned thresholds, sticky names.
- **Saved objects:** enrolment, embeddings, matching at 0.75.

**Stop condition:** an enrolled person or object is named inside a scan, and a stranger is not.

### Gate 6 — only if everything above is solid

Walk mode (§7), QR and sign reading, object outlines. Walk mode is the most impressive feature and
the most likely to fail live: it needs ARCore, depth, motion and good light all at once.

### What to skip on purpose

Fine-tuning a detector (the gain does not justify the hours), outlines on every object, cloud
anything, a login, and a map of the room. Say in the pitch that they were considered and dropped;
judges respect scope discipline.

---

## 13. Testing and device QA

**Unit tests (JVM, seconds):** `gradlew.bat testDebugUnitTest`. One test file per core file. Every
tuned number gets a test. Aim for the same shape as the reference suite: 243 tests across 27 files.

**Instrumented test (phone):** one Room DAO test proving `insertScanWithObjects` and the migrations,
run with `gradlew.bat connectedDebugAndroidTest`.

**What unit tests cannot cover** — a written device checklist, run before the demo, in this order:

1. Fresh install, all permissions denied: every screen still opens and explains what it needs.
2. Full scan in a room with 5+ objects: the summary names them, counts are right, no duplicates.
3. Full scan turning too fast: "Slow down" is spoken once, coverage percent is admitted.
4. Live scan: announcements arrive without overlapping each other.
5. Search for a COCO label and for a saved name: beeps speed up, vibration at the centre, "Lost it"
   after three seconds of nothing.
6. Faces: the enrolled person is named; a stranger is announced as "a person".
7. Voice: ten commands, including "stop listening", "saved", "walk mode", "add person Ali".
8. Settings: switch model and delegate, scan again, check the inference-time log line.
9. Walk mode: obstacle ahead beeps faster; a close wall keeps warning; no "road" indoors; leaving the
   screen does not crash.
10. Rotate the phone, lock the screen, background and resume every screen: no crash, no stuck TTS.
11. Airplane mode: everything still works. This is the pitch, so prove it on stage.
12. Battery and heat after 10 minutes of scanning: note the numbers, judges ask.

**Useful commands**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat installDebug
.\gradlew.bat connectedDebugAndroidTest
adb logcat -v time -s ClassroomScanner:V           # your own lines only
adb logcat -v time -s ClassroomScanner:V > voice.log   # small log buffers
adb shell am force-stop com.<pkg>; adb shell monkey -p com.<pkg> -c android.intent.category.LAUNCHER 1
adb shell dumpsys gfxinfo com.<pkg> | Select-String Janky   # dropped frames
scrcpy --stay-awake                                 # mirror for the demo
```

---

## 14. Accessibility and UI, done properly

This is an accessibility project; judges will check whether it is usable blind, not just whether it
detects.

- **Voice-first.** One big action button per screen, reachable by thumb at the bottom, plus a spoken
  command for everything. Never require a precise tap.
- **TalkBack.** A custom voice guide fights TalkBack if you are careless. Set content descriptions,
  mark headings, fix focus order, and test one full flow with TalkBack on.
- **Touch targets at least 64 dp**, high contrast, a dark theme, and text that survives the largest
  system font.
- **Haptics as a language:** distinct patterns for found, lost, obstacle and done. A blind user reads
  vibration faster than speech.
- **Twenty-second onboarding:** on first launch, speak what the app does and the three commands that
  matter. Offer a compass calibration prompt (figure eight) when accuracy is low.
- **A demo view for sighted judges:** show a large caption of everything spoken, plus boxes and the
  coverage ring, so the room can follow along.
- **Never say "safe".** The app reports what it sees; it does not promise the path is clear. Say this
  out loud in the pitch — it is the difference between a toy and an assistive tool.

---

## 15. Optional module: street navigation

Only after Gate 6, and only if the event is in South Korea, where this actually matters.

**Google Directions is not an option there.** Korea restricts the export of high-precision map data,
so Google Maps has no turn-by-turn walking directions inside the country; walking legs appear as
dotted lines.

**Use TMAP (SK Telecom).** `POST https://apis.openapi.sk.com/tmap/routes/pedestrian` with an
`appKey` header, `startX/startY/endX/endY` in WGS84, `startName/endName` URL-encoded, up to five
waypoints via `passList`. `searchOption=30` means shortest **without stairs**, which is exactly right
for a blind walker. The free key allows on the order of a thousand route calls a day — plenty for a
demo. Naver and Kakao publish driving routes, not pedestrian ones.

**Design that fits the offline promise:** fetch the route once, then guide entirely on-device from
GPS and the compass, exactly like the existing beacon. Announce the next turn at 25 m and again at
5 m; re-request only when the user is more than 40 m off-route for three fixes, at most once every
30 s; fall back to straight-line beacon guidance when the network is gone. Map TMAP's `turnType`
codes to English phrases yourself, since the API's own descriptions are Korean.

**Watch out:** obtaining the key may require a Korean phone number or account — verify before
promising it in a pitch. And the API only routes inside Korea, so test with a mock location in Seoul
(`adb emu geo fix` or a mock-location app) when developing elsewhere.

---

## 16. Risks and fallbacks

| Risk | Early sign | Fallback |
|---|---|---|
| Phone has no ARCore depth | `isDepthModeSupported` false | walk mode keeps the detector hazards and drops wall and step warnings; say so honestly |
| GPU delegate unusable | trial detect throws | CPU with the int8 model; note the inference time on the slide |
| Speech recognition unavailable | `isRecognitionAvailable` and on-device both false | on-screen buttons plus a typed query; keep TTS output |
| Room is too dark for the demo | colours come back null | scan a lit corner; the summary still names objects without colours |
| Walk mode misbehaves on stage | any wrong announcement in rehearsal | cut it from the demo and show it only in a recorded clip |
| Time runs out | Gate 4 not done by the halfway point | ship Gates 0–3 polished; a flawless small demo beats a broken big one |

Record a 60-second screen capture of every working feature as soon as it works. If the live demo
fails, you still have proof.

---

## 17. Demo script (3–4 minutes)

1. **Ten seconds of framing.** "A blind user cannot ask a room what is in it. This phone can, with no
   internet." Show airplane mode on.
2. **Live scan.** Point the phone around; announcements arrive as things are found, captions on
   screen for the audience.
3. **Full 360° scan.** Turn once; the ring fills; one sentence describes the room with counts,
   colours and directions.
4. **Search.** "Find my bag." Beeps accelerate, the phone vibrates when it is centred.
5. **A saved person.** Someone enrolled earlier is greeted by name; a stranger stays "a person".
6. **Voice.** "Saved." "Full scan." "Stop listening." Hands never touch the screen.
7. **One architecture slide:** on-device models, the pure-Kotlin brain with 243 tests, sensor fusion,
   and the numbers that were tuned by measurement.

**Talking points that land:** everything runs on the phone (privacy, offline, latency); counting by
maximum-in-one-frame instead of summing; compass plus camera field of view to give every object a
direction; face matching tuned with a threshold, a margin and a best-of-three average; depth geometry
for steps that no model can detect; and a deliberate refusal to ever say "safe".

**Questions to expect:** How fast is a frame? (Have the measured inference time ready.) What happens
in the dark? (No colours, fewer detections, say so.) Does it work outside a classroom? (COCO covers
80 everyday classes; walk mode is built for outdoors.) Why not the cloud? (Privacy, latency, and it
must work where there is no signal.)

---

## 18. Repository hygiene

- One branch per feature, small commits with an imperative subject that says the user-visible effect.
- Tests green before every merge; never commit a red suite "to fix later".
- Keep `docs/` with this guide, the brief, and one design note per non-obvious subsystem.
- Keep models out of git (they are downloaded at build time) and keep large caches off the system
  drive.
- Anything with a tuned number gets a comment saying *why* that number, and a test that fails if it
  changes. Six hours from now, that comment is the only thing standing between you and re-deriving
  the colour rules.
