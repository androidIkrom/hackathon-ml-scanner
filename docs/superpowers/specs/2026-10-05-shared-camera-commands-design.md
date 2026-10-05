# Shared Camera Commands (stage 2 of the shared scanner modules)

## Why

Every camera screen handles the camera voice commands in its own `onVoiceCommand`, and they have drifted apart:

| Screen | Start | Stop | Front / back camera | What / who is this |
|---|---|---|---|---|
| Look around, Live (ScanFragment) | starts a scan | stops the scan | switches, says which | answers from the frame |
| Find (SearchCameraFragment) | — | leaves the screen | switches silently | leaves Find and opens Live |
| Walk, Go (WalkFragment) | resumes / answers | stops navigation or pauses | "not here" | leaves Walk and opens Live |
| Reader | reads | pauses | "not here" | leaves Reader and opens Live |
| Enroll (person) | starts / resumes | pauses | switches, says which | — |
| ItemEnroll | starts | pauses | — | — |

On 2026-10-04 "front camera" had to be added to four screens one by one. "What is this" said in Walk or Find takes
a walking user out of the screen they are using. Stage 1 made recognition shared; this stage makes the camera
commands shared, so a change reaches every camera screen at once.

Also in this stage (agreed): "Find my charger" resolved to a saved person named "My" instead of the item
"My charger test one" (log 2026-10-05 10:39).

## Decisions (agreed)

1. A `CameraScreen` interface, implemented by every screen with a camera: Scan, Find, Walk/Go, Reader, Enroll,
   ItemEnroll.
2. `CameraCommands` handles front/back camera, what is this, who is this, stop and start for the current
   `CameraScreen`, before the screen's own `onVoiceCommand`. Screens keep their own handlers for everything else
   (Walk's place answers, ItemEnroll's name answers, Delete...).
3. What / who is this answer on every camera screen from its last frame, without leaving it. On screens without a
   camera they still open Live (`Route.OpenAndSay`, unchanged).
