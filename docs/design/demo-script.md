# Demo script (3–4 minutes)

Owner: I. The run order follows team plan §9. I opens and does the hands-free part, A does the scan
and the architecture slide, and Y does search and faces. Every voice command below is one the app's
parser accepts (`core/voice/VoiceCommandParser.kt`, `core/voice/WakeWord.kt`).

## Run order

| Time | Who | What the audience sees and hears |
|---|---|---|
| 0:00–0:15 | I | The opening line. The phone mirrored on screen with airplane mode on. |
| 0:15–1:15 | A | A live scan, then a full scan: the ring fills and one sentence is spoken. |
| 1:15–1:30 | I | The language is switched by voice, and A repeats the full scan in Korean. |
| 1:30–2:15 | Y | "Find my bag": the beeps speed up and the phone vibrates when the bag is centred. |
| 2:15–2:45 | Y | A saved person is named and a stranger stays "a person". |
| 2:45–3:30 | I | Hands-free: wake word, commands, interrupting the app, sleep. |
| 3:30–4:00 | A | One architecture slide. |

## I: opening (0:00–0:15)

Show the mirrored phone with airplane mode on (swipe down the quick settings once).

> EN: "A blind person cannot ask a room what is in it. This phone can, with no internet."
>
> KO: "시각장애인은 방 안에 무엇이 있는지 물어볼 수 없습니다. 이 휴대폰은 인터넷 없이 알려 줍니다."

## I: switch the language (1:15–1:30)

The app starts asleep. Say the wake word first and wait for the rising chime.

| Say | The app |
|---|---|
| "Eye" | rising chime; the Home button reads "Speak" |
| "Korean" (or "한국어") | the screens switch to Korean |
| "눈길아, 주변 둘러보기" | opens the full scan in Korean; A turns once |

## I: hands-free (2:45–3:30)

Put the phone on the stand. Keep your hands off it and away from the table.

| # | Say | The audience sees and hears |
|---|---|---|
| 1 | "Eye stop" | falling chime; the Home button reads "Say “Eye” to start" |
| 2 | "saved" | **nothing happens**, because the app is asleep. Say to the room: "It only listens to its name." |
| 3 | "Eye, saved" | rising chime, and the Saved screen opens: waking and the command in one sentence |
| 4 | "full scan" | the scan opens and starts by itself |
| 5 | (turn the phone a little, then say) "stop" | the scan stops and the summary is spoken |
| 6 | while the summary is being spoken: "back" | **the app goes silent at once** and returns to Home. Say: "When I talk, it stops talking." |
| 7 | "Eye stop" | falling chime; asleep again |

In Korean the same steps are: "눈길아 그만", "저장한 것" (ignored), "눈길아, 저장한 것", "주변 둘러보기",
"멈춰", "뒤로 가" during the summary, "눈길아 그만".

Speaking tips:
- Say the wake word on its own, wait for the chime, then the command. "Eye, saved" in one breath also
  works.
- Speak at normal volume, facing the phone, about 30 cm away.

## If something fails on stage

1. **The app did not react.** Say the command once more, a little slower.
2. **It still did not react.** Tap the microphone button on Home. Every sound stops and the app listens,
   so say the command again.
3. **Voice does not work in the hall at all** (noise, echo). Switch to the recorded clip, say "Here is
   the same thing recorded this morning", and carry on.
4. **The app crashed.** Reopen it from the launcher. It starts asleep, so say "Eye" and continue from
   the current step.

Record the backup clip during the last rehearsal, with the phone connected by USB:

```powershell
scrcpy --record demo-voice.mp4 --stay-awake
```

## Phone checklist before going on stage

- [ ] Battery at least 80 %, charger in the bag.
- [ ] **USB cable for scrcpy.** In airplane mode the Wi-Fi is off, so wireless adb and a wireless
  mirror stop working.
- [ ] Airplane mode on (it is the pitch). Do Not Disturb on with media allowed, so no notification
  pops up.
- [ ] Media volume at 80–100 %. Silent or vibrate mode does not mute the app's speech.
- [ ] Korean TTS voice installed (Settings → Text-to-speech → Google → Korean).
- [ ] **Korean offline speech pack installed** (Google app → Settings → Voice → Offline speech
  recognition → 한국어). Without it "눈길" and the Korean commands are not recognised, and the app says
  "영어로 들을게요".
- [ ] App language set to the one you open with. The onboarding was already shown once.
- [ ] A person enrolled for Y's part; the bag for "find my bag" is on the table.
- [ ] Developer options → Stay awake on; screen timeout 10 minutes.
- [ ] System font size at default. Other apps closed and old notifications cleared.
- [ ] Microphone not covered by a case or a hand. Test "Eye" once in the hall itself.

## Rehearsal

- Three full runs with a timer. Cut words, not steps, if a run goes over 4 minutes.
- Record the best run as the backup clip.
- Each person says their own lines; nobody reads from a screen.

## Questions I should be ready for

- **Does the microphone listen all the time?** Yes, while the app is open, but it acts only between the
  wake word and "Eye stop". It never listens in the background.
- **Does audio leave the phone?** In airplane mode it cannot. Recognition runs on the phone's offline
  speech model.
- **What if it hears its own voice?** The app compares what it hears with what it is saying and ignores
  its own sentence. Only words it is not saying count as the user talking.
- **Why a wake word?** People nearby talk too. Without a wake word the app would react to a whole room.
- **Korean without the offline pack?** The app notices, says so, and keeps working in English.
