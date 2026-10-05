# Shared Announcer and Directions (stage 3 of the shared scanner modules)

## Why

The three screens that describe the scene each decide on their own what to say, when, and with which direction words.

| | Live / Look around | Find | Walk / Go |
|---|---|---|---|
| What, when | `ScanSession`: each object once per scan, when confirmed | `SearchTracker`: first sighting at once, a new zone at most every 2 s, "Lost it" after 3 s | `WalkAlerts`: priority, straight ahead first, a sentence not again within 6 s, a topic within 12 s only when closer |
| Queue | `SpeechQueue`: queued, joined, 0.4–1.5 s gaps | `say` / `sayNow` directly | `WalkPacing`: nothing queued, a stale sentence is never said, "ahead" cuts in |
| Direction words | 4 compass sectors: in front / on your right / behind you / on your left | 5 frame zones: ahead / on your left / far left … | 3 frame zones: ahead / on your left / on your right; Go: "at 2 o'clock" |

Live scan findings were said seconds late from a queue (log 2026-10-04); Walk had already solved that. The same
direction is said with different words on different screens. Goal: one announcer and one set of direction words.

Stage 3 of 4. Live-scan search speed and the deferred stage-2 minors are separate small PRs (agreed).

## Decisions (agreed)

1. **One announcer.** `WalkAlerts` and `WalkPacing` become the shared `Announcer`. Screens decide *what* could be
   said (candidates); the announcer decides *what is said now*.
2. **No queue for scene descriptions.** At most one sentence per frame; straight ahead first, then by priority; a
   sentence about something straight ahead cuts in, anything else waits for silence and is said only if it is still
   a candidate then. A candidate not said stays a candidate while it is in view, so nothing is lost by waiting.