4. Stop (option B, user's choice): the existing rule everywhere.
   - While the app talks, the first bare "stop" silences it only (`SpeechStop`, unchanged).
   - A stop that is not silenced away pauses the screen's work if it is working; the user stays on the screen.
   - A stop while the screen's work is paused leaves the screen (back).
   - "Start" resumes the work.
   So in Find a stop no longer leaves at once: it pauses, and a second stop leaves.
5. Walk/Go has only the back camera (ARCore owns it): "front camera" says so instead of switching.

## Interface

```kotlin
// contract/app/CameraScreen.kt
interface CameraScreen {
    /** The last analysed frame, for "what is this" / "who is this"; null before the first. Any thread. */
    fun lastFrame(): VisionFrame?
    /** The camera to switch, or null when this screen has the back camera only. */
    val switchable: SwitchableCamera?
    /** Scanning, searching, reading, walking or learning is going on (or about to start on its own). */
    val isWorking: Boolean
    /** Pause the work and say so; stay on the screen. */
    fun pause()
    /** Start or resume the work (the screen's "Start"). */
    fun resume()
}

interface SwitchableCamera {
    val facing: Facing
    /** Turn to [to], or to the other camera when null. */
    fun useCamera(to: Facing?)
}
```

`CameraSession` implements `SwitchableCamera` (it already has `facing` and `useCamera`).

## The decision (pure, `core/ui/CameraCommandPolicy.kt`)

```kotlin
sealed interface CameraAction {
    data class Switch(val to: Facing?) : CameraAction
    data object BackCameraOnly : CameraAction
    data object DescribeCentre : CameraAction
    data object NameFace : CameraAction
    data object Pause : CameraAction
    data object Leave : CameraAction
    data object Resume : CameraAction
}

object CameraCommandPolicy {
    /** What a camera screen does with [command]; null: not a camera command, the screen's own handler decides. */
    fun decide(command: VoiceCommand, canSwitch: Boolean, working: Boolean): CameraAction?
}
```

- `SwitchCamera(to)` → `Switch(to)` when `canSwitch`, else `BackCameraOnly`.
- `WhatIsThis` → `DescribeCentre`; `WhoIsThis` → `NameFace`.
- `Stop` → `Pause` when `working`, else `Leave`.
- `Start` → `Resume`.
- Anything else → null.

The speech-only first stop stays where it is (`MainActivity.onHeard`, before any command handling), so the policy
only sees stops that were not silenced away.

## Answering what / who is this

`FrameAnswers` (`scan/FrameAnswers.kt`, Android), one per camera screen, created lazily, worker thread inside:

- **What:** the detection nearest the middle (`SceneRules.centerDetection`) → `ScanPhrases.looksLike(name)`;
  else the 1000-class classifier on the middle of the bitmap (`SceneClassifier.nameCenter`) → `looksLike`; else
  `ScanPhrases.unknownThing`.
- **Who:** faces on the whole bitmap (no person box needed, so it works on screens without the detector); the
  face nearest the middle → `ScanPhrases.thisIs(name)` when it is a saved person, `unknownPerson` when it is not,
  `nobody` when there is no face. Uses `FaceIdentifier`, extended to report unrecognised faces too.
- No frame yet → "I can't see anything yet." / "아직 아무것도 안 보여요."
- The answer is said with `speaker.sayNow`. An answer that arrives after the screen closed is dropped.
- ScanFragment's own `whatIsThis` / `whoIsThis` / `classifyCenter` move here.

## Screens

| Screen | `switchable` | `isWorking` | `pause()` | `resume()` | `lastFrame()` |
|---|---|---|---|---|---|
| Scan | camera | scanning, or the auto-start pending | today's stop (stops the scan, saves it; cancels the auto-start) | `startScan` | last frame (already kept) |
| Find | camera | searching | stop matching frames, say "Paused." | resume matching | last frame |
| Walk/Go | null | navigating, offering, or running | today's Stop branch | today's Start branch | WalkVision's last analysed frame |
| Reader | camera | reading | today's pause | today's resume | last frame |
| Enroll | camera | learning | today's pause | today's start | last frame |
| ItemEnroll | null (learning uses the back camera) | learning | today's pause | today's start | last frame |

`CameraCommands.cameraSwitched` speaks `ScanPhrases.cameraSwitched` after a switch (Find and Reader were silent).
`BackCameraOnly` speaks "Only the back camera works here." / "여기서는 뒤 카메라만 쓸 수 있어요."

## "Find my X"

`SearchResolver.matchSaved`, containment step: today the first saved name whose words appear in the query wins,
and names are also matched against the raw words (so a filler-word name like "Me" is never lost). Change:

1. Among the names that match by containment, the one with the most words in the query wins; on a tie, the longer
   name.
2. A name that matches only through the raw words (its words are all fillers the cleaner drops, like "my", "me")
   wins only when no other saved name and no label matches the query.

Examples: "my charger" → "My charger test one" (not "My"); "find me" → "Me"; "my bag" with a person "My" and no
item "bag" → the label backpack; "my" alone → "My".

## Errors

- No frame yet: the "can't see anything yet" sentence.
- Classifier or face model fails to load: `unknownThing` / `unknownPerson`, logged once.
- Switching fails: the screen's existing camera-problem path (`onCameraError`).
- A screen closed while an answer was being worked out: the answer is dropped.

## Testing

Unit (JUnit):

1. `CameraCommandPolicyTest` — every row of the decision table above, including `Stop` working / not working,
   `SwitchCamera` with and without a switchable camera, and a non-camera command → null.
2. `SearchResolverTest` — "my charger" with "My" and "My charger test one" saved → the charger; "find me" with "Me"
   → Me; "my bag" with "My" → backpack; "my" → My; every existing test still passes.

Device (the physical phone):

- Find: "what is this" answers without leaving; "stop" → "Paused."; "stop" → back to "What should I find?".
- Walk: "front camera" → "Only the back camera works here."; "what is this" answers without leaving Walk.
- Reader: "front camera" switches and says so.
- Live: stop / start as before; who is this names a saved face.
- "Find my charger" → "Looking for My charger test one."

## Out of scope

Announcing rules (stage 3) and the frame source (stage 4). No change to the parser's command words, to
`SpeechStop` or to non-camera screens. On camera screens, start and pause do what each screen does today; the
one new behaviour everywhere is that a stop while the work is already paused leaves the screen (Scan used to say
"Stopped." again, Find used to leave on the first stop).
