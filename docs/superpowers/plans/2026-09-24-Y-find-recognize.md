# Y — Find and Recognise Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Y builds everything that finds and recognises: spoken search with beeps and vibration, saved people (faces), saved cars and objects (by appearance), the Saved screens and a QR/text reader — in English and Korean.

**Architecture:** Pure-Kotlin rules in `core/search`, `core/people` and `core/items` (unit-tested), Android glue in `search/`, `people/`, `items/`, `saved/` and `reader/`. Camera frames come from A's `CameraSession`; speech, beeps and vibration come from I's `AppServices`; storage uses the frozen Room contract. Names reach A's scan through `createNameTaggers`.

**Tech Stack:** Kotlin 2.1.0, Fragments + view binding + safe-args, CameraX (through A), MediaPipe ImageEmbedder (MobileNetV3-Small), ML Kit face detection, barcode scanning and text recognition, TensorFlow Lite 2.17.0 (FaceNet-512), Room 2.7.2, JUnit 4.

**Spec:** `docs/build-guide.md` (§8.6, §8.7, §9.6), `docs/hackathon-brief.md` (§4–§6), and the team plan `docs/superpowers/plans/2026-09-24-00-team-plan.md`. **Read the team plan first; its Global Constraints apply to every task here.**

**Verified:** every code block below was compiled and unit-tested on 2026-09-24 in a copy of the Task 0 bootstrap. `gradlew clean testDebugUnitTest assembleDebug -PskipModels` gave BUILD SUCCESSFUL, with 92 Y tests and 17 bootstrap tests green. `tools/check-ownership.sh Y` accepted all 90 files. Camera, face and embedding code can only be checked on a phone.

**Execution order (team timeline):**

1. Y1 (h1.5–4)
2. Y3 (h4–6)
3. Y2 (h5–10; device test after A3 and I4 merge)
4. Y4 (h10–13)
5. Y5 (h13–17)
6. Y6 (h17–21)
7. Y7 (optional, h21–22)

Tasks are numbered by feature. Y3 comes before Y2 because it is pure Kotlin and needs no camera.

## Global Constraints (Y additions)

- **Folders:** Y edits only `core/search`, `core/people`, `core/items`, `search`, `people`, `items`, `saved` and `reader` (main and test), plus `app/src/main/res-y/`. The pre-commit hook enforces this.
- **Resource names:** they start with `search_`, `saved_`, `person_`, `item_`, `enroll_` or `reader_`.
- **Strings files:** each task adds its **own** file (`strings_search.xml`, `strings_saved.xml`, `strings_items.xml`, `strings_reader.xml`) in both `values/` and `values-ko/`. The bootstrap `res-y/values/strings.xml` stays untouched.
- **Camera screens:** every one uses `CameraGate` (Y2) for the permission and A's `CameraSession` for frames. It has one single-thread extras executor with an `AtomicBoolean` busy flag, and shuts that executor down with `awaitTermination(2, SECONDS)` in `onDestroyView`.
- **Threads:** capture `services()`, `lang` and the nav args on the main thread in `onViewCreated`. Worker threads use only those captured values.
- **Tuned numbers** (each pinned by a test):
  - Search: detector 0.4; zones at 0.2 / 0.4 / 0.6 / 0.8; beeps from 1000 ms down to 150 ms; "Lost it" after 3 s; a direction repeated at most every 2 s.
  - Faces: cosine 0.5, a 0.08 margin over the runner-up, and the mean of the best 3 samples; faces at least 64 px, yaw at most 35°, pitch at most 25°; FaceNet input 160 × 160, standardised; 5 poses × 4 samples with gates of 10° / 20° / 12°; recognition every 3rd frame that has a person box.
  - Items: cosine 0.75; enrolment in 3 steps × 4 samples at detector 0.3, box at least 5% of the frame; at most 3 crops of at least 16 px per frame.
  - Reader: codes cut at 80 characters; text lines of at least 3 characters; nothing repeated within 10 s.
- **Commits:** never add `Co-Authored-By` lines.

## Review Focus

1. **Hangul and odd saved names:**
   - Cases: "김민준" found by "민준" and by "민준이를 찾아줘", a person called "Me" or "나", "Office chair" vs "cup".
   - Pinned in Y1 `SearchResolverTest`: `hangulNamesWithParticlesAndGivenName`, `namesThatAreFillerWords`, `containmentByWholeWords`.
2. **Stranger vs saved person:**
   - Cases: a stranger close to one saved face, two similar saved faces, one lucky sample.
   - Pinned in Y3 `FaceMatcherTest` (`strangerBelowThresholdIsNobody`, `tooCloseToTheRunnerUpIsNobody`, `oneLuckySampleDoesNotWin`) and in Y4 `FaceBoxesTest`.
3. **Camera permission denied**, "don't ask again" included:
   - `CameraGate` asks once per screen. After a refusal it shows the panel with "Open app settings" and says why once, without looping.
   - Checked on the phone in Y2 step 7; Y5, Y6 and Y7 reuse the gate.
4. **The target never appears, or the model is missing:**
   - Search stays quiet except for the start phrase, and the beeps stay off. "Lost it" comes only after the target was seen.
   - A missing face or item model falls back to hunting by label.
   - Pinned in Y1 `SearchTrackerTest.silentUntilSeen`; the fallback is in `SearchCameraFragment`.
5. **Enrolment interrupted halfway** (Back, screen off, "stop"):
   - Nothing is written until the last sample. One `@Transaction` then writes the person or item with all its samples.
   - Pinned by design in Y5 and Y6: `finish()` is the only writer. Checked on the phone in Y5 step 6.

---

## Task Y1: Search core (English + Korean)

**Files:**
- Create: `app/src/main/java/com/nungil/core/search/SearchTarget.kt`, `QueryCleaner.kt`, `SearchResolver.kt`, `SearchGuide.kt`, `SearchTracker.kt`, `SearchPhrases.kt`
- Test: `app/src/test/java/com/nungil/core/search/QueryCleanerTest.kt`, `SearchResolverTest.kt`, `SearchGuideTest.kt`, `SearchTrackerTest.kt`, `SearchPhrasesTest.kt`

