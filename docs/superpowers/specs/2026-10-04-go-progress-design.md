# Go mode: progress, questions and safety guidance

Date: 2026-10-04. Branch: `go-progress`.

## Intent

Go mode guides a blind or low-vision walker to a place, turn by turn, with obstacle warnings from the camera.
Today it says the total distance once ("Guiding you to Seoul Station, 1.2 kilometres") and then only the turns.
The walker cannot tell how far they still have to go or how long it will take, and cannot ask.

This work adds what navigation apps for blind walkers do (BlindSquare, Lazarillo, Soundscape, Google Maps
detailed voice guidance):

1. Every minute, the distance left and about how long it will take.
2. The same, and more, when the walker asks: how far, what next, repeat, which way, where am I.
3. A warning when they walk the wrong way, and when the GPS is too weak to trust.
4. The destination announced on the way in, with the side of the street it is on.

Success: on a real walk the walker hears "350 metres left, about 6 minutes" each minute without it ever
covering an obstacle warning or a turn, gets an answer to each question in English and Korean, and is told
within about 20 metres when they walk away from the route.

## Decisions (agreed in chat)

| Question | Decision |
|---|---|
| What the minute update says | Short: distance left and time. The next turn has its own announcements at 25 m and 5 m. |
| Speed behind the time | The walker's own speed while moving, over the last 2 minutes; 1 m/s until there is enough of it. Stops do not lower it. |
| Turning the minute update off | By voice: "quiet updates" / "updates on". Remembered between walks. Turns and warnings are never turned off. |
| Scope | All four of: questions, wrong way (and weak GPS), the approach, "where am I". |
| Not in scope | A foreground service for a screen-off walk (Go mode needs the camera, so the screen stays on), crossings and traffic lights (no data), a settings screen. |

## Behaviour

All sentences exist in English and Korean. Distances use `WalkPhrases.far` (5 m steps under 100 m, 10 m steps
under 1 km, then kilometres with one decimal).

### 1. The minute update

- Said every 60 s while a destination is set: on a route, or by straight-line beacon when there is no route.
- On a route: "350 metres left, about 6 minutes." / "350미터 남았어요, 약 6분." The distance is
  `Navigator.peek().remainingM`.
- By beacon: "350 metres in a straight line, about 6 minutes." / "직선으로 350미터, 약 6분."
- The time: under 45 s "less than a minute" / "1분도 안 걸려요"; then whole minutes, rounded, at least 1;
  from 60 minutes "about 1 hour 20 minutes" / "약 1시간 20분".
- It is put off, not dropped, while any of these holds, and said at the first moment none does:
  the next turn is within 25 m (its own announcement is near); another navigation sentence was said in the last
  15 s; a new route is being fetched. The next update is 60 s after the one said.
- An answer to "how far" counts as an update: the next one is 60 s after it.
- It goes through the existing navigation slot (`pendingNav`), so obstacle warnings are said first and a turn
  announcement that comes at the same moment replaces it.
- "Quiet updates" stops it until "updates on". The setting is kept in shared preferences.

### 2. The walker's speed

- Progress is the distance covered along the route (`Navigator` already measures it), or, by beacon, how much
  the straight-line distance has shrunk.
- From the fixes of the last 120 s, only the intervals in which the walker moved forward at 0.3 m/s or more
  count. With at least 20 s and 10 m of such moving, speed = moving distance / moving time, kept between
  0.5 and 2.0 m/s. Otherwise the last speed found stands, and 1.0 m/s before any.
- Standing at a crossing therefore does not make the time grow, and walking backwards does not count.
- A new route (reroute) starts the window again but keeps the last speed.

### 3. Questions

Taken in Go mode before anything else parses the words (`WalkFragment.takesWords`), and only while no found
place waits for "yes" or "next". Each answer is said at once.

| Asked (examples, EN / KO) | Answer |
|---|---|
| "how far", "how long", "how much further", "how many minutes", "when will I arrive" / "얼마나 남았어", "몇 분 남았어", "언제 도착해", "거리" | The minute update's sentence. |
| "what's next", "next turn", "next instruction" / "다음은", "다음 안내" | "Next, turn right in 80 metres." After the last turn: "Next, Seoul Station in 80 metres." |
| "repeat", "say again", "what did you say" / "다시", "반복", "뭐라고" | The last navigation sentence (turn, prepare, update, approach, wrong way); the next instruction if none was said yet. |
| "which way", "where do I go", "direction" / "어느 쪽", "어디로 가" | "The next turn is at 2 o'clock, 80 metres." (by beacon: "Seoul Station is at 2 o'clock, 350 metres."). Without a compass heading: "I can't tell the direction yet. Hold the phone up and ask again." |
| "where am I", "what street is this", "my location" / "여기 어디야", "지금 어디야", "현재 위치" | Section 6. |
| "quiet updates", "fewer updates", "updates off", "stop updates" / "안내 조용히", "업데이트 꺼" | "Minute updates off. Ask how far any time." |
| "updates on", "more updates" / "업데이트 켜", "안내 다시 켜" | "Minute updates on." |

