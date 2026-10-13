# Shared models and one frame source (stage 4 of the shared scanner modules)

Status: 4a built 2026-10-13 (user: keep 30 s, 4a now, 4b after the numbers). 4b not started.

## Why

- **Crash.** A model loaded on one thread while another model ran on another thread crashed MediaPipe (SIGBUS, live-quality plan Task 7). `ModelLock` (1fc963c) stops that by making loads wait for running calls. That treats the symptom: every screen still loads and closes its own models, so each screen switch is a load next to running work.
- **Time.** Each screen builds its models when it opens: the detector, the classifier, the item embedder and segmenter, the face embedder. Opening Live after Find loads the same files again.
- **Memory.** Two screens can hold the same model at once while one is closing and the other opening. The same happens with `ItemFinder` in Live (taggers) and in Find (target matcher).

## What there is now

| Model | Built in | Closed in |
|---|---|---|
| Object detector (lite0/lite2/SSD, CPU/GPU) | `CameraSession.detect` (`DetectorFactory`), and `WalkVision` with its own (CPU) | `CameraSession.stop`, `WalkVision.close` |
| Scene classifier | `ScanFragment` (laptop check, "what is this"), `FrameAnswers`, `WalkVision` | each owner |
| Item embedder and segmenter | `ItemFinder` (Live taggers, Find), `ItemSight` (Add Object) | each owner |
| Face embedder and ML Kit face detector | `FaceIdentifier` (Live taggers, "who is this"), `EnrollFragment` | each owner |
| ML Kit text and barcode | `ReaderEngine`, `WalkVision` | each owner |

There are seven camera screens: Live and Look around, Find, the reader, Add Object, Add Person and Walk. Each has its own `CameraSession`, except Walk, where ARCore owns the camera.

## Proposal: two steps, the first one alone first

### 4a. One instance per model kind (`ModelPool`)

- `ModelPool` is app-wide and held by `AppServices`. It hands out one shared instance per kind and key, for example the detector for (model file, compute).
- A screen takes a model with `use(kind)` and gives it back with `done(kind)`. It does not build or close the model itself.
- A model nobody uses stays loaded for 30 s and is then closed under `ModelLock.load`. A screen switch within 30 s reuses it, with no load and no close.
- Taking a detector with another key (another model or processor setting) closes the old one first: two detectors are never loaded at once.
- Calls still go through `ModelLock.run`. With nothing loading during a switch, the lock is rarely contended.
- **Changes:** `CameraSession`, `WalkVision`, `ScanFragment`, `FrameAnswers`, `ItemFinder`, `ItemSight`, `FaceIdentifier`, `EnrollFragment` and `ReaderEngine` take their models from the pool. This is about 10 files plus a new `core/scan/ModelPool` with the pure rules: who holds what, when to close, key changes.
- **Tests:**
  - Unit tests for the pool rules: use and done counts, the 30 s keep, the key change, close once.
  - On the phone: switch Live, Find, reader and Live by voice 10 times. No crash. Log the time to the first frame with detections before and after the change.

### 4b. One camera pipeline (`FrameSource`), later and only if 4a is not enough

- One CameraX binding held by the activity. Screens subscribe to its frames instead of binding their own camera, so there is no rebind on a screen switch.
- Walk keeps ARCore.
- This is bigger: every camera screen's start, stop and switch-camera code changes. The gain is the camera start time (about 0.3–0.6 s per switch). Measure that after 4a before deciding.

## Recommendation

Do 4a, inline, on its own branch:

1. Measure first: screen open to first detections, and memory, on the phone, for Live, Find and the reader.
2. Write the pool rules with tests.
3. Move the owners one by one, building and checking on the phone after each.

Leave 4b until the numbers after 4a are in.

Size of 4a: about 11 files, one to two sessions. Risk: a model closed while a screen still uses it. The pool closes a model only when its count is 0, and only after 30 s.

## Built (4a)

- `core/scan/ModelShelf` (pure, unit-tested): one instance per kind and key, leases, kept 30 s after the last user
  (closed 30-35 s on; swept every 5 s on a thread of its own), another key of a kind closes the kind's idle ones,
  models built and closed outside the shelf's lock.
- `scan/SharedModels`: detector (by file and processor), classifier, item embedder, item segmenter, face embedder.
- `scan/SharedDetector`: the detector has a thread of its own; it is built, used and closed there (the GPU delegate
  is used on one thread), and callers wait for it. `detect` must not be called inside `ModelLock.run`.
- Each model lets one call in at a time (`@Synchronized`), since screens now share it.
- Owners moved: `CameraSession`, `WalkVision` (its classifier is the shared one, `SceneClassifier.top`),
  `ScanFragment`, `FrameAnswers`, `FaceIdentifier`, `EnrollFragment`, `ItemFinder`, `ItemSight`.
- On the phone (androidTest `SharedModelsTest`): two threads use one GPU detector at once and hand over ten times,
  no crash, the detector loaded once; first screen 2.1 s with the loads, a screen later 0.55 s.
- Left: a live check (Live, Find, reader, Live by voice, and model changes in Settings).

## Open questions (answered)

1. Keep a model loaded 30 s after the last screen leaves it (faster switching), or close it at once (less memory)?
2. 4a only now, and 4b decided after the measurements?