**Interfaces:**
- Consumes: `com.nungil.contract.Lang`, `com.nungil.contract.Facing`, `com.nungil.core.lang.LabelNames`, `com.nungil.core.lang.Josa` (Task 0).
- Produces:
  - `enum class TargetType { LABEL, PERSON, ITEM }` (its names are the nav argument `targetType`)
  - `data class SavedName(id: Long, name: String, type: TargetType, label: String)`
  - `data class SearchTarget(type: TargetType, id: Long, label: String, spokenName: String)`
  - `QueryCleaner.clean(text): Cleaned(raw: List<String>, words: List<String>)`, `QueryCleaner.normalize(text)`, `QueryCleaner.withoutParticles(word): List<String>`, `QueryCleaner.PARTICLES`
  - `SearchResolver.resolve(text: String, saved: List<SavedName>, lang: Lang): SearchTarget?`, `SearchResolver.labelFor(term): String?`, `SearchResolver.distance(a, b): Int`, `MAX_TYPOS = 2`, `MIN_FUZZY_LENGTH = 4`
  - `enum class Zone { FAR_LEFT, LEFT, AHEAD, RIGHT, FAR_RIGHT }`; `SearchGuide.userX(centerX, facing)`, `zone(x)`, `isCentered(x)`, `beepIntervalMs(x)`, `zoneWord(zone, lang)`, `FARTHEST_MS = 1000`, `NEAREST_MS = 150`
  - `SearchTracker(lostAfterMs = 3000, repeatMs = 2000).update(nowMs, zone: Zone?): Update(say: Say?, enteredCenter: Boolean)` with `Say.Where(zone)` and `Say.Lost`
  - `SearchPhrases.looking / lost / where / unknown / askWhat(…, lang)`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nungil/core/search/QueryCleanerTest.kt`

```kotlin
package com.nungil.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryCleanerTest {
    @Test fun stripsEnglishFillers() {
        val c = QueryCleaner.clean("Find my bag, please!")
        assertEquals(listOf("bag"), c.words)
        assertEquals(listOf("find", "my", "bag", "please"), c.raw)
    }

    @Test fun stripsWhereIsAndApostrophes() {
        assertEquals(listOf("keys"), QueryCleaner.clean("Where is the keys").words)
        assertEquals(listOf("phone"), QueryCleaner.clean("Where's my phone?").words)
        assertEquals(listOf("cup"), QueryCleaner.clean("search for a cup").words)
    }

    @Test fun stripsKoreanFillers() {
        assertEquals(listOf("가방을"), QueryCleaner.clean("내 가방을 찾아줘").words)
        assertEquals(listOf("휴대폰"), QueryCleaner.clean("휴대폰 어디 있어?").words)
        assertEquals(listOf("컵"), QueryCleaner.clean("컵 좀 찾아 주세요").words)
        assertEquals(listOf("의자"), QueryCleaner.clean("의자 어디에있어").words)
    }

    @Test fun rawKeepsNamesThatAreFillerWords() {
        val c = QueryCleaner.clean("find me")
        assertEquals(emptyList<String>(), c.words)
        assertEquals(listOf("find", "me"), c.raw)
    }

    @Test fun emptyInput() {
        val c = QueryCleaner.clean("  ?! ")
        assertEquals(emptyList<String>(), c.raw)
        assertEquals("", c.phrase)
    }

    @Test fun particles() {
        assertEquals(listOf("배낭"), QueryCleaner.withoutParticles("배낭을"))
        assertEquals(listOf("민준이", "민준"), QueryCleaner.withoutParticles("민준이를"))
        assertEquals(emptyList<String>(), QueryCleaner.withoutParticles("컵"))
        assertEquals(emptyList<String>(), QueryCleaner.withoutParticles("이"))
    }
}
```

`app/src/test/java/com/nungil/core/search/SearchResolverTest.kt`

```kotlin
package com.nungil.core.search

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResolverTest {
    private val ali = SavedName(1, "Ali", TargetType.PERSON, "person")
    private val minjun = SavedName(2, "김민준", TargetType.PERSON, "person")
    private val me = SavedName(3, "Me", TargetType.PERSON, "person")
    private val na = SavedName(4, "나", TargetType.PERSON, "person")
    private val alisher = SavedName(5, "Alisher", TargetType.PERSON, "person")
    private val keys = SavedName(10, "Office chair", TargetType.ITEM, "chair")
    private val redBag = SavedName(11, "빨간 가방", TargetType.ITEM, "backpack")
    private val saved = listOf(ali, minjun, me, na, alisher, keys, redBag)

    private fun label(text: String, lang: Lang = Lang.EN) = SearchResolver.resolve(text, emptyList(), lang)

    @Test fun cocoLabel() = assertEquals(SearchTarget(TargetType.LABEL, -1L, "chair", "chair"), label("find the chair"))

    @Test fun englishSynonyms() {
        assertEquals("backpack", label("find my bag")?.label)
        assertEquals("cell phone", label("where's my phone")?.label)
        assertEquals("dining table", label("desk")?.label)
        assertEquals("couch", label("sofa")?.label)
        assertEquals("tv", label("the monitor")?.label)
        assertEquals("bicycle", label("bike")?.label)
        assertEquals("laptop", label("my computer")?.label)
        assertEquals("laptop", label("notebook")?.label)
    }

    @Test fun pluralsAndTwoWordLabels() {
        assertEquals("chair", label("chairs")?.label)
        assertEquals("cell phone", label("find my cell phone")?.label)
        assertEquals("wine glass", label("wine glasses")?.label)
        assertEquals("knife", label("knives")?.label)
        assertEquals("bus", label("bus")?.label)
    }

    @Test fun koreanLabelsWithParticles() {
        val bag = label("내 가방을 찾아줘", Lang.KO)
        assertEquals(SearchTarget(TargetType.LABEL, -1L, "backpack", "배낭"), bag)
        assertEquals("cell phone", label("핸드폰 어디 있어", Lang.KO)?.label)
        assertEquals("chair", label("의자들을 찾아", Lang.KO)?.label)
        assertEquals("cat", label("고양이", Lang.KO)?.label)
        assertEquals("suitcase", label("여행 가방", Lang.KO)?.label)
        assertEquals("dining table", label("책상이 어디야", Lang.KO)?.label)
        assertEquals("tv", label("티비", Lang.KO)?.label)
    }

    @Test fun savedNamesComeFirst() {
        assertEquals(SearchTarget(TargetType.PERSON, 1L, "person", "Ali"), SearchResolver.resolve("find Ali", saved, Lang.EN))
        assertEquals(keys.id, SearchResolver.resolve("office chair", saved, Lang.EN)?.id)
    }

    @Test fun containmentByWholeWords() {
        assertEquals(keys.id, SearchResolver.resolve("chair", listOf(keys), Lang.EN)?.id)
        // "cup" is never part of "cupboard".
        val cupboard = SavedName(12, "Cupboard key", TargetType.ITEM, "remote")
        assertEquals(TargetType.LABEL, SearchResolver.resolve("cup", listOf(cupboard), Lang.EN)?.type)
    }

    @Test fun hangulNamesWithParticlesAndGivenName() {
        assertEquals(minjun.id, SearchResolver.resolve("김민준을 찾아줘", saved, Lang.KO)?.id)
        assertEquals(minjun.id, SearchResolver.resolve("민준 어디 있어", saved, Lang.KO)?.id)
        assertEquals(redBag.id, SearchResolver.resolve("빨간 가방 찾아줘", saved, Lang.KO)?.id)
    }

    @Test fun namesThatAreFillerWords() {
        assertEquals(me.id, SearchResolver.resolve("find me", saved, Lang.EN)?.id)
        assertEquals(na.id, SearchResolver.resolve("나", saved, Lang.KO)?.id)
    }

    @Test fun fuzzyOnlyForLongNames() {
        assertEquals(alisher.id, SearchResolver.resolve("find alisha", saved, Lang.EN)?.id)
        // "Alo" is 1 edit from "Ali" but "Ali" is shorter than 4 characters: no fuzzy match, no label either.
        assertNull(SearchResolver.resolve("alo", listOf(ali), Lang.EN))
    }

    @Test fun unknownAndEmpty() {
        assertNull(label("spaceship"))
        assertNull(label("find"))
        assertNull(label(""))
        assertNull(label("우주선", Lang.KO))
    }

    @Test fun labelForChips() {
        assertEquals("backpack", SearchResolver.labelFor("가방"))
        assertEquals("cell phone", SearchResolver.labelFor("Phone"))
        assertNull(SearchResolver.labelFor("   "))
    }

    @Test fun distanceCountsSyllables() {
        assertEquals(0, SearchResolver.distance("민준", "민준"))
        assertEquals(1, SearchResolver.distance("김민준", "김민중"))
        assertEquals(2, SearchResolver.distance("alisha", "alisher"))
        assertEquals(3, SearchResolver.distance("", "abc"))
    }
}
```

`app/src/test/java/com/nungil/core/search/SearchGuideTest.kt`

```kotlin
package com.nungil.core.search

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchGuideTest {
    @Test fun zoneBoundaries() {
        assertEquals(Zone.FAR_LEFT, SearchGuide.zone(0.19f))
        assertEquals(Zone.LEFT, SearchGuide.zone(0.2f))
        assertEquals(Zone.LEFT, SearchGuide.zone(0.39f))
        assertEquals(Zone.AHEAD, SearchGuide.zone(0.4f))
        assertEquals(Zone.AHEAD, SearchGuide.zone(0.6f))
        assertEquals(Zone.RIGHT, SearchGuide.zone(0.61f))
        assertEquals(Zone.RIGHT, SearchGuide.zone(0.8f))
        assertEquals(Zone.FAR_RIGHT, SearchGuide.zone(0.81f))
    }

    @Test fun beepIntervalIsLinear() {
        assertEquals(1_000L, SearchGuide.beepIntervalMs(0f))
        assertEquals(1_000L, SearchGuide.beepIntervalMs(1f))
        assertEquals(150L, SearchGuide.beepIntervalMs(0.5f))
        assertEquals(575L, SearchGuide.beepIntervalMs(0.25f))
        assertEquals(1_000L, SearchGuide.beepIntervalMs(-0.3f))
    }

    @Test fun centred() {
        assertTrue(SearchGuide.isCentered(0.5f))
        assertFalse(SearchGuide.isCentered(0.3f))
    }

    @Test fun frontCameraSwapsSides() {
        assertEquals(0.8f, SearchGuide.userX(0.2f, Facing.FRONT), 1e-6f)
        assertEquals(0.2f, SearchGuide.userX(0.2f, Facing.BACK), 1e-6f)
    }

    @Test fun zoneWords() {
        assertEquals("far left", SearchGuide.zoneWord(Zone.FAR_LEFT, Lang.EN))
        assertEquals("ahead", SearchGuide.zoneWord(Zone.AHEAD, Lang.EN))
        assertEquals("왼쪽 끝", SearchGuide.zoneWord(Zone.FAR_LEFT, Lang.KO))
        assertEquals("정면", SearchGuide.zoneWord(Zone.AHEAD, Lang.KO))
        assertEquals("오른쪽 끝", SearchGuide.zoneWord(Zone.FAR_RIGHT, Lang.KO))
    }
}
```

`app/src/test/java/com/nungil/core/search/SearchTrackerTest.kt`

```kotlin
package com.nungil.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTrackerTest {
    private val t = SearchTracker()

    @Test fun silentUntilSeen() {
        assertNull(t.update(0, null).say)
        assertNull(t.update(10_000, null).say)
    }

    @Test fun firstSightingIsAnnouncedAtOnce() =
        assertEquals(SearchTracker.Say.Where(Zone.LEFT), t.update(100, Zone.LEFT).say)

    @Test fun sameZoneIsNotRepeated() {
        t.update(0, Zone.LEFT)
        assertNull(t.update(5_000, Zone.LEFT).say)
    }

    @Test fun newZoneWaitsTwoSeconds() {
        t.update(0, Zone.LEFT)
        assertNull(t.update(1_999, Zone.AHEAD).say)
        assertEquals(SearchTracker.Say.Where(Zone.AHEAD), t.update(2_000, Zone.AHEAD).say)
    }

    @Test fun lostOnceAfterThreeSeconds() {
        t.update(0, Zone.AHEAD)
        assertNull(t.update(2_999, null).say)
        assertEquals(SearchTracker.Say.Lost, t.update(3_000, null).say)
        assertNull(t.update(9_000, null).say)
    }

    @Test fun seeingAgainAfterLostIsAnnouncedAtOnce() {
        t.update(0, Zone.AHEAD)
        t.update(3_000, null)
        assertEquals(SearchTracker.Say.Where(Zone.AHEAD), t.update(3_100, Zone.AHEAD).say)
    }

    @Test fun briefGapsDoNotCountAsLost() {
        t.update(0, Zone.AHEAD)
        t.update(2_000, null)
        t.update(2_500, Zone.AHEAD)
        assertNull(t.update(5_000, null).say)
        assertEquals(SearchTracker.Say.Lost, t.update(5_500, null).say)
    }

    @Test fun vibratesOnEnteringTheCentre() {
        assertFalse(t.update(0, Zone.LEFT).enteredCenter)
        assertTrue(t.update(100, Zone.AHEAD).enteredCenter)
        assertFalse(t.update(200, Zone.AHEAD).enteredCenter)
        t.update(300, Zone.RIGHT)
        assertTrue(t.update(400, Zone.AHEAD).enteredCenter)
    }
}
```

`app/src/test/java/com/nungil/core/search/SearchPhrasesTest.kt`

```kotlin
package com.nungil.core.search

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPhrasesTest {
    @Test fun canonicalSentences() {
        assertEquals("Looking for backpack.", SearchPhrases.looking("backpack", Lang.EN))
        assertEquals("배낭을 찾고 있어요.", SearchPhrases.looking("배낭", Lang.KO))
        assertEquals("Lost it.", SearchPhrases.lost(Lang.EN))
        assertEquals("놓쳤어요.", SearchPhrases.lost(Lang.KO))
    }

    @Test fun particlesFollowTheName() {
        assertEquals("의자를 찾고 있어요.", SearchPhrases.looking("의자", Lang.KO))
        assertEquals("Ali를 찾고 있어요.", SearchPhrases.looking("Ali", Lang.KO))
        assertEquals("민준이 정면에 있어요.", SearchPhrases.where("민준", Zone.AHEAD, Lang.KO))
        assertEquals("의자가 왼쪽 끝에 있어요.", SearchPhrases.where("의자", Zone.FAR_LEFT, Lang.KO))
    }

    @Test fun englishZones() {
        assertEquals("backpack ahead.", SearchPhrases.where("backpack", Zone.AHEAD, Lang.EN))
        assertEquals("Ali on your left.", SearchPhrases.where("Ali", Zone.LEFT, Lang.EN))
        assertEquals("cup far right.", SearchPhrases.where("cup", Zone.FAR_RIGHT, Lang.EN))
    }

    @Test fun unknown() {
        assertEquals("I don't know that. Say it another way.", SearchPhrases.unknown(Lang.EN))
        assertEquals("잘 모르겠어요. 다르게 말해 주세요.", SearchPhrases.unknown(Lang.KO))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.search.*"`
Expected: FAIL — compilation errors such as `Unresolved reference 'QueryCleaner'`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/nungil/core/search/SearchTarget.kt`

```kotlin
package com.nungil.core.search

/** What a search looks for. The name matches the nav argument `targetType` of search_camera. */
enum class TargetType { LABEL, PERSON, ITEM }

/** A saved person or item that can be searched for by name. [label] is "person" or the item's COCO label. */
data class SavedName(val id: Long, val name: String, val type: TargetType, val label: String)

/**
 * The resolved target of a search.
 * @param id saved person or item id, or -1 for a COCO label.
 * @param label COCO label ("backpack"), "person" for a saved person, the item's label for a saved item.
 * @param spokenName what the app says: the saved name, or the label's display name in the current language.
 */
data class SearchTarget(val type: TargetType, val id: Long, val label: String, val spokenName: String)
```

`app/src/main/java/com/nungil/core/search/QueryCleaner.kt`

```kotlin
package com.nungil.core.search

/**
 * Turns a spoken or typed query into words.
 *
 * [Cleaned.raw] keeps every word (lowercased, punctuation removed), so a saved name that is also a filler
 * word ("Me", "나") can still be matched. [Cleaned.words] drops command and filler words in both languages.
 */
object QueryCleaner {
    data class Cleaned(val raw: List<String>, val words: List<String>) {
        val phrase: String get() = words.joinToString(" ")
        val rawPhrase: String get() = raw.joinToString(" ")
    }

    /** Multi-word fillers, removed before single words. Longest first. */
    private val FILLER_PHRASES = listOf(
        "can you find", "search for", "look for", "where is", "where are",
        "어디에 있어", "어디에있어", "어디 있어", "어디있어", "찾아 줘", "찾아 주세요",
    )

    /** "me" and "나" are fillers on purpose: a saved person called "Me" or "나" must still be found (raw words). */
    private val FILLER_WORDS = setOf(
        "find", "search", "show", "help", "wheres", "me", "my", "the", "a", "an", "please",
        "찾아줘", "찾아주세요", "찾아", "찾기", "어디야", "어디", "있어", "나", "내", "나의", "제", "좀", "줘",
    )

    /** Korean particles that may trail a noun: 을/를 (object), 이/가 (subject), 은/는 (topic), 도 (also). */
    val PARTICLES: Set<Char> = setOf('을', '를', '이', '가', '은', '는', '도')

    fun clean(text: String): Cleaned {
        val normal = normalize(text)
        val raw = split(normal)
        var joined = " $normal "
        for (phrase in FILLER_PHRASES) joined = joined.replace(" $phrase ", " ")
        val words = split(joined).filter { it !in FILLER_WORDS }
        return Cleaned(raw, words)
    }

    /** Lowercase, apostrophes removed ("where's" becomes "wheres"), other punctuation becomes a space. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace("'", "")
            .replace("’", "")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")

    /**
     * [word] without up to two trailing Korean particles, shortest last: "배낭을" gives ["배낭"],
     * "민준이를" gives ["민준이", "민준"]. Empty when nothing can be stripped.
     */
    fun withoutParticles(word: String): List<String> {
        val out = mutableListOf<String>()
        var w = word
        repeat(2) {
            if (w.length > 1 && w.last() in PARTICLES) {
                w = w.dropLast(1)
                out += w
            }
        }
        return out
    }

    private fun split(text: String): List<String> = text.split(' ').filter { it.isNotBlank() }
}
```

`app/src/main/java/com/nungil/core/search/SearchResolver.kt`

```kotlin
package com.nungil.core.search

import com.nungil.contract.Lang
import com.nungil.core.lang.LabelNames

/**
 * Resolves a query to a saved person or item first, then to a COCO label.
 *
 * Saved names: exact, then containment, then at most [MAX_TYPOS] edits for names of [MIN_FUZZY_LENGTH]+
 * characters (a Hangul syllable counts as one character). Names are also matched against the raw words, so a
 * name that is a filler word ("Me", "나") is never lost. Labels: the whole phrase, then two-word pairs, then
 * single words, each with Korean particles and English plurals stripped, through the synonym tables.
 */
object SearchResolver {
    const val MAX_TYPOS = 2
    const val MIN_FUZZY_LENGTH = 4

    private val EN_SYNONYMS = mapOf(
        "phone" to "cell phone", "cellphone" to "cell phone", "mobile" to "cell phone",
        "desk" to "dining table", "table" to "dining table",
        "sofa" to "couch",
        "monitor" to "tv", "screen" to "tv", "television" to "tv",
        "bag" to "backpack",
        "bike" to "bicycle",
        "computer" to "laptop", "notebook" to "laptop",
    )

    private val KO_SYNONYMS = mapOf(
        "가방" to "backpack",
        "휴대폰" to "cell phone", "핸드폰" to "cell phone", "폰" to "cell phone", "전화기" to "cell phone",
        "책상" to "dining table", "테이블" to "dining table", "탁자" to "dining table", "식탁" to "dining table",
        "소파" to "couch",
        "모니터" to "tv", "화면" to "tv", "티비" to "tv", "tv" to "tv", "텔레비전" to "tv",
        "자전거" to "bicycle",
        "컴퓨터" to "laptop", "노트북" to "laptop",
        "컵" to "cup", "잔" to "cup",
        "물병" to "bottle", "병" to "bottle",
    )

    /** Particles allowed after a Hangul name inside one word: "민준이를", "Ali가". */
    private val NAME_SUFFIXES: Set<Char> = QueryCleaner.PARTICLES + setOf('의', '에')

    fun resolve(text: String, saved: List<SavedName>, lang: Lang): SearchTarget? {
        val cleaned = QueryCleaner.clean(text)
        if (cleaned.raw.isEmpty()) return null
        matchSaved(cleaned, saved)?.let { return SearchTarget(it.type, it.id, it.label, it.name) }
        val label = matchLabel(cleaned) ?: return null
        return SearchTarget(TargetType.LABEL, -1L, label, LabelNames.name(label, lang))
    }

    /** COCO label for one word or phrase, or null. Also used for suggestion chips. */
    fun labelFor(term: String): String? {
        val t = QueryCleaner.normalize(term)
        if (t.isEmpty()) return null
        val candidates = (listOf(t) + QueryCleaner.withoutParticles(t))
            .flatMap { if (it.length > 1 && it.endsWith("들")) listOf(it, it.dropLast(1)) else listOf(it) }
        for (candidate in candidates) {
            for (form in pluralForms(candidate)) {
                if (LabelNames.isKnown(form)) return form
                EN_SYNONYMS[form]?.let { return it }
                KO_SYNONYMS[form]?.let { return it }
                LabelNames.labelForKorean(form)?.let { return it }
            }
        }
        return null
    }

    private fun matchSaved(cleaned: QueryCleaner.Cleaned, saved: List<SavedName>): SavedName? {
        if (saved.isEmpty()) return null
        val wordLists = listOf(cleaned.words, cleaned.raw).filter { it.isNotEmpty() }
        val names = saved.map { it to QueryCleaner.normalize(it.name) }.filter { it.second.isNotEmpty() }

        // 1. Exact: the cleaned phrase (or the raw phrase) is the name, allowing particles on the last word.
        names.firstOrNull { (_, name) -> wordLists.any { sameAsName(it, name) } }?.let { return it.first }

        // 2. Containment: the name's words appear in a row inside the query, or the phrase is part of the name.
        names.firstOrNull { (_, name) -> wordLists.any { containsName(it, name) } }?.let { return it.first }
        val phrase = cleaned.phrase
        if (phrase.isNotEmpty()) names.firstOrNull { (_, name) -> nameContains(name, phrase) }?.let { return it.first }

        // 3. Fuzzy: small typos in longer names.
        if (phrase.isEmpty()) return null
        return names
            .filter { (_, name) -> name.length >= MIN_FUZZY_LENGTH }
            .map { (entry, name) -> entry to distance(phrase, name) }
            .filter { it.second <= MAX_TYPOS }
            .minByOrNull { it.second }
            ?.first
    }

    private fun sameAsName(words: List<String>, name: String): Boolean {
        val nameWords = name.split(' ')
        return words.size == nameWords.size && words.indices.all { wordIs(words[it], nameWords[it], it == words.lastIndex) }
    }

    private fun containsName(words: List<String>, name: String): Boolean {
        val nameWords = name.split(' ')
        if (nameWords.size > words.size) return false
        for (start in 0..words.size - nameWords.size) {
            if (nameWords.indices.all { wordIs(words[start + it], nameWords[it], it == nameWords.lastIndex) }) return true
        }
        return false
    }

    /**
     * The phrase is part of the name: whole words ("chair" in "office chair"), or for Korean at least two
     * syllables inside a word ("민준" in "김민준"). "cup" never matches "cupboard".
     */
    private fun nameContains(name: String, phrase: String): Boolean {
        if (" $name ".contains(" $phrase ")) return true
        return phrase.count { isHangul(it) } >= 2 && name.contains(phrase)
    }

    private fun isHangul(c: Char): Boolean = c.code in 0xAC00..0xD7A3

    /** [word] equals [nameWord], or (for the last word) is [nameWord] followed by up to two particles. */
    private fun wordIs(word: String, nameWord: String, last: Boolean): Boolean {
        if (word == nameWord) return true
        if (!last || !word.startsWith(nameWord)) return false
        val suffix = word.substring(nameWord.length)
        return suffix.length in 1..2 && suffix.all { it in NAME_SUFFIXES }
    }

    private fun matchLabel(cleaned: QueryCleaner.Cleaned): String? {
        val words = cleaned.words.ifEmpty { cleaned.raw }
        labelFor(words.joinToString(" "))?.let { return it }
        for (i in 0 until words.size - 1) labelFor(words[i] + " " + words[i + 1])?.let { return it }
        for (w in words) labelFor(w)?.let { return it }
        return null
    }

    /** The word as said, then English singulars: "chairs", "glasses", "knives", "batteries". */
    private fun pluralForms(word: String): List<String> {
        val forms = mutableListOf(word)
        if (word.endsWith("ies") && word.length > 4) forms += word.dropLast(3) + "y"
        if (word.endsWith("ves") && word.length > 4) forms += word.dropLast(3) + "fe"
        if (word.endsWith("es") && word.length > 3) forms += word.dropLast(2)
        if (word.endsWith("s") && word.length > 2) forms += word.dropLast(1)
        return forms
    }

    /** Levenshtein distance; one Hangul syllable is one character. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
```

`app/src/main/java/com/nungil/core/search/SearchGuide.kt`

```kotlin
package com.nungil.core.search

import com.nungil.contract.Facing
import com.nungil.contract.Lang
import kotlin.math.abs
import kotlin.math.roundToLong

/** Where the target is across the picture, from the user's point of view. */
enum class Zone { FAR_LEFT, LEFT, AHEAD, RIGHT, FAR_RIGHT }

/** Five zones by box centre and a beep that speeds up as the target nears the centre. */
object SearchGuide {
    const val FARTHEST_MS = 1_000L
    const val NEAREST_MS = 150L

    /**
     * Box centre as the user experiences it. The front camera's boxes are not mirrored (contract), but the user
     * faces the screen, so the camera's left is the user's right.
     */
    fun userX(centerX: Float, facing: Facing): Float = if (facing == Facing.FRONT) 1f - centerX else centerX

    /** far left < 0.2, left < 0.4, ahead 0.4–0.6, right <= 0.8, far right above. */
    fun zone(x: Float): Zone = when {
        x < 0.2f -> Zone.FAR_LEFT
        x < 0.4f -> Zone.LEFT
        x <= 0.6f -> Zone.AHEAD
        x <= 0.8f -> Zone.RIGHT
        else -> Zone.FAR_RIGHT
    }

    fun isCentered(x: Float): Boolean = zone(x) == Zone.AHEAD

    /** 1000 ms at either edge, shrinking linearly to 150 ms at the centre. */
    fun beepIntervalMs(x: Float): Long {
        val offCentre = (abs(x - 0.5f) / 0.5f).coerceIn(0f, 1f)
        return (NEAREST_MS + (FARTHEST_MS - NEAREST_MS) * offCentre).roundToLong()
    }

    fun zoneWord(zone: Zone, lang: Lang): String = when (lang) {
        Lang.EN -> when (zone) {
            Zone.FAR_LEFT -> "far left"
            Zone.LEFT -> "left"
            Zone.AHEAD -> "ahead"
            Zone.RIGHT -> "right"
            Zone.FAR_RIGHT -> "far right"
        }
        Lang.KO -> when (zone) {
            Zone.FAR_LEFT -> "왼쪽 끝"
            Zone.LEFT -> "왼쪽"
            Zone.AHEAD -> "정면"
            Zone.RIGHT -> "오른쪽"
            Zone.FAR_RIGHT -> "오른쪽 끝"
        }
    }
}
```

`app/src/main/java/com/nungil/core/search/SearchTracker.kt`

```kotlin
package com.nungil.core.search

/**
 * Decides what to say while hunting one target. Feed it every analysed result, in time order, from one thread.
 *
 * - The first sighting (at the start, or after "Lost it") is announced at once.
 * - A new zone is announced at most once every [repeatMs]; the same zone is never repeated.
 * - "Lost it" once, after [lostAfterMs] without the target.
 */
class SearchTracker(
    private val lostAfterMs: Long = LOST_AFTER_MS,
    private val repeatMs: Long = REPEAT_MS,
) {
    sealed interface Say {
        data class Where(val zone: Zone) : Say
        data object Lost : Say
    }

    /** [say] null = stay quiet; [enteredCenter] = the target just moved into the ahead zone (vibrate). */
    data class Update(val say: Say?, val enteredCenter: Boolean)

    private var lastSeenMs = -1L
    private var lastSpokenMs = Long.MIN_VALUE / 2
    private var lastSpokenZone: Zone? = null
    private var lastZone: Zone? = null
    private var visible = false

    fun update(nowMs: Long, zone: Zone?): Update {
        if (zone == null) {
            lastZone = null
            if (visible && nowMs - lastSeenMs >= lostAfterMs) {
                visible = false
                lastSpokenZone = null
                lastSpokenMs = nowMs
                return Update(Say.Lost, false)
            }
            return Update(null, false)
        }
        val enteredCenter = zone == Zone.AHEAD && lastZone != Zone.AHEAD
        lastZone = zone
        lastSeenMs = nowMs
        val firstSighting = !visible
        visible = true
        val speak = firstSighting || (zone != lastSpokenZone && nowMs - lastSpokenMs >= repeatMs)
        if (!speak) return Update(null, enteredCenter)
        lastSpokenZone = zone
        lastSpokenMs = nowMs
        return Update(Say.Where(zone), enteredCenter)
    }

    companion object {
        const val LOST_AFTER_MS = 3_000L
        const val REPEAT_MS = 2_000L
    }
}
```

`app/src/main/java/com/nungil/core/search/SearchPhrases.kt`

```kotlin
package com.nungil.core.search

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa

/** Everything the search screens say, in both languages (team plan §5). */
object SearchPhrases {
    fun looking(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "Looking for $name."
        Lang.KO -> "${Josa.eulReul(name)} 찾고 있어요."
    }

    fun lost(lang: Lang): String = when (lang) {
        Lang.EN -> "Lost it."
        Lang.KO -> "놓쳤어요."
    }

    fun where(name: String, zone: Zone, lang: Lang): String = when (lang) {
        Lang.EN -> when (zone) {
            Zone.AHEAD -> "$name ahead."
            Zone.LEFT -> "$name on your left."
            Zone.RIGHT -> "$name on your right."
            Zone.FAR_LEFT -> "$name far left."
            Zone.FAR_RIGHT -> "$name far right."
        }
        Lang.KO -> "${Josa.iGa(name)} ${SearchGuide.zoneWord(zone, lang)}에 있어요."
    }

    fun unknown(lang: Lang): String = when (lang) {
        Lang.EN -> "I don't know that. Say it another way."
        Lang.KO -> "잘 모르겠어요. 다르게 말해 주세요."
    }

    fun askWhat(lang: Lang): String = when (lang) {
        Lang.EN -> "What should I find?"
        Lang.KO -> "무엇을 찾을까요?"
    }
}
```

- [ ] **Step 4: Run the tests again**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.search.*"`
Expected: PASS — QueryCleanerTest 6, SearchResolverTest 12, SearchGuideTest 5, SearchTrackerTest 8, SearchPhrasesTest 4 (35 tests).

- [ ] **Step 5: Commit and open a pull request**

```powershell
git checkout -b y/Y1-search-core
git add app/src/main/java/com/nungil/core/search app/src/test/java/com/nungil/core/search
git commit -m "Resolve spoken searches in English and Korean"
git push -u origin y/Y1-search-core
gh pr create --base main --fill
```

## Task Y3: Face core (matching, quality, preprocessing, enrolment guide)

**Files:**
- Create: `app/src/main/java/com/nungil/core/people/VectorBytes.kt`, `FaceMatcher.kt`, `FaceQuality.kt`, `FaceNetPreprocess.kt`, `EnrollmentGuide.kt`, `EnrollPhrases.kt`
- Test: `app/src/test/java/com/nungil/core/people/VectorBytesTest.kt`, `FaceMatcherTest.kt`, `FaceQualityTest.kt`, `FaceNetPreprocessTest.kt`, `EnrollmentGuideTest.kt`

**Interfaces:**
- Consumes: `Lang`, `Josa` (Task 0).
- Produces:
  - `VectorBytes.toBytes(FloatArray): ByteArray`, `VectorBytes.toFloats(ByteArray): FloatArray` (little-endian, the Room format)
  - `FaceMatcher.cosine(a, b): Float`, `personScore(vector, samples): Float`, `bestMatch(vector, known: Map<Long, List<FloatArray>>, threshold = 0.5f, margin = 0.08f): Match?`, `data class Match(id: Long, score: Float)`, `TOP_SAMPLES = 3`
  - `FaceQuality.usable(sizePx: Int, yawDeg: Float, pitchDeg: Float): Boolean` (64 px, 35°, 25°)
  - `FaceNetPreprocess.SIZE = 160`, `FaceNetPreprocess.standardize(argb: IntArray): FloatArray`
  - `enum class Pose { STRAIGHT, LEFT, RIGHT, UP, DOWN }`; `EnrollmentGuide(samplesPerPose = 4)` with `pose`, `isDone`, `total`, `taken`, `accepts(yaw, pitch)`, `add(yaw): Boolean`, `percent()`
  - `EnrollPhrases.start / prompt / percent / done / noFace / paused(…, lang)`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nungil/core/people/VectorBytesTest.kt`

```kotlin
package com.nungil.core.people

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VectorBytesTest {
    @Test fun roundTrip() {
        val v = floatArrayOf(0f, 1.5f, -2.25f, 1e-7f, 512f)
        assertArrayEquals(v, VectorBytes.toFloats(VectorBytes.toBytes(v)), 0f)
    }

    @Test fun littleEndian() =
        assertArrayEquals(byteArrayOf(0, 0, 0x80.toByte(), 0x3F), VectorBytes.toBytes(floatArrayOf(1f)))

    @Test fun emptyVector() = assertArrayEquals(FloatArray(0), VectorBytes.toFloats(ByteArray(0)), 0f)

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBrokenLength() {
        VectorBytes.toFloats(ByteArray(5))
    }
}
```

`app/src/test/java/com/nungil/core/people/FaceMatcherTest.kt`

```kotlin
package com.nungil.core.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceMatcherTest {
    /** A unit vector in the plane at [deg] degrees; cosine between two of them is cos(difference). */
    private fun at(deg: Double) = floatArrayOf(cos(Math.toRadians(deg)).toFloat(), sin(Math.toRadians(deg)).toFloat(), 0f)

    @Test fun cosine() {
        assertEquals(1f, FaceMatcher.cosine(at(0.0), at(0.0)), 1e-6f)
        assertEquals(0f, FaceMatcher.cosine(at(0.0), at(90.0)), 1e-6f)
        assertEquals(-1f, FaceMatcher.cosine(at(0.0), at(180.0)), 1e-6f)
        assertEquals(0f, FaceMatcher.cosine(floatArrayOf(0f, 0f, 0f), at(0.0)), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cosineRejectsSizeMismatch() {
        FaceMatcher.cosine(floatArrayOf(1f), floatArrayOf(1f, 0f))
    }

    @Test fun personScoreIsTheMeanOfTheBestThree() {
        // cos(0)=1, cos(60)=0.5, cos(90)=0, cos(120)=-0.5 -> best three are 1, 0.5, 0 -> 0.5
        val samples = listOf(at(90.0), at(0.0), at(120.0), at(60.0))
        assertEquals(0.5f, FaceMatcher.personScore(at(0.0), samples), 1e-5f)
        assertEquals(-1f, FaceMatcher.personScore(at(0.0), emptyList()), 0f)
    }

    @Test fun matchesTheRightPerson() {
        val known = mapOf(
            1L to listOf(at(5.0), at(10.0), at(15.0)),
            2L to listOf(at(80.0), at(85.0), at(90.0)),
        )
        assertEquals(1L, FaceMatcher.bestMatch(at(0.0), known)?.id)
        assertEquals(2L, FaceMatcher.bestMatch(at(88.0), known)?.id)
    }

    @Test fun strangerBelowThresholdIsNobody() {
        val known = mapOf(1L to listOf(at(0.0), at(0.0), at(0.0)))
        assertNull(FaceMatcher.bestMatch(at(61.0), known)) // cos(61) = 0.48 < 0.5
        assertEquals(1L, FaceMatcher.bestMatch(at(59.0), known)?.id) // cos(59) = 0.515
    }

    @Test fun oneLuckySampleDoesNotWin() {
        // One sample fits perfectly, the others are far: mean of best three = (1 + 0 + 0) / 3 = 0.33.
        val known = mapOf(1L to listOf(at(0.0), at(90.0), at(90.0), at(90.0)))
        assertNull(FaceMatcher.bestMatch(at(0.0), known))
    }

    @Test fun tooCloseToTheRunnerUpIsNobody() {
        val known = mapOf(
            1L to listOf(at(20.0), at(20.0), at(20.0)),   // cos 20 = 0.940
            2L to listOf(at(-25.0), at(-25.0), at(-25.0)), // cos 25 = 0.906, margin 0.034 < 0.08
        )
        assertNull(FaceMatcher.bestMatch(at(0.0), known))
        val clear = known + (2L to listOf(at(-45.0), at(-45.0), at(-45.0))) // cos 45 = 0.707, margin 0.23
        assertEquals(1L, FaceMatcher.bestMatch(at(0.0), clear)?.id)
    }

    @Test fun nobodySaved() {
        assertNull(FaceMatcher.bestMatch(at(0.0), emptyMap()))
        assertNull(FaceMatcher.bestMatch(at(0.0), mapOf(1L to emptyList())))
    }
}
```

`app/src/test/java/com/nungil/core/people/FaceQualityTest.kt`

```kotlin
package com.nungil.core.people

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityTest {
    @Test fun goodFace() = assertTrue(FaceQuality.usable(64, 0f, 0f))

    @Test fun tooSmall() = assertFalse(FaceQuality.usable(63, 0f, 0f))

    @Test fun yawLimit() {
        assertTrue(FaceQuality.usable(100, 35f, 0f))
        assertFalse(FaceQuality.usable(100, -35.1f, 0f))
    }

    @Test fun pitchLimit() {
        assertTrue(FaceQuality.usable(100, 0f, -25f))
        assertFalse(FaceQuality.usable(100, 0f, 25.1f))
    }
}
```

`app/src/test/java/com/nungil/core/people/FaceNetPreprocessTest.kt`

```kotlin
package com.nungil.core.people

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class FaceNetPreprocessTest {
    @Test fun channelOrderIsRgb() {
        // One pixel R=16, G=32, B=48: mean 32, stddev sqrt(512/3) = 13.064
        val out = FaceNetPreprocess.standardize(intArrayOf(0xFF102030.toInt()))
        assertArrayEquals(floatArrayOf(-1.2247449f, 0f, 1.2247449f), out, 1e-5f)
    }

    @Test fun uniformImageBecomesZeros() {
        val out = FaceNetPreprocess.standardize(IntArray(4) { 0xFF808080.toInt() })
        assertArrayEquals(FloatArray(12), out, 0f)
    }

    @Test fun meanZeroStdOne() {
        val random = Random(7)
        val pixels = IntArray(FaceNetPreprocess.SIZE * FaceNetPreprocess.SIZE) { random.nextInt() or (0xFF shl 24) }
        val out = FaceNetPreprocess.standardize(pixels)
        assertEquals(FaceNetPreprocess.SIZE * FaceNetPreprocess.SIZE * 3, out.size)
        val mean = out.average()
        val std = sqrt(out.sumOf { (it - mean) * (it - mean) } / out.size)
        assertEquals(0.0, mean, 1e-4)
        assertEquals(1.0, std, 1e-4)
    }

    @Test fun emptyInput() = assertEquals(0, FaceNetPreprocess.standardize(IntArray(0)).size)
}
```

`app/src/test/java/com/nungil/core/people/EnrollmentGuideTest.kt`

```kotlin
package com.nungil.core.people

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentGuideTest {
    private val g = EnrollmentGuide()

    private fun fill(yaw: Float, pitch: Float) {
        repeat(g.samplesPerPose) {
            assertTrue("pose ${g.pose} should accept yaw=$yaw pitch=$pitch", g.accepts(yaw, pitch))
            g.add(yaw)
        }
    }

    @Test fun twentySamplesByDefault() {
        assertEquals(20, g.total)
        assertEquals(Pose.STRAIGHT, g.pose)
        assertEquals(0, g.percent())
    }

    @Test fun straightGate() {
        assertTrue(g.accepts(9f, -9f))
        assertFalse(g.accepts(10f, 0f))
        assertFalse(g.accepts(0f, -10f))
    }

    @Test fun addReportsPoseChange() {
        repeat(3) { assertFalse(g.add(0f)) }
        assertTrue(g.add(0f))
        assertEquals(Pose.LEFT, g.pose)
        assertEquals(20, g.percent())
    }

    @Test fun sidesMustBeOpposite() {
        fill(0f, 0f)
        assertFalse(g.accepts(20f, 0f)) // must be more than 20
        fill(-25f, 0f)                  // first side learned as negative yaw
        assertEquals(Pose.RIGHT, g.pose)
        assertFalse(g.accepts(-30f, 0f))
        assertTrue(g.accepts(30f, 0f))
    }

    @Test fun sidesWorkTheOtherWayRound() {
        fill(0f, 0f)
        fill(25f, 0f)
        assertFalse(g.accepts(25f, 0f))
        assertTrue(g.accepts(-25f, 0f))
    }

    @Test fun upAndDown() {
        fill(0f, 0f)
        fill(25f, 0f)
        fill(-25f, 0f)
        assertEquals(Pose.UP, g.pose)
        assertFalse(g.accepts(0f, 12f))
        assertTrue(g.accepts(0f, 13f))
        fill(0f, 15f)
        assertEquals(Pose.DOWN, g.pose)
        assertFalse(g.accepts(0f, 15f))
        fill(0f, -15f)
        assertTrue(g.isDone)
        assertNull(g.pose)
        assertEquals(100, g.percent())
        assertFalse(g.accepts(0f, 0f))
        assertFalse(g.add(0f))
    }

    @Test fun phrases() {
        assertEquals("Look straight at the phone.", EnrollPhrases.prompt(Pose.STRAIGHT, Lang.EN))
        assertEquals("이제 반대쪽으로 돌려 주세요.", EnrollPhrases.prompt(Pose.RIGHT, Lang.KO))
        assertEquals("40퍼센트", EnrollPhrases.percent(40, Lang.KO))
        assertEquals("All done. I will remember Ali.", EnrollPhrases.done("Ali", Lang.EN))
        assertEquals("다 됐어요. 민준을 기억할게요.", EnrollPhrases.done("민준", Lang.KO))
        assertEquals("다 됐어요. 지아를 기억할게요.", EnrollPhrases.done("지아", Lang.KO))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.people.*"`
Expected: FAIL — `Unresolved reference 'FaceMatcher'` and similar.

- [ ] **Step 3: Implement**

ML Kit's yaw sign depends on the camera and on mirroring. `EnrollmentGuide` therefore lets the first side have either sign and requires the opposite sign for the second side; the prompts say "one side" and "the other side". ML Kit's pitch sign is fixed: positive means facing up.

`app/src/main/java/com/nungil/core/people/VectorBytes.kt`

```kotlin
package com.nungil.core.people

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Embedding vectors as stored in Room (FaceEmbeddingEntity.vector, ItemEmbeddingEntity.vector): little-endian floats. */
object VectorBytes {
    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(vector)
        return buffer.array()
    }

    fun toFloats(bytes: ByteArray): FloatArray {
        require(bytes.size % 4 == 0) { "byte count ${bytes.size} is not a multiple of 4" }
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also { floats.get(it) }
    }
}
```

`app/src/main/java/com/nungil/core/people/FaceMatcher.kt`

```kotlin
package com.nungil.core.people

import kotlin.math.sqrt

/**
 * Face matching tuned so strangers are not greeted by a saved name (brief §6): cosine at least [THRESHOLD],
 * the winner ahead of the runner-up by [MARGIN], and each person scored by the mean of their [TOP_SAMPLES]
 * best samples instead of one lucky sample. The reference repository's 0.3 on one sample matched strangers.
 */
object FaceMatcher {
    const val THRESHOLD = 0.5f
    const val MARGIN = 0.08f
    const val TOP_SAMPLES = 3

    data class Match(val id: Long, val score: Float)

    /** Cosine similarity in -1..1; 0 when either vector is all zeros. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "vector sizes differ: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /** Mean cosine of the [TOP_SAMPLES] samples closest to [vector]; -1 without samples. */
    fun personScore(vector: FloatArray, samples: List<FloatArray>): Float {
        if (samples.isEmpty()) return -1f
        return samples.map { cosine(vector, it) }.sortedDescending().take(TOP_SAMPLES).average().toFloat()
    }

    /** The saved person [vector] belongs to, or null. [known] maps person id to that person's samples. */
    fun bestMatch(
        vector: FloatArray,
        known: Map<Long, List<FloatArray>>,
        threshold: Float = THRESHOLD,
        margin: Float = MARGIN,
    ): Match? {
        val ranked = known
            .filterValues { it.isNotEmpty() }
            .map { (id, samples) -> Match(id, personScore(vector, samples)) }
            .sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return null
        if (best.score < threshold) return null
        val second = ranked.getOrNull(1)
        if (second != null && best.score - second.score < margin) return null
        return best
    }
}
```

`app/src/main/java/com/nungil/core/people/FaceQuality.kt`

```kotlin
package com.nungil.core.people

import kotlin.math.abs

/** Faces too small or turned too far give embeddings that match the wrong person; skip them. */
object FaceQuality {
    const val MIN_SIZE_PX = 64
    const val MAX_YAW_DEG = 35f
    const val MAX_PITCH_DEG = 25f

    /** [sizePx] is the smaller side of the face box in frame pixels; angles are ML Kit's Euler Y and X. */
    fun usable(sizePx: Int, yawDeg: Float, pitchDeg: Float): Boolean =
        sizePx >= MIN_SIZE_PX && abs(yawDeg) <= MAX_YAW_DEG && abs(pitchDeg) <= MAX_PITCH_DEG
}
```

`app/src/main/java/com/nungil/core/people/FaceNetPreprocess.kt`

```kotlin
package com.nungil.core.people

import kotlin.math.max
import kotlin.math.sqrt

/** Input preparation for FaceNet-512: 160×160 RGB floats with per-image standardisation. */
object FaceNetPreprocess {
    const val SIZE = 160

    /**
     * [argb] packed pixels (row-major, as Bitmap.getPixels returns them) to R, G, B floats per pixel,
     * standardised as (x - mean) / max(stddev, 1 / sqrt(n)) over all n values, like TensorFlow's
     * per_image_standardization.
     */
    fun standardize(argb: IntArray): FloatArray {
        val out = FloatArray(argb.size * 3)
        for (i in argb.indices) {
            val p = argb[i]
            out[3 * i] = ((p shr 16) and 0xFF).toFloat()
            out[3 * i + 1] = ((p shr 8) and 0xFF).toFloat()
            out[3 * i + 2] = (p and 0xFF).toFloat()
        }
        if (out.isEmpty()) return out
        val mean = out.average()
        var sq = 0.0
        for (v in out) sq += (v - mean) * (v - mean)
        val std = sqrt(sq / out.size)
        val adjusted = max(std, 1.0 / sqrt(out.size.toDouble()))
        for (i in out.indices) out[i] = ((out[i] - mean) / adjusted).toFloat()
        return out
    }
}
```

`app/src/main/java/com/nungil/core/people/EnrollmentGuide.kt`

```kotlin
package com.nungil.core.people

import kotlin.math.abs

/** The five head poses of face enrolment, in the order they are collected. */
enum class Pose { STRAIGHT, LEFT, RIGHT, UP, DOWN }

/**
 * Collects [samplesPerPose] samples for each of the five poses (20 by default), gating each pose on ML Kit's
 * head angles: straight |yaw| and |pitch| < 10; left/right |yaw| > 20; up pitch > 12; down pitch < -12.
 *
 * ML Kit's yaw sign depends on the camera and on whether the image is mirrored, so LEFT accepts either side and
 * remembers its sign; RIGHT then requires the opposite sign. The user hears "one side", then "the other side".
 */
class EnrollmentGuide(val samplesPerPose: Int = SAMPLES_PER_POSE) {
    private val counts = IntArray(Pose.entries.size)
    private var firstSideSign = 0

    val total: Int = samplesPerPose * Pose.entries.size
    val taken: Int get() = counts.sum()

    /** The pose being collected; null when enrolment is complete. */
    val pose: Pose? get() = Pose.entries.firstOrNull { counts[it.ordinal] < samplesPerPose }
    val isDone: Boolean get() = pose == null

    fun accepts(yawDeg: Float, pitchDeg: Float): Boolean = when (pose) {
        Pose.STRAIGHT -> abs(yawDeg) < STRAIGHT_MAX_DEG && abs(pitchDeg) < STRAIGHT_MAX_DEG
        Pose.LEFT -> abs(yawDeg) > SIDE_MIN_YAW_DEG && (firstSideSign == 0 || sign(yawDeg) == firstSideSign)
        Pose.RIGHT -> abs(yawDeg) > SIDE_MIN_YAW_DEG && sign(yawDeg) == -firstSideSign
        Pose.UP -> pitchDeg > TILT_MIN_PITCH_DEG
        Pose.DOWN -> pitchDeg < -TILT_MIN_PITCH_DEG
        null -> false
    }

    /**
     * Records one sample for the current pose. Call only after [accepts] returned true for the same angles.
     * Returns true when this sample finished a pose (or the whole enrolment).
     */
    fun add(yawDeg: Float): Boolean {
        val current = pose ?: return false
        if (current == Pose.LEFT && firstSideSign == 0) firstSideSign = sign(yawDeg)
        counts[current.ordinal]++
        return pose != current
    }

    /** 0..100, for the progress bar and the spoken percent. */
    fun percent(): Int = taken * 100 / total

    private fun sign(v: Float): Int = if (v >= 0f) 1 else -1

    companion object {
        const val SAMPLES_PER_POSE = 4
        const val STRAIGHT_MAX_DEG = 10f
        const val SIDE_MIN_YAW_DEG = 20f
        const val TILT_MIN_PITCH_DEG = 12f
    }
}
```

`app/src/main/java/com/nungil/core/people/EnrollPhrases.kt`

```kotlin
package com.nungil.core.people

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa

/** What face enrolment says, in both languages. Spoken to the person being enrolled. */
object EnrollPhrases {
    fun start(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "Learning $name's face."
        Lang.KO -> "$name 얼굴을 등록할게요."
    }

    fun prompt(pose: Pose, lang: Lang): String = when (lang) {
        Lang.EN -> when (pose) {
            Pose.STRAIGHT -> "Look straight at the phone."
            Pose.LEFT -> "Slowly turn your head to one side."
            Pose.RIGHT -> "Now turn to the other side."
            Pose.UP -> "Tilt your head up a little."
            Pose.DOWN -> "Tilt your head down a little."
        }
        Lang.KO -> when (pose) {
            Pose.STRAIGHT -> "휴대폰을 똑바로 바라봐 주세요."
            Pose.LEFT -> "고개를 한쪽으로 천천히 돌려 주세요."
            Pose.RIGHT -> "이제 반대쪽으로 돌려 주세요."
            Pose.UP -> "고개를 살짝 들어 주세요."
            Pose.DOWN -> "고개를 살짝 숙여 주세요."
        }
    }

    fun percent(percent: Int, lang: Lang): String = when (lang) {
        Lang.EN -> "$percent percent"
        Lang.KO -> "${percent}퍼센트"
    }

    fun done(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "All done. I will remember $name."
        Lang.KO -> "다 됐어요. ${Josa.eulReul(name)} 기억할게요."
    }

    fun noFace(lang: Lang): String = when (lang) {
        Lang.EN -> "I can't see a face. Hold the phone at face height."
        Lang.KO -> "얼굴이 보이지 않아요. 휴대폰을 얼굴 높이로 들어 주세요."
    }

    fun paused(lang: Lang): String = when (lang) {
        Lang.EN -> "Paused. Nothing is saved until the end."
        Lang.KO -> "잠시 멈췄어요. 끝까지 해야 저장돼요."
    }
}
```

- [ ] **Step 4: Run the tests again**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.people.*"`
Expected: PASS — VectorBytesTest 4, FaceMatcherTest 8, FaceQualityTest 4, FaceNetPreprocessTest 4, EnrollmentGuideTest 7 (27 tests).

- [ ] **Step 5: Commit and open a pull request**

```powershell
git checkout main; git pull; git checkout -b y/Y3-face-core
git add app/src/main/java/com/nungil/core/people app/src/test/java/com/nungil/core/people
git commit -m "Match faces with tuned thresholds and guide enrolment poses"
git push -u origin y/Y3-face-core
gh pr create --base main --fill
```

## Task Y2: Search screens (query and camera hunt)

**Files:**
- Create: `app/src/main/java/com/nungil/search/TargetMatcher.kt`, `TargetMatchers.kt` (replaced in Y4 and Y6), `SavedNames.kt`, `CameraGate.kt`
- Replace (bootstrap stubs): `app/src/main/java/com/nungil/search/SearchFragment.kt`, `SearchCameraFragment.kt`
- Create: `app/src/main/res-y/values/strings_search.xml`, `app/src/main/res-y/values-ko/strings_search.xml`, `app/src/main/res-y/drawable/search_ic_mic.xml`, `app/src/main/res-y/layout/search_permission_panel.xml`, `search_fragment.xml`, `search_camera_fragment.xml`
- Test: `app/src/test/java/com/nungil/search/LabelMatcherTest.kt`

**Interfaces:**
- Consumes:
  - Y1.
  - From A: `CameraSession(fragment, previewView, Options(facing, detect, keepBitmap, minScore), onFrame, onError)` and `OverlayView.show(marks, w, h, mirrored)`.
  - From I: `services().speaker / haptics / beeper / navigator / askForWords / lang`.
  - Nav contract: `SearchFragmentArgs(query)`, `SearchCameraFragmentArgs(targetType, targetId, targetLabel, spokenName)`.
  - Room: `AppDatabase.people().allPeople()`, `items().allItems()`.
- Produces:
  - `interface TargetMatcher : Closeable { val slow: Boolean; fun find(frame: VisionFrame): Int }` and `class LabelMatcher(label)`
  - `TargetMatchers.create(context, type: TargetType, id: Long, label: String): TargetMatcher` (worker thread)
  - `SavedNames.load(context): List<SavedName>` (suspend)
  - `class CameraGate(fragment, onGranted: () -> Unit)` with `attach(SearchPermissionPanelBinding)`, `check()`, `detach()`, plus the layout `search_permission_panel`. Every Y camera screen includes this layout with an id.
  - The strings `search_camera_needed_body`, `search_camera_error`, `search_mic` and the drawable `search_ic_mic`, reused by Y5–Y7.

**Before A3 and I4 merge (h5–h6):**
- The search screen, the chips, the voice or keyboard query and the navigation all work.
- The camera screen opens and says "Looking for …" through the bootstrap TTS. The stub `CameraSession` delivers no frames, and the stub beeper and haptics are silent.
- Only `LabelMatcher` is unit-tested.

After A3 and I4 merge, run Step 7 on the phone.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/nungil/search/LabelMatcherTest.kt`

```kotlin
package com.nungil.search

import com.nungil.contract.Box
import com.nungil.contract.Detection
import com.nungil.contract.Facing
import com.nungil.contract.app.VisionFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LabelMatcherTest {
    private fun frame(vararg d: Detection) =
        VisionFrame(d.toList(), null, 640, 480, Facing.BACK, 0f, 65f, 0L, 10L)

    private fun det(label: String, score: Float) = Detection(label, score, Box(0.1f, 0.1f, 0.3f, 0.3f))

    @Test fun picksTheBestScoringDetectionWithTheLabel() {
        val m = LabelMatcher("cup")
        assertEquals(2, m.find(frame(det("chair", 0.9f), det("cup", 0.5f), det("cup", 0.8f))))
    }

    @Test fun missingTargetIsMinusOne() = assertEquals(-1, LabelMatcher("cup").find(frame(det("chair", 0.9f))))

    @Test fun emptyFrame() = assertEquals(-1, LabelMatcher("cup").find(frame()))

    @Test fun labelMatchingIsFast() = assertFalse(LabelMatcher("cup").slow)
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.search.*"`
Expected: FAIL — `Unresolved reference 'LabelMatcher'`.

- [ ] **Step 3: Add the matcher, the saved-name loader and the permission gate**

`app/src/main/java/com/nungil/search/TargetMatcher.kt`

```kotlin
package com.nungil.search

import com.nungil.contract.app.VisionFrame
import java.io.Closeable

/** Finds the search target inside one analysed frame. */
interface TargetMatcher : Closeable {
    /** true = slow (faces, embeddings): run on the extras thread and drop frames while it is busy. */
    val slow: Boolean

    /** Index into [VisionFrame.detections] of the target, or -1. Called by one thread at a time. */
    fun find(frame: VisionFrame): Int

    override fun close() = Unit
}

/** A COCO label target: the highest-scoring detection with that label. */
class LabelMatcher(private val label: String) : TargetMatcher {
    override val slow: Boolean = false

    override fun find(frame: VisionFrame): Int {
        var best = -1
        var bestScore = -1f
        frame.detections.forEachIndexed { i, d ->
            if (d.label == label && d.score > bestScore) {
                best = i
                bestScore = d.score
            }
        }
        return best
    }
}
```

`app/src/main/java/com/nungil/search/TargetMatchers.kt`

```kotlin
package com.nungil.search

import android.content.Context
import com.nungil.core.search.TargetType

/** Builds the matcher for a search target. Call on a worker thread: later versions load models. */
object TargetMatchers {
    fun create(context: Context, type: TargetType, id: Long, label: String): TargetMatcher = when (type) {
        TargetType.LABEL -> LabelMatcher(label)
        // Until faces (Y4) and item embeddings (Y6) exist, a saved target is hunted by its label.
        TargetType.PERSON -> LabelMatcher("person")
        TargetType.ITEM -> LabelMatcher(label)
    }
}
```

`app/src/main/java/com/nungil/search/SavedNames.kt`

```kotlin
package com.nungil.search

import android.content.Context
import com.nungil.core.search.SavedName
import com.nungil.core.search.TargetType
import com.nungil.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every saved person and item as search candidates, read from Room off the main thread. */
object SavedNames {
    suspend fun load(context: Context): List<SavedName> = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        db.people().allPeople().map { SavedName(it.id, it.name, TargetType.PERSON, "person") } +
            db.items().allItems().map { SavedName(it.id, it.name, TargetType.ITEM, it.label) }
    }
}
```

`app/src/main/java/com/nungil/search/CameraGate.kt`

```kotlin
package com.nungil.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nungil.R
import com.nungil.contract.app.services
import com.nungil.databinding.SearchPermissionPanelBinding

/**
 * Camera permission for every Y camera screen. Create it as a Fragment property (it registers an
 * activity-result launcher), [attach] it in onViewCreated, call [check] in onResume, [detach] in onDestroyView.
 * Asks once per screen; after a refusal it shows the panel with an "Open app settings" button and says why,
 * once. It never loops, also with "don't ask again".
 */
class CameraGate(private val fragment: Fragment, private val onGranted: () -> Unit) {
    private var panel: SearchPermissionPanelBinding? = null
    private var asked = false
    private var started = false
    private var explained = false

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) grant() else deny()
    }

    fun attach(panel: SearchPermissionPanelBinding) {
        this.panel = panel
        asked = false
        started = false
        explained = false
        panel.permissionSettings.setOnClickListener { openSettings() }
    }

    fun detach() {
        panel = null
    }

    fun check() {
        if (panel == null) return
        when {
            isGranted() -> grant()
            !asked -> {
                asked = true
                launcher.launch(Manifest.permission.CAMERA)
            }
            else -> deny()
        }
    }

    private fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun grant() {
        val p = panel ?: return
        p.root.visibility = View.GONE
        if (!started) {
            started = true
            onGranted()
        }
    }

    private fun deny() {
        val p = panel ?: return
        p.root.visibility = View.VISIBLE
        if (!explained) {
            explained = true
            fragment.services().speaker.say(fragment.getString(R.string.search_camera_needed_body))
        }
    }

    private fun openSettings() {
        val uri = Uri.fromParts("package", fragment.requireContext().packageName, null)
        fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    }
}
```

- [ ] **Step 4: Add the strings, the icon and the layouts**

`app/src/main/res-y/values/strings_search.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Search screens and the camera permission panel shared by all Y camera screens. -->
<resources>
    <string name="search_title">What should I find?</string>
    <string name="search_subtitle">Say or type a thing or a saved name.</string>
    <string name="search_hint">For example: bag, cup, Ali</string>
    <string name="search_mic">Speak the name</string>
    <string name="search_suggestions">Suggestions</string>
    <string name="search_find">Find</string>
    <string name="search_not_understood">I don\'t know that. Try another word.</string>

    <string name="search_camera_title">Finding %1$s</string>
    <string name="search_camera_looking">Looking…</string>
    <string name="search_stop">Stop</string>
    <string name="search_camera_error">The camera is not available.</string>

    <string name="search_camera_needed_title">Camera needed</string>
    <string name="search_camera_needed_body">To use this, allow the camera in the app settings.</string>
    <string name="search_open_settings">Open app settings</string>
</resources>
```

`app/src/main/res-y/values-ko/strings_search.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. -->
<resources>
    <string name="search_title">무엇을 찾을까요?</string>
    <string name="search_subtitle">물건 이름이나 저장한 이름을 말하거나 입력해 주세요.</string>
    <string name="search_hint">예: 가방, 컵, 민준</string>
    <string name="search_mic">이름 말하기</string>
    <string name="search_suggestions">추천</string>
    <string name="search_find">찾기</string>
    <string name="search_not_understood">잘 모르겠어요. 다른 말로 해 주세요.</string>

    <string name="search_camera_title">%1$s 찾는 중</string>
    <string name="search_camera_looking">찾고 있어요…</string>
    <string name="search_stop">멈춤</string>
    <string name="search_camera_error">카메라를 쓸 수 없어요.</string>

    <string name="search_camera_needed_title">카메라가 필요해요</string>
    <string name="search_camera_needed_body">이 기능을 쓰려면 앱 설정에서 카메라를 허용해 주세요.</string>
    <string name="search_open_settings">앱 설정 열기</string>
</resources>
```

`app/src/main/res-y/drawable/search_ic_mic.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Material Symbols "mic" (Apache 2.0), tinted with the text token. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="28dp"
    android:height="28dp"
    android:tint="?attr/ngText"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z" />
</vector>
```

`app/src/main/res-y/layout/search_permission_panel.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Shown over the camera card when the camera permission is refused (CameraGate). -->
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    style="@style/Widget.Nungil.Card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:id="@+id/permission_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/search_camera_needed_title"
            android:textAppearance="@style/TextAppearance.Nungil.Headline" />

        <TextView
            android:id="@+id/permission_body"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap"
            android:text="@string/search_camera_needed_body"
            android:textAppearance="@style/TextAppearance.Nungil.Body" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/permission_settings"
            style="@style/Widget.Nungil.Button.Tonal"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap_large"
            android:text="@string/search_open_settings" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

`app/src/main/res-y/layout/search_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Korean-style pattern: big headline, one-line subtitle, content, one bottom button. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/search_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/search_title"
        android:textAppearance="@style/TextAppearance.Nungil.Display" />

    <TextView
        android:id="@+id/search_subtitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/search_subtitle"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/search_input_layout"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:hint="@string/search_hint"
        app:boxCornerRadiusBottomEnd="@dimen/ng_radius_button"
        app:boxCornerRadiusBottomStart="@dimen/ng_radius_button"
        app:boxCornerRadiusTopEnd="@dimen/ng_radius_button"
        app:boxCornerRadiusTopStart="@dimen/ng_radius_button"
        app:endIconContentDescription="@string/search_mic"
        app:endIconDrawable="@drawable/search_ic_mic"
        app:endIconMode="custom">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/search_input"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:imeOptions="actionSearch"
            android:inputType="text"
            android:minHeight="@dimen/ng_touch"
            android:textAppearance="@style/TextAppearance.Nungil.Body" />
    </com.google.android.material.textfield.TextInputLayout>

    <TextView
        android:id="@+id/search_suggestions_label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:text="@string/search_suggestions"
        android:textAppearance="@style/TextAppearance.Nungil.Headline" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap"
        android:layout_weight="1">

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/search_chips"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:chipSpacingHorizontal="@dimen/ng_gap"
            app:chipSpacingVertical="@dimen/ng_gap" />
    </ScrollView>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/search_find"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/search_find" />
</LinearLayout>
```

`app/src/main/res-y/layout/search_camera_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Hunting one target: headline, big zone word, camera card, Stop at the bottom. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/search_camera_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <TextView
        android:id="@+id/search_camera_status"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/search_camera_looking"
        android:textAppearance="@style/TextAppearance.Nungil.Display"
        android:textColor="?attr/ngAccentText" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:layout_weight="1">

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.Nungil.Card"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:importantForAccessibility="noHideDescendants"
            app:contentPadding="0dp">

            <FrameLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <androidx.camera.view.PreviewView
                    android:id="@+id/search_camera_preview"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    app:implementationMode="compatible" />

                <com.nungil.scan.OverlayView
                    android:id="@+id/search_camera_overlay"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </FrameLayout>
        </com.google.android.material.card.MaterialCardView>

        <include
            android:id="@+id/search_camera_permission"
            layout="@layout/search_permission_panel" />
    </FrameLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/search_camera_stop"
        style="@style/Widget.Nungil.Button.Danger"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:text="@string/search_stop" />
</LinearLayout>
```

- [ ] **Step 5: Replace the two bootstrap stubs**

`app/src/main/java/com/nungil/search/SearchFragment.kt`

```kotlin
package com.nungil.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.nungil.R
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.lang.LabelNames
import com.nungil.core.search.SavedName
import com.nungil.core.search.SearchPhrases
import com.nungil.core.search.SearchResolver
import com.nungil.databinding.SearchFragmentBinding
import kotlinx.coroutines.launch

/**
 * "What should I find?" The query comes from the nav argument, the voice (askForWords), the keyboard or a
 * suggestion chip, and is resolved to a saved person or item first, then to a COCO label.
 */
class SearchFragment : Fragment(), VoiceHandler {
    private var _binding: SearchFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<SearchFragmentArgs>()
    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private var saved: List<SavedName> = emptyList()

    /** The nav-argument query is used once; coming Back from the camera must not jump forward again. */
    private var queryConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queryConsumed = savedInstanceState?.getBoolean(KEY_CONSUMED) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SearchFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        ViewCompat.setAccessibilityHeading(binding.searchTitle, true)
        binding.searchFind.setOnClickListener { resolveAndGo(binding.searchInput.text?.toString().orEmpty()) }
        binding.searchInputLayout.setEndIconOnClickListener { listen() }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                resolveAndGo(binding.searchInput.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            saved = SavedNames.load(requireContext().applicationContext)
            if (_binding == null) return@launch
            showChips()
            val query = args.query
            if (!queryConsumed && !query.isNullOrBlank()) {
                queryConsumed = true
                binding.searchInput.setText(query)
                resolveAndGo(query)
            } else {
                listen()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_CONSUMED, queryConsumed)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            resolveAndGo(binding.searchInput.text?.toString().orEmpty())
            true
        }
        else -> false
    }

    /** Ask, then treat the next words as the query. */
    private fun listen() {
        services.speaker.say(SearchPhrases.askWhat(lang))
        services.askForWords(viewLifecycleOwner) { text ->
            if (_binding == null) return@askForWords
            binding.searchInput.setText(text)
            resolveAndGo(text)
        }
    }

    private fun showChips() {
        val group = binding.searchChips
        group.removeAllViews()
        val names = saved.map { it.name } + COMMON_LABELS.map { LabelNames.name(it, lang) }
        for (name in names.distinct()) {
            group.addView(Chip(requireContext()).apply {
                text = name
                setTextAppearance(R.style.TextAppearance_Nungil_Label)
                chipMinHeight = resources.getDimension(R.dimen.ng_touch) - CHIP_TOUCH_INSET_PX
                setEnsureMinTouchTargetSize(true)
                setOnClickListener {
                    binding.searchInput.setText(name)
                    resolveAndGo(name)
                }
            })
        }
    }

    private fun resolveAndGo(text: String) {
        val target = SearchResolver.resolve(text, saved, lang)
        if (target == null) {
            binding.searchInputLayout.error = getString(R.string.search_not_understood)
            services.speaker.say(SearchPhrases.unknown(lang))
            return
        }
        binding.searchInputLayout.error = null
        val args = SearchCameraFragmentArgs(
            targetType = target.type.name,
            targetId = target.id,
            targetLabel = target.label,
            spokenName = target.spokenName,
        )
        findNavController().navigate(R.id.search_camera, args.toBundle())
    }

    private companion object {
        const val KEY_CONSUMED = "query_consumed"
        const val CHIP_TOUCH_INSET_PX = 8f

        /** Things people ask for most, shown as chips after the saved names. */
        val COMMON_LABELS = listOf("backpack", "cell phone", "cup", "bottle", "chair", "laptop")
    }
}
```

`app/src/main/java/com/nungil/search/SearchCameraFragment.kt`

```kotlin
package com.nungil.search

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.search.SearchGuide
import com.nungil.core.search.SearchPhrases
import com.nungil.core.search.SearchTracker
import com.nungil.core.search.TargetType
import com.nungil.databinding.SearchCameraFragmentBinding
import com.nungil.scan.CameraSession
import com.nungil.scan.OverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Hunts one target: beeps faster as it nears the centre, vibrates when it enters the centre, names the zone
 * at most every 2 s, and says "Lost it" after 3 s without it.
 */
class SearchCameraFragment : Fragment(), VoiceHandler {
    private var _binding: SearchCameraFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<SearchCameraFragmentArgs>()
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private lateinit var spokenName: String
    private lateinit var type: TargetType

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)
    private val matcher = AtomicReference<TargetMatcher?>(null)
    private val tracker = SearchTracker()

    @Volatile
    private var lastPulseMs = -1L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SearchCameraFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        spokenName = args.spokenName
        type = TargetType.entries.firstOrNull { it.name == args.targetType } ?: TargetType.LABEL
        binding.searchCameraTitle.text = getString(R.string.search_camera_title, spokenName)
        ViewCompat.setAccessibilityHeading(binding.searchCameraTitle, true)
        binding.searchCameraStop.setOnClickListener { services.navigator.back() }
        gate.attach(binding.searchCameraPermission)

        val executor = Executors.newSingleThreadExecutor()
        extras = executor
        val context = requireContext().applicationContext
        val targetId = args.targetId
        val label = args.targetLabel
        executor.execute {
            val created = try {
                TargetMatchers.create(context, type, targetId, label)
            } catch (e: Exception) {
                Log.i(TAG, "Search matcher failed, hunting by label instead", e)
                LabelMatcher(if (type == TargetType.PERSON) "person" else label)
            }
            matcher.set(created)
        }
        services.speaker.sayNow(SearchPhrases.looking(spokenName, lang))
    }

    override fun onResume() {
        super.onResume()
        gate.check()
    }

    override fun onPause() {
        super.onPause()
        services.beeper.stop()
        lastPulseMs = -1L
    }

    override fun onDestroyView() {
        super.onDestroyView()
        camera?.stop()
        camera = null
        services.beeper.stop()
        gate.detach()
        extras?.let { executor ->
            executor.execute { matcher.getAndSet(null)?.close() }
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
        extras = null
        main.removeCallbacksAndMessages(null)
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Stop -> {
            services.navigator.back()
            true
        }
        VoiceCommand.SwitchCamera -> {
            camera?.switchCamera()
            true
        }
        else -> false
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(
            facing = Facing.BACK,
            detect = true,
            keepBitmap = type != TargetType.LABEL,
            minScore = SEARCH_MIN_SCORE,
        )
        camera = CameraSession(this, binding.searchCameraPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    /** Analysis thread. Fast matchers run here; slow ones go to the extras thread, dropping frames while busy. */
    private fun onFrame(frame: VisionFrame) {
        val m = matcher.get() ?: return
        if (!m.slow) {
            handle(frame, m.find(frame))
            return
        }
        val executor = extras ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    handle(frame, safeFind(m, frame))
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    private fun safeFind(m: TargetMatcher, frame: VisionFrame): Int = try {
        m.find(frame)
    } catch (e: Exception) {
        Log.i(TAG, "Search matcher error", e)
        -1
    }

    /** Worker thread: speech, beeps and vibration are thread-safe; the overlay is updated on the main thread. */
    private fun handle(frame: VisionFrame, index: Int) {
        val target = frame.detections.getOrNull(index)
        val x = target?.let { SearchGuide.userX(it.box.centerX, frame.facing) }
        val zone = x?.let { SearchGuide.zone(it) }
        val update = synchronized(tracker) { tracker.update(SystemClock.elapsedRealtime(), zone) }

        val pulse = x?.let { SearchGuide.beepIntervalMs(it) } ?: 0L
        if (pulse == 0L && lastPulseMs != 0L || pulse > 0L && abs(pulse - lastPulseMs) >= PULSE_STEP_MS) {
            lastPulseMs = pulse
            services.beeper.pulse(pulse)
        }
        if (update.enteredCenter) services.haptics.buzz(Buzz.CENTERED)
        when (val say = update.say) {
            is SearchTracker.Say.Where -> services.speaker.say(SearchPhrases.where(spokenName, say.zone, lang))
            SearchTracker.Say.Lost -> services.speaker.say(SearchPhrases.lost(lang))
            null -> Unit
        }

        val marks = frame.detections.mapIndexed { i, d ->
            if (i == index) {
                OverlayView.Mark(d.box, spokenName, OverlayView.Style.TARGET)
            } else {
                OverlayView.Mark(d.box, null, OverlayView.Style.DIM)
            }
        }
        main.post {
            val b = _binding ?: return@post
            b.searchCameraOverlay.show(marks, frame.imageWidth, frame.imageHeight, frame.facing == Facing.FRONT)
            b.searchCameraStatus.text = zone?.let { SearchGuide.zoneWord(it, lang) } ?: getString(R.string.search_camera_looking)
        }
    }

    private fun onCameraError(error: Throwable) {
        Log.i(TAG, "Search camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }

    private companion object {
        const val TAG = "Nungil"

        /** The search detector runs at confidence 0.4 (brief §6). */
        const val SEARCH_MIN_SCORE = 0.4f

        /** Only re-arm the beeper when the interval moved by at least this much. */
        const val PULSE_STEP_MS = 25L
    }
}
```

- [ ] **Step 6: Build and test**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`; LabelMatcherTest passes 4 tests.

- [ ] **Step 7: Check on the phone (after A3 and I4 are on `main`)**

Run `.\gradlew.bat installDebug`. In the developer launcher tap "Search: bag" with the phone in English, then repeat with the app in Korean.
Expected:
1. **English:**
   - "Looking for backpack." is spoken.
   - With a backpack in view, its box is highlighted and the zone word ("left", "ahead" …) is shown large.
   - The beeps speed up towards the centre, and there is one short vibration when the bag enters "ahead".
   - Hide the bag for 3 s: "Lost it." is said once.
2. **Korean:** "배낭을 찾고 있어요.", zone phrases such as "배낭이 정면에 있어요.", and "놓쳤어요."
3. **Search screen:**
   - Say or type "내 가방 찾아줘": the camera opens for 배낭.
   - "spaceship": the app says "I don't know that. Say it another way." and the field shows the error.
4. **Camera permission denied:**
   - The panel appears and "To use this, allow the camera in the app settings." is said once.
   - "Open app settings" opens Android's page for the app. After granting there and coming back, the camera starts.
5. **Back:** from the camera, Back returns to the search screen, which does not jump forward again.

- [ ] **Step 8: Commit and open a pull request**

```powershell
git checkout main; git pull; git checkout -b y/Y2-search-screens
git add app/src/main/java/com/nungil/search app/src/test/java/com/nungil/search app/src/main/res-y
git commit -m "Find a thing with beeps, vibration and speech"
git push -u origin y/Y2-search-screens
gh pr create --base main --fill
```

## Task Y4: Face engines, name tagger and person search

**Files:**
- Create: `app/src/main/java/com/nungil/core/people/FaceGeometry.kt`, `FaceBoxes.kt`, `EveryNth.kt`
- Create: `app/src/main/java/com/nungil/people/FaceFinder.kt`, `FaceCrops.kt`, `FaceEmbedder.kt`, `FaceRecognizer.kt`, `FaceIdentifier.kt`, `FaceTagger.kt`, `PersonTargetMatcher.kt`
- Replace: `app/src/main/java/com/nungil/people/Taggers.kt` (bootstrap stub), `app/src/main/java/com/nungil/search/TargetMatchers.kt` (Y2 version)
- Test: `app/src/test/java/com/nungil/core/people/FaceGeometryTest.kt`, `FaceBoxesTest.kt`

**Interfaces:**
- Consumes: Y3; `VisionFrame`, `NameTagger`, `NameTag`, `TagKind` (contract); `AppDatabase.people().allPeople()/allFaces()`; the asset `facenet_512.tflite` (downloaded in Task 0).
- Produces:
  - `FaceGeometry.eyeAngleDeg / needsLeveling / expand / around / bigEnough`, with the constants 0.1, 0.35, 3° and 24 px
  - `data class FaceHit(centerX, centerY, personId, name, score)`; `FaceBoxes.personBoxFor(x, y, detections): Int`, `FaceBoxes.assign(hits, detections): Map<Int, FaceHit>`; `EveryNth(n).take()`
  - `FaceFinder.find(bitmap): List<Face>`; `FaceEmbedder(context).embed(frame, face): FloatArray?` and `embedCrop(crop)`; `FaceCrops.crop(frame, face)`, `FaceCrops.mirror(bitmap)`
  - `FaceRecognizer(context)`: `reload()`, `identify(vector)`, `nameOf(id)`, `isEmpty`, `FaceRecognizer.hasPeople(context)` (worker thread)
  - `FaceIdentifier(context).identify(bitmap): List<FaceHit>`
  - **`createNameTaggers(context): List<NameTagger>`**, the frozen API A calls. It returns `[FaceTagger]` when anyone is saved.
  - `PersonTargetMatcher(context, personId) : TargetMatcher`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nungil/core/people/FaceGeometryTest.kt`

```kotlin
package com.nungil.core.people

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceGeometryTest {
    @Test fun eyeAngleIgnoresWhichEyeIsWhich() {
        assertEquals(0f, FaceGeometry.eyeAngleDeg(100f, 50f, 200f, 50f), 1e-4f)
        assertEquals(45f, FaceGeometry.eyeAngleDeg(100f, 50f, 200f, 150f), 1e-4f)
        assertEquals(45f, FaceGeometry.eyeAngleDeg(200f, 150f, 100f, 50f), 1e-4f)
        assertEquals(-10f, FaceGeometry.eyeAngleDeg(0f, 0f, 100f, -17.6327f), 1e-3f)
    }

    @Test fun levelingFromThreeDegrees() {
        assertFalse(FaceGeometry.needsLeveling(2.9f))
        assertTrue(FaceGeometry.needsLeveling(3f))
        assertTrue(FaceGeometry.needsLeveling(-4f))
    }

    @Test fun expandAddsMarginAndClamps() {
        assertArrayEquals(intArrayOf(90, 90, 210, 210), FaceGeometry.expand(100, 100, 200, 200, 0.1f, 640, 480))
        assertArrayEquals(intArrayOf(0, 0, 59, 59), FaceGeometry.expand(5, 5, 45, 45, 0.35f, 640, 480))
        assertArrayEquals(intArrayOf(600, 440, 640, 480), FaceGeometry.expand(610, 450, 640, 480, 0.35f, 640, 480))
    }

    @Test fun aroundACentre() =
        assertArrayEquals(intArrayOf(40, 30, 60, 70), FaceGeometry.around(50f, 50f, 10f, 20f, 640, 480))

    @Test fun minimumCropSize() {
        assertTrue(FaceGeometry.bigEnough(intArrayOf(0, 0, 24, 24)))
        assertFalse(FaceGeometry.bigEnough(intArrayOf(0, 0, 23, 40)))
    }
}
```

`app/src/test/java/com/nungil/core/people/FaceBoxesTest.kt`

```kotlin
package com.nungil.core.people

import com.nungil.contract.Box
import com.nungil.contract.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceBoxesTest {
    private val big = Detection("person", 0.9f, Box(0f, 0f, 1f, 1f))
    private val left = Detection("person", 0.9f, Box(0.1f, 0.1f, 0.4f, 0.9f))
    private val right = Detection("person", 0.9f, Box(0.6f, 0.1f, 0.9f, 0.9f))
    private val chair = Detection("chair", 0.9f, Box(0.1f, 0.1f, 0.4f, 0.9f))

    @Test fun smallestContainingPersonBox() {
        assertEquals(1, FaceBoxes.personBoxFor(0.2f, 0.2f, listOf(big, left, right)))
        assertEquals(0, FaceBoxes.personBoxFor(0.5f, 0.5f, listOf(big, left, right)))
        assertEquals(-1, FaceBoxes.personBoxFor(0.2f, 0.2f, listOf(chair)))
    }

    @Test fun oneNamePerBoxAndOneBoxPerPerson() {
        val dets = listOf(left, right)
        val ali = FaceHit(0.2f, 0.3f, 1, "Ali", 0.7f)
        val aliAgain = FaceHit(0.7f, 0.3f, 1, "Ali", 0.6f)   // same person twice: keep the better one
        val jiwoo = FaceHit(0.25f, 0.5f, 2, "지우", 0.65f)     // second face in the same box: dropped
        val result = FaceBoxes.assign(listOf(aliAgain, jiwoo, ali), dets)
        assertEquals(mapOf(0 to ali), result)
    }

    @Test fun faceOutsideEveryBoxIsIgnored() =
        assertTrue(FaceBoxes.assign(listOf(FaceHit(0.5f, 0.5f, 1, "Ali", 0.9f)), listOf(left, right)).isEmpty())

    @Test fun everyThirdPersonFrame() {
        val nth = EveryNth(3)
        assertEquals(listOf(true, false, false, true, false, false, true), List(7) { nth.take() })
        assertTrue(EveryNth(1).take())
        assertFalse(EveryNth(2).let { it.take(); it.take() })
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.people.*"`
Expected: FAIL — `Unresolved reference 'FaceGeometry'`, `'FaceBoxes'`, `'EveryNth'`.

- [ ] **Step 3: Implement the pure helpers**

`app/src/main/java/com/nungil/core/people/FaceGeometry.kt`

```kotlin
package com.nungil.core.people

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Pixel maths for face crops (no Android types, so it is unit-tested). Rects are [left, top, right, bottom]. */
object FaceGeometry {
    const val CROP_MARGIN = 0.1f
    const val ALIGNED_MARGIN = 0.35f
    const val LEVEL_MIN_DEG = 3f
    const val MIN_CROP_PX = 24

    /** Tilt of the line between the eyes, in degrees (-90..90); positive = the right-hand eye in the image is lower. */
    fun eyeAngleDeg(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val (x1, y1, x2, y2) = if (ax <= bx) listOf(ax, ay, bx, by) else listOf(bx, by, ax, ay)
        return Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
    }

    fun needsLeveling(angleDeg: Float): Boolean = abs(angleDeg) >= LEVEL_MIN_DEG

    /** The rect grown by [margin] of its width/height on every side, clamped to a [width] x [height] image. */
    fun expand(left: Int, top: Int, right: Int, bottom: Int, margin: Float, width: Int, height: Int): IntArray {
        val dx = (right - left) * margin
        val dy = (bottom - top) * margin
        return clamp(left - dx, top - dy, right + dx, bottom + dy, width, height)
    }

    /** A rect of half-size [halfW] x [halfH] around (cx, cy), clamped to the image. */
    fun around(cx: Float, cy: Float, halfW: Float, halfH: Float, width: Int, height: Int): IntArray =
        clamp(cx - halfW, cy - halfH, cx + halfW, cy + halfH, width, height)

    /** Big enough to embed: both sides at least [MIN_CROP_PX]. */
    fun bigEnough(rect: IntArray): Boolean = rect[2] - rect[0] >= MIN_CROP_PX && rect[3] - rect[1] >= MIN_CROP_PX

    private fun clamp(l: Float, t: Float, r: Float, b: Float, width: Int, height: Int): IntArray = intArrayOf(
        l.roundToInt().coerceIn(0, width),
        t.roundToInt().coerceIn(0, height),
        r.roundToInt().coerceIn(0, width),
        b.roundToInt().coerceIn(0, height),
    )
}
```

`app/src/main/java/com/nungil/core/people/FaceBoxes.kt`

```kotlin
package com.nungil.core.people

import com.nungil.contract.Detection

/** A recognised face: centre normalised to the frame (0..1) and the saved person it matched. */
data class FaceHit(val centerX: Float, val centerY: Float, val personId: Long, val name: String, val score: Float)

/** Puts recognised faces onto the detector's person boxes. */
object FaceBoxes {
    const val PERSON = "person"

    /** Index of the smallest person box that contains (x, y), or -1. */
    fun personBoxFor(x: Float, y: Float, detections: List<Detection>): Int {
        var best = -1
        var bestArea = Float.MAX_VALUE
        detections.forEachIndexed { i, d ->
            val b = d.box
            if (d.label == PERSON && x >= b.left && x <= b.right && y >= b.top && y <= b.bottom && b.area < bestArea) {
                best = i
                bestArea = b.area
            }
        }
        return best
    }

    /** Detection index to face, best score first: one name per box and one box per person. */
    fun assign(hits: List<FaceHit>, detections: List<Detection>): Map<Int, FaceHit> {
        val out = LinkedHashMap<Int, FaceHit>()
        val usedPeople = mutableSetOf<Long>()
        for (hit in hits.sortedByDescending { it.score }) {
            if (hit.personId in usedPeople) continue
            val index = personBoxFor(hit.centerX, hit.centerY, detections)
            if (index < 0 || index in out) continue
            out[index] = hit
            usedPeople += hit.personId
        }
        return out
    }
}
```

`app/src/main/java/com/nungil/core/people/EveryNth.kt`

```kotlin
package com.nungil.core.people

/** true on the 1st, (n+1)th, (2n+1)th … call: "run face recognition every 3rd frame that has a person box". */
class EveryNth(private val n: Int) {
    private var count = 0

    init {
        require(n >= 1) { "n must be at least 1, was $n" }
    }

    fun take(): Boolean = (count++ % n) == 0
}
```

- [ ] **Step 4: Run the tests again**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.people.*"`
Expected: PASS — FaceGeometryTest 5 and FaceBoxesTest 4 are added (36 people tests in all).

- [ ] **Step 5: Add the Android face engines**

`app/src/main/java/com/nungil/people/FaceFinder.kt`

```kotlin
package com.nungil.people

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.Closeable
import java.util.concurrent.TimeUnit

/** ML Kit face detection (bundled model, works offline). Worker thread only: it blocks. */
class FaceFinder : Closeable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(MIN_FACE_SIZE)
            .build(),
    )

    /** Faces in an upright bitmap; empty on any error or after 2 s. */
    fun find(bitmap: Bitmap): List<Face> = try {
        Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 2, TimeUnit.SECONDS)
    } catch (e: Exception) {
        Log.i(TAG, "Face detection failed: ${e.message}")
        emptyList()
    }

    override fun close() = detector.close()

    private companion object {
        const val TAG = "Nungil"
        const val MIN_FACE_SIZE = 0.1f
    }
}
```

`app/src/main/java/com/nungil/people/FaceCrops.kt`

```kotlin
package com.nungil.people

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import com.nungil.core.people.FaceGeometry

/** Cuts a face out of a frame: 10% margin, levelled by the eye line when it is tilted 3° or more. */
object FaceCrops {
    /** null when the crop would be under 24 px. */
    fun crop(frame: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val angle = if (leftEye != null && rightEye != null) {
            FaceGeometry.eyeAngleDeg(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
        } else {
            0f
        }
        if (!FaceGeometry.needsLeveling(angle)) {
            return cut(frame, FaceGeometry.expand(box.left, box.top, box.right, box.bottom, FaceGeometry.CROP_MARGIN, frame.width, frame.height))
        }
        // Take a wider crop, rotate it level around its centre, then cut the face out of the rotated picture.
        val wide = FaceGeometry.expand(box.left, box.top, box.right, box.bottom, FaceGeometry.ALIGNED_MARGIN, frame.width, frame.height)
        val wideBitmap = cut(frame, wide) ?: return null
        val matrix = Matrix().apply { setRotate(-angle, wideBitmap.width / 2f, wideBitmap.height / 2f) }
        val rotated = Bitmap.createBitmap(wideBitmap, 0, 0, wideBitmap.width, wideBitmap.height, matrix, true)
        val bounds = RectF(0f, 0f, wideBitmap.width.toFloat(), wideBitmap.height.toFloat()).also { matrix.mapRect(it) }
        val centre = floatArrayOf(box.exactCenterX() - wide[0], box.exactCenterY() - wide[1]).also { matrix.mapPoints(it) }
        val rect = FaceGeometry.around(
            centre[0] - bounds.left,
            centre[1] - bounds.top,
            box.width() * (0.5f + FaceGeometry.CROP_MARGIN),
            box.height() * (0.5f + FaceGeometry.CROP_MARGIN),
            rotated.width,
            rotated.height,
        )
        return cut(rotated, rect)
    }

    fun mirror(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cut(source: Bitmap, rect: IntArray): Bitmap? {
        if (!FaceGeometry.bigEnough(rect)) return null
        return Bitmap.createBitmap(source, rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1])
    }
}
```

`app/src/main/java/com/nungil/people/FaceEmbedder.kt`

```kotlin
package com.nungil.people

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.face.Face
import com.nungil.core.people.FaceNetPreprocess
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * FaceNet-512 embeddings. Each face is embedded as seen and mirrored, and the two are averaged (L2-normalised).
 * Not thread-safe: use it from one worker thread. Throws from the constructor when the model is missing.
 */
class FaceEmbedder(context: Context) : Closeable {
    private val interpreter = Interpreter(loadModel(context), Interpreter.Options().setNumThreads(THREADS))
    private val outputSize = interpreter.getOutputTensor(0).shape().last()
    private val input: ByteBuffer = ByteBuffer
        .allocateDirect(4 * SIZE * SIZE * 3)
        .order(ByteOrder.nativeOrder())
    private val pixels = IntArray(SIZE * SIZE)

    /** Embedding of [face] inside [frame], or null when the face crop is under 24 px. */
    fun embed(frame: Bitmap, face: Face): FloatArray? {
        val crop = FaceCrops.crop(frame, face) ?: return null
        return embedCrop(crop)
    }

    /** Embedding of an already cut-out face. */
    fun embedCrop(crop: Bitmap): FloatArray {
        val a = normalize(run(crop))
        val b = normalize(run(FaceCrops.mirror(crop)))
        return normalize(FloatArray(a.size) { (a[it] + b[it]) / 2f })
    }

    private fun run(face: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(face, SIZE, SIZE, true)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        input.asFloatBuffer().put(FaceNetPreprocess.standardize(pixels))
        val output = arrayOf(FloatArray(outputSize))
        interpreter.run(input, output)
        return output[0]
    }

    override fun close() = interpreter.close()

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum).toFloat()
        return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
    }

    private companion object {
        const val MODEL = "facenet_512.tflite"
        const val SIZE = FaceNetPreprocess.SIZE
        const val THREADS = 4

        /** Memory-maps the model from assets (app/build.gradle keeps .tflite uncompressed). */
        fun loadModel(context: Context): MappedByteBuffer =
            context.assets.openFd(MODEL).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
    }
}
```

`app/src/main/java/com/nungil/people/FaceRecognizer.kt`

```kotlin
package com.nungil.people

import android.content.Context
import com.nungil.core.people.FaceMatcher
import com.nungil.core.people.VectorBytes
import com.nungil.data.AppDatabase
import kotlinx.coroutines.runBlocking

/** Saved people and their face samples from Room, matched with FaceMatcher. Load on a worker thread. */
class FaceRecognizer(context: Context) {
    private val db = AppDatabase.get(context.applicationContext)

    @Volatile
    private var known: Map<Long, List<FloatArray>> = emptyMap()

    @Volatile
    private var names: Map<Long, String> = emptyMap()

    val isEmpty: Boolean get() = known.isEmpty()

    /** Blocks on Room; never call on the main thread. */
    fun reload() = runBlocking {
        val people = db.people().allPeople()
        val faces = db.people().allFaces()
        val byId = people.associate { it.id to it.name }
        names = byId
        known = faces
            .filter { it.personId in byId }
            .groupBy({ it.personId }, { VectorBytes.toFloats(it.vector) })
    }

    fun identify(vector: FloatArray): FaceMatcher.Match? = FaceMatcher.bestMatch(vector, known)

    fun nameOf(personId: Long): String? = names[personId]

    companion object {
        /** Blocks on Room; never call on the main thread. */
        fun hasPeople(context: Context): Boolean = runBlocking {
            AppDatabase.get(context.applicationContext).people().allPeople().isNotEmpty()
        }
    }
}
```

`app/src/main/java/com/nungil/people/FaceIdentifier.kt`

```kotlin
package com.nungil.people

import android.content.Context
import android.graphics.Bitmap
import com.nungil.core.people.FaceHit
import com.nungil.core.people.FaceQuality
import java.io.Closeable
import kotlin.math.min

/**
 * Finds faces, skips the unusable ones (under 64 px, turned more than 35° or tilted more than 25°), embeds the
 * rest and matches them against saved people. Worker thread only; loads the model and the people on creation.
 */
class FaceIdentifier(context: Context) : Closeable {
    private val embedder = FaceEmbedder(context)
    private val finder = FaceFinder()
    private val recognizer = FaceRecognizer(context).also { it.reload() }

    fun identify(bitmap: Bitmap): List<FaceHit> {
        if (recognizer.isEmpty) return emptyList()
        return finder.find(bitmap).mapNotNull { face ->
            val box = face.boundingBox
            if (!FaceQuality.usable(min(box.width(), box.height()), face.headEulerAngleY, face.headEulerAngleX)) {
                return@mapNotNull null
            }
            val vector = embedder.embed(bitmap, face) ?: return@mapNotNull null
            val match = recognizer.identify(vector) ?: return@mapNotNull null
            val name = recognizer.nameOf(match.id) ?: return@mapNotNull null
            FaceHit(box.exactCenterX() / bitmap.width, box.exactCenterY() / bitmap.height, match.id, name, match.score)
        }
    }

    override fun close() {
        finder.close()
        embedder.close()
    }
}
```

`app/src/main/java/com/nungil/people/FaceTagger.kt`

```kotlin
package com.nungil.people

import android.content.Context
import com.nungil.contract.app.NameTag
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.core.people.EveryNth
import com.nungil.core.people.FaceBoxes

/**
 * Names saved people inside A's scan. Runs face recognition on every 3rd frame that has a person box; a face
 * inside a person box renames that box. A keeps the name between frames (StickyNames).
 */
class FaceTagger(context: Context) : NameTagger {
    private val faces = FaceIdentifier(context)
    private val throttle = EveryNth(EVERY_NTH_PERSON_FRAME)

    override fun tag(frame: VisionFrame): List<NameTag> {
        val bitmap = frame.bitmap ?: return emptyList()
        if (frame.detections.none { it.label == FaceBoxes.PERSON }) return emptyList()
        if (!throttle.take()) return emptyList()
        return FaceBoxes.assign(faces.identify(bitmap), frame.detections)
            .map { (index, hit) -> NameTag(index, hit.name, TagKind.PERSON) }
    }

    override fun close() = faces.close()

    private companion object {
        const val EVERY_NTH_PERSON_FRAME = 3
    }
}
```

`app/src/main/java/com/nungil/people/PersonTargetMatcher.kt`

```kotlin
package com.nungil.people

import android.content.Context
import com.nungil.contract.app.VisionFrame
import com.nungil.core.people.FaceBoxes
import com.nungil.search.TargetMatcher

/** Search camera target "a saved person": the person box whose face matches [personId] against everyone saved. */
class PersonTargetMatcher(context: Context, private val personId: Long) : TargetMatcher {
    private val faces = FaceIdentifier(context)

    override val slow: Boolean = true

    override fun find(frame: VisionFrame): Int {
        val bitmap = frame.bitmap ?: return -1
        if (frame.detections.none { it.label == FaceBoxes.PERSON }) return -1
        val hit = faces.identify(bitmap).filter { it.personId == personId }.maxByOrNull { it.score } ?: return -1
        return FaceBoxes.personBoxFor(hit.centerX, hit.centerY, frame.detections)
    }

    override fun close() = faces.close()
}
```

- [ ] **Step 6: Replace the tagger factory and the matcher factory (whole files)**

`app/src/main/java/com/nungil/people/Taggers.kt`

```kotlin
package com.nungil.people

import android.content.Context
import android.util.Log
import com.nungil.contract.app.NameTagger

/**
 * The taggers A's scan screen runs on its extras thread: saved faces. Called once per scan screen, off the main
 * thread; A closes every tagger when the screen closes. A tagger that cannot load (model missing, nobody saved)
 * is left out, so the scan still works without names.
 */
fun createNameTaggers(context: Context): List<NameTagger> {
    val app = context.applicationContext
    val taggers = mutableListOf<NameTagger>()
    if (FaceRecognizer.hasPeople(app)) tryCreate("face") { FaceTagger(app) }?.let(taggers::add)
    return taggers
}

internal inline fun tryCreate(what: String, make: () -> NameTagger): NameTagger? =
    try {
        make()
    } catch (e: Exception) {
        Log.i("Nungil", "No $what tagger: ${e.message}")
        null
    } catch (e: LinkageError) {
        Log.i("Nungil", "No $what tagger: ${e.message}")
        null
    }
```

`app/src/main/java/com/nungil/search/TargetMatchers.kt`

```kotlin
package com.nungil.search

import android.content.Context
import com.nungil.core.search.TargetType
import com.nungil.people.PersonTargetMatcher

/** Builds the matcher for a search target. Call on a worker thread: the face matcher loads a model. */
object TargetMatchers {
    fun create(context: Context, type: TargetType, id: Long, label: String): TargetMatcher = when (type) {
        TargetType.LABEL -> LabelMatcher(label)
        TargetType.PERSON -> PersonTargetMatcher(context, id)
        // Until item embeddings (Y6) exist, a saved item is hunted by its label.
        TargetType.ITEM -> LabelMatcher(label)
    }
}
```

- [ ] **Step 7: Build and test**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 8: Check on the phone**

Before Y5 exists, only check that nothing crashes:
- **Nobody saved:** open a full scan (A's screen). No face model is loaded (`createNameTaggers` returns an empty list) and the scan still works.

After Y5, enrol a person and check:
- **Full scan:** the saved person is announced by name in A's summary, and a stranger stays "a person".
- **Person search:** on the person's detail screen, "Find this person" opens the camera, which highlights only that person.
- **Log:** `adb logcat -v time -s Nungil:V` shows no `No face tagger` line while the model is present.

- [ ] **Step 9: Commit and open a pull request**

```powershell
git checkout main; git pull; git checkout -b y/Y4-face-engines
git add app/src/main/java/com/nungil/core/people app/src/test/java/com/nungil/core/people app/src/main/java/com/nungil/people app/src/main/java/com/nungil/search/TargetMatchers.kt
git commit -m "Name saved people inside scans and searches"
git push -u origin y/Y4-face-engines
gh pr create --base main --fill
```

## Task Y5: Saved screens and face enrolment

**Files:**
- Create: `app/src/main/java/com/nungil/core/people/ConfirmWindow.kt`, test `app/src/test/java/com/nungil/core/people/ConfirmWindowTest.kt`
- Create: `app/src/main/java/com/nungil/saved/PhotoFiles.kt`, `SavedNav.kt`, `SavedAdapter.kt`
- Replace (bootstrap stubs): `app/src/main/java/com/nungil/saved/SavedFragment.kt`, `app/src/main/java/com/nungil/people/AddPersonFragment.kt`, `PersonFragment.kt`, `EnrollFragment.kt`
- Create: `app/src/main/res-y/values/strings_saved.xml`, `values-ko/strings_saved.xml`, and the layouts `saved_fragment.xml`, `saved_row.xml`, `person_fragment.xml`, `person_add_fragment.xml`, `enroll_fragment.xml`

**Interfaces:**
- Consumes:
  - Y2: `CameraGate`, `search_permission_panel`, `search_ic_mic`, `SearchCameraFragmentArgs`.
  - Y3; Y4: `FaceFinder`, `FaceEmbedder`, `FaceCrops`.
  - Room: `PersonDao.observePeople / getPerson / insertPersonWithFaces / rename / deletePerson`, `ItemDao.observeItems(kind)`.
  - Nav args: `SavedFragmentArgs(tab)`, `PersonFragmentArgs(personId)`, `AddPersonFragmentArgs(name)`, `EnrollFragmentArgs(name, front)`, `ItemFragmentArgs(itemId)`.
  - `AppScope`.
- Produces:
  - `ConfirmWindow(windowMs = 5000).press(nowMs): Boolean`
  - `PhotoFiles.save(context, folder, bitmap): String?`, `load(path)`, `delete(path)`
  - `NavController.returnToSaved(addDestination: Int, tab: SavedTab)`
  - `data class SavedRow(id, title, subtitle, isPerson)` and `SavedAdapter(onClick)`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/nungil/core/people/ConfirmWindowTest.kt`

```kotlin
package com.nungil.core.people

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmWindowTest {
    private val c = ConfirmWindow()

    @Test fun firstPressOnlyArms() = assertFalse(c.press(0))

    @Test fun secondPressWithinFiveSecondsConfirms() {
        c.press(1_000)
        assertTrue(c.press(6_000))
    }

    @Test fun tooLateArmsAgain() {
        c.press(0)
        assertFalse(c.press(5_001))
        assertTrue(c.press(6_000))
    }

    @Test fun confirmingDisarms() {
        c.press(0)
        c.press(100)
        assertFalse(c.press(200))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.people.ConfirmWindowTest"`
Expected: FAIL — `Unresolved reference 'ConfirmWindow'`.

- [ ] **Step 3: Implement it and the saved helpers**

`app/src/main/java/com/nungil/core/people/ConfirmWindow.kt`

```kotlin
package com.nungil.core.people

/** Voice "Delete" twice within [windowMs] confirms; once only arms it. Nothing is deleted by one misheard word. */
class ConfirmWindow(private val windowMs: Long = WINDOW_MS) {
    private var armed = false
    private var armedAt = 0L

    /** false = armed (ask again); true = confirmed (and disarmed). */
    fun press(nowMs: Long): Boolean {
        if (armed && nowMs - armedAt <= windowMs) {
            armed = false
            return true
        }
        armed = true
        armedAt = nowMs
        return false
    }

    companion object {
        const val WINDOW_MS = 5_000L
    }
}
```

`app/src/main/java/com/nungil/saved/PhotoFiles.kt`

```kotlin
package com.nungil.saved

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** Photos of saved people and items in the app's private files. Blocking: call on an IO thread. */
object PhotoFiles {
    /** Saves a JPEG in filesDir/[folder]/<uuid>.jpg and returns its absolute path, or null on failure. */
    fun save(context: Context, folder: String, bitmap: Bitmap): String? = try {
        val dir = File(context.filesDir, folder).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        file.absolutePath
    } catch (e: IOException) {
        Log.i("Nungil", "Photo not saved: ${e.message}")
        null
    }

    fun load(path: String?): Bitmap? = path?.let { BitmapFactory.decodeFile(it) }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    private const val JPEG_QUALITY = 90
}
```

`app/src/main/java/com/nungil/saved/SavedNav.kt`

```kotlin
package com.nungil.saved

import androidx.navigation.NavController
import com.nungil.R
import com.nungil.contract.SavedTab

/**
 * After an enrolment finishes: leave the "add" and "enrol" screens (Back must not return to them) and show the
 * Saved list on [tab].
 */
fun NavController.returnToSaved(addDestination: Int, tab: SavedTab) {
    if (!popBackStack(addDestination, true)) popBackStack()
    if (currentDestination?.id != R.id.saved) {
        navigate(R.id.saved, SavedFragmentArgs(tab.ordinal).toBundle())
    }
}
```

`app/src/main/java/com/nungil/saved/SavedAdapter.kt`

```kotlin
package com.nungil.saved

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nungil.R
import com.nungil.databinding.SavedRowBinding

/** One saved person or item in the list. */
data class SavedRow(val id: Long, val title: String, val subtitle: String, val isPerson: Boolean)

class SavedAdapter(private val onClick: (SavedRow) -> Unit) : ListAdapter<SavedRow, SavedAdapter.Holder>(Diff) {

    class Holder(val binding: SavedRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(SavedRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        holder.binding.savedRowTitle.text = row.title
        holder.binding.savedRowSubtitle.text = row.subtitle
        holder.binding.root.contentDescription =
            holder.itemView.context.getString(R.string.saved_row_description, row.title, row.subtitle)
        holder.binding.root.setOnClickListener { onClick(row) }
    }

    private object Diff : DiffUtil.ItemCallback<SavedRow>() {
        override fun areItemsTheSame(a: SavedRow, b: SavedRow) = a.id == b.id && a.isPerson == b.isPerson
        override fun areContentsTheSame(a: SavedRow, b: SavedRow) = a == b
    }
}
```

- [ ] **Step 4: Add the strings and layouts**

`app/src/main/res-y/values/strings_saved.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Saved list, saved people and face enrolment. -->
<resources>
    <string name="saved_title">Saved</string>
    <string name="saved_subtitle">People, cars and things I can recognise.</string>
    <string name="saved_tab_people">People</string>
    <string name="saved_tab_cars">Cars</string>
    <string name="saved_tab_objects">Objects</string>
    <string name="saved_add_person">Add a person</string>
    <string name="saved_add_car">Add a car</string>
    <string name="saved_add_object">Add an object</string>
    <string name="saved_empty_people">No one saved yet. Add someone and I will greet them by name.</string>
    <string name="saved_empty_cars">No cars saved yet.</string>
    <string name="saved_empty_objects">No objects saved yet. Add your bag or keys and I will find them.</string>
    <string name="saved_person_subtitle">Person</string>
    <string name="saved_row_description">%1$s, %2$s</string>

    <string name="person_find">Find this person</string>
    <string name="person_rename">Rename</string>
    <string name="person_delete">Delete</string>
    <string name="person_photo">Photo of %1$s</string>
    <string name="person_rename_title">New name</string>
    <string name="person_rename_speak">Say it</string>
    <string name="person_ok">Save</string>
    <string name="person_cancel">Cancel</string>
    <string name="person_delete_title">Delete %1$s?</string>
    <string name="person_delete_body">Their face samples are removed from this phone.</string>
    <string name="person_deleted">Deleted.</string>
    <string name="person_renamed">New name: %1$s.</string>
    <string name="person_delete_again">Say delete again to confirm.</string>
    <string name="person_not_found">This person is no longer saved.</string>

    <string name="person_add_title">Who is this?</string>
    <string name="person_add_subtitle">Say or type the name, then choose the camera.</string>
    <string name="person_add_hint">Name</string>
    <string name="person_add_ask">What is their name?</string>
    <string name="person_add_other">Someone else (back camera)</string>
    <string name="person_add_me">Me (front camera)</string>
    <string name="person_add_start">Start</string>
    <string name="person_add_need_name">Tell me the name first.</string>

    <string name="enroll_title">Learning %1$s</string>
    <string name="enroll_start">Start</string>
    <string name="enroll_pause">Pause</string>
    <string name="enroll_resume">Continue</string>
    <string name="enroll_model_missing">Face recognition is not available on this phone.</string>
    <string name="enroll_progress">Progress</string>
</resources>
```

`app/src/main/res-y/values-ko/strings_saved.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. -->
<resources>
    <string name="saved_title">저장한 것</string>
    <string name="saved_subtitle">알아볼 수 있는 사람, 자동차, 물건이에요.</string>
    <string name="saved_tab_people">사람</string>
    <string name="saved_tab_cars">자동차</string>
    <string name="saved_tab_objects">물건</string>
    <string name="saved_add_person">사람 추가</string>
    <string name="saved_add_car">자동차 추가</string>
    <string name="saved_add_object">물건 추가</string>
    <string name="saved_empty_people">아직 저장한 사람이 없어요. 사람을 추가하면 이름으로 알려 드릴게요.</string>
    <string name="saved_empty_cars">아직 저장한 자동차가 없어요.</string>
    <string name="saved_empty_objects">아직 저장한 물건이 없어요. 가방이나 열쇠를 추가하면 찾아 드릴게요.</string>
    <string name="saved_person_subtitle">사람</string>
    <string name="saved_row_description">%1$s, %2$s</string>

    <string name="person_find">이 사람 찾기</string>
    <string name="person_rename">이름 바꾸기</string>
    <string name="person_delete">삭제</string>
    <string name="person_photo">%1$s 사진</string>
    <string name="person_rename_title">새 이름</string>
    <string name="person_rename_speak">말하기</string>
    <string name="person_ok">저장</string>
    <string name="person_cancel">취소</string>
    <string name="person_delete_title">%1$s 님을 지울까요?</string>
    <string name="person_delete_body">이 휴대폰에서 얼굴 정보가 지워져요.</string>
    <string name="person_deleted">지웠어요.</string>
    <string name="person_renamed">새 이름: %1$s.</string>
    <string name="person_delete_again">지우려면 한 번 더 삭제라고 말해 주세요.</string>
    <string name="person_not_found">저장된 사람이 아니에요.</string>

    <string name="person_add_title">누구를 등록할까요?</string>
    <string name="person_add_subtitle">이름을 말하거나 입력하고 카메라를 골라 주세요.</string>
    <string name="person_add_hint">이름</string>
    <string name="person_add_ask">이름이 뭐예요?</string>
    <string name="person_add_other">다른 사람 (뒤 카메라)</string>
    <string name="person_add_me">나 (앞 카메라)</string>
    <string name="person_add_start">시작하기</string>
    <string name="person_add_need_name">이름을 먼저 알려 주세요.</string>

    <string name="enroll_title">%1$s 등록 중</string>
    <string name="enroll_start">시작하기</string>
    <string name="enroll_pause">잠시 멈춤</string>
    <string name="enroll_resume">계속하기</string>
    <string name="enroll_model_missing">이 휴대폰에서는 얼굴 인식을 쓸 수 없어요.</string>
    <string name="enroll_progress">진행률</string>
</resources>
```

`app/src/main/res-y/layout/saved_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Saved: headline, three tabs, a list of cards, one "Add" button at the bottom. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/saved_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/saved_title"
        android:textAppearance="@style/TextAppearance.Nungil.Display" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/saved_subtitle"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/saved_tabs"
        android:layout_width="match_parent"
        android:layout_height="@dimen/ng_touch"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:background="?attr/ngBackground"
        app:tabIndicatorColor="?attr/ngPrimary"
        app:tabIndicatorFullWidth="true"
        app:tabIndicatorHeight="4dp"
        app:tabSelectedTextColor="?attr/ngText"
        app:tabTextAppearance="@style/TextAppearance.Nungil.Label"
        app:tabTextColor="?attr/ngTextSub" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/saved_list"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false" />

        <TextView
            android:id="@+id/saved_empty"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap_large"
            android:gravity="center"
            android:textAppearance="@style/TextAppearance.Nungil.Body"
            android:textColor="?attr/ngTextSub"
            android:visibility="gone" />
    </FrameLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/saved_add"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap" />
</LinearLayout>
```

`app/src/main/res-y/layout/saved_row.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. One saved person or item: the whole card is the touch target. -->
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    style="@style/Widget.Nungil.Card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/ng_gap"
    android:clickable="true"
    android:focusable="true"
    android:minHeight="88dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:importantForAccessibility="noHideDescendants"
        android:orientation="vertical">

        <TextView
            android:id="@+id/saved_row_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.Nungil.Headline" />

        <TextView
            android:id="@+id/saved_row_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textAppearance="@style/TextAppearance.Nungil.Caption" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

`app/src/main/res-y/layout/person_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Person detail: photo, name, Find (main action), Rename and Delete. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/person_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="@style/TextAppearance.Nungil.Display" />

    <com.google.android.material.card.MaterialCardView
        style="@style/Widget.Nungil.Card"
        android:layout_width="160dp"
        android:layout_height="160dp"
        android:layout_marginTop="@dimen/ng_gap_large"
        app:contentPadding="0dp">

        <ImageView
            android:id="@+id/person_photo"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="centerCrop" />
    </com.google.android.material.card.MaterialCardView>

    <Space
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/person_rename"
        style="@style/Widget.Nungil.Button.Tonal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/person_rename" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/person_delete"
        style="@style/Widget.Nungil.Button.Danger"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/person_delete" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/person_find"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/person_find" />
</LinearLayout>
```

`app/src/main/res-y/layout/person_add_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Add a person: name (voice or keyboard), which camera, Start. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/person_add_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/person_add_title"
        android:textAppearance="@style/TextAppearance.Nungil.Display" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/person_add_subtitle"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/person_add_name_layout"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:hint="@string/person_add_hint"
        app:boxCornerRadiusBottomEnd="@dimen/ng_radius_button"
        app:boxCornerRadiusBottomStart="@dimen/ng_radius_button"
        app:boxCornerRadiusTopEnd="@dimen/ng_radius_button"
        app:boxCornerRadiusTopStart="@dimen/ng_radius_button"
        app:endIconContentDescription="@string/search_mic"
        app:endIconDrawable="@drawable/search_ic_mic"
        app:endIconMode="custom">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/person_add_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:imeOptions="actionDone"
            android:inputType="textPersonName"
            android:minHeight="@dimen/ng_touch"
            android:textAppearance="@style/TextAppearance.Nungil.Body" />
    </com.google.android.material.textfield.TextInputLayout>

    <RadioGroup
        android:id="@+id/person_add_camera"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:checkedButton="@id/person_add_other">

        <com.google.android.material.radiobutton.MaterialRadioButton
            android:id="@+id/person_add_other"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:buttonTint="?attr/ngPrimary"
            android:minHeight="@dimen/ng_touch"
            android:text="@string/person_add_other"
            android:textAppearance="@style/TextAppearance.Nungil.BodyStrong" />

        <com.google.android.material.radiobutton.MaterialRadioButton
            android:id="@+id/person_add_me"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:buttonTint="?attr/ngPrimary"
            android:minHeight="@dimen/ng_touch"
            android:text="@string/person_add_me"
            android:textAppearance="@style/TextAppearance.Nungil.BodyStrong" />
    </RadioGroup>

    <Space
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/person_add_start"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/person_add_start" />
</LinearLayout>
```

`app/src/main/res-y/layout/enroll_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Face enrolment: who, what to do now (big), progress, camera card, Start/Pause. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/enroll_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <TextView
        android:id="@+id/enroll_prompt"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:textAppearance="@style/TextAppearance.Nungil.Headline"
        android:textColor="?attr/ngAccentText" />

    <com.google.android.material.progressindicator.LinearProgressIndicator
        android:id="@+id/enroll_progress"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:contentDescription="@string/enroll_progress"
        android:max="100"
        app:indicatorColor="?attr/ngPrimary"
        app:trackColor="?attr/ngLine"
        app:trackCornerRadius="4dp"
        app:trackThickness="8dp" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:layout_weight="1">

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.Nungil.Card"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:importantForAccessibility="noHideDescendants"
            app:contentPadding="0dp">

            <androidx.camera.view.PreviewView
                android:id="@+id/enroll_preview"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                app:implementationMode="compatible" />
        </com.google.android.material.card.MaterialCardView>

        <include
            android:id="@+id/enroll_permission"
            layout="@layout/search_permission_panel" />
    </FrameLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/enroll_button"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:text="@string/enroll_start" />
</LinearLayout>
```

- [ ] **Step 5: Replace the four bootstrap stubs**

`app/src/main/java/com/nungil/saved/SavedFragment.kt`

```kotlin
package com.nungil.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.nungil.R
import com.nungil.contract.Dest
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.lang.LabelNames
import com.nungil.data.AppDatabase
import com.nungil.databinding.SavedFragmentBinding
import com.nungil.items.ItemFragmentArgs
import com.nungil.people.PersonFragmentArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Saved people, cars and objects in three tabs, with one "Add" button for the open tab. */
class SavedFragment : Fragment(), VoiceHandler {
    private var _binding: SavedFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<SavedFragmentArgs>()
    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private val adapter = SavedAdapter { open(it) }
    private var tab = SavedTab.PEOPLE
    private var collecting: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SavedFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        ViewCompat.setAccessibilityHeading(binding.savedTitle, true)
        binding.savedList.layoutManager = LinearLayoutManager(requireContext())
        binding.savedList.adapter = adapter
        binding.savedAdd.setOnClickListener { add() }

        val tabs = binding.savedTabs
        tabs.addTab(tabs.newTab().setText(R.string.saved_tab_people))
        tabs.addTab(tabs.newTab().setText(R.string.saved_tab_cars))
        tabs.addTab(tabs.newTab().setText(R.string.saved_tab_objects))
        val start = savedInstanceState?.getInt(KEY_TAB)
            ?: args.tab.takeIf { it in SavedTab.entries.indices }
            ?: SavedTab.PEOPLE.ordinal
        tabs.getTabAt(start)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) = show(SavedTab.entries[t.position])
            override fun onTabUnselected(t: TabLayout.Tab) = Unit
            override fun onTabReselected(t: TabLayout.Tab) = Unit
        })
        show(SavedTab.entries[start])
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, tab.ordinal)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            add()
            true
        }
        else -> false
    }

    private fun show(newTab: SavedTab) {
        tab = newTab
        binding.savedAdd.setText(
            when (newTab) {
                SavedTab.PEOPLE -> R.string.saved_add_person
                SavedTab.CARS -> R.string.saved_add_car
                SavedTab.OBJECTS -> R.string.saved_add_object
            },
        )
        binding.savedEmpty.setText(
            when (newTab) {
                SavedTab.PEOPLE -> R.string.saved_empty_people
                SavedTab.CARS -> R.string.saved_empty_cars
                SavedTab.OBJECTS -> R.string.saved_empty_objects
            },
        )
        collecting?.cancel()
        val rows = rowsFor(newTab)
        collecting = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                rows.collect { list ->
                    adapter.submitList(list)
                    binding.savedEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun rowsFor(tab: SavedTab): Flow<List<SavedRow>> {
        val db = AppDatabase.get(requireContext())
        val personSubtitle = getString(R.string.saved_person_subtitle)
        return when (tab) {
            SavedTab.PEOPLE -> db.people().observePeople().map { people ->
                people.map { SavedRow(it.id, it.name, personSubtitle, isPerson = true) }
            }
            SavedTab.CARS, SavedTab.OBJECTS -> {
                val kind = if (tab == SavedTab.CARS) ItemKind.CAR else ItemKind.OBJECT
                db.items().observeItems(kind.name).map { items ->
                    items.map { SavedRow(it.id, it.name, LabelNames.name(it.label, lang), isPerson = false) }
                }
            }
        }
    }

    private fun open(row: SavedRow) {
        if (row.isPerson) {
            findNavController().navigate(R.id.person, PersonFragmentArgs(row.id).toBundle())
        } else {
            findNavController().navigate(R.id.item, ItemFragmentArgs(row.id).toBundle())
        }
    }

    private fun add() {
        services.navigator.open(
            when (tab) {
                SavedTab.PEOPLE -> Dest.AddPerson()
                SavedTab.CARS -> Dest.AddItem(ItemKind.CAR)
                SavedTab.OBJECTS -> Dest.AddItem(ItemKind.OBJECT)
            },
        )
    }

    private companion object {
        const val KEY_TAB = "tab"
    }
}
```

`app/src/main/java/com/nungil/people/AddPersonFragment.kt`

```kotlin
package com.nungil.people

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.databinding.PersonAddFragmentBinding

/** Name a new person (voice or keyboard), pick the camera, then go to the five-pose enrolment. */
class AddPersonFragment : Fragment(), VoiceHandler {
    private var _binding: PersonAddFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddPersonFragmentArgs>()
    private lateinit var services: AppServices
    private var arrived = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PersonAddFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        ViewCompat.setAccessibilityHeading(binding.personAddTitle, true)
        binding.personAddNameLayout.setEndIconOnClickListener { askName() }
        binding.personAddStart.setOnClickListener { start() }
        // Only on first arrival: coming Back from enrolment restores the typed name instead.
        if (!arrived) {
            arrived = true
            val given = args.name
            if (!given.isNullOrBlank()) binding.personAddName.setText(given) else askName()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            start()
            true
        }
        else -> false
    }

    private fun askName() {
        services.speaker.say(getString(R.string.person_add_ask))
        services.askForWords(viewLifecycleOwner) { text ->
            _binding?.personAddName?.setText(text.trim())
        }
    }

    private fun start() {
        val name = binding.personAddName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.personAddNameLayout.error = getString(R.string.person_add_need_name)
            services.speaker.say(getString(R.string.person_add_need_name))
            askName()
            return
        }
        binding.personAddNameLayout.error = null
        val front = binding.personAddCamera.checkedRadioButtonId == R.id.person_add_me
        findNavController().navigate(R.id.enroll, EnrollFragmentArgs(name, front).toBundle())
    }
}
```

`app/src/main/java/com/nungil/people/PersonFragment.kt`

```kotlin
package com.nungil.people

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nungil.R
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.people.ConfirmWindow
import com.nungil.core.search.TargetType
import com.nungil.data.AppDatabase
import com.nungil.data.PersonEntity
import com.nungil.databinding.PersonFragmentBinding
import com.nungil.saved.PhotoFiles
import com.nungil.search.SearchCameraFragmentArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One saved person: photo, name, find, rename (voice or keyboard) and delete (confirmed). */
class PersonFragment : Fragment(), VoiceHandler {
    private var _binding: PersonFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<PersonFragmentArgs>()
    private lateinit var services: AppServices
    private var person: PersonEntity? = null
    private val confirm = ConfirmWindow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PersonFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        ViewCompat.setAccessibilityHeading(binding.personName, true)
        binding.personFind.setOnClickListener { find() }
        binding.personRename.setOnClickListener { rename() }
        binding.personDelete.setOnClickListener { askDelete() }
        val dao = AppDatabase.get(requireContext()).people()
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dao.getPerson(args.personId) }
            if (loaded == null) {
                services.speaker.say(getString(R.string.person_not_found))
                services.navigator.back()
                return@launch
            }
            person = loaded
            binding.personName.text = loaded.name
            binding.personPhoto.contentDescription = getString(R.string.person_photo, loaded.name)
            val photo = withContext(Dispatchers.IO) { PhotoFiles.load(loaded.photoPath) }
            _binding?.personPhoto?.setImageBitmap(photo)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Delete -> {
            if (confirm.press(SystemClock.elapsedRealtime())) delete() else services.speaker.say(getString(R.string.person_delete_again))
            true
        }
        VoiceCommand.Start -> {
            find()
            true
        }
        else -> false
    }

    private fun find() {
        val p = person ?: return
        val args = SearchCameraFragmentArgs(
            targetType = TargetType.PERSON.name,
            targetId = p.id,
            targetLabel = "person",
            spokenName = p.name,
        )
        findNavController().navigate(R.id.search_camera, args.toBundle())
    }

    private fun rename() {
        val p = person ?: return
        val field = TextInputEditText(requireContext()).apply { setText(p.name) }
        val layout = TextInputLayout(requireContext()).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.ng_gutter)
            setPadding(pad, pad / 2, pad, 0)
            addView(field)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.person_rename_title)
            .setView(layout)
            .setPositiveButton(R.string.person_ok) { _, _ -> applyName(field.text?.toString().orEmpty()) }
            .setNeutralButton(R.string.person_rename_speak) { _, _ ->
                services.speaker.say(getString(R.string.person_add_ask))
                services.askForWords(viewLifecycleOwner) { applyName(it) }
            }
            .setNegativeButton(R.string.person_cancel, null)
            .create()
        dialog.show()
    }

    private fun applyName(raw: String) {
        val p = person ?: return
        val name = raw.trim()
        if (name.isEmpty() || name == p.name) return
        val dao = AppDatabase.get(requireContext()).people()
        AppScope.launch { dao.rename(p.id, name) }
        person = p.copy(name = name)
        _binding?.personName?.text = name
        services.speaker.say(getString(R.string.person_renamed, name))
    }

    private fun askDelete() {
        val p = person ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.person_delete_title, p.name))
            .setMessage(R.string.person_delete_body)
            .setPositiveButton(R.string.person_delete) { _, _ -> delete() }
            .setNegativeButton(R.string.person_cancel, null)
            .show()
    }

    private fun delete() {
        val p = person ?: return
        val dao = AppDatabase.get(requireContext()).people()
        AppScope.launch {
            dao.deletePerson(p.id)
            PhotoFiles.delete(p.photoPath)
        }
        services.speaker.say(getString(R.string.person_deleted))
        services.navigator.back()
    }
}
```

`app/src/main/java/com/nungil/people/EnrollFragment.kt`

```kotlin
package com.nungil.people

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.people.EnrollPhrases
import com.nungil.core.people.EnrollmentGuide
import com.nungil.core.people.FaceQuality
import com.nungil.core.people.Pose
import com.nungil.core.people.VectorBytes
import com.nungil.data.AppDatabase
import com.nungil.data.FaceEmbeddingEntity
import com.nungil.data.PersonEntity
import com.nungil.databinding.EnrollFragmentBinding
import com.nungil.saved.PhotoFiles
import com.nungil.saved.returnToSaved
import com.nungil.scan.CameraSession
import com.nungil.search.CameraGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Five-pose face enrolment: 4 samples each for straight, one side, the other side, up and down (20 in all), with
 * spoken prompts and progress. Nothing is saved until the last sample; leaving halfway saves nothing.
 */
class EnrollFragment : Fragment(), VoiceHandler {
    private var _binding: EnrollFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<EnrollFragmentArgs>()
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private lateinit var name: String
    private lateinit var appContext: Context

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)

    @Volatile
    private var running = false

    @Volatile
    private var finished = false

    // Extras thread only.
    private var finder: FaceFinder? = null
    private var embedder: FaceEmbedder? = null
    private val guide = EnrollmentGuide()
    private val samples = mutableListOf<FloatArray>()
    private var photo: Bitmap? = null
    private var lastSampleMs = 0L
    private var lastFaceMs = 0L
    private var lastHintMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = EnrollFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        name = args.name
        appContext = requireContext().applicationContext
        binding.enrollTitle.text = getString(R.string.enroll_title, name)
        ViewCompat.setAccessibilityHeading(binding.enrollTitle, true)
        binding.enrollPrompt.text = EnrollPhrases.prompt(Pose.STRAIGHT, lang)
        binding.enrollProgress.progress = 0
        binding.enrollButton.setOnClickListener { if (running) pause() else start() }
        gate.attach(binding.enrollPermission)

        val executor = Executors.newSingleThreadExecutor()
        extras = executor
        executor.execute {
            try {
                embedder = FaceEmbedder(appContext)
                finder = FaceFinder()
            } catch (e: Exception) {
                Log.i(TAG, "Face enrolment unavailable", e)
                main.post { modelMissing() }
            }
        }
        services.speaker.say(EnrollPhrases.start(name, lang))
    }

    override fun onResume() {
        super.onResume()
        gate.check()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        running = false
        camera?.stop()
        camera = null
        gate.detach()
        extras?.let { executor ->
            executor.execute {
                finder?.close()
                embedder?.close()
            }
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
        extras = null
        main.removeCallbacksAndMessages(null)
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            start()
            true
        }
        VoiceCommand.Stop -> {
            pause()
            true
        }
        else -> false
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(
            facing = if (args.front) Facing.FRONT else Facing.BACK,
            detect = false,
            keepBitmap = true,
        )
        camera = CameraSession(this, binding.enrollPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    private fun start() {
        if (finished || running) return
        running = true
        binding.enrollButton.setText(R.string.enroll_pause)
        services.speaker.say(EnrollPhrases.prompt(guide.pose ?: Pose.STRAIGHT, lang))
    }

    private fun pause() {
        if (!running) return
        running = false
        binding.enrollButton.setText(R.string.enroll_resume)
        services.speaker.say(EnrollPhrases.paused(lang))
    }

    private fun modelMissing() {
        val b = _binding ?: return
        running = false
        finished = true
        b.enrollButton.isEnabled = false
        b.enrollPrompt.setText(R.string.enroll_model_missing)
        services.speaker.say(getString(R.string.enroll_model_missing))
        services.haptics.buzz(Buzz.ERROR)
    }

    /** Analysis thread: hand the frame to the extras thread unless it is still busy. */
    private fun onFrame(frame: VisionFrame) {
        if (!running || finished) return
        val bitmap = frame.bitmap ?: return
        val executor = extras ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    process(bitmap)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    /** Extras thread. */
    private fun process(bitmap: Bitmap) {
        val finder = finder ?: return
        val embedder = embedder ?: return
        if (!running || finished) return
        val now = SystemClock.elapsedRealtime()
        val face = finder.find(bitmap).maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (face == null) {
            if (now - lastFaceMs > NO_FACE_HINT_MS && now - lastHintMs > NO_FACE_HINT_MS) {
                lastHintMs = now
                services.speaker.say(EnrollPhrases.noFace(lang))
            }
            return
        }
        lastFaceMs = now
        if (now - lastSampleMs < SAMPLE_GAP_MS) return
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val box = face.boundingBox
        if (!FaceQuality.usable(min(box.width(), box.height()), yaw, pitch) || !guide.accepts(yaw, pitch)) return
        val vector = embedder.embed(bitmap, face) ?: return
        lastSampleMs = now
        if (photo == null && guide.pose == Pose.STRAIGHT) photo = FaceCrops.crop(bitmap, face)
        samples += vector
        val poseDone = guide.add(yaw)
        val percent = guide.percent()
        val next = guide.pose
        main.post {
            val b = _binding ?: return@post
            b.enrollProgress.setProgressCompat(percent, true)
            if (next != null) b.enrollPrompt.text = EnrollPhrases.prompt(next, lang)
        }
        services.haptics.buzz(Buzz.TAP)
        when {
            guide.isDone -> finish()
            poseDone && next != null -> {
                services.speaker.say(EnrollPhrases.percent(percent, lang))
                services.speaker.say(EnrollPhrases.prompt(next, lang))
            }
        }
    }

    /** Extras thread: everything is written in one transaction on the process-wide scope. */
    private fun finish() {
        finished = true
        running = false
        val vectors = samples.toList()
        val face = photo
        val context = appContext
        val personName = name
        AppScope.launch {
            val path = face?.let { PhotoFiles.save(context, PHOTO_FOLDER, it) }
            AppDatabase.get(context).people().insertPersonWithFaces(
                PersonEntity(name = personName, photoPath = path, createdAt = System.currentTimeMillis()),
                vectors.map { FaceEmbeddingEntity(vector = VectorBytes.toBytes(it)) },
            )
            withContext(Dispatchers.Main) {
                services.haptics.buzz(Buzz.DONE)
                services.speaker.say(EnrollPhrases.done(personName, lang))
                if (_binding != null) findNavController().returnToSaved(R.id.add_person, SavedTab.PEOPLE)
            }
        }
    }

    private fun onCameraError(error: Throwable) {
        Log.i(TAG, "Enrol camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }

    private companion object {
        const val TAG = "Nungil"
        const val PHOTO_FOLDER = "people"

        /** At most one sample every 250 ms, so the 4 samples of a pose differ a little. */
        const val SAMPLE_GAP_MS = 250L
        const val NO_FACE_HINT_MS = 4_000L
    }
}
```

- [ ] **Step 6: Build, test and check on the phone**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug installDebug`
Expected: `BUILD SUCCESSFUL`, and ConfirmWindowTest passes 4 tests.

On the phone (with the models downloaded):
1. **Saved screen:** open "Saved" from the launcher. There are three tabs, and the People tab says "No one saved yet…" / "아직 저장한 사람이 없어요…". The bottom button reads "Add a person" / "사람 추가".
2. **Add a person:** the app says "What is their name?". Say or type "Ali" / "민준", choose "Someone else (back camera)", and tap Start.
3. **Enrolment:**
   - The prompt and the voice go straight → one side → the other side → up → down.
   - The bar fills in 20% steps, with "20 percent" / "20퍼센트" spoken.
   - With no face in view for 4 s: "I can't see a face…".
   - At 100%: "All done. I will remember Ali." / "다 됐어요. 민준을 기억할게요.", and the Saved list shows the person. Back from that list does not return to the enrolment.
4. **Interrupted enrolment:** start again and press Back at 40%. The person is **not** saved.
5. **Person detail:**
   - The photo and the name are shown.
   - Rename works with the keyboard and with "Say it".
   - Saying "delete" once gives "Say delete again to confirm."; saying it twice deletes the person and returns to the list.

- [ ] **Step 7: Commit and open a pull request**

```powershell
git checkout main; git pull; git checkout -b y/Y5-saved-people
git add app/src/main/java/com/nungil/core/people app/src/test/java/com/nungil/core/people app/src/main/java/com/nungil/saved app/src/main/java/com/nungil/people app/src/main/res-y
git commit -m "Save people by name with guided face enrolment"
git push -u origin y/Y5-saved-people
gh pr create --base main --fill
```

## Task Y6: Saved cars and objects

**Files:**
- Create: `app/src/main/java/com/nungil/core/items/ItemMatcher.kt`, `ItemEnrollmentGuide.kt`, `ItemCrop.kt`, `CenterPick.kt`, `ItemKinds.kt`, `ItemPhrases.kt`
- Test: `app/src/test/java/com/nungil/core/items/ItemMatcherTest.kt`, `ItemEnrollmentTest.kt`
- Create: `app/src/main/java/com/nungil/items/ItemEmbedder.kt`, `ItemRecognizer.kt`, `ItemTagger.kt`, `ItemTargetMatcher.kt`
- Replace (bootstrap stubs): `app/src/main/java/com/nungil/items/AddItemFragment.kt`, `ItemEnrollFragment.kt`, `ItemFragment.kt`
- Replace (Y4 versions): `app/src/main/java/com/nungil/people/Taggers.kt`, `app/src/main/java/com/nungil/search/TargetMatchers.kt`
- Create: `app/src/main/res-y/values/strings_items.xml`, `values-ko/strings_items.xml`, and the layouts `item_add_fragment.xml`, `item_enroll_fragment.xml`, `item_fragment.xml`

**Interfaces:**
- Consumes:
  - Y2–Y5.
  - Room: `ItemDao.allItems / allEmbeddings / getItem / insertItemWithEmbeddings / rename / deleteItem`.
  - Nav args: `AddItemFragmentArgs(kind, name)`, `ItemEnrollFragmentArgs(name, kind)`, `ItemFragmentArgs(itemId)`.
  - The asset `mobilenet_v3_small.tflite`.
- Produces:
  - `ItemMatcher.bestMatch(vector, known, threshold = 0.75f): Match?`, `ItemMatcher.mostCommon(labels)`
  - `enum class ItemStep { STILL, LEFT, RIGHT }`, `ItemEnrollmentGuide(samplesPerStep = 4)`
  - `ItemCrop.rect(box, w, h)`, `ItemCrop.candidates(detections, w, h, preferLabel)`
  - `CenterPick.pick(candidates, minArea = 0.05f): Int`
  - `ItemKinds.allows(kind, label)`
  - `ItemPhrases`
  - `ItemEmbedder(context).embed(frame, box)` and `ItemRecognizer`
  - `ItemTagger : NameTagger` and `ItemTargetMatcher(context, itemId, label) : TargetMatcher`
  - `createNameTaggers` now returns both the face and the item tagger.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/nungil/core/items/ItemMatcherTest.kt`

```kotlin
package com.nungil.core.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ItemMatcherTest {
    private fun at(deg: Double) = floatArrayOf(cos(Math.toRadians(deg)).toFloat(), sin(Math.toRadians(deg)).toFloat())

    @Test fun closestSampleDecides() {
        val known = mapOf(
            1L to listOf(at(90.0), at(10.0)),  // best sample cos 10 = 0.985
            2L to listOf(at(30.0)),            // cos 30 = 0.866
        )
        assertEquals(1L, ItemMatcher.bestMatch(at(0.0), known)?.id)
    }

    @Test fun thresholdIsPointSevenFive() {
        val known = mapOf(1L to listOf(at(0.0)))
        assertEquals(1L, ItemMatcher.bestMatch(at(41.0), known)?.id) // cos 41 = 0.755
        assertNull(ItemMatcher.bestMatch(at(42.0), known))            // cos 42 = 0.743
    }

    @Test fun nothingSaved() {
        assertNull(ItemMatcher.bestMatch(at(0.0), emptyMap()))
        assertNull(ItemMatcher.bestMatch(at(0.0), mapOf(1L to emptyList())))
    }

    @Test fun mostCommonLabel() {
        assertEquals("backpack", ItemMatcher.mostCommon(listOf("suitcase", "backpack", "handbag", "backpack")))
        assertEquals("suitcase", ItemMatcher.mostCommon(listOf("suitcase", "backpack")))
        assertNull(ItemMatcher.mostCommon(emptyList()))
    }
}
```

`app/src/test/java/com/nungil/core/items/ItemEnrollmentTest.kt`

```kotlin
package com.nungil.core.items

import com.nungil.contract.Box
import com.nungil.contract.Detection
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemEnrollmentTest {
    private fun det(label: String, l: Float, t: Float, r: Float, b: Float) = Detection(label, 0.9f, Box(l, t, r, b))

    @Test fun twelveSamplesInThreeSteps() {
        val g = ItemEnrollmentGuide()
        assertEquals(12, g.total)
        assertEquals(ItemStep.STILL, g.step)
        repeat(3) { assertFalse(g.add()) }
        assertTrue(g.add())
        assertEquals(ItemStep.LEFT, g.step)
        repeat(8) { g.add() }
        assertTrue(g.isDone)
        assertNull(g.step)
        assertEquals(100, g.percent())
        assertFalse(g.add())
    }

    @Test fun centerPickPrefersTheMiddleAndIgnoresTinyBoxes() {
        val side = det("cup", 0.0f, 0.0f, 0.4f, 0.4f)         // area 0.16, centre far from the middle
        val middle = det("bottle", 0.4f, 0.4f, 0.6f, 0.6f)    // area 0.04 < 5%: ignored
        val bigMiddle = det("backpack", 0.3f, 0.3f, 0.7f, 0.7f)
        assertEquals(2, CenterPick.pick(listOf(side, middle, bigMiddle)))
        assertEquals(0, CenterPick.pick(listOf(side, middle)))
        assertEquals(-1, CenterPick.pick(listOf(middle)))
        assertEquals(-1, CenterPick.pick(emptyList()))
    }

    @Test fun kinds() {
        assertTrue(ItemKinds.allows(ItemKind.CAR, "truck"))
        assertFalse(ItemKinds.allows(ItemKind.CAR, "backpack"))
        assertTrue(ItemKinds.allows(ItemKind.OBJECT, "backpack"))
        assertFalse(ItemKinds.allows(ItemKind.OBJECT, "person"))
    }

    @Test fun cropRects() {
        assertArrayEquals(intArrayOf(64, 48, 320, 240), ItemCrop.rect(Box(0.1f, 0.1f, 0.5f, 0.5f), 640, 480))
        assertNull(ItemCrop.rect(Box(0.1f, 0.1f, 0.12f, 0.5f), 640, 480)) // 13 px wide
        assertArrayEquals(intArrayOf(0, 0, 640, 480), ItemCrop.rect(Box(-0.2f, -0.1f, 1.3f, 1.2f), 640, 480))
    }

    @Test fun atMostThreeCandidatesPreferredLabelFirst() {
        val dets = listOf(
            det("person", 0f, 0f, 1f, 1f),
            det("cup", 0.1f, 0.1f, 0.2f, 0.2f),
            det("chair", 0.0f, 0.0f, 0.6f, 0.6f),
            det("tv", 0.0f, 0.0f, 0.5f, 0.5f),
            det("book", 0.0f, 0.0f, 0.3f, 0.3f),
        )
        assertEquals(listOf(2, 3, 4), ItemCrop.candidates(dets, 640, 480))
        assertEquals(listOf(1, 2, 3), ItemCrop.candidates(dets, 640, 480, preferLabel = "cup"))
    }

    @Test fun phrases() {
        assertEquals("Hold the phone still, pointing at it.", ItemPhrases.prompt(ItemStep.STILL, Lang.EN))
        assertEquals("휴대폰을 왼쪽으로 조금 옮겨 주세요.", ItemPhrases.prompt(ItemStep.LEFT, Lang.KO))
        assertEquals("내 가방을 등록할게요. 카메라로 비춰 주세요.", ItemPhrases.start("내 가방", Lang.KO))
        assertEquals("다 됐어요. 열쇠를 기억할게요.", ItemPhrases.done("열쇠", Lang.KO))
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.items.*"`
Expected: FAIL — `Unresolved reference 'ItemMatcher'` and similar.

- [ ] **Step 3: Implement the item core**

`app/src/main/java/com/nungil/core/items/ItemMatcher.kt`

```kotlin
package com.nungil.core.items

import com.nungil.core.people.FaceMatcher

/** Saved objects are matched by how they look: cosine between MobileNetV3-Small embeddings of at least [THRESHOLD]. */
object ItemMatcher {
    const val THRESHOLD = 0.75f

    data class Match(val id: Long, val score: Float)

    /** Best saved item for [vector] by its closest sample, or null below [threshold]. */
    fun bestMatch(vector: FloatArray, known: Map<Long, List<FloatArray>>, threshold: Float = THRESHOLD): Match? =
        known
            .filterValues { it.isNotEmpty() }
            .map { (id, samples) -> Match(id, samples.maxOf { FaceMatcher.cosine(vector, it) }) }
            .filter { it.score >= threshold }
            .maxByOrNull { it.score }

    /** The label seen most often while enrolling (ties: the one seen first), instead of the first frame's guess. */
    fun mostCommon(labels: List<String>): String? {
        if (labels.isEmpty()) return null
        val counts = labels.groupingBy { it }.eachCount()
        val top = counts.values.max()
        return labels.first { counts[it] == top }
    }
}
```

`app/src/main/java/com/nungil/core/items/ItemEnrollmentGuide.kt`

```kotlin
package com.nungil.core.items

/** The three steps of item enrolment. */
enum class ItemStep { STILL, LEFT, RIGHT }

/** [samplesPerStep] samples while holding still, moved left and moved right (12 by default). */
class ItemEnrollmentGuide(val samplesPerStep: Int = SAMPLES_PER_STEP) {
    private val counts = IntArray(ItemStep.entries.size)

    val total: Int = samplesPerStep * ItemStep.entries.size
    val taken: Int get() = counts.sum()

    /** The step being collected; null when done. */
    val step: ItemStep? get() = ItemStep.entries.firstOrNull { counts[it.ordinal] < samplesPerStep }
    val isDone: Boolean get() = step == null

    /** Records one sample. Returns true when it finished a step (or the whole enrolment). */
    fun add(): Boolean {
        val current = step ?: return false
        counts[current.ordinal]++
        return step != current
    }

    fun percent(): Int = taken * 100 / total

    companion object {
        const val SAMPLES_PER_STEP = 4
    }
}
```

`app/src/main/java/com/nungil/core/items/ItemCrop.kt`

```kotlin
package com.nungil.core.items

import com.nungil.contract.Box
import com.nungil.contract.Detection
import kotlin.math.roundToInt

/** Which detector boxes to embed, and their pixel rects. */
object ItemCrop {
    const val MIN_CROP_PX = 16
    const val MAX_CROPS_PER_FRAME = 3
    private const val PERSON = "person"

    /** Pixel rect [left, top, right, bottom] of [box] in a [width] x [height] image; null under 16 x 16 px. */
    fun rect(box: Box, width: Int, height: Int): IntArray? {
        val l = (box.left * width).roundToInt().coerceIn(0, width)
        val t = (box.top * height).roundToInt().coerceIn(0, height)
        val r = (box.right * width).roundToInt().coerceIn(0, width)
        val b = (box.bottom * height).roundToInt().coerceIn(0, height)
        if (r - l < MIN_CROP_PX || b - t < MIN_CROP_PX) return null
        return intArrayOf(l, t, r, b)
    }

    /**
     * Up to [MAX_CROPS_PER_FRAME] indices of non-person boxes big enough to crop: boxes labelled [preferLabel]
     * first, then the largest.
     */
    fun candidates(detections: List<Detection>, width: Int, height: Int, preferLabel: String? = null): List<Int> =
        detections.indices
            .filter { detections[it].label != PERSON && rect(detections[it].box, width, height) != null }
            .sortedWith(
                compareByDescending<Int> { detections[it].label == preferLabel }
                    .thenByDescending { detections[it].box.area },
            )
            .take(MAX_CROPS_PER_FRAME)
}
```

`app/src/main/java/com/nungil/core/items/CenterPick.kt`

```kotlin
package com.nungil.core.items

import com.nungil.contract.Detection

/** Chooses the box the user is pointing at while enrolling an item. */
object CenterPick {
    const val MIN_AREA = 0.05f

    /** Index of the box nearest the centre among those covering at least [minArea] of the frame; ties go to the bigger box. */
    fun pick(candidates: List<Detection>, minArea: Float = MIN_AREA): Int =
        candidates.indices
            .filter { candidates[it].box.area >= minArea }
            .minWithOrNull(
                compareBy<Int> { distanceToCentre(candidates[it]) }
                    .thenByDescending { candidates[it].box.area },
            ) ?: -1

    private fun distanceToCentre(d: Detection): Float {
        val dx = d.box.centerX - 0.5f
        val dy = d.box.centerY - 0.5f
        return dx * dx + dy * dy
    }
}
```

`app/src/main/java/com/nungil/core/items/ItemKinds.kt`

```kotlin
package com.nungil.core.items

import com.nungil.contract.ItemKind

/** Keeps cars and objects apart: a car is enrolled only from vehicle boxes, an object from anything but a person. */
object ItemKinds {
    val VEHICLES = setOf("car", "truck", "bus", "motorcycle", "bicycle")

    fun allows(kind: ItemKind, label: String): Boolean = when (kind) {
        ItemKind.CAR -> label in VEHICLES
        ItemKind.OBJECT -> label != "person"
    }
}
```

`app/src/main/java/com/nungil/core/items/ItemPhrases.kt`

```kotlin
package com.nungil.core.items

import com.nungil.contract.Lang
import com.nungil.core.lang.Josa

/** What item enrolment says, in both languages. */
object ItemPhrases {
    fun start(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "Learning $name. Point the camera at it."
        Lang.KO -> "${Josa.eulReul(name)} 등록할게요. 카메라로 비춰 주세요."
    }

    fun prompt(step: ItemStep, lang: Lang): String = when (lang) {
        Lang.EN -> when (step) {
            ItemStep.STILL -> "Hold the phone still, pointing at it."
            ItemStep.LEFT -> "Move the phone a little to the left."
            ItemStep.RIGHT -> "Now a little to the right."
        }
        Lang.KO -> when (step) {
            ItemStep.STILL -> "물건을 향해 휴대폰을 가만히 들어 주세요."
            ItemStep.LEFT -> "휴대폰을 왼쪽으로 조금 옮겨 주세요."
            ItemStep.RIGHT -> "이제 오른쪽으로 조금 옮겨 주세요."
        }
    }

    fun noItem(lang: Lang): String = when (lang) {
        Lang.EN -> "I can't see it. Point the camera at it from about an arm's length."
        Lang.KO -> "물건이 보이지 않아요. 팔 길이 정도 떨어져서 비춰 주세요."
    }

    fun done(name: String, lang: Lang): String = when (lang) {
        Lang.EN -> "All done. I will remember $name."
        Lang.KO -> "다 됐어요. ${Josa.eulReul(name)} 기억할게요."
    }
}
```

- [ ] **Step 4: Run the tests again**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.core.items.*"`
Expected: PASS — ItemMatcherTest 4, ItemEnrollmentTest 6.

- [ ] **Step 5: Add the embedder, recogniser, tagger and search matcher**

`app/src/main/java/com/nungil/items/ItemEmbedder.kt`

```kotlin
package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.nungil.contract.Box
import com.nungil.core.items.ItemCrop
import java.io.Closeable

/**
 * MobileNetV3-Small image embeddings (about 4 MB, L2-normalised) through MediaPipe's ImageEmbedder.
 * One worker thread only. Throws from the constructor when the model is missing.
 */
class ItemEmbedder(context: Context) : Closeable {
    private val embedder: ImageEmbedder = ImageEmbedder.createFromOptions(
        context,
        ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setL2Normalize(true)
            .setQuantize(false)
            .build(),
    )

    /** Embedding of the part of [frame] inside [box], or null when it is under 16 x 16 px or fails. */
    fun embed(frame: Bitmap, box: Box): FloatArray? {
        val r = ItemCrop.rect(box, frame.width, frame.height) ?: return null
        return embed(Bitmap.createBitmap(frame, r[0], r[1], r[2] - r[0], r[3] - r[1]))
    }

    fun embed(image: Bitmap): FloatArray? = try {
        embedder.embed(BitmapImageBuilder(image).build())
            .embeddingResult()
            .embeddings()
            .firstOrNull()
            ?.floatEmbedding()
    } catch (e: Exception) {
        Log.i("Nungil", "Item embedding failed: ${e.message}")
        null
    }

    override fun close() = embedder.close()

    private companion object {
        const val MODEL = "mobilenet_v3_small.tflite"
    }
}
```

`app/src/main/java/com/nungil/items/ItemRecognizer.kt`

```kotlin
package com.nungil.items

import android.content.Context
import com.nungil.core.items.ItemMatcher
import com.nungil.core.people.VectorBytes
import com.nungil.data.AppDatabase
import kotlinx.coroutines.runBlocking

/** Saved items and their samples from Room, matched with ItemMatcher. Load on a worker thread. */
class ItemRecognizer(context: Context) {
    private val db = AppDatabase.get(context.applicationContext)

    @Volatile
    private var known: Map<Long, List<FloatArray>> = emptyMap()

    @Volatile
    private var names: Map<Long, String> = emptyMap()

    val isEmpty: Boolean get() = known.isEmpty()

    /** Blocks on Room; never call on the main thread. */
    fun reload() = runBlocking {
        val items = db.items().allItems()
        val embeddings = db.items().allEmbeddings()
        val byId = items.associate { it.id to it.name }
        names = byId
        known = embeddings
            .filter { it.itemId in byId }
            .groupBy({ it.itemId }, { VectorBytes.toFloats(it.vector) })
    }

    fun identify(vector: FloatArray): ItemMatcher.Match? = ItemMatcher.bestMatch(vector, known)

    fun nameOf(itemId: Long): String? = names[itemId]

    companion object {
        /** Blocks on Room; never call on the main thread. */
        fun hasItems(context: Context): Boolean = runBlocking {
            AppDatabase.get(context.applicationContext).items().allItems().isNotEmpty()
        }
    }
}
```

`app/src/main/java/com/nungil/items/ItemTagger.kt`

```kotlin
package com.nungil.items

import android.content.Context
import com.nungil.contract.app.NameTag
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.ItemCrop

/**
 * Names saved items inside A's scan: embeds at most 3 non-person boxes per frame (largest first) and renames the
 * box whose look matches a saved item. Each saved item names at most one box per frame.
 */
class ItemTagger(context: Context) : NameTagger {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }

    override fun tag(frame: VisionFrame): List<NameTag> {
        val bitmap = frame.bitmap ?: return emptyList()
        if (recognizer.isEmpty) return emptyList()
        val best = HashMap<Long, Pair<Int, Float>>()
        for (index in ItemCrop.candidates(frame.detections, bitmap.width, bitmap.height)) {
            val vector = embedder.embed(bitmap, frame.detections[index].box) ?: continue
            val match = recognizer.identify(vector) ?: continue
            val previous = best[match.id]
            if (previous == null || match.score > previous.second) best[match.id] = index to match.score
        }
        return best.mapNotNull { (id, hit) -> recognizer.nameOf(id)?.let { NameTag(hit.first, it, TagKind.ITEM) } }
    }

    override fun close() = embedder.close()
}
```

`app/src/main/java/com/nungil/items/ItemTargetMatcher.kt`

```kotlin
package com.nungil.items

import android.content.Context
import com.nungil.contract.app.VisionFrame
import com.nungil.core.items.ItemCrop
import com.nungil.search.TargetMatcher

/**
 * Search camera target "a saved item": any non-person box (items are matched by how they look, not by label),
 * boxes with the item's label tried first, at most 3 per frame. The box counts only when [itemId] is the best
 * match among all saved items.
 */
class ItemTargetMatcher(context: Context, private val itemId: Long, private val label: String) : TargetMatcher {
    private val embedder = ItemEmbedder(context)
    private val recognizer = ItemRecognizer(context).also { it.reload() }

    override val slow: Boolean = true

    override fun find(frame: VisionFrame): Int {
        val bitmap = frame.bitmap ?: return -1
        var best = -1
        var bestScore = 0f
        for (index in ItemCrop.candidates(frame.detections, bitmap.width, bitmap.height, preferLabel = label)) {
            val vector = embedder.embed(bitmap, frame.detections[index].box) ?: continue
            val match = recognizer.identify(vector) ?: continue
            if (match.id == itemId && match.score > bestScore) {
                best = index
                bestScore = match.score
            }
        }
        return best
    }

    override fun close() = embedder.close()
}
```

- [ ] **Step 6: Replace the tagger factory and the matcher factory again (whole files)**

`app/src/main/java/com/nungil/people/Taggers.kt`

```kotlin
package com.nungil.people

import android.content.Context
import android.util.Log
import com.nungil.contract.app.NameTagger
import com.nungil.items.ItemRecognizer
import com.nungil.items.ItemTagger

/**
 * The taggers A's scan screen runs on its extras thread: saved faces, then saved items. Called once per scan
 * screen, off the main thread; A closes every tagger when the screen closes. A tagger that cannot load (model
 * missing, nothing saved) is left out, so the scan still works without names.
 */
fun createNameTaggers(context: Context): List<NameTagger> {
    val app = context.applicationContext
    val taggers = mutableListOf<NameTagger>()
    if (FaceRecognizer.hasPeople(app)) tryCreate("face") { FaceTagger(app) }?.let(taggers::add)
    if (ItemRecognizer.hasItems(app)) tryCreate("item") { ItemTagger(app) }?.let(taggers::add)
    return taggers
}

internal inline fun tryCreate(what: String, make: () -> NameTagger): NameTagger? =
    try {
        make()
    } catch (e: Exception) {
        Log.i("Nungil", "No $what tagger: ${e.message}")
        null
    } catch (e: LinkageError) {
        Log.i("Nungil", "No $what tagger: ${e.message}")
        null
    }
```

`app/src/main/java/com/nungil/search/TargetMatchers.kt`

```kotlin
package com.nungil.search

import android.content.Context
import com.nungil.core.search.TargetType
import com.nungil.items.ItemTargetMatcher
import com.nungil.people.PersonTargetMatcher

/** Builds the matcher for a search target. Call on a worker thread: face and item matchers load models. */
object TargetMatchers {
    fun create(context: Context, type: TargetType, id: Long, label: String): TargetMatcher = when (type) {
        TargetType.LABEL -> LabelMatcher(label)
        TargetType.PERSON -> PersonTargetMatcher(context, id)
        TargetType.ITEM -> ItemTargetMatcher(context, id, label)
    }
}
```

- [ ] **Step 7: Add the strings, layouts and the three screens**

`app/src/main/res-y/values/strings_items.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Saved cars and objects. -->
<resources>
    <string name="item_add_title_car">What is this car called?</string>
    <string name="item_add_title_object">What is this called?</string>
    <string name="item_add_subtitle">For example: my bag, dad\'s car. Then point the camera at it.</string>
    <string name="item_add_hint">Name</string>
    <string name="item_add_ask">What should I call it?</string>
    <string name="item_add_start">Start</string>
    <string name="item_add_need_name">Tell me the name first.</string>

    <string name="item_enroll_title">Learning %1$s</string>
    <string name="item_enroll_start">Start</string>
    <string name="item_enroll_pause">Pause</string>
    <string name="item_enroll_resume">Continue</string>
    <string name="item_enroll_model_missing">Object recognition is not available on this phone.</string>
    <string name="item_enroll_progress">Progress</string>

    <string name="item_find">Find it</string>
    <string name="item_rename">Rename</string>
    <string name="item_delete">Delete</string>
    <string name="item_photo">Photo of %1$s</string>
    <string name="item_rename_title">New name</string>
    <string name="item_rename_speak">Say it</string>
    <string name="item_ok">Save</string>
    <string name="item_cancel">Cancel</string>
    <string name="item_delete_title">Delete %1$s?</string>
    <string name="item_delete_body">Its samples are removed from this phone.</string>
    <string name="item_deleted">Deleted.</string>
    <string name="item_renamed">New name: %1$s.</string>
    <string name="item_delete_again">Say delete again to confirm.</string>
    <string name="item_not_found">This item is no longer saved.</string>
</resources>
```

`app/src/main/res-y/values-ko/strings_items.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. -->
<resources>
    <string name="item_add_title_car">이 자동차를 뭐라고 부를까요?</string>
    <string name="item_add_title_object">이 물건을 뭐라고 부를까요?</string>
    <string name="item_add_subtitle">예: 내 가방, 아빠 차. 그다음 카메라로 비춰 주세요.</string>
    <string name="item_add_hint">이름</string>
    <string name="item_add_ask">뭐라고 부를까요?</string>
    <string name="item_add_start">시작하기</string>
    <string name="item_add_need_name">이름을 먼저 알려 주세요.</string>

    <string name="item_enroll_title">%1$s 등록 중</string>
    <string name="item_enroll_start">시작하기</string>
    <string name="item_enroll_pause">잠시 멈춤</string>
    <string name="item_enroll_resume">계속하기</string>
    <string name="item_enroll_model_missing">이 휴대폰에서는 물건 인식을 쓸 수 없어요.</string>
    <string name="item_enroll_progress">진행률</string>

    <string name="item_find">찾기</string>
    <string name="item_rename">이름 바꾸기</string>
    <string name="item_delete">삭제</string>
    <string name="item_photo">%1$s 사진</string>
    <string name="item_rename_title">새 이름</string>
    <string name="item_rename_speak">말하기</string>
    <string name="item_ok">저장</string>
    <string name="item_cancel">취소</string>
    <string name="item_delete_title">%1$s, 지울까요?</string>
    <string name="item_delete_body">이 휴대폰에서 저장한 정보가 지워져요.</string>
    <string name="item_deleted">지웠어요.</string>
    <string name="item_renamed">새 이름: %1$s.</string>
    <string name="item_delete_again">지우려면 한 번 더 삭제라고 말해 주세요.</string>
    <string name="item_not_found">저장된 물건이 아니에요.</string>
</resources>
```

`app/src/main/res-y/layout/item_add_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Name a car or an object (voice or keyboard), then Start. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/item_add_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="@style/TextAppearance.Nungil.Display" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/item_add_subtitle"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/item_add_name_layout"
        style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:hint="@string/item_add_hint"
        app:boxCornerRadiusBottomEnd="@dimen/ng_radius_button"
        app:boxCornerRadiusBottomStart="@dimen/ng_radius_button"
        app:boxCornerRadiusTopEnd="@dimen/ng_radius_button"
        app:boxCornerRadiusTopStart="@dimen/ng_radius_button"
        app:endIconContentDescription="@string/search_mic"
        app:endIconDrawable="@drawable/search_ic_mic"
        app:endIconMode="custom">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/item_add_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:imeOptions="actionDone"
            android:inputType="text"
            android:minHeight="@dimen/ng_touch"
            android:textAppearance="@style/TextAppearance.Nungil.Body" />
    </com.google.android.material.textfield.TextInputLayout>

    <Space
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/item_add_start"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/item_add_start" />
</LinearLayout>
```

`app/src/main/res-y/layout/item_enroll_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Item enrolment: what to do now, progress, camera card with the picked box, Start/Pause. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/item_enroll_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <TextView
        android:id="@+id/item_enroll_prompt"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:textAppearance="@style/TextAppearance.Nungil.Headline"
        android:textColor="?attr/ngAccentText" />

    <com.google.android.material.progressindicator.LinearProgressIndicator
        android:id="@+id/item_enroll_progress"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:contentDescription="@string/item_enroll_progress"
        android:max="100"
        app:indicatorColor="?attr/ngPrimary"
        app:trackColor="?attr/ngLine"
        app:trackCornerRadius="4dp"
        app:trackThickness="8dp" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:layout_weight="1">

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.Nungil.Card"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:importantForAccessibility="noHideDescendants"
            app:contentPadding="0dp">

            <FrameLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <androidx.camera.view.PreviewView
                    android:id="@+id/item_enroll_preview"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    app:implementationMode="compatible" />

                <com.nungil.scan.OverlayView
                    android:id="@+id/item_enroll_overlay"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent" />
            </FrameLayout>
        </com.google.android.material.card.MaterialCardView>

        <include
            android:id="@+id/item_enroll_permission"
            layout="@layout/search_permission_panel" />
    </FrameLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/item_enroll_button"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:text="@string/item_enroll_start" />
</LinearLayout>
```

`app/src/main/res-y/layout/item_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Item detail: photo, name, what it looks like, Find (main action), Rename and Delete. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/item_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="@style/TextAppearance.Nungil.Display" />

    <TextView
        android:id="@+id/item_label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <com.google.android.material.card.MaterialCardView
        style="@style/Widget.Nungil.Card"
        android:layout_width="160dp"
        android:layout_height="160dp"
        android:layout_marginTop="@dimen/ng_gap_large"
        app:contentPadding="0dp">

        <ImageView
            android:id="@+id/item_photo"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="centerCrop" />
    </com.google.android.material.card.MaterialCardView>

    <Space
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/item_rename"
        style="@style/Widget.Nungil.Button.Tonal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/item_rename" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/item_delete"
        style="@style/Widget.Nungil.Button.Danger"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/item_delete" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/item_find"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/item_find" />
</LinearLayout>
```

`app/src/main/java/com/nungil/items/AddItemFragment.kt`

```kotlin
package com.nungil.items

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.ItemKind
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.databinding.ItemAddFragmentBinding

/** Name a new car or object (voice or keyboard), then go to the three-step item enrolment. */
class AddItemFragment : Fragment(), VoiceHandler {
    private var _binding: ItemAddFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<AddItemFragmentArgs>()
    private lateinit var services: AppServices
    private var arrived = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ItemAddFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        binding.itemAddTitle.setText(
            if (args.kind == ItemKind.CAR) R.string.item_add_title_car else R.string.item_add_title_object,
        )
        ViewCompat.setAccessibilityHeading(binding.itemAddTitle, true)
        binding.itemAddNameLayout.setEndIconOnClickListener { askName() }
        binding.itemAddStart.setOnClickListener { start() }
        // Only on first arrival: coming Back from enrolment restores the typed name instead.
        if (!arrived) {
            arrived = true
            val given = args.name
            if (!given.isNullOrBlank()) binding.itemAddName.setText(given) else askName()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            start()
            true
        }
        else -> false
    }

    private fun askName() {
        services.speaker.say(getString(R.string.item_add_ask))
        services.askForWords(viewLifecycleOwner) { text ->
            _binding?.itemAddName?.setText(text.trim())
        }
    }

    private fun start() {
        val name = binding.itemAddName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.itemAddNameLayout.error = getString(R.string.item_add_need_name)
            services.speaker.say(getString(R.string.item_add_need_name))
            askName()
            return
        }
        binding.itemAddNameLayout.error = null
        findNavController().navigate(R.id.item_enroll, ItemEnrollFragmentArgs(name, args.kind).toBundle())
    }
}
```

`app/src/main/java/com/nungil/items/ItemEnrollFragment.kt`

```kotlin
package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.Box
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.items.CenterPick
import com.nungil.core.items.ItemCrop
import com.nungil.core.items.ItemEnrollmentGuide
import com.nungil.core.items.ItemKinds
import com.nungil.core.items.ItemMatcher
import com.nungil.core.items.ItemPhrases
import com.nungil.core.items.ItemStep
import com.nungil.core.people.EnrollPhrases
import com.nungil.core.people.VectorBytes
import com.nungil.data.AppDatabase
import com.nungil.data.ItemEmbeddingEntity
import com.nungil.data.ItemEntity
import com.nungil.databinding.ItemEnrollFragmentBinding
import com.nungil.saved.PhotoFiles
import com.nungil.saved.returnToSaved
import com.nungil.scan.CameraSession
import com.nungil.scan.OverlayView
import com.nungil.search.CameraGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Three-step item enrolment: 4 samples held still, 4 moved left, 4 moved right (12 in all). The box nearest the
 * centre that covers at least 5% of the frame is embedded; detector confidence is 0.3 here. Nothing is saved
 * until the last sample.
 */
class ItemEnrollFragment : Fragment(), VoiceHandler {
    private var _binding: ItemEnrollFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<ItemEnrollFragmentArgs>()
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private lateinit var name: String
    private lateinit var kind: ItemKind
    private lateinit var appContext: Context

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)

    @Volatile
    private var running = false

    @Volatile
    private var finished = false

    // Extras thread only.
    private var embedder: ItemEmbedder? = null
    private val guide = ItemEnrollmentGuide()
    private val samples = mutableListOf<FloatArray>()
    private val labels = mutableListOf<String>()
    private var photo: Bitmap? = null
    private var lastSampleMs = 0L
    private var lastSeenMs = 0L
    private var lastHintMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ItemEnrollFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        name = args.name
        kind = args.kind
        appContext = requireContext().applicationContext
        binding.itemEnrollTitle.text = getString(R.string.item_enroll_title, name)
        ViewCompat.setAccessibilityHeading(binding.itemEnrollTitle, true)
        binding.itemEnrollPrompt.text = ItemPhrases.prompt(ItemStep.STILL, lang)
        binding.itemEnrollButton.setOnClickListener { if (running) pause() else start() }
        gate.attach(binding.itemEnrollPermission)

        val executor = Executors.newSingleThreadExecutor()
        extras = executor
        executor.execute {
            try {
                embedder = ItemEmbedder(appContext)
            } catch (e: Exception) {
                Log.i(TAG, "Item enrolment unavailable", e)
                main.post { modelMissing() }
            }
        }
        services.speaker.say(ItemPhrases.start(name, lang))
    }

    override fun onResume() {
        super.onResume()
        gate.check()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        running = false
        camera?.stop()
        camera = null
        gate.detach()
        extras?.let { executor ->
            executor.execute { embedder?.close() }
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
        extras = null
        main.removeCallbacksAndMessages(null)
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            start()
            true
        }
        VoiceCommand.Stop -> {
            pause()
            true
        }
        else -> false
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(
            facing = Facing.BACK,
            detect = true,
            keepBitmap = true,
            minScore = ENROLL_MIN_SCORE,
        )
        camera = CameraSession(this, binding.itemEnrollPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    private fun start() {
        if (finished || running) return
        running = true
        binding.itemEnrollButton.setText(R.string.item_enroll_pause)
        services.speaker.say(ItemPhrases.prompt(guide.step ?: ItemStep.STILL, lang))
    }

    private fun pause() {
        if (!running) return
        running = false
        binding.itemEnrollButton.setText(R.string.item_enroll_resume)
        services.speaker.say(EnrollPhrases.paused(lang))
    }

    private fun modelMissing() {
        val b = _binding ?: return
        running = false
        finished = true
        b.itemEnrollButton.isEnabled = false
        b.itemEnrollPrompt.setText(R.string.item_enroll_model_missing)
        services.speaker.say(getString(R.string.item_enroll_model_missing))
        services.haptics.buzz(Buzz.ERROR)
    }

    /** Analysis thread: show the picked box, then hand the frame to the extras thread unless it is busy. */
    private fun onFrame(frame: VisionFrame) {
        val allowed = frame.detections.filter { ItemKinds.allows(kind, it.label) }
        val picked = allowed.getOrNull(CenterPick.pick(allowed))
        val marks = listOfNotNull(picked?.let { OverlayView.Mark(it.box, null, OverlayView.Style.TARGET) })
        main.post { _binding?.itemEnrollOverlay?.show(marks, frame.imageWidth, frame.imageHeight, false) }

        if (!running || finished) return
        val bitmap = frame.bitmap ?: return
        val executor = extras ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    process(bitmap, picked?.box, picked?.label)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    /** Extras thread. */
    private fun process(bitmap: Bitmap, box: Box?, label: String?) {
        val embedder = embedder ?: return
        if (!running || finished) return
        val now = SystemClock.elapsedRealtime()
        if (box == null || label == null) {
            if (now - lastSeenMs > NO_ITEM_HINT_MS && now - lastHintMs > NO_ITEM_HINT_MS) {
                lastHintMs = now
                services.speaker.say(ItemPhrases.noItem(lang))
            }
            return
        }
        lastSeenMs = now
        if (now - lastSampleMs < SAMPLE_GAP_MS) return
        val vector = embedder.embed(bitmap, box) ?: return
        lastSampleMs = now
        if (photo == null) {
            photo = ItemCrop.rect(box, bitmap.width, bitmap.height)?.let { r ->
                Bitmap.createBitmap(bitmap, r[0], r[1], r[2] - r[0], r[3] - r[1])
            }
        }
        samples += vector
        labels += label
        val stepDone = guide.add()
        val percent = guide.percent()
        val next = guide.step
        main.post {
            val b = _binding ?: return@post
            b.itemEnrollProgress.setProgressCompat(percent, true)
            if (next != null) b.itemEnrollPrompt.text = ItemPhrases.prompt(next, lang)
        }
        services.haptics.buzz(Buzz.TAP)
        when {
            guide.isDone -> finish()
            stepDone && next != null -> {
                services.speaker.say(EnrollPhrases.percent(percent, lang))
                services.speaker.say(ItemPhrases.prompt(next, lang))
            }
        }
    }

    /** Extras thread: the item and its 12 samples are written in one transaction on the process-wide scope. */
    private fun finish() {
        finished = true
        running = false
        val vectors = samples.toList()
        val label = ItemMatcher.mostCommon(labels) ?: "object"
        val image = photo
        val context = appContext
        val itemName = name
        val itemKind = kind
        AppScope.launch {
            val path = image?.let { PhotoFiles.save(context, PHOTO_FOLDER, it) }
            AppDatabase.get(context).items().insertItemWithEmbeddings(
                ItemEntity(
                    name = itemName,
                    kind = itemKind.name,
                    label = label,
                    photoPath = path,
                    createdAt = System.currentTimeMillis(),
                ),
                vectors.map { ItemEmbeddingEntity(vector = VectorBytes.toBytes(it)) },
            )
            withContext(Dispatchers.Main) {
                services.haptics.buzz(Buzz.DONE)
                services.speaker.say(ItemPhrases.done(itemName, lang))
                val tab = if (itemKind == ItemKind.CAR) SavedTab.CARS else SavedTab.OBJECTS
                if (_binding != null) findNavController().returnToSaved(R.id.add_item, tab)
            }
        }
    }

    private fun onCameraError(error: Throwable) {
        Log.i(TAG, "Item enrol camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }

    private companion object {
        const val TAG = "Nungil"
        const val PHOTO_FOLDER = "items"

        /** Enrol at confidence 0.3 so the item keeps being found while the phone moves (brief §6). */
        const val ENROLL_MIN_SCORE = 0.3f
        const val SAMPLE_GAP_MS = 300L
        const val NO_ITEM_HINT_MS = 4_000L
    }
}
```

`app/src/main/java/com/nungil/items/ItemFragment.kt`

```kotlin
package com.nungil.items

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nungil.R
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.lang.LabelNames
import com.nungil.core.people.ConfirmWindow
import com.nungil.core.search.TargetType
import com.nungil.data.AppDatabase
import com.nungil.data.ItemEntity
import com.nungil.databinding.ItemFragmentBinding
import com.nungil.saved.PhotoFiles
import com.nungil.search.SearchCameraFragmentArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One saved car or object: photo, name, find, rename (voice or keyboard) and delete (confirmed). */
class ItemFragment : Fragment(), VoiceHandler {
    private var _binding: ItemFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<ItemFragmentArgs>()
    private lateinit var services: AppServices
    private var item: ItemEntity? = null
    private val confirm = ConfirmWindow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ItemFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        ViewCompat.setAccessibilityHeading(binding.itemName, true)
        binding.itemFind.setOnClickListener { find() }
        binding.itemRename.setOnClickListener { rename() }
        binding.itemDelete.setOnClickListener { askDelete() }
        val dao = AppDatabase.get(requireContext()).items()
        val lang = services.lang
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dao.getItem(args.itemId) }
            if (loaded == null) {
                services.speaker.say(getString(R.string.item_not_found))
                services.navigator.back()
                return@launch
            }
            item = loaded
            binding.itemName.text = loaded.name
            binding.itemLabel.text = LabelNames.name(loaded.label, lang)
            binding.itemPhoto.contentDescription = getString(R.string.item_photo, loaded.name)
            val photo = withContext(Dispatchers.IO) { PhotoFiles.load(loaded.photoPath) }
            _binding?.itemPhoto?.setImageBitmap(photo)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Delete -> {
            if (confirm.press(SystemClock.elapsedRealtime())) delete() else services.speaker.say(getString(R.string.item_delete_again))
            true
        }
        VoiceCommand.Start -> {
            find()
            true
        }
        else -> false
    }

    private fun find() {
        val i = item ?: return
        val args = SearchCameraFragmentArgs(
            targetType = TargetType.ITEM.name,
            targetId = i.id,
            targetLabel = i.label,
            spokenName = i.name,
        )
        findNavController().navigate(R.id.search_camera, args.toBundle())
    }

    private fun rename() {
        val i = item ?: return
        val field = TextInputEditText(requireContext()).apply { setText(i.name) }
        val layout = TextInputLayout(requireContext()).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.ng_gutter)
            setPadding(pad, pad / 2, pad, 0)
            addView(field)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.item_rename_title)
            .setView(layout)
            .setPositiveButton(R.string.item_ok) { _, _ -> applyName(field.text?.toString().orEmpty()) }
            .setNeutralButton(R.string.item_rename_speak) { _, _ ->
                services.speaker.say(getString(R.string.item_add_ask))
                services.askForWords(viewLifecycleOwner) { applyName(it) }
            }
            .setNegativeButton(R.string.item_cancel, null)
            .show()
    }

    private fun applyName(raw: String) {
        val i = item ?: return
        val name = raw.trim()
        if (name.isEmpty() || name == i.name) return
        val dao = AppDatabase.get(requireContext()).items()
        AppScope.launch { dao.rename(i.id, name) }
        item = i.copy(name = name)
        _binding?.itemName?.text = name
        services.speaker.say(getString(R.string.item_renamed, name))
    }

    private fun askDelete() {
        val i = item ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.item_delete_title, i.name))
            .setMessage(R.string.item_delete_body)
            .setPositiveButton(R.string.item_delete) { _, _ -> delete() }
            .setNegativeButton(R.string.item_cancel, null)
            .show()
    }

    private fun delete() {
        val i = item ?: return
        val dao = AppDatabase.get(requireContext()).items()
        AppScope.launch {
            dao.deleteItem(i.id)
            PhotoFiles.delete(i.photoPath)
        }
        services.speaker.say(getString(R.string.item_deleted))
        services.navigator.back()
    }
}
```

- [ ] **Step 8: Build, test and check on the phone**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug installDebug`
Expected: `BUILD SUCCESSFUL`.

On the phone:
1. **Add an object:** in Saved → Objects, tap "Add an object". When asked "What should I call it?", say "my bag" / "내 가방" and tap Start.
   - The box nearest the centre is highlighted.
   - The prompts are "Hold the phone still…", "Move the phone a little to the left." and "Now a little to the right." (KO: "물건을 향해 휴대폰을 가만히 들어 주세요." …).
   - After 12 samples the app says "All done. I will remember my bag.", and the Objects tab lists the bag with its label ("backpack" / "배낭").
2. **Add a car:** in the Cars tab, "Add a car" picks only vehicle boxes.
3. **Find the item:** on the item's detail screen, "Find it" beeps towards the saved bag, not towards a bag that looks different.
4. **Full scan (A's screen)** with the bag in view: the bag is announced by its saved name.

- [ ] **Step 9: Commit and open a pull request**

```powershell
git checkout main; git pull; git checkout -b y/Y6-saved-items
git add app/src/main/java/com/nungil/core/items app/src/test/java/com/nungil/core/items app/src/main/java/com/nungil/items app/src/main/java/com/nungil/people/Taggers.kt app/src/main/java/com/nungil/search/TargetMatchers.kt app/src/main/res-y
git commit -m "Save cars and objects and find them by how they look"
git push -u origin y/Y6-saved-items
gh pr create --base main --fill
```

## Task Y7 (optional): Reader for QR codes and printed text

Version 1 reads **Latin text only**, because the Korean ML Kit text model is not in the frozen dependencies (see Hand-offs). Codes are read in any language.

**Files:**
- Create: `app/src/main/java/com/nungil/reader/ReaderPolicy.kt`, test `app/src/test/java/com/nungil/reader/ReaderPolicyTest.kt`
- Replace (bootstrap stub): `app/src/main/java/com/nungil/reader/ReaderFragment.kt`
- Create: `app/src/main/res-y/values/strings_reader.xml`, `values-ko/strings_reader.xml`, `layout/reader_fragment.xml`

**Interfaces:**
- Consumes: `CameraSession` (A; `detect = false`, `keepBitmap = true`), `CameraGate` (Y2), ML Kit `BarcodeScanning` and `TextRecognition` (Latin).
- Produces:
  - `ReaderPolicy(repeatMs = 10000).shouldSpeak(text, nowMs)`
  - `ReaderPolicy.truncateCode(text)` (80 characters), `longestLine(lines)` (at least 3 characters), `codePhrase(text, lang)`, `READ_EVERY_MS = 1500`
  - The reader screen handles `ReadText`, `Start` and `Stop`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/nungil/reader/ReaderPolicyTest.kt`

```kotlin
package com.nungil.reader

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPolicyTest {
    @Test fun codesAreCutAtEightyCharacters() {
        assertEquals(80, ReaderPolicy.truncateCode("x".repeat(200)).length)
        assertEquals("short", ReaderPolicy.truncateCode("short"))
        assertEquals("Code: abc", ReaderPolicy.codePhrase("abc", Lang.EN))
        assertEquals("코드: abc", ReaderPolicy.codePhrase("abc", Lang.KO))
    }

    @Test fun longestLineOfAtLeastThreeCharacters() {
        assertEquals("EXIT ONLY", ReaderPolicy.longestLine(listOf("A", " EXIT ONLY ", "EXIT")))
        assertNull(ReaderPolicy.longestLine(listOf("A", "BC", "  ")))
    }

    @Test fun noRepeatWithinTenSeconds() {
        val p = ReaderPolicy()
        assertTrue(p.shouldSpeak("EXIT", 0))
        assertFalse(p.shouldSpeak("EXIT", 9_999))
        assertTrue(p.shouldSpeak("PUSH", 5_000))
        assertTrue(p.shouldSpeak("EXIT", 10_000))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.nungil.reader.*"`
Expected: FAIL — `Unresolved reference 'ReaderPolicy'`.

- [ ] **Step 3: Implement the policy, strings, layout and screen**

`app/src/main/java/com/nungil/reader/ReaderPolicy.kt`

```kotlin
package com.nungil.reader

import com.nungil.contract.Lang

/** When to read and what to say in the reader (pure Kotlin, unit-tested). Use from one thread. */
class ReaderPolicy(private val repeatMs: Long = REPEAT_MS) {
    private val spokenAt = HashMap<String, Long>()

    /** The same text is never spoken twice within [repeatMs]. */
    fun shouldSpeak(text: String, nowMs: Long): Boolean {
        val last = spokenAt[text]
        if (last != null && nowMs - last < repeatMs) return false
        spokenAt[text] = nowMs
        return true
    }

    companion object {
        const val MAX_CHARS = 80
        const val MIN_LINE_CHARS = 3
        const val READ_EVERY_MS = 1_500L
        const val REPEAT_MS = 10_000L

        fun truncateCode(text: String): String = if (text.length > MAX_CHARS) text.take(MAX_CHARS) else text

        /** The longest line of at least [MIN_LINE_CHARS] characters, or null. */
        fun longestLine(lines: List<String>): String? =
            lines.map { it.trim() }.filter { it.length >= MIN_LINE_CHARS }.maxByOrNull { it.length }

        fun codePhrase(text: String, lang: Lang): String = when (lang) {
            Lang.EN -> "Code: ${truncateCode(text)}"
            Lang.KO -> "코드: ${truncateCode(text)}"
        }
    }
}
```

`app/src/main/res-y/values/strings_reader.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. QR codes, barcodes and printed text. -->
<resources>
    <string name="reader_title">Read text</string>
    <string name="reader_subtitle">Point the camera at a sign, a label or a QR code.</string>
    <string name="reader_last">Last read</string>
    <string name="reader_nothing">Nothing read yet.</string>
    <string name="reader_pause">Pause reading</string>
    <string name="reader_resume">Start reading</string>
</resources>
```

`app/src/main/res-y/values-ko/strings_reader.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. -->
<resources>
    <string name="reader_title">글자 읽기</string>
    <string name="reader_subtitle">표지판, 라벨, QR 코드에 카메라를 비춰 주세요.</string>
    <string name="reader_last">마지막으로 읽은 글자</string>
    <string name="reader_nothing">아직 읽은 글자가 없어요.</string>
    <string name="reader_pause">읽기 멈춤</string>
    <string name="reader_resume">읽기 시작</string>
</resources>
```

`app/src/main/res-y/layout/reader_fragment.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner Y. Reader: headline, camera card, the last text read in big type, Pause/Start at the bottom. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/ngBackground"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingTop="@dimen/ng_gap"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gutter">

    <TextView
        android:id="@+id/reader_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/reader_title"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/reader_subtitle"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:layout_weight="1">

        <com.google.android.material.card.MaterialCardView
            style="@style/Widget.Nungil.Card"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:importantForAccessibility="noHideDescendants"
            app:contentPadding="0dp">

            <androidx.camera.view.PreviewView
                android:id="@+id/reader_preview"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                app:implementationMode="compatible" />
        </com.google.android.material.card.MaterialCardView>

        <include
            android:id="@+id/reader_permission"
            layout="@layout/search_permission_panel" />
    </FrameLayout>

    <com.google.android.material.card.MaterialCardView
        style="@style/Widget.Nungil.Card.Primary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/reader_last"
                android:textAppearance="@style/TextAppearance.Nungil.Caption" />

            <TextView
                android:id="@+id/reader_text"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="@string/reader_nothing"
                android:textAppearance="@style/TextAppearance.Nungil.Title" />
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/reader_button"
        style="@style/Widget.Nungil.Button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/reader_pause" />
</LinearLayout>
```

`app/src/main/java/com/nungil/reader/ReaderFragment.kt`

```kotlin
package com.nungil.reader

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nungil.R
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.databinding.ReaderFragmentBinding
import com.nungil.scan.CameraSession
import com.nungil.search.CameraGate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads QR codes, barcodes and printed text aloud, every 1.5 s, never the same text twice within 10 s.
 * Version 1 reads Latin text only (Korean OCR needs an extra ML Kit model; see the Hand-offs).
 */
class ReaderFragment : Fragment(), VoiceHandler {
    private var _binding: ReaderFragmentBinding? = null
    private val binding get() = _binding!!
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val policy = ReaderPolicy()

    @Volatile
    private var running = true

    @Volatile
    private var lastRunMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ReaderFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        ViewCompat.setAccessibilityHeading(binding.readerTitle, true)
        binding.readerButton.setOnClickListener { if (running) pause() else resume() }
        gate.attach(binding.readerPermission)
        extras = Executors.newSingleThreadExecutor()
    }

    override fun onResume() {
        super.onResume()
        gate.check()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        camera?.stop()
        camera = null
        gate.detach()
        extras?.let { executor ->
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
        extras = null
        main.removeCallbacksAndMessages(null)
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner.close()
        recognizer.close()
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.ReadText, VoiceCommand.Start -> {
            resume()
            true
        }
        VoiceCommand.Stop -> {
            pause()
            true
        }
        else -> false
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(facing = Facing.BACK, detect = false, keepBitmap = true)
        camera = CameraSession(this, binding.readerPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    private fun pause() {
        running = false
        binding.readerButton.setText(R.string.reader_resume)
    }

    private fun resume() {
        running = true
        lastRunMs = 0L
        binding.readerButton.setText(R.string.reader_pause)
    }

    /** Analysis thread. */
    private fun onFrame(frame: VisionFrame) {
        if (!running) return
        val bitmap = frame.bitmap ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRunMs < ReaderPolicy.READ_EVERY_MS) return
        val executor = extras ?: return
        if (!busy.compareAndSet(false, true)) return
        lastRunMs = now
        try {
            executor.execute {
                try {
                    read(bitmap)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    /** Extras thread: a code wins over text in the same frame. */
    private fun read(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val spoken = try {
            val code = Tasks.await(scanner.process(image), 2, TimeUnit.SECONDS)
                .firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
            if (code != null) {
                ReaderPolicy.codePhrase(code, lang)
            } else {
                val text = Tasks.await(recognizer.process(image), 2, TimeUnit.SECONDS)
                ReaderPolicy.longestLine(text.textBlocks.flatMap { block -> block.lines.map { it.text } })
            }
        } catch (e: Exception) {
            Log.i("Nungil", "Reader failed: ${e.message}")
            null
        } ?: return
        if (!policy.shouldSpeak(spoken, SystemClock.elapsedRealtime())) return
        services.speaker.say(spoken)
        main.post { _binding?.readerText?.text = spoken }
    }

    private fun onCameraError(error: Throwable) {
        Log.i("Nungil", "Reader camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }
}
```

- [ ] **Step 4: Build, test and check on the phone**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug installDebug`
Expected: `BUILD SUCCESSFUL`, and ReaderPolicyTest passes 3 tests.

On the phone, open "Reader" from the launcher:
- A QR code is read as "Code: …" / "코드: …".
- An "EXIT" sign is read once, and not again for 10 s.
- "Pause reading" stops the reading.
- The last text read is shown in large type.

- [ ] **Step 5: Commit and open a pull request**

```powershell
git checkout main; git pull; git checkout -b y/Y7-reader
git add app/src/main/java/com/nungil/reader app/src/test/java/com/nungil/reader app/src/main/res-y
git commit -m "Read QR codes and printed text aloud"
git push -u origin y/Y7-reader
gh pr create --base main --fill
```

---

## Hand-offs

**At S1 (h8), to A:**
- `createNameTaggers(context)` keeps its frozen signature. From Y4 it returns a face tagger, only when someone is saved; from Y6 it also returns an item tagger.
- Please:
  - create the taggers off the main thread;
  - run `CameraSession` with `keepBitmap = true` on the scan screen;
  - call `tag(frame)` on every frame, on your extras thread, with a busy flag (the face tagger itself only works on every 3rd frame that has a person box);
  - close every tagger in `onDestroyView`;
  - keep names between frames with StickyNames.
- `NameTag.detectionIndex` indexes `frame.detections`. `TagKind.ITEM` tags rename non-person boxes.

**At S1, to I:**
- Y calls `askForWords` for the search query, person and item names, and renaming.
- Y calls `beeper.pulse(ms)` only when the interval changes by at least 25 ms, `pulse(0)` to stop, and `beeper.stop()` in `onPause`. The `Buzz` values used are `CENTERED`, `TAP`, `DONE` and `ERROR`. `speaker.sayNow` is used once, when a search starts.
- Voice commands the Y screens handle:
  - search: `Start`
  - search camera: `Stop`, `SwitchCamera`
  - saved: `Start` (add)
  - add person / add item: `Start`
  - enrolment: `Start`, `Stop`
  - person / item detail: `Delete` (said twice), `Start` (find)
  - reader: `ReadText`, `Start`, `Stop`
- Please write the help topics for `search`, `search_camera`, `saved`, `add_person`, `enroll`, `add_item`, `item_enroll` and `reader`, and review Y's screens against team plan §4.

**REQ issues Y files:**
1. `REQ → A: add com.google.mlkit:text-recognition-korean:16.0.1 to app/build.gradle`. This is optional; it lets the reader read Korean signs.
2. Y needs no contract change.

**At S2 (h15):** demo items 3 ("Find my bag" / "가방 찾아줘") and 4 (a saved person greeted by name) are ready. Y runs QA items 5 and 6 of the team plan.
