# Nungil (눈길): an on-device scene assistant for blind users

A blind or low-vision user holds the phone upright and turns around, or walks. The phone says what is
around them: what each thing is, which direction it is in and what colour it is. It can find a named
thing with beeps, recognise saved people and objects, read signs and QR codes, and warn about walls,
steps and stairs while walking. Everything is in **English and Korean**, controlled by voice, and runs
**on the phone with no internet**.

> *"Around you: 3 blue chairs in front; a black laptop on your right."*
> *"앞에 파란 의자 세 개, 오른쪽에 검은 노트북 한 대가 있어요."*

## What it does

| Feature | How it works |
|---|---|
| **Look around** (full 360° scan) | Turn once. The coverage ring fills and one summary is spoken. Objects get a compass direction from heading + camera field of view, and a colour from white-balanced HSV voting. Duplicates are merged: the count is the most seen in one frame, never a sum. |
| **Live scan** | The same detection, announced as things appear. |
| **Find** ("find my bag") | Beeps get faster as the camera points at the target, a vibration when it is centred, "Lost it" after 3 s. Works for object types, saved people and saved objects. |
| **Saved people and objects** | Face enrolment in one head sweep (ML Kit + FaceNet-512, cosine 0.5 with a 0.08 margin over the runner-up). Objects by appearance (MobileNetV3 embeddings, cosine 0.75). Named inside scans. |
| **Reader** | QR codes, barcodes and printed Latin text read aloud. |
| **Walk mode** | ARCore depth. Walls ("Wall ahead, 3 steps" … "very close"), steps, stairs up and down, drops, people and vehicles, traffic-light colour, signs. Beeps and vibration for closeness. It never says "safe". |
| **Places** | "save this place as home", "take me home". Routes come from openrouteservice when a key is set; without one, an offline direction beacon ("Home, 300 metres, at 2 o'clock"). |
| **Voice** | Always listening, with the wake word **"Eye"** (Korean **"눈길아"**). While the user speaks, every app sound stops. |
| **Accessibility** | Korean-style design (Pretendard, 64 dp targets, one big bottom button). Light, dark and high-contrast themes, all checked for contrast. Everything spoken is also shown in a caption bar. Every control has a spoken label for TalkBack. |

## Voice commands

Say the wake word first: "Eye" or "눈길아". Commands then work until "Eye stop" / "눈길아 그만". A wake
word plus a command in one sentence also works ("Eye, saved").

| English | 한국어 | Does |
|---|---|---|
| look around / full scan | 주변 둘러보기 | full 360° scan |
| live scan | 실시간 안내 | announce things as they appear |
| find my bag | 가방 찾아줘 | search with beeps |
| saved | 저장한 것 | saved people, cars and objects |
| add person Ali | 사람 추가 민준 | enrol a face |
| walk mode | 걷기 모드 | walk mode |
| save this place as home | 여기를 집으로 저장해 줘 | remember where you stand |
| take me home | 집까지 안내해 줘 | guide to a saved place |
| read text | 글자 읽어 줘 | reader |
| who is this / what is this | 누구야 / 이게 뭐야 | name a person or a thing |
| repeat / stop / back / help | 다시 / 멈춰 / 뒤로 가 / 도움말 | on any screen |
| Korean / English | 한국어 / 영어 | switch language |
| stop listening | 듣기 중지 | microphone off (tap the mic button to turn it back on) |

## Build and run

Requirements: Android Studio (or JDK 17+), Android SDK 35, a phone with Android 7.0+ (API 24),
a gyroscope and a compass. Walk mode needs a phone that supports ARCore depth; without it, walk mode
falls back to detections only.

```powershell
git clone https://github.com/androidIkrom/hackathon-ml-scanner.git
cd hackathon-ml-scanner
"sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk" | Out-File -Encoding ascii local.properties
.\gradlew.bat testDebugUnitTest      # 592 unit tests
.\gradlew.bat installDebug           # downloads the models (~100 MB) into app/src/main/assets on the first build
```

Optional, for real walking routes: add a free [openrouteservice](https://openrouteservice.org) key to
`local.properties` (it is git-ignored):

```properties
ORS_API_KEY=your-key
```

Phone setup for the best experience:
- Korean voice: Settings → Text-to-speech → Google → install Korean.
- Offline speech recognition: Google app → Settings → Voice → Offline speech recognition → English and 한국어.
- On Infinix/Transsion phones, turn off the Wi-Fi assistant (or use airplane mode). Its pop-up interrupts the camera.

## How it is built

```
voice ─► VoiceInput (always on, wake word) ─► VoiceCommandParser (EN/KO) ─► screen or navigation
camera ─► CameraSession (CameraX + MediaPipe EfficientDet) ─► scan brain (angles, clustering, colour, summary EN/KO)
ARCore ─► WalkRenderer (screen-shaped capture + depth lookup table) ─► WalkVision ─► alerts by priority
speech ◄─ TtsSpeaker (queue, barge-in, captions) · haptics · beeps
```

- `app/src/main/java/com/nungil/core/`: pure Kotlin rules with no Android imports, covered by the unit
  tests: geometry, clustering, colour, summaries, search, face and item matching, voice parsing, walk
  depth and stairs, and the route navigator.
- `contract/`: the frozen interfaces the three parts talk through.
- Stack: Kotlin 2.1, AGP 8.11, Views + Navigation, Material 3, CameraX 1.4, MediaPipe tasks-vision, ML Kit,
  TensorFlow Lite, ARCore 1.49, Room.
- Only one feature uses the network: the walk-mode route request, which falls back to the offline beacon.

## Team and how we work

| | Owns |
|---|---|
| **A**: scan engine and build | camera, detector, heading, 360° scan brain, scan screen, Gradle |
| **I**: interface, voice and walk mode | design system, screens, speech, voice commands, accessibility, walk mode |
| **Y**: find and recognise | search, faces, saved objects, reader |

Every folder has one owner (`OWNERS`). A pre-commit hook (`git config core.hooksPath .githooks`) and CI
refuse changes to someone else's files. Branches are `a/…`, `i/…`, `y/…`; contract changes go on `c/…`
and need all three. Everything reaches `main` through a pull request with green CI.

## Documents

- [Team plan](docs/superpowers/plans/2026-09-24-00-team-plan.md) and the per-person plans for
  [A](docs/superpowers/plans/2026-09-24-A-scan-engine.md), [I](docs/superpowers/plans/2026-09-24-I-interface.md) and
  [Y](docs/superpowers/plans/2026-09-24-Y-find-recognize.md)
- [Build guide](docs/build-guide.md) and [hackathon brief](docs/hackathon-brief.md): the measured decisions behind every number
- [UI guide](docs/design/ui-guide.md), [Figma plugin](docs/design/figma-plugin/) and [demo script](docs/design/demo-script.md)

## Known limits

- The reader reads Latin text only. Korean OCR needs an extra ML Kit model.
- ARCore scene semantics ("road under your feet") is off: on some phones pausing it crashed inside ARCore.
- GPS is weak indoors, so place arrival uses 15 m or the GPS accuracy, whichever is larger.

## Credits and licences

- Detection, classification and embedding models: [MediaPipe](https://ai.google.dev/edge/mediapipe) (Apache-2.0).
- FaceNet-512 from [shubham0204/FaceRecognition_With_FaceNet_Android](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android) (Apache-2.0).
- [Pretendard](https://github.com/orioncactus/pretendard) font (SIL Open Font License 1.1).
- Routing by [openrouteservice](https://openrouteservice.org); map data © OpenStreetMap contributors.
- Design references: KRDS and the Wanted Design System, used for look and feel only.