- Without a destination, "how far", "what's next" and "which way" answer "No route is running. Say go to, and
  a place." "Repeat" then keeps its global meaning. "Where am I", "quiet updates" and "updates on" work in walk
  mode and on "Where to?" too.
- Only whole questions count, as in `QuickAsk`: "next time" and "repeat after me" are not questions.
- "Repeat" anywhere else in the app keeps its global meaning (the last thing said).

### 4. Wrong way and weak GPS

- Wrong way: on a route, when the progress along it falls 15 m or more below the farthest it has reached, while
  the walker is still within 40 m of the line (so not yet off the route): "You are walking away from the route.
  Turn around." / "경로에서 멀어지고 있어요. 뒤로 돌아가세요." It is said once, then again only after the
  walker has regained that farthest point or a new route started. The off-route rule (40 m, three fixes) is
  unchanged.
- Weak GPS: when the fix accuracy has been worse than 30 m for 10 s while a destination is set: "The GPS signal
  is weak, directions may be off." / "GPS 신호가 약해서 안내가 정확하지 않을 수 있어요." Once, and again only
  after it has been 20 m or better.
- Both go through the navigation slot, like the turns.

### 5. The approach

- On a route, at 50 m and at 20 m left: "Seoul Station in 50 metres, on your right." / "50미터 앞 오른쪽에
  서울역이 있어요." Each said once per route.
- The side: the destination point against the direction of the route's last segment (its last two points).
  More than 3 m to the side gives "on your right" or "on your left"; otherwise no side.
- Arrival keeps its radius (15 m, or the GPS accuracy when worse) and its buzz, and says the side when there is
  one: "You have arrived at Seoul Station, on your right."
- By beacon there is no route direction, so no side; the 50 m and 20 m sentences still come.

### 6. Where am I

- Needs the network and the openrouteservice key: reverse geocoding (Pelias `reverse` on the current host,
  the old host as fallback, as `geocode` does), the nearest street or address.
- "You are on Sejong-daero, near Seoul Station." The "near" part is the nearest saved place or the
  destination when it is within 300 m; left out otherwise.
- Without network, key or an answer: "I can't look up the street now." followed by the destination's distance
  and clock direction when a destination is set.
- Without a location fix yet: the existing "Waiting for location" and the answer when the fix comes.
- In walk mode or on "Where to?", location is started for the question and stopped after it.

## Units

| Unit | Kind | Does | Tested by |
|---|---|---|---|
| `core/walk/GoPace` | new, pure | Moving speed from (time, progress) samples; minutes left for a distance. | unit tests |
| `core/walk/GoProgress` | new, pure | When the minute update is due, the put-off rules, the quiet switch. | unit tests |
| `core/walk/GoQuestion` | new, pure | Words to a question (EN/KO), whole questions only. | unit tests |
| `core/walk/GpsSignal` | new, pure | Weak for 10 s, good again at 20 m. | unit tests |
| `core/walk/Navigator` | changed | Wrong way, approach at 50/20 m, destination side, progress exposed for `GoPace`. | unit tests |
| `core/walk/RoutePhrases`, `WalkPhrases` | changed | The new sentences. | unit tests |
| `core/walk/OrsJson` | changed | Reverse-geocode answer to a street and a name. | unit tests |
| `walk/WalkServices` | changed | `RouteSource.reverse(LatLon)`; the quiet setting in shared preferences. | device |
| `walk/WalkFragment` | changed | Wiring: ticker, questions in `takesWords`, the navigation slot, last sentence for "repeat". | device |

## Logging (for the device walk)

- `Go update: 350 m left, 1.1 m/s, 6 min` each time one is said, and `Go update put off: <reason>`.
- `Go asked: HOW_FAR -> "<answer>"` for each question.
- `Go wrong way: 18 m back` and `Go GPS weak: 42 m` when said.

## Verification

- Unit tests for every pure unit above, EN and KO sentences included.
- A device walk outdoors, about 10 minutes with two turns: an update each minute, never over a warning or a
  turn; each question answered; walking back 20 m triggers the wrong-way sentence; the approach at 50 m and
  20 m with the right side.
