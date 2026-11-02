# nungil (눈길)

[![license: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-green.svg)](LICENSE)
[![android](https://img.shields.io/badge/android-7.0%2B-3DDC84.svg)](app/build.gradle)
[![kotlin](https://img.shields.io/badge/kotlin-2.1.0-blue.svg)](build.gradle)
[![languages](https://img.shields.io/badge/languages-english%20%7C%20korean-orange.svg)](app/src/main/res-i)

**an android scene assistant for blind and low-vision users, with vision processing on the device.**

nungil describes nearby objects, helps find people and belongings, reads printed text and codes, and provides walking alerts. camera, compass and depth information become spoken guidance, captions, beeps and vibration. the interface supports english and korean, voice commands and TalkBack.

> “around you: three blue chairs in front; a black laptop on your right.”

---

## ✨ features

- **look around:** turn once for a 360° scene summary with object counts, directions and colours.
- **live scan:** hear objects as the camera detects them.
- **find something:** locate object types, saved people and saved belongings using directional beeps and vibration.
- **saved recognition:** enrol faces and objects, then hear their names during scans.
- **reader:** read printed latin text, QR codes and barcodes aloud.
- **walk mode:** receive obstacle, wall, step and stair alerts, with ARCore depth where supported.
- **saved places:** remember locations and use route guidance or a direction beacon.
- **voice control:** use english or korean commands with the wake word “Eye” or “눈길아”.
- **accessible interface:** 64 dp touch targets, captions, spoken labels, and light, dark and high-contrast themes.
- **weather and clock:** view or hear the time, date and local weather from the home screen.

vision models run locally. weather updates and online walking routes use the network; offline speech depends on the recognition engine and language packs installed on the phone.

---

## 📦 installation

### requirements

- android studio, or JDK 17 with the android command-line tools.
- android SDK 35, NDK 28.2.13676358 and CMake 3.22.1.
- an android 7.0+ phone (API 24+) with a camera; a compass and gyroscope support directional scanning.
- an ARCore depth-capable device for depth-based walking alerts.
- internet access for the initial Gradle dependencies and model downloads.

### clone and build

```powershell
git clone https://github.com/androidIkrom/hackathon-ml-scanner.git
cd hackathon-ml-scanner
```

open the project in android studio and let it configure the SDK, or create `local.properties` in the project root:

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
```

build a debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

with USB debugging enabled and the phone connected:

```powershell
.\gradlew.bat :app:installDebug
```

Gradle downloads the configured models into `app/src/main/assets/` on the first build. model files are excluded from git. see the [build guide](docs/build-guide.md) for additional setup details.

### phone setup

1. grant camera and microphone permissions; allow location for weather and saved places.
2. install english and korean text-to-speech voices as needed.
3. install offline speech recognition language packs if supported by the phone's recognition service.
4. hold the phone upright when scanning so camera directions match the heading calculations.

## 📚 dependencies

android dependencies are resolved by Gradle. versions below come from [app/build.gradle](app/build.gradle) and [build.gradle](build.gradle).

| component | version / purpose |
|---|---|
| kotlin / android Gradle plugin | 2.1.0 / 8.11.0 |
| Java / android SDK | 17 / compile and target API 35 |
| CameraX | 1.4.2 — camera capture |
| MediaPipe tasks vision | 1.0.0 — detection, classification and embeddings |
| LiteRT and GPU delegate | 1.4.2 — TensorFlow Lite model execution |
| ML Kit | face detection 16.1.7, text recognition 16.0.1, barcode scanning 17.3.0 |
| ARCore | 1.49.0 — walking depth |
| Room | 2.7.2 — local persistence |
| Navigation | 2.8.9 — screen navigation |
| Material components | 1.12.0 — interface components |

## 🧠 vision models

| task | model / engine |
|---|---|
| object detection | YOLO26n, EfficientDet-Lite2, EfficientDet-Lite0 or SSD MobileNet V2 |
| face detection and recognition | ML Kit + FaceNet-512 |
| object recognition | MobileNetV3 embeddings |
| image classification | EfficientNet-Lite0 |
| interactive segmentation | MediaPipe magic touch |
| text and code reading | ML Kit text recognition and barcode scanning |

YOLO26 is the default detector setting. its model is exported separately using [tools/yolo26/export.py](tools/yolo26/export.py), which documents the Linux, macOS and WSL setup. if the YOLO model is absent, the app falls back to EfficientDet-Lite2.

## ⚙️ configuration

### optional walking routes

add an openrouteservice key to the git-ignored `local.properties` file:

```properties
ORS_API_KEY=your-key
```

without a key, saved-place guidance uses an offline direction beacon. online routes require connectivity and a location fix.

### app settings

use the settings screen to change language, theme, detection model and scan preferences. voice controls also allow language switching and microphone control.

### builds without model downloads

use `-PskipModels` for development checks that do not need model inference:

```powershell
.\gradlew.bat :app:compileDebugKotlin -PskipModels
```

this skips model downloads; it does not supply missing model assets for device use.

## 🎙️ usage and voice commands

use home to open **look around**, **find something**, **saved**, **live scan** or **walk mode**. history and settings are also available from home.

say “Eye” or “눈길아” before a command. a wake word and command can be spoken together, for example “Eye, saved”.

| english | 한국어 | action |
|---|---|---|
| look around / full scan | 주변 둘러보기 | start a 360° scan |
| live scan | 실시간 안내 | announce nearby objects |
| find my bag | 가방 찾아줘 | find a target with beeps |
| saved | 저장한 것 | open saved people and objects |
| add person Ali | 사람 추가 민준 | enrol a person |
| walk mode | 걷기 모드 | start walking alerts |
| save this place as home | 여기를 집으로 저장해 줘 | save the current location |
| take me home | 집까지 안내해 줘 | guide to a saved place |
| read text | 글자 읽어 줘 | open the reader |
| who is this / what is this | 누구야 / 이게 뭐야 | recognise a person or object |
| repeat / stop / back / help | 다시 / 멈춰 / 뒤로 가 / 도움말 | control the current screen |
| Korean / English | 한국어 / 영어 | switch language |
| stop listening | 듣기 중지 | turn off the microphone |

tap the microphone button to speak; long-press it to toggle always-on voice listening. spoken output also appears in the caption bar.

## 📁 project structure

```text
app/src/main/
├── java/com/nungil/
│   ├── core/       # pure Kotlin rules: scanning, matching, voice and walking
│   ├── contract/   # shared interfaces and navigation destinations
│   ├── shell/      # activity, home and app coordination
│   ├── scan/       # camera, shared detectors and scan screens
│   ├── search/     # finding targets
│   ├── people/     # face recognition
│   ├── items/      # saved object recognition
│   ├── reader/     # text and code reading
│   ├── speech/     # speech recognition and spoken output
│   ├── weather/    # weather and clock controls
│   └── data/       # settings and persistence
├── res-a/          # scan resources
├── res-i/          # interface, voice and walking resources
├── res-y/          # search, saved and reader resources
└── assets/         # local models, downloaded or exported
app/src/test/       # JVM unit tests
app/src/androidTest/ # device tests
baselineprofile/    # baseline profile generation
tools/              # model export and diagnostic tools
docs/               # build, design and implementation documentation
```

## 🧪 testing

run a focused saved-feature regression check:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.nungil.core.saved.*" -PskipModels
```

run the complete JVM tests when needed:

```powershell
.\gradlew.bat :app:testDebugUnitTest -PskipModels
```

check compilation and android lint:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:lintDebug -PskipModels
```

[CI](.github/workflows/ci.yml) runs JVM tests and a debug build for pull requests targeting `main`. device tests live in `app/src/androidTest/` and require a connected device or emulator; model and sensor checks need the corresponding assets and hardware.

## 📌 notes and limitations

- the reader currently uses latin text recognition; korean OCR needs an additional model.
- depth alerts depend on ARCore support. without usable depth, walking feedback is limited to detections.
- GPS is less reliable indoors; saved-place arrival accounts for reported location accuracy.
- ARCore scene semantics is disabled because of device-specific stability problems.
- walking alerts are assistive cues and never announce that a path is “safe”.
- weather comes from Open-Meteo; route requests use openrouteservice when configured.

## 📖 documentation

- [build guide](docs/build-guide.md) — setup, architecture and implementation details.
- [hackathon brief](docs/hackathon-brief.md) — product scope and engineering decisions.
- [UI guide](docs/design/ui-guide.md) — design and accessibility conventions.
- [demo script](docs/design/demo-script.md) — demonstration flow.
- [team plan](docs/superpowers/plans/2026-09-24-00-team-plan.md) — original hackathon work split.

## 🙏 acknowledgements

- [MediaPipe](https://ai.google.dev/edge/mediapipe) — vision models and task APIs.
- [Ultralytics](https://github.com/ultralytics/ultralytics) — YOLO26 detection.
- [FaceRecognition_With_FaceNet_Android](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android) — FaceNet-512 model source.
- [Pretendard](https://github.com/orioncactus/pretendard) — interface font.
- [openrouteservice](https://openrouteservice.org) and OpenStreetMap contributors — routing and map data.
- [Open-Meteo](https://open-meteo.com) — weather data.
- KRDS and the Wanted Design System — visual design references.

## ⚖️ license

nungil is released under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. see [LICENSE](LICENSE) for the terms.

YOLO26 is licensed under AGPL-3.0. MediaPipe models and the referenced FaceNet project use Apache-2.0; Pretendard uses the SIL Open Font License. third-party components retain their respective licences.