3. **Live: an object is said again after 10 s out of view** (user's choice). In view the whole time, it is said once.
4. **One direction model.** Screens give a bearing (degrees, 0 = ahead, negative = left); one function turns it into
   words or a clock hour.
5. **Words by default, clock hours as a setting** (user's choice). Settings gets "Clock directions"; when on, every
   screen says hours ("at 10 o'clock"). Go's clock direction every 10 s is unchanged (it already uses hours).

## Directions (`core/ui/Directions.kt`, pure)

```kotlin
enum class DirectionStyle { WORDS, CLOCK }

object Bearings {
    /** Words or a clock hour for [deg] (0 = ahead, negative = left, -180..180). */
    fun say(deg: Float, style: DirectionStyle, lang: Lang): String
}
```

| Bearing | Words EN | Words KO | Clock EN | Clock KO |
|---|---|---|---|---|
| |deg| ≤ 7 | ahead | 앞에 | at 12 o'clock | 12시 방향에 |
| 7 < |deg| ≤ 20 | slightly left / slightly right | 조금 왼쪽에 / 조금 오른쪽에 | at the nearest hour | N시 방향에 |
| 20 < |deg| ≤ 135 | on your left / on your right | 왼쪽에 / 오른쪽에 | at the nearest hour | N시 방향에 |
| |deg| > 135 | behind you | 뒤에 | at the nearest hour | N시 방향에 |

Clock hour = `round(deg / 30)` mod 12, 0 shown as 12 (−30° → 11, +90° → 3, 180° → 6).

Where bearings come from:

- **Live / Look around:** the object's angle relative to where the user faces (compass), as today.
- **Find:** the target's place in the frame: zone → bearing FAR_LEFT −26, LEFT −13, AHEAD 0, RIGHT 13, FAR_RIGHT 26
  (the zones and beeps stay as they are).
- **Walk:** zone → bearing LEFT −22, AHEAD 0, RIGHT 22.

So Find's "far left" becomes "on your left" and its "on your left" becomes "slightly left"; Walk keeps "on your
left"; Live says "ahead" / "slightly left" where it said "in front". Korean sentences keep their current grammar
with the new direction word in the same place.

The style is app-wide: `AppPrefs.clockDirections` (default off), read through `AppServices.directionStyle`, and a
switch on the Settings screen ("Clock directions" / "시계 방향으로 말하기").

## Announcer (`core/ui/Announcer.kt`, pure)

```kotlin
data class Notice(
    val key: String,           // the situation ("wall:ahead", "obj:12", "find:LEFT"); not said again while it lasts
    val text: String,
    val priority: Int,         // lower is more important
    val ahead: Boolean = false,
    val urgent: Boolean = false,
    val topic: String? = null,
    val level: Int = Notice.FAR,
)

class Announcer(private val rules: Rules) {
    data class Rules(val goneMs: Long, val repeatMs: Long, val topicRepeatMs: Long)
    /** The one sentence to say now, or null (nothing new, or the last sentence is still playing). */
    fun choose(nowMs: Long, candidates: List<Notice>): Notice?
    /** A sentence said outside the announcer: waiting notices do not talk over it. */
    fun said(nowMs: Long, text: String)
    fun reset()
}
```

The body is `WalkAlerts.choose` + `WalkPacing` as they are today (per-key newness with `goneMs`, text repeat with
`repeatMs`, topic repeat with `topicRepeatMs` unless urgent or closer, sort by `!ahead` then `priority`, a
non-ahead notice only when the last sentence's estimated duration has passed). One extra entry point for sentences
said outside the announcer (Walk's answers to questions): `said(nowMs, text)` so waiting notices do not talk over them.

Rules per screen:

| Screen | goneMs | repeatMs | topicRepeatMs |
|---|---|---|---|
| Walk / Go | 2 000 | 6 000 | 12 000 (today's values) |
| Live / Look around | 10 000 | 6 000 | 12 000 |
| Find | 2 000 | 2 000 | 12 000 |

`AlertKind` stays Walk's list of priorities (its ordinal is the notice's priority). Walk keeps `Alert` as its own
type and maps it to `Notice` in one place (`Alert.toNotice()`, used by `WalkFragment`).

## Screens

- **Walk / Go:** `WalkAlerts` + `WalkPacing` → `Announcer(Walk rules)`; `WalkVision` builds `Notice`s; zone
  words come from `Bearings`. Behaviour unchanged apart from the direction words above.
- **Live / Look around:** `ScanSession` reports, each frame, the confirmed objects in view with a stable key (the
  cluster) and their bearing; each becomes a `Notice` (key `obj:<cluster>`, text = the live phrase with
  `Bearings`, priority 0, ahead when |bearing| ≤ 7). The chosen one is said with `sayNow`. `Speaker.sayLive` and
  `SpeechQueue.addLive` are removed (no other user). Notices such as "Slow down." stay ordinary `say` calls. The
  full-scan summary at the end is unchanged.
- **Find:** `SearchTracker` still decides when the target's place is worth saying; its `Where(zone)` / `Lost`
  become `Notice`s (keys `find:<zone>` / `find:lost`, ahead when AHEAD) through `Announcer(Find rules)` and are
  said with `sayNow`; the text uses `Bearings`.

## Errors

- No compass on Live: bearings come from the frame as in a full scan without compass today (relative angle 0 =
  the middle of the frame).
- A sentence that is never finished by the engine: the duration estimate ends the wait (today's `WalkPacing`).

## Testing

Unit (JUnit):

1. `BearingsTest` — every row of the table in both styles and both languages; the bucket edges (7, 20, 135);
   clock hours −30 → 11, 90 → 3, 180 → 6, 0 → 12.
2. `AnnouncerTest` — today's `WalkAlertsTest` and `WalkPacing` tests moved over unchanged in meaning; plus: a
   notice not said because another spoke stays a candidate and is said once quiet; with Live rules an object out of
   view for 9 s is not said again, for 11 s it is; `said()` from outside holds non-ahead notices back.
3. `ScanSessionTest` — confirmed objects in view are reported every frame with stable keys and bearings.
4. Existing `SummaryBuilderTest`, `SearchPhrasesTest`, `WalkPhrasesTest` updated to the new direction words.

Device: Live in a room (objects said on time, again after 10 s away, not while in view); Find (zones as words, then
with Clock directions on as hours); Walk (hazards unchanged in timing, words as before for left/right).

## Out of scope

Live-scan search speed and the stage-2 minors (separate PRs), the frame source (stage 4), Go's route guidance
sentences, and the full-scan summary's wording apart from its sector words.
