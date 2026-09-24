# I — Interface, Speech and Voice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status (2026-09-25):** all tasks I1–I10 are done and merged. After they ran, the voice input, the
> mic buttons, the coverage ring and the caption bar were changed from device testing. For those parts
> **the code in the repository is the source of truth**, not the code blocks below. The changes are
> listed in "Changes after execution" at the end of this file.

**Goal:** Everything the user sees and hears in Nungil: a Korean-style design system with light, dark and high-contrast themes, the Home/Settings/History/Onboarding screens, one speech queue, haptics and beeps, and English + Korean voice commands with an always-on microphone.

**Architecture:** Pure rules live in `core/ui` and `core/voice` (no Android imports, JVM-tested); Android code in `speech/` (TTS, vibration, tones, recognizer, voice guide), `design/` (components) and `shell/` (activity, screens, prefs). `MainActivity` implements the frozen `AppServices`/`AppNavigator` contract that A and Y call.

**Tech Stack:** Kotlin 2.1.0, Views + view binding, Material 3 (1.12.0), Navigation 2.8.9, AppCompat per-app locales, Android `TextToSpeech` and `SpeechRecognizer`, Pretendard 1.3.9, JUnit 4.

**Spec:** `docs/build-guide.md` (§9.7 speech, §10 voice, §14 accessibility), `docs/hackathon-brief.md` (§8 UI), and the team plan `docs/superpowers/plans/2026-09-24-00-team-plan.md`. **Read the team plan first; its Global Constraints apply to every task here.**

**Verification of this plan:** every code block below was generated from a scratch clone of the Task 0 bootstrap in which the tasks were executed in this order and committed one by one. The final tree ran `gradlew testDebugUnitTest assembleDebug -PskipModels` successfully with 215 unit tests passing (17 from Task 0, 198 from this plan), and `tools/check-ownership.sh I` accepted every changed file.

## Global Constraints (I-specific additions)

- I owns `core/ui`, `core/voice`, `shell/`, `speech/`, `design/`, `res-i/` (except `res-i/navigation/` and `res-i/values/attrs.xml`, which are contract) and `docs/design/`. Never edit anything else.
- Keep every frozen name: `AppServices`, `AppNavigator`, the `open(dest)` mapping and its popUpTo rule, `CoverageRingView.setCoverage`, and every style, text-appearance, dimen and colour-token name from Appendix A. Values may change; names may not.
- Resource name prefixes: `app_`, `nav_title_`, `home_`, `hub_`, `settings_`, `history_`, `onboarding_`, `voice_`, `help_`, `ng_`. Extra string files per feature (`home_strings.xml`, …) are fine and keep commits small.
- Every user-visible string exists in `values/` and `values-ko/` in the same commit; Korean copy is 해요체.
- Tasks run in this order (it matches the team timeline and each task builds on the one before): **I1 → I3 → I4 → I2 → I5 → I8 → I9 → I6 → I7 → I10**.
- Test command for every task: `.\gradlew.bat testDebugUnitTest -PskipModels`; before each PR also `.\gradlew.bat assembleDebug -PskipModels`.
- Commits have no co-author lines.

## Review Focus

1. **Korean TTS voice or Korean offline recognition not installed**: say so once in English, keep working in English, show a visible hint (snackbar with "Install"). Pinned by `VoicePickTest` (I3) and `RecognizerPolicyTest.missingKoreanFallsBackToEnglishOnce` (I9).
2. **Microphone denied, then denied with "don't ask again"**: explain, then say it is blocked and offer app settings; never loop. Pinned by `RecognizerPolicyTest.permissionOutcomes` and `missingPermissionStopsTheLoop` (I9); checked on the phone in I9.
3. **TalkBack on**: the custom voice guide must stay silent (it would talk over TalkBack) and every screen must be navigable by swiping. `VoiceGuide` is gated by `isTalkBackOn()` (I10); checked in the I10 TalkBack pass.
4. **Largest system font and high contrast**: nothing clipped, every text pair still ≥ 4.5:1. Pinned by `ColorContrastTest` (I1, reads the real colour files); layouts use `wrap_content` heights and scroll (I6, I7, I10).
5. **Language switched while the app is speaking**: the activity is recreated, the old speaker is shut down, and the confirmation is spoken in the new language after the new engine starts. Pinned by `ShellPhrasesTest.languageSentenceIsSpokenInTheNewLanguage` (I5) and `SpeechQueueTest` (queued text waits for the engine, I3).

---

### Task I1: Design system: Figma, Pretendard, and a contrast test over the real colours

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/Contrast.kt`
- Create: `app/src/main/res-i/font/pretendard.xml`
- Create: `app/src/main/res-i/font/pretendard_bold.otf`
- Create: `app/src/main/res-i/font/pretendard_regular.otf`
- Create: `app/src/main/res-i/font/pretendard_semibold.otf`
- Modify: `app/src/main/res-i/values/themes.xml`
- Create: `app/src/test/java/com/nungil/core/ui/ColorContrastTest.kt`
- Create: `app/src/test/java/com/nungil/core/ui/ContrastTest.kt`
- Create: `docs/design/ui-guide.md`

**Interfaces:**
- Consumes: Appendix A colours (`res-i/values/colors.xml`, `values-night/colors.xml`) and style names.
- Produces: `com.nungil.core.ui.Contrast` — `parseHex(hex: String): Int`, `luminance(rgb: Int): Double`, `ratio(a: Int, b: Int): Double`, `TEXT_MIN = 4.5`, `NON_TEXT_MIN = 3.0`; `@font/pretendard` (400/600/700), `@font/pretendard_semibold`, `@font/pretendard_bold`; every `TextAppearance.Nungil.*` now uses Pretendard (names unchanged); `docs/design/ui-guide.md` for A and Y.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I1-design-system
```

- [ ] **Step 2: Figma (h0–h1.5, while A bootstraps)**

Create one team Figma file named `Nungil UI` with the pages `0 Tokens`, `1 Screens light`, `2 Screens dark`, `3 Screens high contrast`, `4 Components`.

- `0 Tokens`: colour styles named exactly like the tokens in team plan §4 (`ngBackground`, `ngCard`, … with light, dark and high-contrast values); text styles `Display 30/40 Bold`, `Title 24/32 Bold`, `Headline 20/28 SemiBold`, `Body 18/27 Regular`, `BodyStrong 18/27 Bold`, `Label 18/24 SemiBold`, `Caption 15/21 Regular`, all Pretendard; spacing 8-pt grid (8, 12, 20, 24).
- Screens are 360 × 800 frames (Android compact phone) with 20 px side margins. Use vertical auto-layout with a 12 px gap between items and 24 px between sections. The bottom button is 72 px tall with 20 px corner radius; cards have 24 px corners.
- Draw six screens in each theme: Home, Scan (full scan in progress: ring at 60 %), Search camera (target centred, green "found" state), Saved (people tab), Enroll (pose 2 of 5), Settings.
- `4 Components`: BigCard (icon 40, title, subtitle, chevron; 88 px minimum height), primary/tonal/danger buttons, toggle group, switch row, caption bar, coverage ring.
- Look and feel references, used for inspiration only (no assets copied): KRDS v1.0.0 <https://www.figma.com/community/file/1452915208095182951/krds-v1-0-0> for accessibility patterns, and the Wanted Design System <https://www.figma.com/community/file/1355516515676178246/wanted-design-system> for the visual tone. Do not use the Toss TDS kit (its licence limits it to Apps-in-Toss).

Expected: a share link that I pastes into `docs/design/ui-guide.md` (created in Step 6), and screenshots A and Y can open while building their screens.

- [ ] **Step 3: Download the Pretendard fonts (SIL Open Font License 1.1)**

```powershell
New-Item -ItemType Directory -Force app/src/main/res-i/font | Out-Null
$base = "https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/packages/pretendard/dist/public/static"
Invoke-WebRequest "$base/Pretendard-Regular.otf"  -OutFile app/src/main/res-i/font/pretendard_regular.otf
Invoke-WebRequest "$base/Pretendard-SemiBold.otf" -OutFile app/src/main/res-i/font/pretendard_semibold.otf
Invoke-WebRequest "$base/Pretendard-Bold.otf"     -OutFile app/src/main/res-i/font/pretendard_bold.otf
```

Expected: three files of about 1.5 MB each. They are committed (fonts are resources, not models).

- [ ] **Step 4: Write the failing tests**

Create `app/src/test/java/com/nungil/core/ui/ColorContrastTest.kt`:

```kotlin
package com.nungil.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the real colour resources (unit tests run with the app module as working directory) and checks
 * every token pair the design system promises. Changing a colour so that it fails WCAG fails the build.
 */
class ColorContrastTest {

    private fun colours(path: String): Map<String, Int> {
        val file = File(path)
        assertTrue("missing ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("color")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as Element
            e.getAttribute("name") to Contrast.parseHex(e.textContent.trim())
        }
    }

    private val light = colours("src/main/res-i/values/colors.xml")
    private val dark = colours("src/main/res-i/values-night/colors.xml")

    /** Palette with the "ng_" (or "ng_hc_") prefix removed, e.g. "text", "bg". */
    private fun palette(all: Map<String, Int>, prefix: String): Map<String, Int> =
        all.filterKeys { it.startsWith(prefix) && (prefix == "ng_hc_" || !it.startsWith("ng_hc_")) }
            .mapKeys { it.key.removePrefix(prefix) }

    private fun check(name: String, p: Map<String, Int>) {
        val failures = mutableListOf<String>()
        fun need(fg: String, bg: String, min: Double) {
            val a = p[fg] ?: error("$name palette has no $fg")
            val b = p[bg] ?: error("$name palette has no $bg")
            val r = Contrast.ratio(a, b)
            if (r < min) failures += "$name: $fg on $bg is ${"%.2f".format(r)} (needs $min)"
        }
        for (bg in listOf("bg", "card")) {
            need("text", bg, Contrast.TEXT_MIN)
            need("text_sub", bg, Contrast.TEXT_MIN)
            need("accent_text", bg, Contrast.TEXT_MIN)
            need("success", bg, Contrast.TEXT_MIN)
        }
        need("text", "primary_soft", Contrast.TEXT_MIN)
        need("on_primary", "primary", Contrast.TEXT_MIN)
        need("on_danger", "danger", Contrast.TEXT_MIN)
        need("primary", "bg", Contrast.NON_TEXT_MIN)
        need("danger", "bg", Contrast.NON_TEXT_MIN)
        need("focus", "bg", Contrast.NON_TEXT_MIN)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun lightPalettePasses() = check("light", palette(light, "ng_"))

    @Test fun darkPalettePasses() = check("dark", palette(dark, "ng_"))

    @Test fun highContrastPalettePasses() = check("high contrast", palette(light, "ng_hc_"))

    @Test fun highContrastTextIsAtLeastSeven() {
        val hc = palette(light, "ng_hc_")
        assertTrue(Contrast.ratio(hc.getValue("text"), hc.getValue("bg")) >= 7.0)
        assertTrue(Contrast.ratio(hc.getValue("on_primary"), hc.getValue("primary")) >= 7.0)
    }
}
```

Create `app/src/test/java/com/nungil/core/ui/ContrastTest.kt`:

```kotlin
package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ContrastTest {
    @Test fun parsesRgbAndArgb() {
        assertEquals(0x1B64DA, Contrast.parseHex("#1B64DA"))
        assertEquals(0x1B64DA, Contrast.parseHex("#FF1B64DA"))
        assertEquals(0xFFFFFF, Contrast.parseHex("#fff"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTranslucentColours() {
        Contrast.parseHex("#801B64DA")
    }

    @Test fun blackOnWhiteIs21() = assertEquals(21.0, Contrast.ratio(0x000000, 0xFFFFFF), 0.01)

    @Test fun ratioIsSymmetric() =
        assertEquals(Contrast.ratio(0x1B64DA, 0xFFFFFF), Contrast.ratio(0xFFFFFF, 0x1B64DA), 1e-9)

    @Test fun sameColourIsOne() = assertEquals(1.0, Contrast.ratio(0x777777, 0x777777), 1e-9)

    @Test fun knownPair() {
        // WCAG reference: #767676 on white is the lightest grey that passes 4.5:1.
        assertEquals(4.54, Contrast.ratio(0x767676, 0xFFFFFF), 0.01)
    }
}
```

- [ ] **Step 5: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'Contrast'` (compileDebugUnitTestKotlin FAILED).

- [ ] **Step 6: Implement**

The three `.otf` files come from Step 3 (binary, not shown).

Create `app/src/main/java/com/nungil/core/ui/Contrast.kt`:

```kotlin
package com.nungil.core.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x contrast maths for opaque sRGB colours given as 0xRRGGBB. */
object Contrast {
    /** Normal text (and icons that carry meaning) needs 4.5:1. */
    const val TEXT_MIN = 4.5

    /** Large shapes such as a button against the page need 3:1. */
    const val NON_TEXT_MIN = 3.0

    /** "#RGB", "#RRGGBB" or an opaque "#FFRRGGBB" to 0xRRGGBB. Translucent colours are rejected. */
    fun parseHex(hex: String): Int {
        val h = hex.trim().removePrefix("#")
        return when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("").toInt(16)
            6 -> h.toInt(16)
            8 -> {
                require(h.substring(0, 2).equals("FF", ignoreCase = true)) { "translucent colour $hex" }
                h.substring(2).toInt(16)
            }
            else -> throw IllegalArgumentException("not a colour: $hex")
        }
    }

    fun luminance(rgb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(rgb shr 16 and 0xFF) +
            0.7152 * channel(rgb shr 8 and 0xFF) +
            0.0722 * channel(rgb and 0xFF)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
```

Create `app/src/main/res-i/font/pretendard.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Pretendard 1.3.9 (SIL Open Font License 1.1). textStyle="bold" picks the 700 weight. -->
<font-family xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <font
        android:font="@font/pretendard_regular"
        android:fontStyle="normal"
        android:fontWeight="400"
        app:font="@font/pretendard_regular"
        app:fontStyle="normal"
        app:fontWeight="400" />
    <font
        android:font="@font/pretendard_semibold"
        android:fontStyle="normal"
        android:fontWeight="600"
        app:font="@font/pretendard_semibold"
        app:fontStyle="normal"
        app:fontWeight="600" />
    <font
        android:font="@font/pretendard_bold"
        android:fontStyle="normal"
        android:fontWeight="700"
        app:font="@font/pretendard_bold"
        app:fontStyle="normal"
        app:fontWeight="700" />
</font-family>
```

Replace the whole file `app/src/main/res-i/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Style NAMES are a frozen contract for A and Y; I may tune every VALUE. -->
<resources>

    <style name="Theme.Nungil" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="ngBackground">@color/ng_bg</item>
        <item name="ngCard">@color/ng_card</item>
        <item name="ngText">@color/ng_text</item>
        <item name="ngTextSub">@color/ng_text_sub</item>
        <item name="ngPrimary">@color/ng_primary</item>
        <item name="ngOnPrimary">@color/ng_on_primary</item>
        <item name="ngPrimarySoft">@color/ng_primary_soft</item>
        <item name="ngAccentText">@color/ng_accent_text</item>
        <item name="ngDanger">@color/ng_danger</item>
        <item name="ngOnDanger">@color/ng_on_danger</item>
        <item name="ngSuccess">@color/ng_success</item>
        <item name="ngLine">@color/ng_line</item>
        <item name="ngFocus">@color/ng_focus</item>
        <item name="ngCardStrokeWidth">0dp</item>

        <item name="colorPrimary">@color/ng_primary</item>
        <item name="colorOnPrimary">@color/ng_on_primary</item>
        <item name="colorSecondary">@color/ng_primary</item>
        <item name="colorOnSecondary">@color/ng_on_primary</item>
        <item name="colorError">@color/ng_danger</item>
        <item name="colorOnError">@color/ng_on_danger</item>
        <item name="android:colorBackground">@color/ng_bg</item>
        <item name="colorOnBackground">@color/ng_text</item>
        <item name="colorSurface">@color/ng_card</item>
        <item name="colorOnSurface">@color/ng_text</item>
        <item name="colorOnSurfaceVariant">@color/ng_text_sub</item>
        <item name="colorOutline">@color/ng_line</item>
        <item name="android:windowBackground">@color/ng_bg</item>

        <item name="materialButtonStyle">@style/Widget.Nungil.Button</item>
        <item name="materialCardViewStyle">@style/Widget.Nungil.Card</item>
        <item name="android:textColorPrimary">@color/ng_text</item>
        <item name="android:textColorSecondary">@color/ng_text_sub</item>
    </style>

    <!-- Black, white and yellow. Chosen in Settings; overrides only the tokens. -->
    <style name="Theme.Nungil.HighContrast">
        <item name="ngBackground">@color/ng_hc_bg</item>
        <item name="ngCard">@color/ng_hc_card</item>
        <item name="ngText">@color/ng_hc_text</item>
        <item name="ngTextSub">@color/ng_hc_text_sub</item>
        <item name="ngPrimary">@color/ng_hc_primary</item>
        <item name="ngOnPrimary">@color/ng_hc_on_primary</item>
        <item name="ngPrimarySoft">@color/ng_hc_primary_soft</item>
        <item name="ngAccentText">@color/ng_hc_accent_text</item>
        <item name="ngDanger">@color/ng_hc_danger</item>
        <item name="ngOnDanger">@color/ng_hc_on_danger</item>
        <item name="ngSuccess">@color/ng_hc_success</item>
        <item name="ngLine">@color/ng_hc_line</item>
        <item name="ngFocus">@color/ng_hc_focus</item>
        <item name="ngCardStrokeWidth">2dp</item>

        <item name="colorPrimary">@color/ng_hc_primary</item>
        <item name="colorOnPrimary">@color/ng_hc_on_primary</item>
        <item name="colorSecondary">@color/ng_hc_primary</item>
        <item name="colorOnSecondary">@color/ng_hc_on_primary</item>
        <item name="colorError">@color/ng_hc_danger</item>
        <item name="colorOnError">@color/ng_hc_on_danger</item>
        <item name="android:colorBackground">@color/ng_hc_bg</item>
        <item name="colorOnBackground">@color/ng_hc_text</item>
        <item name="colorSurface">@color/ng_hc_card</item>
        <item name="colorOnSurface">@color/ng_hc_text</item>
        <item name="colorOnSurfaceVariant">@color/ng_hc_text_sub</item>
        <item name="colorOutline">@color/ng_hc_line</item>
        <item name="android:windowBackground">@color/ng_hc_bg</item>
        <item name="android:textColorPrimary">@color/ng_hc_text</item>
        <item name="android:textColorSecondary">@color/ng_hc_text_sub</item>
    </style>

    <!-- Type scale: Pretendard, large and bold headlines, generous line height, slightly tight Korean tracking. -->
    <style name="TextAppearance.Nungil" parent="TextAppearance.Material3.BodyLarge">
        <item name="android:fontFamily">@font/pretendard</item>
        <item name="fontFamily">@font/pretendard</item>
        <item name="android:textColor">?attr/ngText</item>
        <item name="android:letterSpacing">-0.01</item>
    </style>

    <style name="TextAppearance.Nungil.Display">
        <item name="android:textSize">30sp</item>
        <item name="lineHeight">40sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">-0.02</item>
    </style>

    <style name="TextAppearance.Nungil.Title">
        <item name="android:textSize">24sp</item>
        <item name="lineHeight">32sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">-0.02</item>
    </style>

    <style name="TextAppearance.Nungil.Headline">
        <item name="android:textSize">20sp</item>
        <item name="lineHeight">28sp</item>
        <item name="android:fontFamily">@font/pretendard_semibold</item>
        <item name="fontFamily">@font/pretendard_semibold</item>
    </style>

    <style name="TextAppearance.Nungil.Body">
        <item name="android:textSize">18sp</item>
        <item name="lineHeight">27sp</item>
    </style>

    <style name="TextAppearance.Nungil.BodyStrong">
        <item name="android:textSize">18sp</item>
        <item name="lineHeight">27sp</item>
        <item name="android:textStyle">bold</item>
    </style>

    <style name="TextAppearance.Nungil.Label">
        <item name="android:textSize">18sp</item>
        <item name="lineHeight">24sp</item>
        <item name="android:fontFamily">@font/pretendard_semibold</item>
        <item name="fontFamily">@font/pretendard_semibold</item>
    </style>

    <style name="TextAppearance.Nungil.Caption">
        <item name="android:textSize">15sp</item>
        <item name="lineHeight">21sp</item>
        <item name="android:textColor">?attr/ngTextSub</item>
    </style>

    <!-- Buttons: 72 dp tall, 20 dp corners, full width at the bottom of the screen. -->
    <style name="Widget.Nungil.Button" parent="Widget.Material3.Button">
        <item name="android:minHeight">@dimen/ng_button_height</item>
        <item name="android:insetTop">0dp</item>
        <item name="android:insetBottom">0dp</item>
        <item name="cornerRadius">@dimen/ng_radius_button</item>
        <item name="backgroundTint">?attr/ngPrimary</item>
        <item name="android:textColor">?attr/ngOnPrimary</item>
        <item name="iconTint">?attr/ngOnPrimary</item>
        <item name="iconSize">28dp</item>
        <item name="iconGravity">textStart</item>
        <item name="android:textAppearance">@style/TextAppearance.Nungil.Label</item>
    </style>

    <style name="Widget.Nungil.Button.Tonal">
        <item name="backgroundTint">?attr/ngPrimarySoft</item>
        <item name="android:textColor">?attr/ngText</item>
        <item name="iconTint">?attr/ngText</item>
        <item name="strokeColor">?attr/ngLine</item>
        <item name="strokeWidth">?attr/ngCardStrokeWidth</item>
    </style>

    <style name="Widget.Nungil.Button.Danger">
        <item name="backgroundTint">?attr/ngDanger</item>
        <item name="android:textColor">?attr/ngOnDanger</item>
        <item name="iconTint">?attr/ngOnDanger</item>
    </style>

    <style name="Widget.Nungil.Button.Text" parent="Widget.Material3.Button.TextButton">
        <item name="android:minHeight">@dimen/ng_touch</item>
        <item name="android:textColor">?attr/ngAccentText</item>
        <item name="android:textAppearance">@style/TextAppearance.Nungil.Label</item>
    </style>

    <!-- Cards: flat, 24 dp corners; a 2 dp outline only in high contrast. -->
    <style name="Widget.Nungil.Card" parent="Widget.Material3.CardView.Filled">
        <item name="cardBackgroundColor">?attr/ngCard</item>
        <item name="cardCornerRadius">@dimen/ng_radius_card</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeColor">?attr/ngLine</item>
        <item name="strokeWidth">?attr/ngCardStrokeWidth</item>
        <item name="contentPadding">@dimen/ng_gutter</item>
    </style>

    <style name="Widget.Nungil.Card.Primary">
        <item name="cardBackgroundColor">?attr/ngPrimarySoft</item>
    </style>
</resources>
```

Create `docs/design/ui-guide.md`:

````markdown
# Nungil UI guide (owner: I)

How every screen in Nungil looks and behaves. A and Y follow this guide on their own screens; I checks
it in pull-request reviews and never edits their files. Team Figma file: add its link here when it is
created (I1 step 1).

## 1. The one screen pattern

Every screen is built the same way. This is what makes the app feel like a Korean app: one idea per
screen, a big friendly headline, soft cards on a light grey page, and one button within thumb reach.

```
┌──────────────────────────────────┐
│ ←  small title (toolbar, from I) │
│                                  │
│  Headline (Title or Display)     │  TextAppearance.Nungil.Title, top-left, accessibility heading
│  One or two lines of help        │  TextAppearance.Nungil.Body, ?attr/ngTextSub
│                                  │
│  ╭──────────────────────────────╮│  Widget.Nungil.Card (24 dp corners, flat)
│  │  the content                 ││
│  ╰──────────────────────────────╯│
│                                  │
│  ┌──────────────────────────────┐│  Widget.Nungil.Button: 72 dp, full width, 20 dp side margins
│  │        the main action       ││
│  └──────────────────────────────┘│
│  caption bar (owned by I)        │  never add your own caption
└──────────────────────────────────┘
```

Layout skeleton to copy (replace ids, strings and the content):

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingEnd="@dimen/ng_gutter"
    android:paddingBottom="@dimen/ng_gap">

    <TextView
        android:id="@+id/headline"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/scan_headline"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/scan_subtitle"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:layout_weight="1"
        app:contentPadding="0dp">
        <!-- content -->
    </com.google.android.material.card.MaterialCardView>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/main_action"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:text="@string/scan_start" />
</LinearLayout>
```

In Kotlin, mark the headline once: `binding.headline.setHeading()` (from `com.nungil.design`).

## 2. Tokens: use only these

| Need | Use | Never |
|---|---|---|
| page background | nothing (the theme sets it) or `?attr/ngBackground` | `#F4F6F8` |
| card | `Widget.Nungil.Card` (default card style) or `Widget.Nungil.Card.Primary` for the one highlighted card | elevation, shadows |
| main text / secondary text | `?attr/ngText` / `?attr/ngTextSub` | black, grey hex values |
| main button | `MaterialButton` (default style is `Widget.Nungil.Button`) | a second filled button on the same screen |
| other buttons | `style="@style/Widget.Nungil.Button.Tonal"`, `.Text`, `.Danger` (Stop, Delete) | borderless tiny buttons |
| success / found | `?attr/ngSuccess` | green hex values |
| the search target box, focus | `?attr/ngFocus` | |
| spacing | `@dimen/ng_gutter` 20 dp, `ng_gap` 12 dp, `ng_gap_large` 24 dp | odd numbers such as 7 dp |
| touch size | at least `@dimen/ng_touch` 64 dp | anything under 48 dp |
| text | `TextAppearance.Nungil.Display / Title / Headline / Body / BodyStrong / Label / Caption` | `textSize` in a layout |

The three themes (light, dark, high contrast) swap every token, so a screen that uses only tokens
works in all three with no extra work. `ColorContrastTest` proves every text pair is at least 4.5:1.

## 3. Components I provides

| Component | How to use |
|---|---|
| `com.nungil.design.CoverageRingView` | `ring.setCoverage(percent, bins)` on the main thread; 36 bins, index 0 = start heading |
| `com.nungil.design.BigCardView` | `app:ngIcon`, `app:ngTitle`, `app:ngSubtitle`, `app:ngEmphasis`; a large tappable card (Home) |
| `View.setHeading()` | marks the screen headline for TalkBack |
| `View.announce(text)` | speaks through TalkBack only; use `services().speaker` for everything else |
| `Context.resolveColorAttr(R.attr.ngPrimary)` | a token colour in Kotlin (for Canvas drawing) |
| `Context.isTalkBackOn()` | true while TalkBack (touch exploration) is on |
| `Context.openAppSettings()` | opens this app's system settings page (for a permanently denied permission) |
| `R.drawable.ng_ic_*` | icons: `ng_ic_mic`, `ng_ic_mic_off`, `ng_ic_search`, `ng_ic_explore`, `ng_ic_bolt`, `ng_ic_walk`, `ng_ic_saved`, `ng_ic_history`, `ng_ic_tune`, `ng_ic_person`, `ng_ic_chevron` |

## 4. Camera screens

- Put the `PreviewView` and `OverlayView` inside a `MaterialCardView` with 24 dp corners
  (`app:contentPadding="0dp"`), never full-bleed behind text.
- Stop is `Widget.Nungil.Button.Danger`, the same size and place as Start (swap the text and style).
- When the camera permission is missing, show the headline "카메라 권한이 필요해요" / "Camera permission
  is needed", one line why, and a tonal button that calls `openAppSettings()` if the permission is
  permanently denied.

## 5. Words

- Korean copy is 해요체: "찾았어요", "다시 해 볼까요?", "시작하기". Never "찾았음" or "찾았습니다".
- English copy is short, plain and friendly: "Found it.", "Try again?".
- Use the glossary in the team plan §5 for every feature name.
- No emoji in strings: TalkBack reads them out ("waving hand sign").
- Every icon-only control gets a `contentDescription` string in both languages.

## 6. Accessibility checklist for every screen

1. One heading (`setHeading()`), the first thing TalkBack reads after the toolbar.
2. Focus order top to bottom; the main button is last.
3. Every control at least 64 dp tall, every icon-only control labelled.
4. Nothing important only in colour: "found" also changes text or sound.
5. The screen still works at the largest system font size (text wraps, nothing clipped) and in the
   high-contrast theme.
6. Speak through `services().speaker`, not `announceForAccessibility`, so users without TalkBack hear it.

## 7. Figma

Pages: `0 Tokens`, `1 Screens light`, `2 Screens dark`, `3 Screens high contrast`, `4 Components`.
Frames are 360 × 800 (Android compact phone), 8-point grid, 20 px side margins, auto-layout vertical with
12 px gaps (24 px between sections). Screens: Home, Scan (full scan in progress with the ring), Search
camera (target centred), Saved (people tab), Enroll (pose 2 of 5), Settings. References used only for
look and feel: KRDS v1.0.0 and the Wanted Design System (Montage). The Toss TDS kit is not used.
````

- [ ] **Step 7: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `ContrastTest` 6 and `ColorContrastTest` 4 pass. To see the guard work, change `ng_text_sub` to `#8B95A1` and run again: `lightPalettePasses` fails with the exact pair and ratio. Undo the change.

- [ ] **Step 8: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

Install and open Home, Settings and any stub screen in English and Korean: all text is Pretendard (Korean glyphs are the same family as Latin, no fallback font), titles are bold, nothing is clipped at the largest system font size.

- [ ] **Step 9: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/ui/Contrast.kt app/src/main/res-i/font/pretendard.xml app/src/main/res-i/font/pretendard_bold.otf app/src/main/res-i/font/pretendard_regular.otf app/src/main/res-i/font/pretendard_semibold.otf app/src/main/res-i/values/themes.xml app/src/test/java/com/nungil/core/ui/ColorContrastTest.kt app/src/test/java/com/nungil/core/ui/ContrastTest.kt docs/design/ui-guide.md
git commit -m "Use Pretendard, prove every colour pair meets WCAG, add the UI guide"
git push -u origin i/I1-design-system
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I3: Speech output: one queue, Korean voice fallback

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/SpeechQueue.kt`
- Create: `app/src/main/java/com/nungil/core/ui/VoicePick.kt`
- Delete: `app/src/main/java/com/nungil/shell/BootstrapSpeaker.kt`
- Modify: `app/src/main/java/com/nungil/shell/MainActivity.kt`
- Create: `app/src/main/java/com/nungil/speech/TtsSpeaker.kt`
- Create: `app/src/test/java/com/nungil/core/ui/SpeechQueueTest.kt`
- Create: `app/src/test/java/com/nungil/core/ui/VoicePickTest.kt`

**Interfaces:**
- Consumes: `Speaker` (contract), `Lang`.
- Produces: `com.nungil.core.ui.SpeechQueue(clock: () -> Long)` with `add`, `now`, `final`, `next`, `done`, `finalFinished`, `clear`, `lastSpoken`, `GAP_MS = 1500`, `POLL_MS = 250`, `MAX_PENDING = 3`, `SHUTDOWN_SAFETY_MS = 15000`; `VoicePick.choose(wanted: Lang, support: LangSupport): VoiceChoice`; `com.nungil.speech.TtsSpeaker(context, wanted, onCaption, onVoiceChoice) : Speaker` with `applyLanguage()`, `repeatLast(): Boolean`, `lastSpoken`, `shutdown()`; `MainActivity.lang` = the voice actually used (English when the Korean voice is missing), `MainActivity.uiLang` = the chosen UI language.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I3-speech
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/nungil/core/ui/SpeechQueueTest.kt`:

```kotlin
package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechQueueTest {
    private var t = 0L
    private val q = SpeechQueue { t }

    @Test fun firstPhraseStartsAtOnce() {
        q.add("a chair")
        assertEquals("a chair", q.next())
        assertTrue(q.isSpeaking)
    }

    @Test fun nothingNewWhileSpeaking() {
        q.add("one")
        q.add("two")
        q.next()
        assertNull(q.next())
    }

    @Test fun keepsAGapOfOneAndAHalfSeconds() {
        q.add("one")
        q.add("two")
        q.next()
        t = 1_000
        q.done()
        t = 2_000
        assertNull(q.next())
        t = 2_500
        assertEquals("two", q.next())
    }

    @Test fun dropsTheStalestWhenMoreThanThreeWait() {
        listOf("1", "2", "3", "4", "5").forEach(q::add)
        assertEquals(SpeechQueue.MAX_PENDING, q.pendingCount)
        assertEquals("3", q.next())
    }

    @Test fun nowClearsTheQueueAndSpeaksImmediately() {
        q.add("old")
        assertEquals("urgent", q.now("urgent"))
        assertEquals(0, q.pendingCount)
        assertEquals("urgent", q.lastSpoken)
    }

    @Test fun blankTextIsIgnored() {
        q.add("   ")
        assertNull(q.now(""))
        assertEquals(0, q.pendingCount)
        assertNull(q.next())
    }

    @Test fun finalBlocksTheQueueUntilItEnds() {
        q.add("before")
        assertEquals("summary", q.final("summary"))
        assertEquals(0, q.pendingCount)
        q.add("after")
        assertNull(q.next())
        assertFalse(q.finalFinished())
        t = 4_000
        q.done()
        assertTrue(q.finalFinished())
        assertFalse(q.finalFinished())
        t = 5_500
        assertEquals("after", q.next())
    }

    @Test fun finalGivesUpAfterTheSafetyLimit() {
        q.final("summary")
        t = SpeechQueue.SHUTDOWN_SAFETY_MS - 1
        assertFalse(q.finalFinished())
        t = SpeechQueue.SHUTDOWN_SAFETY_MS
        assertTrue(q.finalFinished())
        assertFalse(q.isSpeaking)
    }

    @Test fun aStuckEngineDoesNotBlockTheQueueForever() {
        q.add("one")
        q.add("two")
        q.next()
        t = SpeechQueue.SHUTDOWN_SAFETY_MS
        assertNull(q.next())
        t += SpeechQueue.GAP_MS
        assertEquals("two", q.next())
    }

    @Test fun remembersTheLastSentenceForRepeat() {
        assertNull(q.lastSpoken)
        q.add("a black laptop on your right")
        q.next()
        assertEquals("a black laptop on your right", q.lastSpoken)
    }

    @Test fun clearForgetsEverythingButTheLastSentence() {
        q.add("one")
        q.next()
        q.add("two")
        q.clear()
        assertFalse(q.isSpeaking)
        assertEquals(0, q.pendingCount)
        assertEquals("one", q.lastSpoken)
    }

    @Test fun doneWithoutSpeakingIsHarmless() {
        q.done()
        q.add("one")
        assertEquals("one", q.next())
    }
}
```

Create `app/src/test/java/com/nungil/core/ui/VoicePickTest.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePickTest {
    @Test fun mapsTextToSpeechResultCodes() {
        assertEquals(LangSupport.AVAILABLE, VoicePick.support(2))
        assertEquals(LangSupport.AVAILABLE, VoicePick.support(1))
        assertEquals(LangSupport.AVAILABLE, VoicePick.support(0))
        assertEquals(LangSupport.MISSING_DATA, VoicePick.support(-1))
        assertEquals(LangSupport.NOT_SUPPORTED, VoicePick.support(-2))
    }

    @Test fun koreanWhenInstalled() {
        val choice = VoicePick.choose(Lang.KO, LangSupport.AVAILABLE)
        assertEquals(Lang.KO, choice.speak)
        assertNull(choice.notice)
    }

    @Test fun missingKoreanFallsBackToEnglishWithANotice() {
        for (support in listOf(LangSupport.MISSING_DATA, LangSupport.NOT_SUPPORTED)) {
            val choice = VoicePick.choose(Lang.KO, support)
            assertEquals(Lang.EN, choice.speak)
            assertNotNull(choice.notice)
        }
    }

    @Test fun theNoticeIsEnglishBecauseItIsSpokenByTheEnglishVoice() {
        val notice = VoicePick.choose(Lang.KO, LangSupport.MISSING_DATA).notice!!
        assertEquals(notice, notice.filter { it.code < 0x1100 })
    }

    @Test fun englishNeverProducesANotice() {
        assertNull(VoicePick.choose(Lang.EN, LangSupport.MISSING_DATA).notice)
        assertEquals(Lang.EN, VoicePick.choose(Lang.EN, LangSupport.NOT_SUPPORTED).speak)
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'SpeechQueue'` and `'VoicePick'`.

- [ ] **Step 4: Implement**

Create `app/src/main/java/com/nungil/core/ui/SpeechQueue.kt`:

```kotlin
package com.nungil.core.ui

/**
 * What to say next, and when. Pure logic behind TtsSpeaker, driven by a clock and polled every [POLL_MS].
 * Not thread-safe: TtsSpeaker only touches it on the main thread.
 *
 * - [add] queues a phrase; when more than [MAX_PENDING] wait, the stalest are dropped.
 * - [next] hands out the next phrase once the previous one ended at least [GAP_MS] ago.
 * - [now] drops the queue and speaks at once; [final] does the same and holds the queue until it ends.
 * - A phrase the engine never finishes is given up after [SHUTDOWN_SAFETY_MS].
 */
class SpeechQueue(private val clock: () -> Long) {
    private val pending = ArrayDeque<String>()
    private var speakingSince: Long? = null
    private var lastEndAt: Long? = null
    private var finalSince: Long? = null

    /** The last phrase handed out; what "repeat" says again. */
    var lastSpoken: String? = null
        private set

    val pendingCount: Int get() = pending.size
    val isSpeaking: Boolean get() = speakingSince != null

    fun add(text: String) {
        val t = clean(text) ?: return
        pending.addLast(t)
        while (pending.size > MAX_PENDING) pending.removeFirst()
    }

    /** Clears the queue; returns the phrase to speak immediately (interrupting), or null if blank. */
    fun now(text: String): String? {
        val t = clean(text) ?: return null
        pending.clear()
        start(t)
        return t
    }

    /** Like [now], and nothing else is handed out until [finalFinished] has returned true. */
    fun final(text: String): String? {
        val t = now(text) ?: return null
        finalSince = clock()
        return t
    }

    fun next(): String? {
        val since = speakingSince
        if (since != null) {
            if (clock() - since < SHUTDOWN_SAFETY_MS) return null
            done()
        }
        if (finalSince != null) return null
        val end = lastEndAt
        if (end != null && clock() - end < GAP_MS) return null
        val t = pending.removeFirstOrNull() ?: return null
        start(t)
        return t
    }

    /** The engine finished (or failed) the current phrase. */
    fun done() {
        if (speakingSince == null) return
        speakingSince = null
        lastEndAt = clock()
    }

    /** True exactly once after the final phrase ended or ran past the safety limit. */
    fun finalFinished(): Boolean {
        val since = finalSince ?: return false
        if (speakingSince != null && clock() - since < SHUTDOWN_SAFETY_MS) return false
        finalSince = null
        if (speakingSince != null) {
            speakingSince = null
            lastEndAt = clock()
        }
        return true
    }

    fun clear() {
        pending.clear()
        speakingSince = null
        finalSince = null
    }

    private fun start(text: String) {
        speakingSince = clock()
        lastSpoken = text
    }

    private fun clean(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

    companion object {
        const val GAP_MS = 1_500L
        const val POLL_MS = 250L
        const val MAX_PENDING = 3
        const val SHUTDOWN_SAFETY_MS = 15_000L
    }
}
```

Create `app/src/main/java/com/nungil/core/ui/VoicePick.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang

enum class LangSupport { AVAILABLE, MISSING_DATA, NOT_SUPPORTED }

/** The voice actually used, and a sentence to say when it is not the one the user chose. */
data class VoiceChoice(val speak: Lang, val notice: String?)

/** Picks the TTS voice. A missing Korean voice falls back to English and says so, never to silence. */
object VoicePick {
    // TextToSpeech.isLanguageAvailable() results, copied so this stays pure Kotlin.
    private const val LANG_AVAILABLE = 0
    private const val LANG_MISSING_DATA = -1

    const val KOREAN_VOICE_MISSING =
        "The Korean voice is not installed, so I will speak English. " +
            "To hear Korean, install the Korean voice in the phone's text-to-speech settings."

    fun support(result: Int): LangSupport = when {
        result >= LANG_AVAILABLE -> LangSupport.AVAILABLE
        result == LANG_MISSING_DATA -> LangSupport.MISSING_DATA
        else -> LangSupport.NOT_SUPPORTED
    }

    fun choose(wanted: Lang, support: LangSupport): VoiceChoice =
        if (support == LangSupport.AVAILABLE || wanted == Lang.EN) {
            VoiceChoice(wanted, null)
        } else {
            VoiceChoice(Lang.EN, KOREAN_VOICE_MISSING)
        }
}
```

Delete `app/src/main/java/com/nungil/shell/BootstrapSpeaker.kt`:

```powershell
git rm app/src/main/java/com/nungil/shell/BootstrapSpeaker.kt
```

Replace the whole file `app/src/main/java/com/nungil/shell/MainActivity.kt`:

```kotlin
package com.nungil.shell

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.app.AppNavigator
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.Beeper
import com.nungil.contract.app.Haptics
import com.nungil.contract.app.Speaker
import com.nungil.core.ui.VoiceChoice
import com.nungil.databinding.ActivityMainBinding
import com.nungil.items.AddItemFragmentArgs
import com.nungil.people.AddPersonFragmentArgs
import com.nungil.saved.SavedFragmentArgs
import com.nungil.scan.ScanFragmentArgs
import com.nungil.search.SearchFragmentArgs
import com.nungil.speech.TtsSpeaker
import java.util.Locale

/**
 * Owner I. Single activity: toolbar, nav host, spoken-caption bar, and the AppServices every screen
 * uses. Haptics, beeps and voice input arrive in tasks I4, I5 and I9.
 */
class MainActivity : AppCompatActivity(), AppServices, AppNavigator {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var tts: TtsSpeaker
    private var voiceChoice: VoiceChoice? = null

    /** The language the user chose for the screens. */
    val uiLang: Lang
        get() {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val tag = if (appLocales.isEmpty) Locale.getDefault().language else appLocales[0]?.language
            return Lang.fromTag(tag)
        }

    /** The language of spoken sentences: the UI language, unless its voice is missing (then English). */
    override val lang: Lang
        get() = voiceChoice?.speak ?: uiLang
    override val speaker: Speaker get() = tts
    override val haptics: Haptics = object : Haptics {
        override fun buzz(kind: Buzz) = Unit
    }
    override val beeper: Beeper = object : Beeper {
        override fun beep() = Unit
        override fun pulse(intervalMs: Long) = Unit
        override fun stop() = Unit
    }
    override val navigator: AppNavigator get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 35 draws edge-to-edge on Android 15: keep content out from under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        navController = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        binding.toolbar.setupWithNavController(navController, AppBarConfiguration(setOf(R.id.home)))
        tts = TtsSpeaker(
            context = this,
            wanted = { uiLang },
            onCaption = { text -> binding.caption.text = text },
            onVoiceChoice = { choice -> voiceChoice = choice },
        )
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }

    override fun askForWords(owner: LifecycleOwner, onText: (String) -> Unit) = Unit

    override fun open(dest: Dest) {
        when (dest) {
            Dest.Home -> navController.popBackStack(R.id.home, false)
            Dest.ScanHub -> go(R.id.scan_hub)
            is Dest.Scan -> go(R.id.scan, ScanFragmentArgs(dest.mode).toBundle())
            Dest.Walk -> go(R.id.walk)
            is Dest.Search -> go(R.id.search, SearchFragmentArgs(dest.query).toBundle())
            is Dest.Saved -> go(R.id.saved, SavedFragmentArgs(dest.tab?.ordinal ?: -1).toBundle())
            Dest.Settings -> go(R.id.settings)
            Dest.History -> go(R.id.history)
            is Dest.AddPerson -> go(R.id.add_person, AddPersonFragmentArgs(dest.name).toBundle())
            is Dest.AddItem -> go(R.id.add_item, AddItemFragmentArgs(dest.kind, dest.name).toBundle())
            Dest.Reader -> go(R.id.reader)
            Dest.Onboarding -> go(R.id.onboarding)
        }
    }

    override fun back() {
        if (!navController.popBackStack()) finish()
    }

    /** popUpTo the same destination, so "full scan" said twice never stacks two scan screens. */
    private fun go(id: Int, args: Bundle? = null) {
        val options = NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(id, true).build()
        navController.navigate(id, args, options)
    }
}
```

Create `app/src/main/java/com/nungil/speech/TtsSpeaker.kt`:

```kotlin
package com.nungil.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nungil.contract.Lang
import com.nungil.contract.app.Speaker
import com.nungil.core.ui.SpeechQueue
import com.nungil.core.ui.VoiceChoice
import com.nungil.core.ui.VoicePick
import java.util.Locale

/**
 * The app's only voice. Wraps TextToSpeech with [SpeechQueue] so phrases never talk over each other.
 * Safe to call from any thread; all work happens on the main thread.
 *
 * @param wanted the language the user chose (UI language).
 * @param onCaption every phrase, as it starts, for the caption bar.
 * @param onVoiceChoice the voice actually used; a missing Korean voice reports English plus a notice.
 */
class TtsSpeaker(
    context: Context,
    private val wanted: () -> Lang,
    private val onCaption: (String) -> Unit,
    private val onVoiceChoice: (VoiceChoice) -> Unit,
) : Speaker, TextToSpeech.OnInitListener {

    private enum class State { STARTING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    private val queue = SpeechQueue { SystemClock.elapsedRealtime() }
    private val tts = TextToSpeech(context.applicationContext, this)
    private var state = State.STARTING
    private var currentId: String? = null
    private var counter = 0
    private var finalCallback: (() -> Unit)? = null
    private var closed = false

    /** The last phrase spoken, for "repeat". Main thread. */
    val lastSpoken: String? get() = queue.lastSpoken

    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            pump()
            if (queue.finalFinished()) finishFinal()
            main.postDelayed(this, SpeechQueue.POLL_MS)
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    init {
        main.post(poll)
    }

    override fun onInit(status: Int) {
        main.post {
            if (closed) return@post
            if (status != TextToSpeech.SUCCESS) {
                state = State.FAILED
                return@post
            }
            tts.setOnUtteranceProgressListener(progress)
            applyLanguage()
            state = State.READY
        }
    }

    /** Re-reads [wanted] and picks the voice; call after the language changes. Main thread. */
    fun applyLanguage() {
        val want = wanted()
        val result = runCatching { tts.isLanguageAvailable(Locale.forLanguageTag(want.speechTag)) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val choice = VoicePick.choose(want, VoicePick.support(result))
        tts.language = Locale.forLanguageTag(choice.speak.speechTag)
        onVoiceChoice(choice)
        choice.notice?.let { queue.add(it) }
    }

    override fun say(text: String) = onMain {
        queue.add(text)
        pump()
    }

    override fun sayNow(text: String) = onMain {
        if (state == State.STARTING) {
            queue.clear()
            queue.add(text)
        } else {
            queue.now(text)?.let { speak(it, TextToSpeech.QUEUE_FLUSH) }
        }
    }

    override fun sayFinal(text: String, onDone: () -> Unit) = onMain {
        finishFinal()
        if (state == State.STARTING) {
            // The engine is still starting: the sentence is spoken as soon as it is ready.
            queue.clear()
            queue.add(text)
            main.post(onDone)
            return@onMain
        }
        val t = queue.final(text)
        if (t == null) {
            onDone()
        } else {
            finalCallback = onDone
            speak(t, TextToSpeech.QUEUE_FLUSH)
        }
    }

    override fun stop() = onMain {
        queue.clear()
        currentId = null
        runCatching { tts.stop() }
        finishFinal()
    }

    /** Says the last phrase again; false when nothing has been said yet. Main thread. */
    fun repeatLast(): Boolean {
        val t = queue.lastSpoken ?: return false
        sayNow(t)
        return true
    }

    fun shutdown() {
        closed = true
        main.removeCallbacks(poll)
        finishFinal()
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    private fun pump() {
        if (state == State.STARTING) return
        queue.next()?.let { speak(it, TextToSpeech.QUEUE_ADD) }
    }

    private fun speak(text: String, mode: Int) {
        onCaption(text)
        if (state != State.READY) {
            // No working engine: the caption still shows the sentence.
            queue.done()
            return
        }
        val id = "nungil-${counter++}"
        currentId = id
        if (tts.speak(text, mode, null, id) != TextToSpeech.SUCCESS) {
            currentId = null
            queue.done()
        }
    }

    private fun finished(utteranceId: String?) {
        main.post {
            if (utteranceId != null && utteranceId == currentId) {
                currentId = null
                queue.done()
            }
        }
    }

    private fun finishFinal() {
        val callback = finalCallback ?: return
        finalCallback = null
        callback()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
```

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `SpeechQueueTest` 12 and `VoicePickTest` 5 pass.

- [ ] **Step 6: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

Open the launcher, press "Full scan" (A's stub) and back several times: the caption bar shows each sentence A speaks. On a phone without the Korean Google TTS voice, switch the phone to Korean: the app says the English notice once and the caption shows it (Review Focus 1).

- [ ] **Step 7: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/ui/SpeechQueue.kt app/src/main/java/com/nungil/core/ui/VoicePick.kt app/src/main/java/com/nungil/shell/BootstrapSpeaker.kt app/src/main/java/com/nungil/shell/MainActivity.kt app/src/main/java/com/nungil/speech/TtsSpeaker.kt app/src/test/java/com/nungil/core/ui/SpeechQueueTest.kt app/src/test/java/com/nungil/core/ui/VoicePickTest.kt
git commit -m "Speak through one queue so phrases never overlap; fall back to English when Korean voice is missing"
git push -u origin i/I3-speech
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I4: Haptics and the search beeper

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/HapticPatterns.kt`
- Modify: `app/src/main/java/com/nungil/shell/MainActivity.kt`
- Create: `app/src/main/java/com/nungil/speech/ToneBeeper.kt`
- Create: `app/src/main/java/com/nungil/speech/VibratorHaptics.kt`
- Create: `app/src/test/java/com/nungil/core/ui/HapticPatternsTest.kt`

**Interfaces:**
- Consumes: `Haptics`, `Beeper`, `Buzz` (contract).
- Produces: `HapticPatterns.timings(kind)`, `amplitudes(kind)`, `pulses(kind)`, `onMs(kind)`; `BeepPolicy.BEEP_MS = 60`, `MIN_INTERVAL_MS = 100`, `restartNow(old, new)`, `clamp(ms)`; `VibratorHaptics(context) : Haptics`; `ToneBeeper() : Beeper` with `release()`. From this task on `services().haptics` and `services().beeper` are real for A and Y.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I4-haptics-beeper
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/nungil/core/ui/HapticPatternsTest.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Buzz
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternsTest {
    @Test fun everyBuzzHasItsOwnPattern() {
        val shapes = Buzz.values().map { HapticPatterns.timings(it).toList() to HapticPatterns.amplitudes(it).toList() }
        assertEquals(Buzz.values().size, shapes.toSet().size)
    }

    @Test fun centredIsOneSixtyMillisecondPulse() =
        assertArrayEquals(longArrayOf(0, 60), HapticPatterns.timings(Buzz.CENTERED))

    @Test fun tapIsShort() = assertArrayEquals(longArrayOf(0, 20), HapticPatterns.timings(Buzz.TAP))

    @Test fun obstacleIsThreePulses() = assertEquals(3, HapticPatterns.pulses(Buzz.OBSTACLE))

    @Test fun foundIsTwoPulses() = assertEquals(2, HapticPatterns.pulses(Buzz.FOUND))

    @Test fun errorIsTheLongest() {
        val error = HapticPatterns.onMs(Buzz.ERROR)
        Buzz.values().filter { it != Buzz.ERROR }.forEach { assertTrue(it.name, HapticPatterns.onMs(it) < error) }
    }

    @Test fun doneRises() {
        val amps = HapticPatterns.amplitudes(Buzz.DONE).filter { it > 0 }
        assertEquals(amps.sorted(), amps)
        assertFalse(amps.toSet().size == 1)
    }

    @Test fun waveformsAreWellFormed() {
        for (kind in Buzz.values()) {
            val t = HapticPatterns.timings(kind)
            val a = HapticPatterns.amplitudes(kind)
            assertEquals(kind.name, t.size, a.size)
            assertEquals(kind.name, 0L, t[0])
            assertTrue(kind.name, t.size % 2 == 0)
            for (i in t.indices) {
                if (i % 2 == 1) {
                    assertTrue(kind.name, t[i] > 0 && a[i] in 1..255)
                } else {
                    assertEquals(kind.name, 0, a[i])
                }
            }
        }
    }

    @Test fun beeperRestartsAtOnceWhenStartingOrSpeedingUp() {
        assertTrue(BeepPolicy.restartNow(oldIntervalMs = 0, newIntervalMs = 800))
        assertTrue(BeepPolicy.restartNow(oldIntervalMs = 800, newIntervalMs = 400))
        assertFalse(BeepPolicy.restartNow(oldIntervalMs = 400, newIntervalMs = 800))
        assertFalse(BeepPolicy.restartNow(oldIntervalMs = 400, newIntervalMs = 400))
    }

    @Test fun beeperNeverGoesFasterThanItsOwnLength() {
        assertEquals(BeepPolicy.MIN_INTERVAL_MS, BeepPolicy.clamp(10))
        assertEquals(1_000L, BeepPolicy.clamp(1_000))
        assertTrue(BeepPolicy.MIN_INTERVAL_MS > BeepPolicy.BEEP_MS)
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'HapticPatterns'` and `'BeepPolicy'`.

- [ ] **Step 4: Implement**

Create `app/src/main/java/com/nungil/core/ui/HapticPatterns.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Buzz

/**
 * The haptic vocabulary: one distinct vibration per [Buzz], so a blind user can tell "found" from
 * "obstacle" without waiting for speech. Waveforms use Android's format: [off, on, off, on, …] in ms,
 * with amplitudes 0 for off and 1..255 for on.
 */
object HapticPatterns {
    private class Wave(val timings: LongArray, val amplitudes: IntArray)

    private val waves: Map<Buzz, Wave> = mapOf(
        Buzz.TAP to Wave(longArrayOf(0, 20), intArrayOf(0, 160)),
        Buzz.FOUND to Wave(longArrayOf(0, 40, 60, 40), intArrayOf(0, 200, 0, 200)),
        Buzz.CENTERED to Wave(longArrayOf(0, 60), intArrayOf(0, 255)),
        Buzz.LOST to Wave(longArrayOf(0, 150), intArrayOf(0, 120)),
        Buzz.OBSTACLE to Wave(longArrayOf(0, 80, 60, 80, 60, 80), intArrayOf(0, 255, 0, 255, 0, 255)),
        Buzz.DONE to Wave(longArrayOf(0, 30, 50, 50, 50, 70), intArrayOf(0, 100, 0, 170, 0, 255)),
        Buzz.ERROR to Wave(longArrayOf(0, 300), intArrayOf(0, 255)),
    )

    fun timings(kind: Buzz): LongArray = waves.getValue(kind).timings.copyOf()

    fun amplitudes(kind: Buzz): IntArray = waves.getValue(kind).amplitudes.copyOf()

    /** Number of separate pulses. */
    fun pulses(kind: Buzz): Int = timings(kind).indices.count { it % 2 == 1 }

    /** Total vibrating time in ms. */
    fun onMs(kind: Buzz): Long = timings(kind).filterIndexed { i, _ -> i % 2 == 1 }.sum()
}

/** Timing rules for the repeating search beep. */
object BeepPolicy {
    const val BEEP_MS = 60
    const val MIN_INTERVAL_MS = 100L

    /** Start at once when the beep was off, or when it must go faster; slowing down waits for the next beep. */
    fun restartNow(oldIntervalMs: Long, newIntervalMs: Long): Boolean =
        oldIntervalMs <= 0 || newIntervalMs < oldIntervalMs

    fun clamp(intervalMs: Long): Long = maxOf(intervalMs, MIN_INTERVAL_MS)
}
```

Replace the whole file `app/src/main/java/com/nungil/shell/MainActivity.kt`:

```kotlin
package com.nungil.shell

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.nungil.R
import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.app.AppNavigator
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.Beeper
import com.nungil.contract.app.Haptics
import com.nungil.contract.app.Speaker
import com.nungil.core.ui.VoiceChoice
import com.nungil.databinding.ActivityMainBinding
import com.nungil.items.AddItemFragmentArgs
import com.nungil.people.AddPersonFragmentArgs
import com.nungil.saved.SavedFragmentArgs
import com.nungil.scan.ScanFragmentArgs
import com.nungil.search.SearchFragmentArgs
import com.nungil.speech.ToneBeeper
import com.nungil.speech.TtsSpeaker
import com.nungil.speech.VibratorHaptics
import java.util.Locale

/**
 * Owner I. Single activity: toolbar, nav host, spoken-caption bar, and the AppServices every screen
 * uses. The full shell arrives in task I5, voice input in task I9.
 */
class MainActivity : AppCompatActivity(), AppServices, AppNavigator {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var tts: TtsSpeaker
    private var voiceChoice: VoiceChoice? = null

    /** The language the user chose for the screens. */
    val uiLang: Lang
        get() {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val tag = if (appLocales.isEmpty) Locale.getDefault().language else appLocales[0]?.language
            return Lang.fromTag(tag)
        }

    /** The language of spoken sentences: the UI language, unless its voice is missing (then English). */
    override val lang: Lang
        get() = voiceChoice?.speak ?: uiLang
    override val speaker: Speaker get() = tts
    private lateinit var vibration: VibratorHaptics
    private val tones = ToneBeeper()
    override val haptics: Haptics get() = vibration
    override val beeper: Beeper get() = tones
    override val navigator: AppNavigator get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 35 draws edge-to-edge on Android 15: keep content out from under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        navController = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        binding.toolbar.setupWithNavController(navController, AppBarConfiguration(setOf(R.id.home)))
        vibration = VibratorHaptics(this)
        tts = TtsSpeaker(
            context = this,
            wanted = { uiLang },
            onCaption = { text -> binding.caption.text = text },
            onVoiceChoice = { choice -> voiceChoice = choice },
        )
    }

    override fun onDestroy() {
        tts.shutdown()
        tones.release()
        super.onDestroy()
    }

    override fun askForWords(owner: LifecycleOwner, onText: (String) -> Unit) = Unit

    override fun open(dest: Dest) {
        when (dest) {
            Dest.Home -> navController.popBackStack(R.id.home, false)
            Dest.ScanHub -> go(R.id.scan_hub)
            is Dest.Scan -> go(R.id.scan, ScanFragmentArgs(dest.mode).toBundle())
            Dest.Walk -> go(R.id.walk)
            is Dest.Search -> go(R.id.search, SearchFragmentArgs(dest.query).toBundle())
            is Dest.Saved -> go(R.id.saved, SavedFragmentArgs(dest.tab?.ordinal ?: -1).toBundle())
            Dest.Settings -> go(R.id.settings)
            Dest.History -> go(R.id.history)
            is Dest.AddPerson -> go(R.id.add_person, AddPersonFragmentArgs(dest.name).toBundle())
            is Dest.AddItem -> go(R.id.add_item, AddItemFragmentArgs(dest.kind, dest.name).toBundle())
            Dest.Reader -> go(R.id.reader)
            Dest.Onboarding -> go(R.id.onboarding)
        }
    }

    override fun back() {
        if (!navController.popBackStack()) finish()
    }

    /** popUpTo the same destination, so "full scan" said twice never stacks two scan screens. */
    private fun go(id: Int, args: Bundle? = null) {
        val options = NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(id, true).build()
        navController.navigate(id, args, options)
    }
}
```

Create `app/src/main/java/com/nungil/speech/ToneBeeper.kt`:

```kotlin
package com.nungil.speech

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.HandlerThread
import com.nungil.contract.app.Beeper
import com.nungil.core.ui.BeepPolicy

/**
 * Short beeps on their own thread, so a busy main thread never makes the search beep stutter.
 * Safe from any thread. Call [release] when the activity is destroyed.
 */
class ToneBeeper : Beeper {
    private val thread = HandlerThread("nungil-beeper").apply { start() }
    private val handler = Handler(thread.looper)
    private var tone: ToneGenerator? = null

    @Volatile
    private var intervalMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            val interval = intervalMs
            if (interval <= 0) return
            play()
            handler.postDelayed(this, interval)
        }
    }

    override fun beep() {
        handler.post { play() }
    }

    override fun pulse(intervalMs: Long) {
        if (intervalMs <= 0) {
            stop()
            return
        }
        val old = this.intervalMs
        val next = BeepPolicy.clamp(intervalMs)
        this.intervalMs = next
        if (BeepPolicy.restartNow(old, next)) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun stop() {
        intervalMs = 0
        handler.removeCallbacks(tick)
    }

    fun release() {
        stop()
        handler.post {
            tone?.release()
            tone = null
        }
        thread.quitSafely()
    }

    /** Runs on the beeper thread. */
    private fun play() {
        val generator = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME) }
            .getOrNull()
            ?.also { tone = it }
            ?: return
        generator.startTone(ToneGenerator.TONE_PROP_BEEP, BeepPolicy.BEEP_MS)
    }

    private companion object {
        const val VOLUME = 80
    }
}
```

Create `app/src/main/java/com/nungil/speech/VibratorHaptics.kt`:

```kotlin
package com.nungil.speech

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.nungil.contract.Buzz
import com.nungil.contract.app.Haptics
import com.nungil.core.ui.HapticPatterns

/** Plays [HapticPatterns] on the phone's vibrator. Safe from any thread; silent on phones without one. */
class VibratorHaptics(context: Context) : Haptics {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun buzz(kind: Buzz) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val timings = HapticPatterns.timings(kind)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, HapticPatterns.amplitudes(kind), -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }
}
```

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `HapticPatternsTest` 10 pass (every Buzz distinct, CENTERED exactly 60 ms, beeper restarts at once when speeding up).

- [ ] **Step 6: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

Temporarily call `services().beeper.pulse(1000)` then `pulse(150)` from a debugger evaluation on any screen: beeps start at once and speed up immediately; `stop()` silences them. Vibration patterns feel different for FOUND (two taps) and OBSTACLE (three).

- [ ] **Step 7: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/ui/HapticPatterns.kt app/src/main/java/com/nungil/shell/MainActivity.kt app/src/main/java/com/nungil/speech/ToneBeeper.kt app/src/main/java/com/nungil/speech/VibratorHaptics.kt app/src/test/java/com/nungil/core/ui/HapticPatternsTest.kt
git commit -m "Give every event its own vibration and add the search beeper"
git push -u origin i/I4-haptics-beeper
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I2: Coverage ring, big card, icons and accessibility helpers

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/RingGeometry.kt`
- Create: `app/src/main/java/com/nungil/design/BigCardView.kt`
- Modify: `app/src/main/java/com/nungil/design/CoverageRingView.kt`
- Create: `app/src/main/java/com/nungil/design/Ui.kt`
- Create: `app/src/main/res-i/drawable/ng_ic_bolt.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_chevron.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_explore.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_history.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_mic.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_mic_off.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_person.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_saved.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_search.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_tune.xml`
- Create: `app/src/main/res-i/drawable/ng_ic_walk.xml`
- Create: `app/src/main/res-i/layout/ng_big_card.xml`
- Modify: `app/src/main/res-i/values-ko/strings.xml`
- Create: `app/src/main/res-i/values/ng_big_card_attrs.xml`
- Modify: `app/src/main/res-i/values/strings.xml`
- Create: `app/src/test/java/com/nungil/core/ui/RingGeometryTest.kt`

**Interfaces:**
- Consumes: theme attributes `ngPrimary`, `ngLine`, `ngText`, `ngPrimarySoft`; `@font/pretendard_bold` (I1).
- Produces (frozen API for A): `CoverageRingView.setCoverage(percent: Int, bins: BooleanArray)`. For A and Y: `BigCardView` (`app:ngIcon`, `app:ngTitle`, `app:ngSubtitle`, `app:ngEmphasis`, `title`, `subtitle`, `setIcon`), `View.setHeading()`, `View.announce(text)`, `Context.resolveColorAttr(attr): Int`, `Context.isTalkBackOn(): Boolean`, `Context.openAppSettings()`, icons `R.drawable.ng_ic_*`; `RingGeometry.segment(index, count): Arc`, `STROKE_SHARE`, `clampPercent`.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I2-components
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/nungil/core/ui/RingGeometryTest.kt`:

```kotlin
package com.nungil.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RingGeometryTest {
    @Test fun firstSegmentStartsAtTwelveOClock() {
        val arc = RingGeometry.segment(0, 36)
        assertEquals(-89f, arc.startDeg, 1e-4f)
        assertEquals(8f, arc.sweepDeg, 1e-4f)
    }

    @Test fun lastSegmentEndsBeforeTwelveOClock() {
        val arc = RingGeometry.segment(35, 36)
        assertEquals(261f, arc.startDeg, 1e-4f)
        assertEquals(269f, arc.startDeg + arc.sweepDeg, 1e-4f)
    }

    @Test fun segmentsGoClockwiseWithEqualGaps() {
        val a = RingGeometry.segment(3, 36)
        val b = RingGeometry.segment(4, 36)
        assertEquals(10f, b.startDeg - a.startDeg, 1e-4f)
        assertEquals(RingGeometry.GAP_DEG, b.startDeg - (a.startDeg + a.sweepDeg), 1e-4f)
    }

    @Test fun aSingleSegmentIsAFullCircle() {
        val arc = RingGeometry.segment(0, 1)
        assertEquals(-90f, arc.startDeg, 1e-4f)
        assertEquals(360f, arc.sweepDeg, 1e-4f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun indexOutOfRangeIsRejected() {
        RingGeometry.segment(36, 36)
    }

    @Test fun percentIsClamped() {
        assertEquals(0, RingGeometry.clampPercent(-5))
        assertEquals(100, RingGeometry.clampPercent(140))
        assertEquals(42, RingGeometry.clampPercent(42))
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'RingGeometry'`.

- [ ] **Step 4: Implement**

Create `app/src/main/java/com/nungil/core/ui/RingGeometry.kt`:

```kotlin
package com.nungil.core.ui

/** A Canvas arc: degrees, 0 = three o'clock, positive = clockwise. */
data class Arc(val startDeg: Float, val sweepDeg: Float)

/** Geometry of the coverage ring: segment 0 starts at twelve o'clock, segments run clockwise. */
object RingGeometry {
    const val GAP_DEG = 2f

    /** Ring thickness as a share of the ring's diameter. */
    const val STROKE_SHARE = 0.09f

    fun segment(index: Int, count: Int): Arc {
        require(count > 0 && index in 0 until count) { "segment $index of $count" }
        val slice = 360f / count
        val gap = if (count == 1) 0f else GAP_DEG
        return Arc(-90f + index * slice + gap / 2f, slice - gap)
    }

    fun clampPercent(percent: Int): Int = percent.coerceIn(0, 100)
}
```

Create `app/src/main/java/com/nungil/design/BigCardView.kt`:

```kotlin
package com.nungil.design

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.core.content.withStyledAttributes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.card.MaterialCardView
import com.nungil.R
import com.nungil.databinding.NgBigCardBinding

/**
 * Owner I. A large tappable card: icon, title, subtitle and a chevron. TalkBack reads it as one button
 * ("Look around. Turn once and hear what is around you. Button").
 *
 * XML: app:ngIcon, app:ngTitle, app:ngSubtitle, app:ngEmphasis (tinted background for the main card).
 */
class BigCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val binding = NgBigCardBinding.inflate(LayoutInflater.from(context), this)

    var title: CharSequence
        get() = binding.ngBigCardTitle.text
        set(value) {
            binding.ngBigCardTitle.text = value
            describe()
        }

    var subtitle: CharSequence
        get() = binding.ngBigCardSubtitle.text
        set(value) {
            binding.ngBigCardSubtitle.text = value
            binding.ngBigCardSubtitle.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
            describe()
        }

    init {
        isClickable = true
        isFocusable = true
        minimumHeight = resources.getDimensionPixelSize(R.dimen.ng_touch)
        context.withStyledAttributes(attrs, R.styleable.BigCardView) {
            setIcon(getDrawable(R.styleable.BigCardView_ngIcon))
            title = getString(R.styleable.BigCardView_ngTitle).orEmpty()
            subtitle = getString(R.styleable.BigCardView_ngSubtitle).orEmpty()
            if (getBoolean(R.styleable.BigCardView_ngEmphasis, false)) {
                setCardBackgroundColor(context.resolveColorAttr(R.attr.ngPrimarySoft))
            }
        }
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
    }

    fun setIcon(icon: Drawable?) {
        binding.ngBigCardIcon.setImageDrawable(icon)
        binding.ngBigCardIcon.visibility = if (icon == null) View.GONE else View.VISIBLE
    }

    private fun describe() {
        contentDescription = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(". ")
    }
}
```

Replace the whole file `app/src/main/java/com/nungil/design/CoverageRingView.kt`:

```kotlin
package com.nungil.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.nungil.R
import com.nungil.core.ui.RingGeometry
import kotlin.math.min

/**
 * Owner I. A 360° ring of segments with the percent in the middle; seen slices use ?attr/ngPrimary,
 * unseen ones ?attr/ngLine, so it follows the light, dark and high-contrast themes. Main thread only.
 */
class CoverageRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var bins = BooleanArray(DEFAULT_BINS)
    private var percent = 0
    private val oval = RectF()

    private val seenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.resolveColorAttr(R.attr.ngPrimary)
    }
    private val unseenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.resolveColorAttr(R.attr.ngLine)
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.resolveColorAttr(R.attr.ngText)
        textAlign = Paint.Align.CENTER
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.pretendard_bold) }.getOrNull()
    }
    private val maxTextPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f, resources.displayMetrics)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setCoverage(0, bins)
    }

    /** [percent] 0..100; [bins] true = that slice has been seen (index 0 = start heading). */
    fun setCoverage(percent: Int, bins: BooleanArray) {
        this.percent = RingGeometry.clampPercent(percent)
        this.bins = if (bins.isEmpty()) BooleanArray(DEFAULT_BINS) else bins.copyOf()
        contentDescription = resources.getString(R.string.ng_ring_description, this.percent)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferred = (DEFAULT_SIZE_DP * resources.displayMetrics.density).toInt()
        val size = min(resolveSize(preferred, widthMeasureSpec), resolveSize(preferred, heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (size <= 0f) return
        val stroke = size * RingGeometry.STROKE_SHARE
        seenPaint.strokeWidth = stroke
        unseenPaint.strokeWidth = stroke
        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val radius = size / 2f - stroke / 2f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        for (i in bins.indices) {
            val arc = RingGeometry.segment(i, bins.size)
            canvas.drawArc(oval, arc.startDeg, arc.sweepDeg, false, if (bins[i]) seenPaint else unseenPaint)
        }
        textPaint.textSize = min(maxTextPx, size * 0.22f)
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("$percent%", cx, baseline, textPaint)
    }

    private companion object {
        const val DEFAULT_BINS = 36
        const val DEFAULT_SIZE_DP = 200
    }
}
```

Create `app/src/main/java/com/nungil/design/Ui.kt`:

```kotlin
package com.nungil.design

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat

/** Marks this view as the screen's heading, so TalkBack users can jump to it. */
fun View.setHeading() = ViewCompat.setAccessibilityHeading(this, true)

/** Speaks through TalkBack only. For everyone else use services().speaker. */
fun View.announce(text: CharSequence) = announceForAccessibility(text)

/** A theme colour such as R.attr.ngPrimary, for drawing in Kotlin. */
@ColorInt
fun Context.resolveColorAttr(@AttrRes attr: Int): Int {
    val value = TypedValue()
    check(theme.resolveAttribute(attr, value, true)) { "theme has no value for attribute $attr" }
    return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
}

/** True while TalkBack (or another touch-exploration service) is on. */
fun Context.isTalkBackOn(): Boolean =
    (getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

/** Opens this app's page in system settings, where a permanently denied permission can be allowed. */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
```

Create `app/src/main/res-i/drawable/ng_ic_bolt.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "flash_on" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M7,2v11h3v9l7,-12h-4l4,-8z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_chevron.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "chevron_right" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_explore.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "explore" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,10.9c-0.61,0 -1.1,0.49 -1.1,1.1s0.49,1.1 1.1,1.1c0.61,0 1.1,-0.49 1.1,-1.1s-0.49,-1.1 -1.1,-1.1zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM14.19,14.19L6,18l3.81,-8.19L18,6l-3.81,8.19z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_history.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "history" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_mic.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "mic" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_mic_off.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "mic_off" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M19,11h-1.7c0,0.74 -0.16,1.43 -0.43,2.05l1.23,1.23c0.56,-0.98 0.9,-2.09 0.9,-3.28zM14.98,11.17c0,-0.06 0.02,-0.11 0.02,-0.17L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v0.18l5.98,5.99zM4.27,3L3,4.27l6.01,6.01L9,11c0,1.66 1.33,3 2.99,3 0.22,0 0.44,-0.03 0.65,-0.08l1.66,1.66c-0.71,0.33 -1.5,0.52 -2.31,0.52 -2.76,0 -5.3,-2.1 -5.3,-5.1L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c0.91,-0.13 1.77,-0.45 2.54,-0.9L19.73,21 21,19.73 4.27,3z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_person.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "person" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_saved.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "bookmark" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_search.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "search" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_tune.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "tune" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="false"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M3,17v2h6v-2L3,17zM3,5v2h10L13,5L3,5zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2L3,11v2h4v2h2L9,9L7,9zM21,13v-2L11,11v2h10zM15,9h2L17,7h4L21,5h-4L17,3h-2v6z" />
</vector>
```

Create `app/src/main/res-i/drawable/ng_ic_walk.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Material Icons "directions_walk" (Apache License 2.0). Tint it with a token where it is used. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:autoMirrored="true"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M13.5,5.5c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM9.8,8.9L7,23h2.1l1.8,-8 2.1,2v6h2v-7.5l-2.1,-2 0.6,-3C14.8,12 16.8,13 19,13v-2c-1.9,0 -3.5,-1 -4.3,-2.4l-1,-1.6c-0.4,-0.6 -1,-1 -1.7,-1 -0.3,0 -0.5,0.1 -0.8,0.1L6,8.3V13h2V9.6l1.8,-0.7" />
</vector>
```

Create `app/src/main/res-i/layout/ng_big_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Content of BigCardView: icon, title, subtitle, chevron. The card itself is the one
     accessible element; the children are hidden from TalkBack. -->
<merge xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:minHeight="72dp"
        android:orientation="horizontal">

        <ImageView
            android:id="@+id/ng_big_card_icon"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:importantForAccessibility="no"
            app:tint="?attr/ngPrimary" />

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:layout_marginEnd="8dp"
            android:layout_weight="1"
            android:orientation="vertical">

            <TextView
                android:id="@+id/ng_big_card_title"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:importantForAccessibility="no"
                android:textAppearance="@style/TextAppearance.Nungil.Headline" />

            <TextView
                android:id="@+id/ng_big_card_subtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:importantForAccessibility="no"
                android:textAppearance="@style/TextAppearance.Nungil.Body"
                android:textColor="?attr/ngTextSub" />
        </LinearLayout>

        <ImageView
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:importantForAccessibility="no"
            android:src="@drawable/ng_ic_chevron"
            app:tint="?attr/ngTextSub" />
    </LinearLayout>
</merge>
```

Replace the whole file `app/src/main/res-i/values-ko/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. 해요체, short and friendly. -->
<resources>
    <string name="app_name">눈길</string>

    <string name="nav_title_home">눈길</string>
    <string name="nav_title_onboarding">시작하기</string>
    <string name="nav_title_scan_hub">둘러보기</string>
    <string name="nav_title_settings">설정</string>
    <string name="nav_title_history">기록</string>
    <string name="nav_title_scan">주변 둘러보기</string>
    <string name="nav_title_walk">걷기 모드</string>
    <string name="nav_title_search">찾기</string>
    <string name="nav_title_search_camera">찾는 중</string>
    <string name="nav_title_saved">저장한 것</string>
    <string name="nav_title_person">사람</string>
    <string name="nav_title_add_person">사람 추가</string>
    <string name="nav_title_enroll">얼굴 등록</string>
    <string name="nav_title_item">물건</string>
    <string name="nav_title_add_item">물건 추가</string>
    <string name="nav_title_item_enroll">물건 등록</string>
    <string name="nav_title_reader">글자 읽기</string>

    <!-- Design components -->
    <string name="ng_ring_description">%1$d퍼센트 살펴봤어요</string>
</resources>
```

Create `app/src/main/res-i/values/ng_big_card_attrs.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Attributes of com.nungil.design.BigCardView. -->
<resources>
    <declare-styleable name="BigCardView">
        <attr name="ngIcon" format="reference" />
        <attr name="ngTitle" format="string" />
        <attr name="ngSubtitle" format="string" />
        <attr name="ngEmphasis" format="boolean" />
    </declare-styleable>
</resources>
```

Replace the whole file `app/src/main/res-i/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Prefixes owned by I: app_, nav_title_, home_, hub_, settings_, history_, onboarding_, voice_, help_, ng_. -->
<resources>
    <string name="app_name">Nungil</string>

    <string name="nav_title_home">Nungil</string>
    <string name="nav_title_onboarding">Welcome</string>
    <string name="nav_title_scan_hub">Scan</string>
    <string name="nav_title_settings">Settings</string>
    <string name="nav_title_history">History</string>
    <string name="nav_title_scan">Look around</string>
    <string name="nav_title_walk">Walk mode</string>
    <string name="nav_title_search">Find</string>
    <string name="nav_title_search_camera">Finding</string>
    <string name="nav_title_saved">Saved</string>
    <string name="nav_title_person">Person</string>
    <string name="nav_title_add_person">Add a person</string>
    <string name="nav_title_enroll">Learn a face</string>
    <string name="nav_title_item">Item</string>
    <string name="nav_title_add_item">Add an item</string>
    <string name="nav_title_item_enroll">Learn an item</string>
    <string name="nav_title_reader">Read text</string>

    <!-- Design components -->
    <string name="ng_ring_description">Scanned %1$d percent</string>
</resources>
```

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `RingGeometryTest` 6 pass.

- [ ] **Step 6: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

A puts the ring on the scan screen at S1; until then check it in a scratch layout: 36 segments starting at twelve o'clock, seen segments blue (yellow in high contrast), the percent in the middle; TalkBack reads "Scanned 60 percent" / "60퍼센트 살펴봤어요".

- [ ] **Step 7: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/ui/RingGeometry.kt app/src/main/java/com/nungil/design/BigCardView.kt app/src/main/java/com/nungil/design/CoverageRingView.kt app/src/main/java/com/nungil/design/Ui.kt app/src/main/res-i/drawable/ng_ic_bolt.xml app/src/main/res-i/drawable/ng_ic_chevron.xml app/src/main/res-i/drawable/ng_ic_explore.xml app/src/main/res-i/drawable/ng_ic_history.xml app/src/main/res-i/drawable/ng_ic_mic.xml app/src/main/res-i/drawable/ng_ic_mic_off.xml app/src/main/res-i/drawable/ng_ic_person.xml app/src/main/res-i/drawable/ng_ic_saved.xml app/src/main/res-i/drawable/ng_ic_search.xml app/src/main/res-i/drawable/ng_ic_tune.xml app/src/main/res-i/drawable/ng_ic_walk.xml app/src/main/res-i/layout/ng_big_card.xml app/src/main/res-i/values-ko/strings.xml app/src/main/res-i/values/ng_big_card_attrs.xml app/src/main/res-i/values/strings.xml app/src/test/java/com/nungil/core/ui/RingGeometryTest.kt
git commit -m "Draw the coverage ring and add the big card, icons and accessibility helpers"
git push -u origin i/I2-components
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I5: Shell: routing, language, high contrast, first launch

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/CommandRouter.kt`
- Create: `app/src/main/java/com/nungil/core/ui/ShellPhrases.kt`
- Create: `app/src/main/java/com/nungil/shell/AppPrefs.kt`
- Modify: `app/src/main/java/com/nungil/shell/MainActivity.kt`
- Modify: `app/src/main/res-i/values-ko/strings.xml`
- Modify: `app/src/main/res-i/values/strings.xml`
- Create: `app/src/test/java/com/nungil/core/ui/CommandRouterTest.kt`
- Create: `app/src/test/java/com/nungil/core/ui/ShellPhrasesTest.kt`

**Interfaces:**
- Consumes: `VoiceCommand`, `Dest`, `VoiceHandler` (contract); `TtsSpeaker`, `VibratorHaptics`, `ToneBeeper`.
- Produces: `CommandRouter.isGlobal(command)`, `CommandRouter.route(command): Route`; `ShellPhrases.text(phrase: Phrase, lang: Lang)` (the canonical "I did not understand." / "잘 못 알아들었어요."); `AppLanguage`, `LanguageChoice`; `AppPrefs`; `MainActivity.handleCommand(command)`, `setLanguage(choice)`, `languageChoice`, `setHighContrast(on)`, `highContrast`, `setLearner(on)`, `learnerOn`, `setVoiceOn(on)`.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I5-shell
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/nungil/core/ui/CommandRouterTest.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {
    @Test fun globalCommandsNeverReachTheScreen() {
        assertTrue(CommandRouter.isGlobal(VoiceCommand.Repeat))
        assertTrue(CommandRouter.isGlobal(VoiceCommand.StopListening))
        assertTrue(CommandRouter.isGlobal(VoiceCommand.SetLanguage(Lang.KO)))
        assertTrue(CommandRouter.isGlobal(VoiceCommand.Learner(true)))
    }

    @Test fun screenCommandsGoToTheScreenFirst() {
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Start))
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Stop))
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Delete))
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Go(Dest.Home)))
        assertFalse(CommandRouter.isGlobal(VoiceCommand.Unknown("Ali")))
    }

    @Test fun goOpensTheDestination() =
        assertEquals(Route.Open(Dest.Search("bag")), CommandRouter.route(VoiceCommand.Go(Dest.Search("bag"))))

    @Test fun readTextOpensTheReader() = assertEquals(Route.Open(Dest.Reader), CommandRouter.route(VoiceCommand.ReadText))

    @Test fun whatAndWhoOpenLiveScanAndSaySo() {
        val expected = Route.OpenAndSay(Dest.Scan(ScanMode.LIVE), Phrase.OPENING_LIVE_SCAN)
        assertEquals(expected, CommandRouter.route(VoiceCommand.WhatIsThis))
        assertEquals(expected, CommandRouter.route(VoiceCommand.WhoIsThis))
    }

    @Test fun unhandledScreenActionsSayNotHere() {
        for (c in listOf(VoiceCommand.Start, VoiceCommand.SwitchCamera, VoiceCommand.Delete)) {
            assertEquals(Route.Say(Phrase.NOT_HERE), CommandRouter.route(c))
        }
    }

    @Test fun stopWithNoScreenStopsSpeech() = assertEquals(Route.StopSpeaking, CommandRouter.route(VoiceCommand.Stop))

    @Test fun unknownSaysNotUnderstood() =
        assertEquals(Route.Say(Phrase.NOT_UNDERSTOOD), CommandRouter.route(VoiceCommand.Unknown("Corazon")))

    @Test fun globalRoutes() {
        assertEquals(Route.GoBack, CommandRouter.route(VoiceCommand.Back))
        assertEquals(Route.RepeatLast, CommandRouter.route(VoiceCommand.Repeat))
        assertEquals(Route.StopListening, CommandRouter.route(VoiceCommand.StopListening))
        assertEquals(Route.SwitchLanguage(Lang.KO), CommandRouter.route(VoiceCommand.SetLanguage(Lang.KO)))
        assertEquals(Route.SetLearner(false), CommandRouter.route(VoiceCommand.Learner(false)))
        assertEquals(Route.SpeakHelp("search"), CommandRouter.route(VoiceCommand.Help("search")))
    }

    @Test fun languageChoiceTags() {
        assertEquals("", AppLanguage.tag(LanguageChoice.SYSTEM))
        assertEquals("en", AppLanguage.tag(LanguageChoice.ENGLISH))
        assertEquals("ko", AppLanguage.tag(LanguageChoice.KOREAN))
        assertEquals(LanguageChoice.SYSTEM, AppLanguage.choiceOf(null))
        assertEquals(LanguageChoice.SYSTEM, AppLanguage.choiceOf(""))
        assertEquals(LanguageChoice.KOREAN, AppLanguage.choiceOf("ko-KR"))
        assertEquals(LanguageChoice.ENGLISH, AppLanguage.choiceOf("en"))
        assertEquals(LanguageChoice.KOREAN, AppLanguage.forLang(Lang.KO))
    }
}
```

Create `app/src/test/java/com/nungil/core/ui/ShellPhrasesTest.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPhrasesTest {
    private fun hasHangul(s: String) = s.any { it.code in 0xAC00..0xD7A3 }

    @Test fun canonicalNotUnderstood() {
        assertEquals("I did not understand.", ShellPhrases.text(Phrase.NOT_UNDERSTOOD, Lang.EN))
        assertEquals("잘 못 알아들었어요.", ShellPhrases.text(Phrase.NOT_UNDERSTOOD, Lang.KO))
    }

    @Test fun everyPhraseExistsInBothLanguages() {
        for (p in Phrase.values()) {
            val en = ShellPhrases.text(p, Lang.EN)
            val ko = ShellPhrases.text(p, Lang.KO)
            assertTrue(p.name, en.isNotBlank() && !hasHangul(en))
            assertTrue(p.name, hasHangul(ko))
        }
    }

    @Test fun koreanIsPoliteHaeyoStyle() {
        for (p in Phrase.values()) {
            val ko = ShellPhrases.text(p, Lang.KO)
            assertTrue("${p.name}: $ko", !ko.contains("습니다") && !ko.contains("음."))
        }
    }

    @Test fun noEmoji() {
        for (p in Phrase.values()) for (lang in Lang.values()) {
            val s = ShellPhrases.text(p, lang)
            assertTrue(p.name, s.none { Character.isSurrogate(it) })
        }
    }

    @Test fun languageSentenceIsSpokenInTheNewLanguage() {
        assertEquals("Language: English.", ShellPhrases.text(Phrase.LANGUAGE_SET, Lang.EN))
        assertEquals("언어를 한국어로 바꿨어요.", ShellPhrases.text(Phrase.LANGUAGE_SET, Lang.KO))
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'ShellPhrases'`, `'CommandRouter'`, `'AppLanguage'`.

- [ ] **Step 4: Implement**

Create `app/src/main/java/com/nungil/core/ui/CommandRouter.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand

/** What MainActivity does with a command that no screen handled. */
sealed interface Route {
    data class Open(val dest: Dest) : Route
    data object GoBack : Route
    data object RepeatLast : Route
    data class SpeakHelp(val topic: String?) : Route
    data class SetLearner(val on: Boolean) : Route
    data object StopListening : Route
    data class SwitchLanguage(val lang: Lang) : Route
    data class OpenAndSay(val dest: Dest, val phrase: Phrase) : Route
    data class Say(val phrase: Phrase) : Route
    data object StopSpeaking : Route
}

/**
 * Voice-command routing. Global commands are handled by the shell before any screen sees them; all
 * others go to the current screen's VoiceHandler first and come here only if it returns false.
 */
object CommandRouter {
    fun isGlobal(command: VoiceCommand): Boolean =
        command == VoiceCommand.Repeat ||
            command == VoiceCommand.StopListening ||
            command is VoiceCommand.SetLanguage ||
            command is VoiceCommand.Learner

    fun route(command: VoiceCommand): Route = when (command) {
        is VoiceCommand.Go -> Route.Open(command.dest)
        VoiceCommand.Back -> Route.GoBack
        VoiceCommand.Repeat -> Route.RepeatLast
        is VoiceCommand.Help -> Route.SpeakHelp(command.topic)
        is VoiceCommand.Learner -> Route.SetLearner(command.on)
        VoiceCommand.StopListening -> Route.StopListening
        is VoiceCommand.SetLanguage -> Route.SwitchLanguage(command.lang)
        VoiceCommand.ReadText -> Route.Open(Dest.Reader)
        VoiceCommand.WhatIsThis, VoiceCommand.WhoIsThis ->
            Route.OpenAndSay(Dest.Scan(ScanMode.LIVE), Phrase.OPENING_LIVE_SCAN)
        VoiceCommand.Stop -> Route.StopSpeaking
        VoiceCommand.Start, VoiceCommand.SwitchCamera, VoiceCommand.Delete -> Route.Say(Phrase.NOT_HERE)
        is VoiceCommand.Unknown -> Route.Say(Phrase.NOT_UNDERSTOOD)
    }
}

enum class LanguageChoice { SYSTEM, ENGLISH, KOREAN }

/** Maps the Settings language choice to AppCompat per-app locale tags and back. */
object AppLanguage {
    fun tag(choice: LanguageChoice): String = when (choice) {
        LanguageChoice.SYSTEM -> ""
        LanguageChoice.ENGLISH -> "en"
        LanguageChoice.KOREAN -> "ko"
    }

    /** [appLocaleTag] is the first per-app locale, or null/empty when the app follows the phone. */
    fun choiceOf(appLocaleTag: String?): LanguageChoice = when {
        appLocaleTag.isNullOrEmpty() -> LanguageChoice.SYSTEM
        Lang.fromTag(appLocaleTag) == Lang.KO -> LanguageChoice.KOREAN
        else -> LanguageChoice.ENGLISH
    }

    fun forLang(lang: Lang): LanguageChoice = if (lang == Lang.KO) LanguageChoice.KOREAN else LanguageChoice.ENGLISH
}
```

Create `app/src/main/java/com/nungil/core/ui/ShellPhrases.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang

/** Sentences the app shell speaks. */
enum class Phrase {
    NOT_UNDERSTOOD,
    NOT_HERE,
    OPENING_LIVE_SCAN,
    LANGUAGE_SET,
    VOICE_ON,
    VOICE_OFF,
    LEARNER_ON,
    LEARNER_OFF,
    NOTHING_TO_REPEAT,
    HELP_GENERAL,
    MIC_NEEDED,
    MIC_BLOCKED,
    VOICE_UNAVAILABLE,
    KOREAN_RECOGNITION_MISSING,
    LISTENING,
}

/** English and Korean (해요체) text of every shell sentence. */
object ShellPhrases {
    private val en = mapOf(
        Phrase.NOT_UNDERSTOOD to "I did not understand.",
        Phrase.NOT_HERE to "That does not work on this screen.",
        Phrase.OPENING_LIVE_SCAN to "Opening live scan. Point the phone at it.",
        Phrase.LANGUAGE_SET to "Language: English.",
        Phrase.VOICE_ON to "Voice commands on. Say help to hear what you can say.",
        Phrase.VOICE_OFF to "Voice commands off.",
        Phrase.LEARNER_ON to "Learner mode on. I will explain each screen.",
        Phrase.LEARNER_OFF to "Learner mode off.",
        Phrase.NOTHING_TO_REPEAT to "Nothing to repeat yet.",
        Phrase.HELP_GENERAL to "You can say: look around, find and the name of a thing, saved, settings, or help.",
        Phrase.MIC_NEEDED to "Voice commands need the microphone permission.",
        Phrase.MIC_BLOCKED to "The microphone is blocked. Allow it in the app settings.",
        Phrase.VOICE_UNAVAILABLE to "Voice input is not available on this phone.",
        Phrase.KOREAN_RECOGNITION_MISSING to "Korean speech recognition is not installed, so I will listen in English.",
        Phrase.LISTENING to "Listening.",
    )

    private val ko = mapOf(
        Phrase.NOT_UNDERSTOOD to "잘 못 알아들었어요.",
        Phrase.NOT_HERE to "이 화면에서는 할 수 없어요.",
        Phrase.OPENING_LIVE_SCAN to "실시간 안내를 열게요. 휴대폰을 그쪽으로 향해 주세요.",
        Phrase.LANGUAGE_SET to "언어를 한국어로 바꿨어요.",
        Phrase.VOICE_ON to "음성 명령을 켰어요. 무엇을 말할 수 있는지 들으려면 도움말이라고 말해 주세요.",
        Phrase.VOICE_OFF to "음성 명령을 껐어요.",
        Phrase.LEARNER_ON to "학습 모드를 켰어요. 화면마다 설명해 드릴게요.",
        Phrase.LEARNER_OFF to "학습 모드를 껐어요.",
        Phrase.NOTHING_TO_REPEAT to "아직 다시 들려드릴 말이 없어요.",
        Phrase.HELP_GENERAL to "주변 둘러보기, 무엇 찾아줘, 저장한 것, 설정, 도움말이라고 말해 보세요.",
        Phrase.MIC_NEEDED to "음성 명령을 쓰려면 마이크 권한이 필요해요.",
        Phrase.MIC_BLOCKED to "마이크가 막혀 있어요. 앱 설정에서 허용해 주세요.",
        Phrase.VOICE_UNAVAILABLE to "이 휴대폰에서는 음성 입력을 쓸 수 없어요.",
        Phrase.KOREAN_RECOGNITION_MISSING to "한국어 음성 인식이 설치되어 있지 않아서 영어로 들을게요.",
        Phrase.LISTENING to "듣고 있어요.",
    )

    fun text(phrase: Phrase, lang: Lang): String =
        (if (lang == Lang.KO) ko else en).getValue(phrase)
}
```

Create `app/src/main/java/com/nungil/shell/AppPrefs.kt`:

```kotlin
package com.nungil.shell

import android.content.Context

/** App-wide choices in SharedPreferences "app_prefs". The UI language itself is stored by AppCompat. */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var highContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    /** Always-on voice commands. */
    var voiceOn: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ON, value).apply()

    /** Speak each screen's help when it opens. */
    var learnerOn: Boolean
        get() = prefs.getBoolean(KEY_LEARNER_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_LEARNER_ON, value).apply()

    /** Tapping a control speaks its name (only while TalkBack is off). */
    var voiceGuideOn: Boolean
        get() = prefs.getBoolean(KEY_VOICE_GUIDE_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_GUIDE_ON, value).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /** A sentence to speak after the activity is recreated (for example after a language change). */
    fun setPendingAnnouncement(text: String) = prefs.edit().putString(KEY_PENDING, text).apply()

    fun takePendingAnnouncement(): String? {
        val text = prefs.getString(KEY_PENDING, null) ?: return null
        prefs.edit().remove(KEY_PENDING).apply()
        return text
    }

    private companion object {
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_VOICE_ON = "voice_on"
        const val KEY_LEARNER_ON = "learner_on"
        const val KEY_VOICE_GUIDE_ON = "voice_guide_on"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_PENDING = "pending_announcement"
    }
}
```

Replace the whole file `app/src/main/java/com/nungil/shell/MainActivity.kt`:

```kotlin
package com.nungil.shell

import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.nungil.R
import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppNavigator
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.Beeper
import com.nungil.contract.app.Haptics
import com.nungil.contract.app.Speaker
import com.nungil.contract.app.VoiceHandler
import com.nungil.core.ui.AppLanguage
import com.nungil.core.ui.CommandRouter
import com.nungil.core.ui.LanguageChoice
import com.nungil.core.ui.Phrase
import com.nungil.core.ui.Route
import com.nungil.core.ui.ShellPhrases
import com.nungil.core.ui.VoiceChoice
import com.nungil.databinding.ActivityMainBinding
import com.nungil.items.AddItemFragmentArgs
import com.nungil.people.AddPersonFragmentArgs
import com.nungil.saved.SavedFragmentArgs
import com.nungil.scan.ScanFragmentArgs
import com.nungil.search.SearchFragmentArgs
import com.nungil.speech.ToneBeeper
import com.nungil.speech.TtsSpeaker
import com.nungil.speech.VibratorHaptics

/**
 * Owner I. The single activity: theme (light, dark or high contrast), toolbar, nav host, caption bar,
 * the AppServices every screen uses, and voice-command routing. Voice input arrives in task I9.
 */
class MainActivity : AppCompatActivity(), AppServices, AppNavigator {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var prefs: AppPrefs
    private lateinit var tts: TtsSpeaker
    private lateinit var vibration: VibratorHaptics
    private val tones = ToneBeeper()
    private var voiceChoice: VoiceChoice? = null

    /** The language the user chose for the screens (per-app locale, else the phone's language). */
    val uiLang: Lang
        get() {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val tag = if (appLocales.isEmpty) {
                ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language
            } else {
                appLocales[0]?.language
            }
            return Lang.fromTag(tag)
        }

    /** The language of spoken sentences: the UI language, unless its voice is missing (then English). */
    override val lang: Lang
        get() = voiceChoice?.speak ?: uiLang
    override val speaker: Speaker get() = tts
    override val haptics: Haptics get() = vibration
    override val beeper: Beeper get() = tones
    override val navigator: AppNavigator get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = AppPrefs(this)
        val highContrast = prefs.highContrast
        if (highContrast) setTheme(R.style.Theme_Nungil_HighContrast)
        // High contrast is black in light and dark mode, so its bar icons are always light.
        val bars = if (highContrast) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(bars, bars)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 35 draws edge-to-edge on Android 15: keep content out from under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        navController = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        binding.toolbar.setupWithNavController(navController, AppBarConfiguration(setOf(R.id.home)))

        vibration = VibratorHaptics(this)
        tts = TtsSpeaker(
            context = this,
            wanted = { uiLang },
            onCaption = { text -> binding.caption.text = text },
            onVoiceChoice = ::onVoiceChoice,
        )
        prefs.takePendingAnnouncement()?.let { tts.say(it) }
        if (savedInstanceState == null && !prefs.onboarded) {
            prefs.onboarded = true // shown once, even if the user backs out of it
            open(Dest.Onboarding)
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        tones.release()
        super.onDestroy()
    }

    override fun askForWords(owner: LifecycleOwner, onText: (String) -> Unit) = Unit

    // ---- Navigation -------------------------------------------------------------------------------

    override fun open(dest: Dest) {
        when (dest) {
            Dest.Home -> navController.popBackStack(R.id.home, false)
            Dest.ScanHub -> go(R.id.scan_hub)
            is Dest.Scan -> go(R.id.scan, ScanFragmentArgs(dest.mode).toBundle())
            Dest.Walk -> go(R.id.walk)
            is Dest.Search -> go(R.id.search, SearchFragmentArgs(dest.query).toBundle())
            is Dest.Saved -> go(R.id.saved, SavedFragmentArgs(dest.tab?.ordinal ?: -1).toBundle())
            Dest.Settings -> go(R.id.settings)
            Dest.History -> go(R.id.history)
            is Dest.AddPerson -> go(R.id.add_person, AddPersonFragmentArgs(dest.name).toBundle())
            is Dest.AddItem -> go(R.id.add_item, AddItemFragmentArgs(dest.kind, dest.name).toBundle())
            Dest.Reader -> go(R.id.reader)
            Dest.Onboarding -> go(R.id.onboarding)
        }
    }

    override fun back() {
        if (!navController.popBackStack()) finish()
    }

    /** popUpTo the same destination, so "full scan" said twice never stacks two scan screens. */
    private fun go(id: Int, args: Bundle? = null) {
        val options = NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(id, true).build()
        navController.navigate(id, args, options)
    }

    /** The screen on top, which gets voice commands first. */
    private fun currentScreen(): Fragment? =
        supportFragmentManager.findFragmentById(R.id.nav_host)?.childFragmentManager?.primaryNavigationFragment

    // ---- Voice commands ---------------------------------------------------------------------------

    /** Runs one parsed command: global ones here, the rest on the current screen first. Main thread. */
    fun handleCommand(command: VoiceCommand) {
        if (!CommandRouter.isGlobal(command)) {
            val screen = currentScreen() as? VoiceHandler
            if (screen?.onVoiceCommand(command) == true) return
        }
        perform(CommandRouter.route(command))
    }

    private fun perform(route: Route) {
        when (route) {
            is Route.Open -> open(route.dest)
            Route.GoBack -> back()
            Route.RepeatLast -> if (!tts.repeatLast()) say(Phrase.NOTHING_TO_REPEAT)
            is Route.SpeakHelp -> say(Phrase.HELP_GENERAL)
            is Route.SetLearner -> setLearner(route.on)
            Route.StopListening -> setVoiceOn(false)
            is Route.SwitchLanguage -> setLanguage(AppLanguage.forLang(route.lang))
            is Route.OpenAndSay -> {
                open(route.dest)
                say(route.phrase)
            }
            is Route.Say -> say(route.phrase)
            Route.StopSpeaking -> tts.stop()
        }
    }

    private fun say(phrase: Phrase) = tts.say(ShellPhrases.text(phrase, lang))

    // ---- App settings -----------------------------------------------------------------------------

    val languageChoice: LanguageChoice
        get() = AppLanguage.choiceOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /** Switches the UI, speech and recognition language; the activity is recreated by AppCompat. */
    fun setLanguage(choice: LanguageChoice) {
        val tag = AppLanguage.tag(choice)
        val target = if (choice == LanguageChoice.SYSTEM) {
            Lang.fromTag(ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language)
        } else {
            Lang.fromTag(tag)
        }
        val sentence = ShellPhrases.text(Phrase.LANGUAGE_SET, target)
        if (choice == languageChoice) {
            tts.say(sentence)
            return
        }
        prefs.setPendingAnnouncement(sentence)
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    val highContrast: Boolean get() = prefs.highContrast

    fun setHighContrast(on: Boolean) {
        if (prefs.highContrast == on) return
        prefs.highContrast = on
        recreate()
    }

    val learnerOn: Boolean get() = prefs.learnerOn

    fun setLearner(on: Boolean) {
        prefs.learnerOn = on
        say(if (on) Phrase.LEARNER_ON else Phrase.LEARNER_OFF)
    }

    val voiceOn: Boolean get() = prefs.voiceOn

    fun setVoiceOn(on: Boolean) {
        prefs.voiceOn = on
        say(if (on) Phrase.VOICE_ON else Phrase.VOICE_OFF)
    }

    private fun onVoiceChoice(choice: VoiceChoice) {
        voiceChoice = choice
        if (choice.notice == null) return
        Snackbar.make(binding.root, R.string.voice_korean_voice_missing, Snackbar.LENGTH_LONG)
            .setAction(R.string.voice_install) {
                runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            }
            .show()
    }
}
```

Replace the whole file `app/src/main/res-i/values-ko/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. 해요체, short and friendly. -->
<resources>
    <string name="app_name">눈길</string>

    <string name="nav_title_home">눈길</string>
    <string name="nav_title_onboarding">시작하기</string>
    <string name="nav_title_scan_hub">둘러보기</string>
    <string name="nav_title_settings">설정</string>
    <string name="nav_title_history">기록</string>
    <string name="nav_title_scan">주변 둘러보기</string>
    <string name="nav_title_walk">걷기 모드</string>
    <string name="nav_title_search">찾기</string>
    <string name="nav_title_search_camera">찾는 중</string>
    <string name="nav_title_saved">저장한 것</string>
    <string name="nav_title_person">사람</string>
    <string name="nav_title_add_person">사람 추가</string>
    <string name="nav_title_enroll">얼굴 등록</string>
    <string name="nav_title_item">물건</string>
    <string name="nav_title_add_item">물건 추가</string>
    <string name="nav_title_item_enroll">물건 등록</string>
    <string name="nav_title_reader">글자 읽기</string>

    <!-- Design components -->
    <string name="ng_ring_description">%1$d퍼센트 살펴봤어요</string>

    <!-- 음성 -->
    <string name="voice_korean_voice_missing">한국어 음성이 설치되어 있지 않아 영어로 말해요.</string>
    <string name="voice_install">설치</string>
</resources>
```

Replace the whole file `app/src/main/res-i/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Prefixes owned by I: app_, nav_title_, home_, hub_, settings_, history_, onboarding_, voice_, help_, ng_. -->
<resources>
    <string name="app_name">Nungil</string>

    <string name="nav_title_home">Nungil</string>
    <string name="nav_title_onboarding">Welcome</string>
    <string name="nav_title_scan_hub">Scan</string>
    <string name="nav_title_settings">Settings</string>
    <string name="nav_title_history">History</string>
    <string name="nav_title_scan">Look around</string>
    <string name="nav_title_walk">Walk mode</string>
    <string name="nav_title_search">Find</string>
    <string name="nav_title_search_camera">Finding</string>
    <string name="nav_title_saved">Saved</string>
    <string name="nav_title_person">Person</string>
    <string name="nav_title_add_person">Add a person</string>
    <string name="nav_title_enroll">Learn a face</string>
    <string name="nav_title_item">Item</string>
    <string name="nav_title_add_item">Add an item</string>
    <string name="nav_title_item_enroll">Learn an item</string>
    <string name="nav_title_reader">Read text</string>

    <!-- Design components -->
    <string name="ng_ring_description">Scanned %1$d percent</string>

    <!-- Voice -->
    <string name="voice_korean_voice_missing">The Korean voice is not installed. Speaking English.</string>
    <string name="voice_install">Install</string>
</resources>
```

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `ShellPhrasesTest` 5 and `CommandRouterTest` 10 pass.

- [ ] **Step 6: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

Fresh install: the app opens on the onboarding stub once, Back goes Home, relaunching goes straight Home. In a debugger, call `setLanguage(LanguageChoice.KOREAN)`: the activity is recreated in Korean and says "언어를 한국어로 바꿨어요.". Call `setHighContrast(true)`: black background, white text, yellow buttons, light status-bar icons. The toolbar never sits under the status bar (Android 15 edge-to-edge).

- [ ] **Step 7: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/ui/CommandRouter.kt app/src/main/java/com/nungil/core/ui/ShellPhrases.kt app/src/main/java/com/nungil/shell/AppPrefs.kt app/src/main/java/com/nungil/shell/MainActivity.kt app/src/main/res-i/values-ko/strings.xml app/src/main/res-i/values/strings.xml app/src/test/java/com/nungil/core/ui/CommandRouterTest.kt app/src/test/java/com/nungil/core/ui/ShellPhrasesTest.kt
git commit -m "Route voice commands, switch language and high contrast, and open onboarding on first launch"
git push -u origin i/I5-shell
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I8: Voice command parser (English and Korean)

**Files:**
- Create: `app/src/main/java/com/nungil/core/voice/VoiceCommandParser.kt`
- Create: `app/src/test/java/com/nungil/core/voice/VoiceCommandParserTest.kt`

**Interfaces:**
- Consumes: `VoiceCommand`, `Dest`, `ItemKind`, `SavedTab`, `ScanMode`, `Lang` (contract).
- Produces: `com.nungil.core.voice.VoiceCommandParser.parse(text: String): VoiceCommand`. English and Korean are detected from the words themselves, so the same call works whatever the recognizer language is. Search payloads are the user's words as spoken (Y strips "my", "내", particles, …); names in `AddPerson`/`AddItem` and `Unknown` keep their original case.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I8-voice-parser
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/nungil/core/voice/VoiceCommandParserTest.kt`:

```kotlin
package com.nungil.core.voice

import com.nungil.contract.Dest
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand
import com.nungil.contract.VoiceCommand.Go
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {
    private fun p(text: String) = VoiceCommandParser.parse(text)

    // ---- microphone words come first ----------------------------------------------------------
    @Test fun stopListening() = assertEquals(VoiceCommand.StopListening, p("Stop listening"))
    @Test fun micOff() = assertEquals(VoiceCommand.StopListening, p("mic off"))
    @Test fun voiceOff() = assertEquals(VoiceCommand.StopListening, p("voice off please"))
    @Test fun microphoneBeatsStop() = assertEquals(VoiceCommand.StopListening, p("stop the microphone"))
    @Test fun koStopListening() = assertEquals(VoiceCommand.StopListening, p("듣기 중지"))
    @Test fun koMicOffWithoutSpaces() = assertEquals(VoiceCommand.StopListening, p("마이크꺼"))
    @Test fun koVoiceOff() = assertEquals(VoiceCommand.StopListening, p("음성 명령 꺼 줘"))

    // ---- search with a payload ------------------------------------------------------------------
    @Test fun findMyBag() = assertEquals(Go(Dest.Search("my bag")), p("Find my bag"))
    @Test fun searchFor() = assertEquals(Go(Dest.Search("the keys")), p("search for the keys"))
    @Test fun whereIs() = assertEquals(Go(Dest.Search("my phone")), p("Where is my phone?"))
    @Test fun wheresWithApostrophe() = assertEquals(Go(Dest.Search("Ali")), p("Where's Ali"))
    @Test fun lookFor() = assertEquals(Go(Dest.Search("a cup")), p("look for a cup"))
    @Test fun findAloneOpensSearch() = assertEquals(Go(Dest.Search(null)), p("find"))
    @Test fun helpMeFind() = assertEquals(Go(Dest.Search("my keys")), p("help me find my keys"))
    @Test fun koFind() = assertEquals(Go(Dest.Search("가방")), p("가방 찾아줘"))
    @Test fun koFindKeepsWordsBefore() = assertEquals(Go(Dest.Search("내 가방")), p("내 가방 찾아 줘"))
    @Test fun koFindWithParticle() = assertEquals(Go(Dest.Search("가방을")), p("가방을 찾아"))
    @Test fun koFindNoSpace() = assertEquals(Go(Dest.Search("가방")), p("가방찾아줘"))
    @Test fun koWhere() = assertEquals(Go(Dest.Search("휴대폰")), p("휴대폰 어디 있어"))
    @Test fun koWhereYa() = assertEquals(Go(Dest.Search("민준이")), p("민준이 어디야"))
    @Test fun koFindAfterVerb() = assertEquals(Go(Dest.Search("내 가방")), p("찾아줘 내 가방"))
    @Test fun koFindScreen() = assertEquals(Go(Dest.Search(null)), p("물건 찾기"))
    @Test fun koFindSomething() = assertEquals(Go(Dest.Search(null)), p("무엇 찾아줘"))

    // ---- questions ------------------------------------------------------------------------------
    @Test fun whoIsThis() = assertEquals(VoiceCommand.WhoIsThis, p("Who is this?"))
    @Test fun whatIsThis() = assertEquals(VoiceCommand.WhatIsThis, p("What's this"))
    @Test fun whatIsThisScreen() = assertEquals(VoiceCommand.Help(null), p("what is this screen"))
    @Test fun whereAmI() = assertEquals(VoiceCommand.Help(null), p("where am I"))
    @Test fun helpTopic() = assertEquals(VoiceCommand.Help("search"), p("What is search?"))
    @Test fun helpTopicTwoWords() = assertEquals(VoiceCommand.Help("walk mode"), p("what is walk mode"))
    @Test fun help() = assertEquals(VoiceCommand.Help(null), p("help"))
    @Test fun whatCanISay() = assertEquals(VoiceCommand.Help(null), p("what can I say"))
    @Test fun koWho() = assertEquals(VoiceCommand.WhoIsThis, p("이 사람 누구야"))
    @Test fun koWhat() = assertEquals(VoiceCommand.WhatIsThis, p("이게 뭐야"))
    @Test fun koHelp() = assertEquals(VoiceCommand.Help(null), p("도움말"))
    @Test fun koHelpMe() = assertEquals(VoiceCommand.Help(null), p("도와 주세요"))
    @Test fun koHelpTopic() = assertEquals(VoiceCommand.Help("검색"), p("검색이 뭐야"))
    @Test fun koHelpTopicTwoWords() = assertEquals(VoiceCommand.Help("걷기 모드"), p("걷기 모드가 뭐예요"))
    @Test fun koThisScreen() = assertEquals(VoiceCommand.Help(null), p("이 화면 뭐야"))
    @Test fun koWhatAlone() = assertEquals(VoiceCommand.WhatIsThis, p("뭐야"))

    // ---- adding people and things ---------------------------------------------------------------
    @Test fun addPersonCalledAli() = assertEquals(Go(Dest.AddPerson("Ali")), p("add person called Ali"))
    @Test fun addPersonWithoutName() = assertEquals(Go(Dest.AddPerson(null)), p("add a new person"))
    @Test fun nameIsKeptEvenWhenItIsACommandWord() = assertEquals(Go(Dest.AddPerson("Me")), p("add person Me"))
    @Test fun saveThisPersonAs() = assertEquals(Go(Dest.AddPerson("Kim Minjun")), p("save this person as Kim Minjun"))
    @Test fun addCar() = assertEquals(Go(Dest.AddItem(ItemKind.CAR, "my Kia")), p("add car my Kia"))
    @Test fun addObject() = assertEquals(Go(Dest.AddItem(ItemKind.OBJECT, "water bottle")), p("new object water bottle"))
    @Test fun koAddPerson() = assertEquals(Go(Dest.AddPerson("민준")), p("사람 추가 민준"))
    @Test fun koAddPersonNameFirst() = assertEquals(Go(Dest.AddPerson("민준")), p("민준 사람 등록"))
    @Test fun koAddPersonWithParticle() = assertEquals(Go(Dest.AddPerson("민준")), p("민준을 사람으로 등록해줘"))
    @Test fun koAddPersonNamedLikeACommand() = assertEquals(Go(Dest.AddPerson("나")), p("사람 추가 나"))
    @Test fun koAddPersonNoSpace() = assertEquals(Go(Dest.AddPerson(null)), p("사람추가"))
    @Test fun koAddCar() = assertEquals(Go(Dest.AddItem(ItemKind.CAR, null)), p("자동차 등록"))
    @Test fun koAddObject() = assertEquals(Go(Dest.AddItem(ItemKind.OBJECT, "물병")), p("물건 추가 물병"))
    @Test fun savedPeopleIsNotAdding() = assertEquals(Go(Dest.Saved(SavedTab.PEOPLE)), p("saved people"))
    @Test fun koSavedPeopleIsNotAdding() = assertEquals(Go(Dest.Saved(SavedTab.PEOPLE)), p("저장한 사람"))

    // ---- language and learner mode --------------------------------------------------------------
    @Test fun korean() = assertEquals(VoiceCommand.SetLanguage(Lang.KO), p("Korean"))
    @Test fun english() = assertEquals(VoiceCommand.SetLanguage(Lang.EN), p("speak English"))
    @Test fun koKorean() = assertEquals(VoiceCommand.SetLanguage(Lang.KO), p("한국어"))
    @Test fun koEnglish() = assertEquals(VoiceCommand.SetLanguage(Lang.EN), p("영어로 바꿔 줘"))
    @Test fun learnerOn() = assertEquals(VoiceCommand.Learner(true), p("learner mode on"))
    @Test fun learnerOff() = assertEquals(VoiceCommand.Learner(false), p("turn off learning mode"))
    @Test fun koLearnerOff() = assertEquals(VoiceCommand.Learner(false), p("학습 모드 꺼"))
    @Test fun koLearnerOn() = assertEquals(VoiceCommand.Learner(true), p("학습 모드 켜 줘"))

    // ---- actions --------------------------------------------------------------------------------
    @Test fun stop() = assertEquals(VoiceCommand.Stop, p("stop"))
    @Test fun stopBeatsDestination() = assertEquals(VoiceCommand.Stop, p("stop full scan"))
    @Test fun switchCamera() = assertEquals(VoiceCommand.SwitchCamera, p("switch camera"))
    @Test fun backCameraIsNotBack() = assertEquals(VoiceCommand.SwitchCamera, p("back camera"))
    @Test fun readText() = assertEquals(VoiceCommand.ReadText, p("read this"))
    @Test fun delete() = assertEquals(VoiceCommand.Delete, p("delete"))
    @Test fun back() = assertEquals(VoiceCommand.Back, p("go back"))
    @Test fun start() = assertEquals(VoiceCommand.Start, p("start"))
    @Test fun repeat() = assertEquals(VoiceCommand.Repeat, p("say that again"))
    @Test fun koStop() = assertEquals(VoiceCommand.Stop, p("멈춰"))
    @Test fun koEnough() = assertEquals(VoiceCommand.Stop, p("그만"))
    @Test fun koSwitchCamera() = assertEquals(VoiceCommand.SwitchCamera, p("카메라 전환"))
    @Test fun koRead() = assertEquals(VoiceCommand.ReadText, p("글자 읽어줘"))
    @Test fun koDelete() = assertEquals(VoiceCommand.Delete, p("삭제해"))
    @Test fun koBack() = assertEquals(VoiceCommand.Back, p("뒤로 가"))
    @Test fun koStart() = assertEquals(VoiceCommand.Start, p("시작"))
    @Test fun koRepeat() = assertEquals(VoiceCommand.Repeat, p("다시 말해 줘"))

    // ---- screens --------------------------------------------------------------------------------
    @Test fun fullScan() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("Full scan"))
    @Test fun startFullScanOpensIt() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("start full scan"))
    @Test fun lookAround() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("look around"))
    @Test fun liveScan() = assertEquals(Go(Dest.Scan(ScanMode.LIVE)), p("live scan"))
    @Test fun scanMenu() = assertEquals(Go(Dest.ScanHub), p("scan menu"))
    @Test fun saved() = assertEquals(Go(Dest.Saved(null)), p("saved"))
    @Test fun savedCars() = assertEquals(Go(Dest.Saved(SavedTab.CARS)), p("saved cars"))
    @Test fun history() = assertEquals(Go(Dest.History), p("scan history"))
    @Test fun settings() = assertEquals(Go(Dest.Settings), p("open settings"))
    @Test fun home() = assertEquals(Go(Dest.Home), p("home"))
    @Test fun walk() = assertEquals(Go(Dest.Walk), p("Walk mode"))
    @Test fun koFullScan() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("주변 둘러보기"))
    @Test fun koFullScanNoSpace() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("전체스캔"))
    @Test fun koLive() = assertEquals(Go(Dest.Scan(ScanMode.LIVE)), p("실시간 안내"))
    @Test fun koSaved() = assertEquals(Go(Dest.Saved(null)), p("저장한 것"))
    @Test fun koSavedMisheard() = assertEquals(Go(Dest.Saved(null)), p("저장된"))
    @Test fun koSavedObjects() = assertEquals(Go(Dest.Saved(SavedTab.OBJECTS)), p("저장한 물건"))
    @Test fun koHistory() = assertEquals(Go(Dest.History), p("기록"))
    @Test fun koSettings() = assertEquals(Go(Dest.Settings), p("설정 열어 줘"))
    @Test fun koHome() = assertEquals(Go(Dest.Home), p("처음으로"))
    @Test fun koWalk() = assertEquals(Go(Dest.Walk), p("걷기 모드"))

    // ---- mishearings (build guide §10.5) ----------------------------------------------------------
    @Test fun saveItMeansSaved() = assertEquals(Go(Dest.Saved(null)), p("save it"))
    @Test fun safeMeansSaved() = assertEquals(Go(Dest.Saved(null)), p("safe"))
    @Test fun savesMeansSaved() = assertEquals(Go(Dest.Saved(null)), p("saves"))
    @Test fun workingModeMeansWalk() = assertEquals(Go(Dest.Walk), p("Open Working mode"))
    @Test fun workModeMeansWalk() = assertEquals(Go(Dest.Walk), p("work mode"))
    @Test fun forScan() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("for scan"))
    @Test fun foolScan() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("fool scan"))
    @Test fun fullscanOneWord() = assertEquals(Go(Dest.Scan(ScanMode.FULL)), p("fullscan"))
    @Test fun bareKindOpensItsTab() {
        assertEquals(Go(Dest.Saved(SavedTab.PEOPLE)), p("person"))
        assertEquals(Go(Dest.Saved(SavedTab.CARS)), p("car"))
        assertEquals(Go(Dest.Saved(SavedTab.OBJECTS)), p("object"))
        assertEquals(Go(Dest.Saved(SavedTab.PEOPLE)), p("사람"))
        assertEquals(Go(Dest.Saved(SavedTab.CARS)), p("자동차"))
        assertEquals(Go(Dest.Saved(SavedTab.OBJECTS)), p("물건"))
    }
    @Test fun corazonIsUnknown() = assertEquals(VoiceCommand.Unknown("Corazon"), p("Corazon"))
    @Test fun googleIsUnknown() = assertEquals(VoiceCommand.Unknown("Google"), p("Google"))
    @Test fun koStopSignIsASearch() = assertEquals(Go(Dest.Search("정지 표지판")), p("정지 표지판 찾아줘"))

    // ---- dictation text survives as Unknown -------------------------------------------------------
    @Test fun nameIsUnknownWithOriginalCase() = assertEquals(VoiceCommand.Unknown("Ali"), p("Ali"))
    @Test fun twoWordsAreUnknown() = assertEquals(VoiceCommand.Unknown("water bottle"), p("water bottle"))
    @Test fun koNameIsUnknown() = assertEquals(VoiceCommand.Unknown("민준"), p("민준"))
    @Test fun emptyIsUnknown() = assertEquals(VoiceCommand.Unknown(""), p("   "))
    @Test fun punctuationIsDropped() = assertEquals(VoiceCommand.Unknown("Kim Minjun"), p("  Kim,  Minjun!  "))
}
```

- [ ] **Step 3: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'VoiceCommandParser'`.

- [ ] **Step 4: Implement**

Create `app/src/main/java/com/nungil/core/voice/VoiceCommandParser.kt`:

```kotlin
package com.nungil.core.voice

import com.nungil.contract.Dest
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand
import java.util.Locale

/**
 * Turns one recognised phrase (English or Korean) into a [VoiceCommand] by keywords, never by whole
 * sentences (build guide §10.5). Rules run in this order, first match wins:
 *
 * 1. microphone words ("stop listening", "마이크 꺼"), so they can never stop a scan instead;
 * 2. search with a payload ("find my bag", "가방 찾아줘");
 * 3. questions (who / what is this, help, "what is <topic>");
 * 4. adding a person, car or object, with the words that are not command words as the name;
 * 5. learner mode, then language;
 * 6. actions: stop, switch camera, read text, delete, back (verbs beat destinations: "stop full scan" stops);
 * 7. destinations, including known mishearings ("safe", "working mode", "fool scan", bare "person");
 * 8. start and repeat;
 * 9. anything else is Unknown with the original text (dictation for names and queries).
 */
object VoiceCommandParser {

    fun parse(text: String): VoiceCommand {
        val original = clean(text)
        if (original.isEmpty()) return VoiceCommand.Unknown("")
        val s = Said(original)
        return micOff(s)
            ?: search(s)
            ?: question(s)
            ?: adding(s)
            ?: learner(s)
            ?: language(s)
            ?: action(s)
            ?: destination(s)
            ?: late(s)
            ?: VoiceCommand.Unknown(original)
    }

    /** Drops apostrophes ("where's" -> "wheres"), turns other punctuation into spaces, collapses spaces. */
    private fun clean(text: String): String =
        text.replace("'", "").replace("’", "")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .trim()
            .replace(Regex(" +"), " ")

    private class Said(val original: String) {
        val raw: List<String> = original.split(' ')
        val words: List<String> = original.lowercase(Locale.ROOT).split(' ')
        val compact: String = words.joinToString("")

        fun has(vararg w: String): Boolean = w.any { it in words }

        fun seq(vararg w: String): Boolean =
            (0..words.size - w.size).any { i -> w.indices.all { words[i + it] == w[it] } }

        fun ko(vararg k: String): Boolean = k.any { compact.contains(it) }
    }

    // 1 ------------------------------------------------------------------------------------------------
    private fun micOff(s: Said): VoiceCommand? {
        val en = s.has("listening", "microphone") || s.seq("mic", "off") || s.seq("voice", "off") || s.seq("stop", "voice")
        val ko = s.ko(
            "듣기중지", "듣기그만", "듣지마", "그만들어", "마이크꺼", "마이크끄", "마이크중지",
            "음성꺼", "음성끄", "음성명령꺼", "음성명령끄",
        )
        return if (en || ko) VoiceCommand.StopListening else null
    }

    // 2 ------------------------------------------------------------------------------------------------
    private val vagueKo = setOf("물건", "것", "뭐", "무엇", "뭔가")
    private val searchFillersKo = setOf("줘", "좀", "있어", "있니", "있어요", "있나요", "해줘", "봐", "줄래")

    private fun search(s: Said): VoiceCommand? {
        englishSearch(s)?.let { return it }
        return koreanSearch(s)
    }

    private fun englishSearch(s: Said): VoiceCommand? {
        val w = s.words
        // "what is search" is a help question, not a search.
        if (w.first() in setOf("what", "whats", "how", "explain")) return null
        for (i in w.indices) {
            var start = when {
                w[i] == "find" || w[i] == "locate" -> i + 1
                w[i] == "search" -> i + 1
                w[i] == "look" && w.getOrNull(i + 1) == "for" -> i + 2
                w[i] == "where" || w[i] == "wheres" -> i + 1
                else -> continue
            }
            if (w[i] == "where" && w.getOrNull(start) == "am") return null // "where am I" is help
            if (w[i] == "search" && w.getOrNull(start) == "for") start++
            if (w[i] == "where" && w.getOrNull(start) in setOf("is", "are")) start++
            return goSearch(s.raw.drop(start).joinToString(" "))
        }
        return null
    }

    private fun koreanSearch(s: Said): VoiceCommand? {
        val j = s.raw.indexOfFirst { it.contains("찾") || it.contains("어디") }
        if (j < 0) return null
        val trigger = s.raw[j]
        val cut = listOf(trigger.indexOf("찾"), trigger.indexOf("어디")).filter { it >= 0 }.min()
        val before = (s.raw.take(j) + trigger.substring(0, cut)).filter { it.isNotBlank() }
        val payload = if (before.isNotEmpty()) {
            before
        } else {
            s.raw.drop(j + 1).filter { it !in searchFillersKo }
        }
        val query = payload.joinToString(" ")
        if (query == "여기" || query == "여기가") return VoiceCommand.Help(null)
        return goSearch(if (query in vagueKo) "" else query)
    }

    private fun goSearch(query: String): VoiceCommand =
        VoiceCommand.Go(Dest.Search(query.trim().ifEmpty { null }))

    // 3 ------------------------------------------------------------------------------------------------
    private val topicParticlesKo = listOf("이란", "란", "이", "가", "은", "는")

    private fun question(s: Said): VoiceCommand? {
        val w = s.words
        val helpScreen = s.seq("what", "is", "this", "screen") || s.seq("whats", "this", "screen") ||
            s.seq("where", "am", "i") || s.seq("what", "can", "i", "say") || s.seq("which", "screen") ||
            s.ko("이화면", "여기어디", "뭐라고말", "무슨말", "도움말", "도와주", "도와줘")
        if (helpScreen) return VoiceCommand.Help(null)

        val who = s.seq("who", "is", "this") || s.seq("who", "is", "that") || s.seq("who", "is", "it") ||
            s.seq("whos", "this") || s.seq("whos", "that") || s.ko("누구", "누군")
        if (who) return VoiceCommand.WhoIsThis

        val what = s.seq("what", "is", "this") || s.seq("what", "is", "that") || s.seq("whats", "this") ||
            s.seq("whats", "that") || s.seq("what", "am", "i", "looking", "at") ||
            s.ko("이게뭐", "이거뭐", "이건뭐", "이것뭐", "뭐야이거", "뭐야이게")
        if (what) return VoiceCommand.WhatIsThis

        val topicWords = when {
            w.size > 2 && w[0] == "what" && w[1] == "is" -> w.drop(2)
            w.size > 1 && w[0] == "whats" -> w.drop(1)
            w.size > 3 && w[0] == "how" && w[1] == "does" && w.last() == "work" -> w.subList(2, w.size - 1)
            w.size > 1 && w[0] == "explain" -> w.drop(1)
            else -> null
        }
        if (topicWords != null) {
            val topic = topicWords.dropWhile { it == "the" || it == "a" }.joinToString(" ")
            if (topic.isNotEmpty()) return VoiceCommand.Help(topic)
        }

        if (!s.ko("뭐라고")) {
            val k = s.raw.indexOfFirst { it.contains("뭐") || it.contains("설명") }
            if (k == 0) return VoiceCommand.WhatIsThis
            if (k > 0) {
                val parts = s.raw.take(k).toMutableList()
                val last = parts.last()
                val particle = topicParticlesKo.firstOrNull { last.endsWith(it) && last.length > it.length }
                if (particle != null) parts[parts.size - 1] = last.dropLast(particle.length)
                return VoiceCommand.Help(parts.joinToString(" "))
            }
        }

        return if (s.has("help")) VoiceCommand.Help(null) else null
    }

    // 4 ------------------------------------------------------------------------------------------------
    private enum class Kind { PERSON, CAR, OBJECT }

    private val addVerbsEn = setOf("add", "create", "new", "register", "save", "make", "remember", "teach", "learn")
    private val personEn = setOf("person", "people", "persons", "face", "faces", "friend", "friends", "someone")
    private val carEn = setOf("car", "cars", "vehicle", "vehicles")
    private val objectEn = setOf("object", "objects", "item", "items", "thing", "things")
    private val nameLeadFillersEn = setOf("called", "named", "as", "is", "a", "an", "the", "name", "whose")
    private val betweenFillersEn = setOf("a", "an", "the", "new", "this", "that", "my")
    private val nameFillersKo = setOf("좀", "줘", "해줘", "해", "해주세요", "주세요", "이름은", "이름", "새", "새로", "이", "그", "저")
    private val nameSuffixesKo = listOf("이라는", "라는", "이라고", "라고", "을", "를")

    private fun kindEn(word: String): Kind? = when (word) {
        in personEn -> Kind.PERSON
        in carEn -> Kind.CAR
        in objectEn -> Kind.OBJECT
        else -> null
    }

    private fun kindKo(word: String): Kind? = when {
        word.contains("사람") || word.contains("얼굴") || word.contains("친구") -> Kind.PERSON
        word.contains("자동차") || word == "차" || word.startsWith("차를") || word.startsWith("차로") -> Kind.CAR
        word.contains("물건") || word.contains("사물") -> Kind.OBJECT
        else -> null
    }

    private fun isAddVerbKo(word: String): Boolean =
        word.contains("추가") || word.contains("등록") || word.contains("만들") || word.contains("기억해") ||
            word.contains("저장해") || word.contains("저장하") || word == "새" || word == "새로"

    private fun adding(s: Said): VoiceCommand? {
        val w = s.words
        val verbEn = w.indexOfFirst { it in addVerbsEn }
        val kindIdxEn = w.indexOfFirst { kindEn(it) != null }
        if (verbEn >= 0 && kindIdxEn >= 0) {
            val after = s.raw.drop(kindIdxEn + 1).dropWhile { it.lowercase(Locale.ROOT) in nameLeadFillersEn }
            val name = if (after.isNotEmpty()) {
                after
            } else {
                s.raw.subList(minOf(verbEn + 1, kindIdxEn), kindIdxEn)
                    .filter { it.lowercase(Locale.ROOT) !in betweenFillersEn }
            }
            return add(kindEn(w[kindIdxEn])!!, name.joinToString(" "))
        }

        val verbKo = s.raw.indexOfFirst { isAddVerbKo(it) }
        val kindIdxKo = s.raw.indexOfFirst { kindKo(it) != null }
        if (verbKo >= 0 && kindIdxKo >= 0) {
            val name = s.raw.filterIndexed { i, word -> i != verbKo && i != kindIdxKo && word !in nameFillersKo }
                .map { word ->
                    val suffix = nameSuffixesKo.firstOrNull { word.endsWith(it) && word.length > it.length }
                    if (suffix == null) word else word.dropLast(suffix.length)
                }
            return add(kindKo(s.raw[kindIdxKo])!!, name.joinToString(" "))
        }
        return null
    }

    private fun add(kind: Kind, name: String): VoiceCommand {
        val n = name.trim().ifEmpty { null }
        return VoiceCommand.Go(
            when (kind) {
                Kind.PERSON -> Dest.AddPerson(n)
                Kind.CAR -> Dest.AddItem(ItemKind.CAR, n)
                Kind.OBJECT -> Dest.AddItem(ItemKind.OBJECT, n)
            },
        )
    }

    // 5 ------------------------------------------------------------------------------------------------
    private fun learner(s: Said): VoiceCommand? {
        if (s.has("learner", "learning") || s.seq("tutorial", "mode")) {
            return VoiceCommand.Learner(on = !s.has("off", "disable", "stop", "end"))
        }
        if (s.ko("학습모드", "배움모드", "연습모드")) {
            return VoiceCommand.Learner(on = !s.ko("꺼", "끄", "중지", "그만"))
        }
        return null
    }

    private fun language(s: Said): VoiceCommand? = when {
        s.has("korean") || s.ko("한국어", "한국말") -> VoiceCommand.SetLanguage(Lang.KO)
        s.has("english") || s.ko("영어") -> VoiceCommand.SetLanguage(Lang.EN)
        else -> null
    }

    // 6 ------------------------------------------------------------------------------------------------
    private fun action(s: Said): VoiceCommand? = when {
        s.has("stop", "cancel", "pause", "enough", "quiet") ||
            s.ko("멈춰", "멈춤", "정지", "그만", "중지", "스톱", "취소") -> VoiceCommand.Stop
        s.seq("switch", "camera") || s.seq("flip", "camera") || s.seq("change", "camera") ||
            s.seq("front", "camera") || s.seq("back", "camera") || s.seq("rear", "camera") || s.has("selfie") ||
            s.ko("카메라전환", "카메라바꿔", "카메라바꾸", "전면카메라", "후면카메라", "셀카") -> VoiceCommand.SwitchCamera
        s.has("read", "text", "qr", "barcode", "code") ||
            s.ko("글자", "읽어", "텍스트", "큐알", "바코드") -> VoiceCommand.ReadText
        s.has("delete", "remove", "erase") || s.ko("삭제", "지워", "지우") -> VoiceCommand.Delete
        s.has("back", "previous") || s.ko("뒤로", "이전", "돌아가") -> VoiceCommand.Back
        else -> null
    }

    // 7 ------------------------------------------------------------------------------------------------
    private val savedEn = setOf("saved", "save", "safe", "saves", "favorites", "favourites")

    private fun tabOf(s: Said): SavedTab? = when {
        s.words.any { kindEn(it) == Kind.PERSON } || s.ko("사람", "얼굴", "친구") -> SavedTab.PEOPLE
        s.words.any { kindEn(it) == Kind.CAR } || s.ko("자동차") || "차" in s.words -> SavedTab.CARS
        s.words.any { kindEn(it) == Kind.OBJECT } || s.ko("물건", "사물") -> SavedTab.OBJECTS
        else -> null
    }

    private fun destination(s: Said): VoiceCommand? {
        val dest = when {
            s.seq("scan", "menu") || s.ko("스캔메뉴", "둘러보기메뉴") -> Dest.ScanHub
            s.has("walk", "walking") || (s.has("mode") && s.has("work", "working")) ||
                s.ko("걷기", "보행", "걸을", "워킹", "산책") -> Dest.Walk
            s.has("history") || s.seq("past", "scans") || s.ko("기록", "히스토리") -> Dest.History
            s.has("settings", "setting", "options", "preferences") || s.ko("설정", "세팅", "셋팅", "옵션") -> Dest.Settings
            s.has("home") || s.seq("main", "menu") || s.seq("start", "screen") ||
                s.ko("홈", "처음", "메인", "첫화면", "시작화면") -> Dest.Home
            s.has("live", "realtime") || s.seq("real", "time") || s.ko("실시간", "라이브") -> Dest.Scan(ScanMode.LIVE)
            s.words.any { it.contains("scan") } || s.seq("look", "around") || s.seq("around", "me") ||
                s.has("surroundings") || s.ko("전체스캔", "스캔", "스켄", "둘러보", "둘러봐", "주변", "한바퀴") ->
                Dest.Scan(ScanMode.FULL)
            s.words.any { it in savedEn } || s.ko("저장") -> Dest.Saved(tabOf(s))
            else -> tabOf(s)?.let { Dest.Saved(it) }
        }
        return dest?.let { VoiceCommand.Go(it) }
    }

    // 8 ------------------------------------------------------------------------------------------------
    private fun late(s: Said): VoiceCommand? = when {
        s.has("start", "begin", "go") || s.ko("시작", "스타트", "눌러") -> VoiceCommand.Start
        s.has("repeat", "again") || s.seq("what", "did", "you", "say") || s.ko("다시", "반복", "뭐라고") -> VoiceCommand.Repeat
        else -> null
    }
}
```

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `VoiceCommandParserTest` passes (117 cases: every example in the team plan, every mishearing in build guide §10.5, Korean with and without spaces and particles).

- [ ] **Step 6: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

Nothing to see yet; the parser is wired to the microphone in I9.

- [ ] **Step 7: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/voice/VoiceCommandParser.kt app/src/test/java/com/nungil/core/voice/VoiceCommandParserTest.kt
git commit -m "Understand spoken commands in English and Korean, including common mishearings"
git push -u origin i/I8-voice-parser
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I9: Voice input: always-on microphone and dictation

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/RecognizerPolicy.kt`
- Modify: `app/src/main/java/com/nungil/shell/MainActivity.kt`
- Modify: `app/src/main/java/com/nungil/speech/TtsSpeaker.kt`
- Create: `app/src/main/java/com/nungil/speech/VoiceInput.kt`
- Modify: `app/src/main/res-i/values-ko/strings.xml`
- Modify: `app/src/main/res-i/values/strings.xml`
- Create: `app/src/test/java/com/nungil/core/ui/RecognizerPolicyTest.kt`

**Interfaces:**
- Consumes: `VoiceCommandParser` (I8), `TtsSpeaker.isBusy`, `ShellPhrases`.
- Produces: `RecognizerPolicy` (delays 1500/2500/700 ms, rebuild after 3 errors, English fallback, `shouldWait`), `PermissionOutcome.of(granted, showRationale)`; `speech.VoiceInput(context, language, isSpeaking, stopSpeaking, onHeard, onProblem)` with `start()`, `stop()`, `listenOnce(onText)`, `destroy()`, `isOn`; the real `AppServices.askForWords(owner, onText)`; `MainActivity.voiceOn: StateFlow<Boolean>`. Log line on every phrase: `I/Nungil: Heard "<text>" -> <command>`.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I9-voice-input
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/nungil/core/ui/RecognizerPolicyTest.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizerPolicyTest {
    private val policy = RecognizerPolicy()

    @Test fun delaysFromTheGuide() {
        assertEquals(1_500L, RecognizerPolicy.DELAY_AFTER_ENABLE_MS)
        assertEquals(2_500L, policy.afterResult())
        assertEquals(700L, policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).delayMs)
    }

    @Test fun rebuildsAfterThreeErrorsInARow() {
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_SPEECH_TIMEOUT, Lang.EN).rebuild)
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).rebuild)
        assertTrue(policy.afterError(RecognizerPolicy.ERROR_CLIENT, Lang.EN).rebuild)
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).rebuild)
    }

    @Test fun aResultResetsTheErrorCount() {
        policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN)
        policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN)
        policy.afterResult()
        assertFalse(policy.afterError(RecognizerPolicy.ERROR_NO_MATCH, Lang.EN).rebuild)
    }

    @Test fun missingPermissionStopsTheLoop() =
        assertEquals(ListenAction.STOP_NO_PERMISSION, policy.afterError(RecognizerPolicy.ERROR_INSUFFICIENT_PERMISSIONS, Lang.EN).action)

    @Test fun missingKoreanFallsBackToEnglishOnce() {
        val first = policy.afterError(RecognizerPolicy.ERROR_LANGUAGE_UNAVAILABLE, Lang.KO)
        assertEquals(ListenAction.FALL_BACK_TO_ENGLISH, first.action)
        assertTrue(first.rebuild)
        assertEquals(Lang.EN, policy.language(Lang.KO))
        assertEquals(ListenAction.RETRY, policy.afterError(RecognizerPolicy.ERROR_LANGUAGE_NOT_SUPPORTED, Lang.KO).action)
    }

    @Test fun englishNeverFallsBack() {
        assertEquals(ListenAction.RETRY, policy.afterError(RecognizerPolicy.ERROR_LANGUAGE_NOT_SUPPORTED, Lang.EN).action)
        assertEquals(Lang.KO, RecognizerPolicy().language(Lang.KO))
    }

    @Test fun waitsForTheAppToFinishSpeakingButNotForever() {
        assertTrue(policy.shouldWait(speaking = true, waitedMs = 0))
        assertFalse(policy.shouldWait(speaking = false, waitedMs = 0))
        assertFalse(policy.shouldWait(speaking = true, waitedMs = RecognizerPolicy.MAX_WAIT_FOR_SPEECH_MS))
    }

    @Test fun permissionOutcomes() {
        assertEquals(PermissionOutcome.GRANTED, PermissionOutcome.of(granted = true, showRationale = false))
        assertEquals(PermissionOutcome.DENIED, PermissionOutcome.of(granted = false, showRationale = true))
        assertEquals(PermissionOutcome.BLOCKED, PermissionOutcome.of(granted = false, showRationale = false))
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'RecognizerPolicy'` and `'PermissionOutcome'`.

- [ ] **Step 4: Implement**

Create `app/src/main/java/com/nungil/core/ui/RecognizerPolicy.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang

enum class ListenAction { RETRY, STOP_NO_PERMISSION, FALL_BACK_TO_ENGLISH }

data class ListenDecision(val action: ListenAction, val delayMs: Long, val rebuild: Boolean)

/**
 * Keeps the always-on microphone alive (build guide §10.3): restart after every result and error with
 * the guide's delays, rebuild a stuck recognizer after 3 errors in a row, stop on a permission error,
 * and fall back to English once when Korean recognition is not installed.
 */
class RecognizerPolicy {
    private var errorsInARow = 0

    var englishFallback = false
        private set

    /** Delay before listening again after a phrase was heard. */
    fun afterResult(): Long {
        errorsInARow = 0
        return DELAY_AFTER_COMMAND_MS
    }

    fun afterError(code: Int, wanted: Lang): ListenDecision {
        if (code == ERROR_INSUFFICIENT_PERMISSIONS) return ListenDecision(ListenAction.STOP_NO_PERMISSION, 0, false)
        val languageMissing = code == ERROR_LANGUAGE_NOT_SUPPORTED || code == ERROR_LANGUAGE_UNAVAILABLE
        if (languageMissing && wanted == Lang.KO && !englishFallback) {
            englishFallback = true
            errorsInARow = 0
            return ListenDecision(ListenAction.FALL_BACK_TO_ENGLISH, DELAY_AFTER_ERROR_MS, rebuild = true)
        }
        errorsInARow++
        val rebuild = errorsInARow >= REBUILD_AFTER_ERRORS
        if (rebuild) errorsInARow = 0
        return ListenDecision(ListenAction.RETRY, DELAY_AFTER_ERROR_MS, rebuild)
    }

    /** Recognition language: what the user chose, unless Korean recognition turned out to be missing. */
    fun language(wanted: Lang): Lang = if (englishFallback) Lang.EN else wanted

    /** Let the app finish speaking before opening the microphone, but never wait longer than the limit. */
    fun shouldWait(speaking: Boolean, waitedMs: Long): Boolean = speaking && waitedMs < MAX_WAIT_FOR_SPEECH_MS

    companion object {
        const val DELAY_AFTER_ENABLE_MS = 1_500L
        const val DELAY_AFTER_COMMAND_MS = 2_500L
        const val DELAY_AFTER_ERROR_MS = 700L
        const val REBUILD_AFTER_ERRORS = 3
        const val WAIT_POLL_MS = 500L
        const val MAX_WAIT_FOR_SPEECH_MS = SpeechQueue.SHUTDOWN_SAFETY_MS

        // android.speech.SpeechRecognizer error codes, copied so this stays pure Kotlin.
        const val ERROR_SPEECH_TIMEOUT = 6
        const val ERROR_NO_MATCH = 7
        const val ERROR_CLIENT = 5
        const val ERROR_INSUFFICIENT_PERMISSIONS = 9
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}

enum class PermissionOutcome {
    GRANTED,
    DENIED,

    /** Denied with "don't ask again": only the app settings page can allow it now. */
    BLOCKED;

    companion object {
        fun of(granted: Boolean, showRationale: Boolean): PermissionOutcome = when {
            granted -> GRANTED
            showRationale -> DENIED
            else -> BLOCKED
        }
    }
}
```

Replace the whole file `app/src/main/java/com/nungil/shell/MainActivity.kt`:

```kotlin
package com.nungil.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppNavigator
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.Beeper
import com.nungil.contract.app.Haptics
import com.nungil.contract.app.Speaker
import com.nungil.contract.app.VoiceHandler
import com.nungil.core.ui.AppLanguage
import com.nungil.core.ui.CommandRouter
import com.nungil.core.ui.LanguageChoice
import com.nungil.core.ui.PermissionOutcome
import com.nungil.core.ui.Phrase
import com.nungil.core.ui.Route
import com.nungil.core.ui.ShellPhrases
import com.nungil.core.ui.VoiceChoice
import com.nungil.core.voice.VoiceCommandParser
import com.nungil.databinding.ActivityMainBinding
import com.nungil.design.openAppSettings
import com.nungil.items.AddItemFragmentArgs
import com.nungil.people.AddPersonFragmentArgs
import com.nungil.saved.SavedFragmentArgs
import com.nungil.scan.ScanFragmentArgs
import com.nungil.search.SearchFragmentArgs
import com.nungil.speech.ToneBeeper
import com.nungil.speech.TtsSpeaker
import com.nungil.speech.VibratorHaptics
import com.nungil.speech.VoiceInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner I. The single activity: theme (light, dark or high contrast), toolbar, nav host, caption bar,
 * the AppServices every screen uses, the always-on microphone and voice-command routing.
 */
class MainActivity : AppCompatActivity(), AppServices, AppNavigator {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var prefs: AppPrefs
    private lateinit var tts: TtsSpeaker
    private lateinit var vibration: VibratorHaptics
    private val tones = ToneBeeper()
    private var voiceChoice: VoiceChoice? = null
    private lateinit var voice: VoiceInput
    private val voiceOnState = MutableStateFlow(false)

    /** A screen waiting for words (askForWords) while the always-on listener runs. */
    private var dictation: ((String) -> Unit)? = null
    private var dictationOwner: LifecycleOwner? = null

    private var afterMicGranted: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onMicPermission(granted)
    }

    /** The language the user chose for the screens (per-app locale, else the phone's language). */
    val uiLang: Lang
        get() {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val tag = if (appLocales.isEmpty) {
                ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language
            } else {
                appLocales[0]?.language
            }
            return Lang.fromTag(tag)
        }

    /** The language of spoken sentences: the UI language, unless its voice is missing (then English). */
    override val lang: Lang
        get() = voiceChoice?.speak ?: uiLang
    override val speaker: Speaker get() = tts
    override val haptics: Haptics get() = vibration
    override val beeper: Beeper get() = tones
    override val navigator: AppNavigator get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = AppPrefs(this)
        val highContrast = prefs.highContrast
        if (highContrast) setTheme(R.style.Theme_Nungil_HighContrast)
        // High contrast is black in light and dark mode, so its bar icons are always light.
        val bars = if (highContrast) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(bars, bars)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 35 draws edge-to-edge on Android 15: keep content out from under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        navController = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        binding.toolbar.setupWithNavController(navController, AppBarConfiguration(setOf(R.id.home)))

        vibration = VibratorHaptics(this)
        tts = TtsSpeaker(
            context = this,
            wanted = { uiLang },
            onCaption = { text -> binding.caption.text = text },
            onVoiceChoice = ::onVoiceChoice,
        )
        voice = VoiceInput(
            context = this,
            language = { uiLang },
            isSpeaking = { tts.isBusy },
            stopSpeaking = { tts.stop() },
            onHeard = ::onHeard,
            onProblem = ::onVoiceProblem,
        )
        prefs.takePendingAnnouncement()?.let { tts.say(it) }
        if (savedInstanceState == null && !prefs.onboarded) {
            prefs.onboarded = true // shown once, even if the user backs out of it
            open(Dest.Onboarding)
        }
    }

    override fun onResume() {
        super.onResume()
        // Listen only while the app is in front; the user's choice survives in AppPrefs.
        if (prefs.voiceOn && hasMic()) voice.start()
        voiceOnState.value = voice.isOn
    }

    override fun onPause() {
        voice.stop()
        super.onPause()
    }

    override fun onDestroy() {
        voice.destroy()
        tts.shutdown()
        tones.release()
        super.onDestroy()
    }

    override fun askForWords(owner: LifecycleOwner, onText: (String) -> Unit) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        if (voice.isOn) {
            // One microphone, one owner: the always-on listener hands the next non-command words over.
            dictation = onText
            dictationOwner = owner
            owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (dictationOwner === owner) {
                        dictation = null
                        dictationOwner = null
                    }
                }
            })
            return
        }
        ensureMic {
            haptics.buzz(Buzz.TAP)
            voice.listenOnce { text ->
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) onText(text)
            }
        }
    }

    // ---- Navigation -------------------------------------------------------------------------------

    override fun open(dest: Dest) {
        when (dest) {
            Dest.Home -> navController.popBackStack(R.id.home, false)
            Dest.ScanHub -> go(R.id.scan_hub)
            is Dest.Scan -> go(R.id.scan, ScanFragmentArgs(dest.mode).toBundle())
            Dest.Walk -> go(R.id.walk)
            is Dest.Search -> go(R.id.search, SearchFragmentArgs(dest.query).toBundle())
            is Dest.Saved -> go(R.id.saved, SavedFragmentArgs(dest.tab?.ordinal ?: -1).toBundle())
            Dest.Settings -> go(R.id.settings)
            Dest.History -> go(R.id.history)
            is Dest.AddPerson -> go(R.id.add_person, AddPersonFragmentArgs(dest.name).toBundle())
            is Dest.AddItem -> go(R.id.add_item, AddItemFragmentArgs(dest.kind, dest.name).toBundle())
            Dest.Reader -> go(R.id.reader)
            Dest.Onboarding -> go(R.id.onboarding)
        }
    }

    override fun back() {
        if (!navController.popBackStack()) finish()
    }

    /** popUpTo the same destination, so "full scan" said twice never stacks two scan screens. */
    private fun go(id: Int, args: Bundle? = null) {
        val options = NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(id, true).build()
        navController.navigate(id, args, options)
    }

    /** The screen on top, which gets voice commands first. */
    private fun currentScreen(): Fragment? =
        supportFragmentManager.findFragmentById(R.id.nav_host)?.childFragmentManager?.primaryNavigationFragment

    // ---- Voice commands ---------------------------------------------------------------------------

    private fun onHeard(text: String) {
        val command = VoiceCommandParser.parse(text)
        Log.i(TAG, "Heard \"$text\" -> $command")
        val claim = dictation
        if (claim != null && command is VoiceCommand.Unknown) {
            dictation = null
            dictationOwner = null
            claim(command.text)
            return
        }
        handleCommand(command)
    }

    private fun onVoiceProblem(phrase: Phrase) {
        if (phrase == Phrase.MIC_NEEDED || phrase == Phrase.VOICE_UNAVAILABLE) {
            prefs.voiceOn = false
            voiceOnState.value = false
        }
        say(phrase)
    }

    /** Runs one parsed command: global ones here, the rest on the current screen first. Main thread. */
    fun handleCommand(command: VoiceCommand) {
        if (!CommandRouter.isGlobal(command)) {
            val screen = currentScreen() as? VoiceHandler
            if (screen?.onVoiceCommand(command) == true) return
        }
        perform(CommandRouter.route(command))
    }

    private fun perform(route: Route) {
        when (route) {
            is Route.Open -> open(route.dest)
            Route.GoBack -> back()
            Route.RepeatLast -> if (!tts.repeatLast()) say(Phrase.NOTHING_TO_REPEAT)
            is Route.SpeakHelp -> say(Phrase.HELP_GENERAL)
            is Route.SetLearner -> setLearner(route.on)
            Route.StopListening -> setVoiceOn(false)
            is Route.SwitchLanguage -> setLanguage(AppLanguage.forLang(route.lang))
            is Route.OpenAndSay -> {
                open(route.dest)
                say(route.phrase)
            }
            is Route.Say -> say(route.phrase)
            Route.StopSpeaking -> tts.stop()
        }
    }

    private fun say(phrase: Phrase) = tts.say(ShellPhrases.text(phrase, lang))

    // ---- App settings -----------------------------------------------------------------------------

    val languageChoice: LanguageChoice
        get() = AppLanguage.choiceOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /** Switches the UI, speech and recognition language; the activity is recreated by AppCompat. */
    fun setLanguage(choice: LanguageChoice) {
        val tag = AppLanguage.tag(choice)
        val target = if (choice == LanguageChoice.SYSTEM) {
            Lang.fromTag(ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language)
        } else {
            Lang.fromTag(tag)
        }
        val sentence = ShellPhrases.text(Phrase.LANGUAGE_SET, target)
        if (choice == languageChoice) {
            tts.say(sentence)
            return
        }
        prefs.setPendingAnnouncement(sentence)
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    val highContrast: Boolean get() = prefs.highContrast

    fun setHighContrast(on: Boolean) {
        if (prefs.highContrast == on) return
        prefs.highContrast = on
        recreate()
    }

    val learnerOn: Boolean get() = prefs.learnerOn

    fun setLearner(on: Boolean) {
        prefs.learnerOn = on
        say(if (on) Phrase.LEARNER_ON else Phrase.LEARNER_OFF)
    }

    /** Always-on voice commands, for the Home microphone button. */
    val voiceOn: StateFlow<Boolean> get() = voiceOnState.asStateFlow()

    fun setVoiceOn(on: Boolean) {
        if (!on) {
            prefs.voiceOn = false
            voice.stop()
            voiceOnState.value = false
            say(Phrase.VOICE_OFF)
            return
        }
        ensureMic {
            prefs.voiceOn = true
            say(Phrase.VOICE_ON)
            voice.start()
            voiceOnState.value = true
        }
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Runs [then] once the microphone permission is granted, asking for it first if needed. */
    private fun ensureMic(then: () -> Unit) {
        if (hasMic()) {
            then()
            return
        }
        afterMicGranted = then
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) say(Phrase.MIC_NEEDED)
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun onMicPermission(granted: Boolean) {
        val then = afterMicGranted
        afterMicGranted = null
        when (PermissionOutcome.of(granted, shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))) {
            PermissionOutcome.GRANTED -> then?.invoke()
            PermissionOutcome.DENIED -> say(Phrase.MIC_NEEDED)
            PermissionOutcome.BLOCKED -> {
                say(Phrase.MIC_BLOCKED)
                Snackbar.make(binding.root, R.string.voice_mic_blocked, Snackbar.LENGTH_LONG)
                    .setAction(R.string.voice_open_settings) { openAppSettings() }
                    .show()
            }
        }
    }

    private fun onVoiceChoice(choice: VoiceChoice) {
        voiceChoice = choice
        if (choice.notice == null) return
        Snackbar.make(binding.root, R.string.voice_korean_voice_missing, Snackbar.LENGTH_LONG)
            .setAction(R.string.voice_install) {
                runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            }
            .show()
    }

    private companion object {
        const val TAG = "Nungil"
    }
}
```

Replace the whole file `app/src/main/java/com/nungil/speech/TtsSpeaker.kt`:

```kotlin
package com.nungil.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nungil.contract.Lang
import com.nungil.contract.app.Speaker
import com.nungil.core.ui.SpeechQueue
import com.nungil.core.ui.VoiceChoice
import com.nungil.core.ui.VoicePick
import java.util.Locale

/**
 * The app's only voice. Wraps TextToSpeech with [SpeechQueue] so phrases never talk over each other.
 * Safe to call from any thread; all work happens on the main thread.
 *
 * @param wanted the language the user chose (UI language).
 * @param onCaption every phrase, as it starts, for the caption bar.
 * @param onVoiceChoice the voice actually used; a missing Korean voice reports English plus a notice.
 */
class TtsSpeaker(
    context: Context,
    private val wanted: () -> Lang,
    private val onCaption: (String) -> Unit,
    private val onVoiceChoice: (VoiceChoice) -> Unit,
) : Speaker, TextToSpeech.OnInitListener {

    private enum class State { STARTING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    private val queue = SpeechQueue { SystemClock.elapsedRealtime() }
    private val tts = TextToSpeech(context.applicationContext, this)
    private var state = State.STARTING
    private var currentId: String? = null
    private var counter = 0
    private var finalCallback: (() -> Unit)? = null
    private var closed = false

    /** The last phrase spoken, for "repeat". Main thread. */
    val lastSpoken: String? get() = queue.lastSpoken

    /** Speaking now or about to. The microphone waits for this to become false. Main thread. */
    val isBusy: Boolean get() = queue.isSpeaking || queue.pendingCount > 0

    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            pump()
            if (queue.finalFinished()) finishFinal()
            main.postDelayed(this, SpeechQueue.POLL_MS)
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    init {
        main.post(poll)
    }

    override fun onInit(status: Int) {
        main.post {
            if (closed) return@post
            if (status != TextToSpeech.SUCCESS) {
                state = State.FAILED
                return@post
            }
            tts.setOnUtteranceProgressListener(progress)
            applyLanguage()
            state = State.READY
        }
    }

    /** Re-reads [wanted] and picks the voice; call after the language changes. Main thread. */
    fun applyLanguage() {
        val want = wanted()
        val result = runCatching { tts.isLanguageAvailable(Locale.forLanguageTag(want.speechTag)) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val choice = VoicePick.choose(want, VoicePick.support(result))
        tts.language = Locale.forLanguageTag(choice.speak.speechTag)
        onVoiceChoice(choice)
        choice.notice?.let { queue.add(it) }
    }

    override fun say(text: String) = onMain {
        queue.add(text)
        pump()
    }

    override fun sayNow(text: String) = onMain {
        if (state == State.STARTING) {
            queue.clear()
            queue.add(text)
        } else {
            queue.now(text)?.let { speak(it, TextToSpeech.QUEUE_FLUSH) }
        }
    }

    override fun sayFinal(text: String, onDone: () -> Unit) = onMain {
        finishFinal()
        if (state == State.STARTING) {
            // The engine is still starting: the sentence is spoken as soon as it is ready.
            queue.clear()
            queue.add(text)
            main.post(onDone)
            return@onMain
        }
        val t = queue.final(text)
        if (t == null) {
            onDone()
        } else {
            finalCallback = onDone
            speak(t, TextToSpeech.QUEUE_FLUSH)
        }
    }

    override fun stop() = onMain {
        queue.clear()
        currentId = null
        runCatching { tts.stop() }
        finishFinal()
    }

    /** Says the last phrase again; false when nothing has been said yet. Main thread. */
    fun repeatLast(): Boolean {
        val t = queue.lastSpoken ?: return false
        sayNow(t)
        return true
    }

    fun shutdown() {
        closed = true
        main.removeCallbacks(poll)
        finishFinal()
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }

    private fun pump() {
        if (state == State.STARTING) return
        queue.next()?.let { speak(it, TextToSpeech.QUEUE_ADD) }
    }

    private fun speak(text: String, mode: Int) {
        onCaption(text)
        if (state != State.READY) {
            // No working engine: the caption still shows the sentence.
            queue.done()
            return
        }
        val id = "nungil-${counter++}"
        currentId = id
        if (tts.speak(text, mode, null, id) != TextToSpeech.SUCCESS) {
            currentId = null
            queue.done()
        }
    }

    private fun finished(utteranceId: String?) {
        main.post {
            if (utteranceId != null && utteranceId == currentId) {
                currentId = null
                queue.done()
            }
        }
    }

    private fun finishFinal() {
        val callback = finalCallback ?: return
        finalCallback = null
        callback()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
```

Create `app/src/main/java/com/nungil/speech/VoiceInput.kt`:

```kotlin
package com.nungil.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.nungil.contract.Lang
import com.nungil.core.ui.ListenAction
import com.nungil.core.ui.Phrase
import com.nungil.core.ui.RecognizerPolicy

/**
 * The app's only microphone. Main thread only.
 *
 * Always-on mode ([start]/[stop]) restarts the recognizer after every result and error; [listenOnce]
 * hears one phrase for a screen while always-on is off. Every rule from build guide §10 lives here or
 * in [RecognizerPolicy].
 */
class VoiceInput(
    private val context: Context,
    private val language: () -> Lang,
    private val isSpeaking: () -> Boolean,
    private val stopSpeaking: () -> Unit,
    private val onHeard: (String) -> Unit,
    private val onProblem: (Phrase) -> Unit,
) : RecognitionListener {

    private val main = Handler(Looper.getMainLooper())
    private val policy = RecognizerPolicy()
    private var recognizer: SpeechRecognizer? = null
    private var alwaysOn = false
    private var oneShot: ((String) -> Unit)? = null
    private var lastPartial = ""
    private var waitedMs = 0L
    private val listenNow = Runnable { listen() }

    val isOn: Boolean get() = alwaysOn

    fun start() {
        alwaysOn = true
        schedule(RecognizerPolicy.DELAY_AFTER_ENABLE_MS)
    }

    fun stop() {
        alwaysOn = false
        oneShot = null
        main.removeCallbacks(listenNow)
        recognizer?.cancel()
    }

    /** One phrase for a screen (always-on is off). */
    fun listenOnce(onText: (String) -> Unit) {
        oneShot = onText
        if (!alwaysOn) schedule(0)
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun schedule(delayMs: Long) {
        main.removeCallbacks(listenNow)
        main.postDelayed(listenNow, delayMs)
    }

    private fun listen() {
        if (!alwaysOn && oneShot == null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stop()
            onProblem(Phrase.MIC_NEEDED)
            return
        }
        // The app must not hear itself: wait until it stops talking (up to the limit), then silence it.
        if (policy.shouldWait(isSpeaking(), waitedMs)) {
            waitedMs += RecognizerPolicy.WAIT_POLL_MS
            schedule(RecognizerPolicy.WAIT_POLL_MS)
            return
        }
        waitedMs = 0
        val r = recognizer ?: create() ?: run {
            stop()
            onProblem(Phrase.VOICE_UNAVAILABLE)
            return
        }
        stopSpeaking()
        lastPartial = ""
        r.startListening(intent())
    }

    /** Some phones report no recognition service but still have Android's on-device recognizer. */
    private fun create(): SpeechRecognizer? {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        val r = when {
            available -> SpeechRecognizer.createSpeechRecognizer(context)
            Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else -> null
        }
        r?.setRecognitionListener(this)
        recognizer = r
        return r
    }

    /**
     * Never add EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS: on Google's on-device recognizer it switches to
     * continuous dictation and every final result arrives empty (build guide §10.2).
     */
    private fun intent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, policy.language(language()).speechTag)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            .orEmpty()
            .ifEmpty { lastPartial }
        if (text.isEmpty()) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
            return
        }
        val delay = policy.afterResult()
        val once = oneShot
        if (once != null) {
            oneShot = null
            once(text)
        } else {
            onHeard(text)
        }
        if (alwaysOn) schedule(delay)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { lastPartial = it }
    }

    override fun onError(error: Int) {
        val decision = policy.afterError(error, language())
        when (decision.action) {
            ListenAction.STOP_NO_PERMISSION -> {
                stop()
                onProblem(Phrase.MIC_NEEDED)
                return
            }
            ListenAction.FALL_BACK_TO_ENGLISH -> onProblem(Phrase.KOREAN_RECOGNITION_MISSING)
            ListenAction.RETRY -> if (!alwaysOn) oneShot = null // a one-shot request gives up on silence
        }
        if (decision.rebuild) {
            recognizer?.destroy()
            recognizer = null
        }
        if (alwaysOn || oneShot != null) schedule(decision.delayMs)
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
```

Replace the whole file `app/src/main/res-i/values-ko/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. 해요체, short and friendly. -->
<resources>
    <string name="app_name">눈길</string>

    <string name="nav_title_home">눈길</string>
    <string name="nav_title_onboarding">시작하기</string>
    <string name="nav_title_scan_hub">둘러보기</string>
    <string name="nav_title_settings">설정</string>
    <string name="nav_title_history">기록</string>
    <string name="nav_title_scan">주변 둘러보기</string>
    <string name="nav_title_walk">걷기 모드</string>
    <string name="nav_title_search">찾기</string>
    <string name="nav_title_search_camera">찾는 중</string>
    <string name="nav_title_saved">저장한 것</string>
    <string name="nav_title_person">사람</string>
    <string name="nav_title_add_person">사람 추가</string>
    <string name="nav_title_enroll">얼굴 등록</string>
    <string name="nav_title_item">물건</string>
    <string name="nav_title_add_item">물건 추가</string>
    <string name="nav_title_item_enroll">물건 등록</string>
    <string name="nav_title_reader">글자 읽기</string>

    <!-- Design components -->
    <string name="ng_ring_description">%1$d퍼센트 살펴봤어요</string>

    <!-- 음성 -->
    <string name="voice_korean_voice_missing">한국어 음성이 설치되어 있지 않아 영어로 말해요.</string>
    <string name="voice_install">설치</string>
    <string name="voice_mic_blocked">이 앱의 마이크가 막혀 있어요.</string>
    <string name="voice_open_settings">설정</string>
</resources>
```

Replace the whole file `app/src/main/res-i/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Prefixes owned by I: app_, nav_title_, home_, hub_, settings_, history_, onboarding_, voice_, help_, ng_. -->
<resources>
    <string name="app_name">Nungil</string>

    <string name="nav_title_home">Nungil</string>
    <string name="nav_title_onboarding">Welcome</string>
    <string name="nav_title_scan_hub">Scan</string>
    <string name="nav_title_settings">Settings</string>
    <string name="nav_title_history">History</string>
    <string name="nav_title_scan">Look around</string>
    <string name="nav_title_walk">Walk mode</string>
    <string name="nav_title_search">Find</string>
    <string name="nav_title_search_camera">Finding</string>
    <string name="nav_title_saved">Saved</string>
    <string name="nav_title_person">Person</string>
    <string name="nav_title_add_person">Add a person</string>
    <string name="nav_title_enroll">Learn a face</string>
    <string name="nav_title_item">Item</string>
    <string name="nav_title_add_item">Add an item</string>
    <string name="nav_title_item_enroll">Learn an item</string>
    <string name="nav_title_reader">Read text</string>

    <!-- Design components -->
    <string name="ng_ring_description">Scanned %1$d percent</string>

    <!-- Voice -->
    <string name="voice_korean_voice_missing">The Korean voice is not installed. Speaking English.</string>
    <string name="voice_install">Install</string>
    <string name="voice_mic_blocked">The microphone is blocked for this app.</string>
    <string name="voice_open_settings">Settings</string>
</resources>
```

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `RecognizerPolicyTest` 8 pass.

- [ ] **Step 6: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

Stream the log: `adb logcat -v time -s Nungil:V`. Turn voice on (debugger `setVoiceOn(true)` until Home lands in I6) and say ten commands in English and ten in Korean, including "stop listening", "saved", "add person Ali", "사람 추가 민준", "가방 찾아줘", "한국어". Expected: one `Heard "…" -> …` line each with the right command, the app never hears its own voice, and after ten silent minutes the microphone still answers. Deny the microphone twice: the app says it needs the microphone, then that it is blocked, and the snackbar opens app settings (Review Focus 5).

- [ ] **Step 7: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/ui/RecognizerPolicy.kt app/src/main/java/com/nungil/shell/MainActivity.kt app/src/main/java/com/nungil/speech/TtsSpeaker.kt app/src/main/java/com/nungil/speech/VoiceInput.kt app/src/main/res-i/values-ko/strings.xml app/src/main/res-i/values/strings.xml app/src/test/java/com/nungil/core/ui/RecognizerPolicyTest.kt
git commit -m "Keep the microphone listening, hand dictation to screens, and fall back to English recognition"
git push -u origin i/I9-voice-input
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I6: Home and scan menu (Korean-style, voice-first)

**Files:**
- Modify: `app/src/main/java/com/nungil/shell/HomeFragment.kt`
- Modify: `app/src/main/java/com/nungil/shell/ScanHubFragment.kt`
- Create: `app/src/main/res-i/layout/home_fragment.xml`
- Create: `app/src/main/res-i/layout/hub_fragment.xml`
- Create: `app/src/main/res-i/values-ko/home_strings.xml`
- Create: `app/src/main/res-i/values/home_strings.xml`

**Interfaces:**
- Consumes: `BigCardView`, icons (I2); `MainActivity.voiceOn`, `setVoiceOn` (I9); `services().navigator`.
- Produces: the real `HomeFragment` (replaces the developer launcher) and `ScanHubFragment`; strings `home_*`, `hub_*` in `home_strings.xml` (EN) and `values-ko/home_strings.xml`.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I6-home
```

- [ ] **Step 2: Implement**

Replace the whole file `app/src/main/java/com/nungil/shell/HomeFragment.kt`:

```kotlin
package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nungil.R
import com.nungil.contract.Dest
import com.nungil.contract.ScanMode
import com.nungil.contract.app.services
import com.nungil.databinding.HomeFragmentBinding
import com.nungil.design.setHeading
import kotlinx.coroutines.launch

/** Owner I. Voice-first Home: one card per job and the microphone button within thumb reach. */
class HomeFragment : Fragment() {
    private var _binding: HomeFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = HomeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.homeHeadline.setHeading()
        val nav = services().navigator
        binding.homeFullScan.setOnClickListener { nav.open(Dest.Scan(ScanMode.FULL)) }
        binding.homeSearch.setOnClickListener { nav.open(Dest.Search()) }
        binding.homeSaved.setOnClickListener { nav.open(Dest.Saved()) }
        binding.homeLive.setOnClickListener { nav.open(Dest.Scan(ScanMode.LIVE)) }
        binding.homeWalk.setOnClickListener { nav.open(Dest.Walk) }
        binding.homeHistory.setOnClickListener { nav.open(Dest.History) }
        binding.homeSettings.setOnClickListener { nav.open(Dest.Settings) }

        val main = requireActivity() as MainActivity
        binding.homeMic.setOnClickListener { main.setVoiceOn(!main.voiceOn.value) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                main.voiceOn.collect { on ->
                    binding.homeMic.setText(if (on) R.string.home_mic_turn_off else R.string.home_mic_turn_on)
                    binding.homeMic.setIconResource(if (on) R.drawable.ng_ic_mic_off else R.drawable.ng_ic_mic)
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
```

Replace the whole file `app/src/main/java/com/nungil/shell/ScanHubFragment.kt`:

```kotlin
package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nungil.contract.Dest
import com.nungil.contract.ScanMode
import com.nungil.contract.app.services
import com.nungil.databinding.HubFragmentBinding
import com.nungil.design.setHeading

/** Owner I. Scan menu: full scan, live scan, walk mode and history. */
class ScanHubFragment : Fragment() {
    private var _binding: HubFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = HubFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.hubHeadline.setHeading()
        val nav = services().navigator
        binding.hubFullScan.setOnClickListener { nav.open(Dest.Scan(ScanMode.FULL)) }
        binding.hubLive.setOnClickListener { nav.open(Dest.Scan(ScanMode.LIVE)) }
        binding.hubWalk.setOnClickListener { nav.open(Dest.Walk) }
        binding.hubHistory.setOnClickListener { nav.open(Dest.History) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
```

Create `app/src/main/res-i/layout/home_fragment.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Voice-first Home: a big friendly question, one card per job, the microphone at the bottom. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingEnd="@dimen/ng_gutter">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingBottom="@dimen/ng_gap_large">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap"
                android:text="@string/home_greeting"
                android:textAppearance="@style/TextAppearance.Nungil.Body"
                android:textColor="?attr/ngTextSub" />

            <TextView
                android:id="@+id/home_headline"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:text="@string/home_headline"
                android:textAppearance="@style/TextAppearance.Nungil.Display" />

            <com.nungil.design.BigCardView
                android:id="@+id/home_full_scan"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap_large"
                app:ngEmphasis="true"
                app:ngIcon="@drawable/ng_ic_explore"
                app:ngSubtitle="@string/home_full_scan_subtitle"
                app:ngTitle="@string/home_full_scan_title" />

            <com.nungil.design.BigCardView
                android:id="@+id/home_search"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap"
                app:ngIcon="@drawable/ng_ic_search"
                app:ngSubtitle="@string/home_search_subtitle"
                app:ngTitle="@string/home_search_title" />

            <com.nungil.design.BigCardView
                android:id="@+id/home_saved"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap"
                app:ngIcon="@drawable/ng_ic_saved"
                app:ngSubtitle="@string/home_saved_subtitle"
                app:ngTitle="@string/home_saved_title" />

            <com.nungil.design.BigCardView
                android:id="@+id/home_live"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap"
                app:ngIcon="@drawable/ng_ic_bolt"
                app:ngSubtitle="@string/home_live_subtitle"
                app:ngTitle="@string/home_live_title" />

            <com.nungil.design.BigCardView
                android:id="@+id/home_walk"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap"
                app:ngIcon="@drawable/ng_ic_walk"
                app:ngSubtitle="@string/home_walk_subtitle"
                app:ngTitle="@string/home_walk_title" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap_large"
                android:orientation="horizontal">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/home_history"
                    style="@style/Widget.Nungil.Button.Tonal"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="6dp"
                    android:layout_weight="1"
                    android:text="@string/home_history"
                    app:icon="@drawable/ng_ic_history" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/home_settings"
                    style="@style/Widget.Nungil.Button.Tonal"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="6dp"
                    android:layout_weight="1"
                    android:text="@string/home_settings"
                    app:icon="@drawable/ng_ic_tune" />
            </LinearLayout>
        </LinearLayout>
    </ScrollView>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/home_mic"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:layout_marginBottom="@dimen/ng_gap"
        android:text="@string/home_mic_turn_on"
        app:icon="@drawable/ng_ic_mic" />
</LinearLayout>
```

Create `app/src/main/res-i/layout/hub_fragment.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Scan menu: the three ways to look around, plus history. -->
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="@dimen/ng_gutter"
        android:paddingEnd="@dimen/ng_gutter"
        android:paddingBottom="@dimen/ng_gap_large">

        <TextView
            android:id="@+id/hub_headline"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap"
            android:text="@string/hub_headline"
            android:textAppearance="@style/TextAppearance.Nungil.Title" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/hub_subtitle"
            android:textAppearance="@style/TextAppearance.Nungil.Body"
            android:textColor="?attr/ngTextSub" />

        <com.nungil.design.BigCardView
            android:id="@+id/hub_full_scan"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap_large"
            app:ngEmphasis="true"
            app:ngIcon="@drawable/ng_ic_explore"
            app:ngSubtitle="@string/home_full_scan_subtitle"
            app:ngTitle="@string/home_full_scan_title" />

        <com.nungil.design.BigCardView
            android:id="@+id/hub_live"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap"
            app:ngIcon="@drawable/ng_ic_bolt"
            app:ngSubtitle="@string/home_live_subtitle"
            app:ngTitle="@string/home_live_title" />

        <com.nungil.design.BigCardView
            android:id="@+id/hub_walk"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap"
            app:ngIcon="@drawable/ng_ic_walk"
            app:ngSubtitle="@string/home_walk_subtitle"
            app:ngTitle="@string/home_walk_title" />

        <com.nungil.design.BigCardView
            android:id="@+id/hub_history"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap"
            app:ngIcon="@drawable/ng_ic_history"
            app:ngSubtitle="@string/hub_history_subtitle"
            app:ngTitle="@string/home_history" />
    </LinearLayout>
</ScrollView>
```

Create `app/src/main/res-i/values-ko/home_strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. 홈과 둘러보기 메뉴. -->
<resources>
    <string name="home_greeting">안녕하세요</string>
    <string name="home_headline">무엇을 도와드릴까요?</string>
    <string name="home_full_scan_title">주변 둘러보기</string>
    <string name="home_full_scan_subtitle">한 바퀴 돌면 주변을 알려드려요</string>
    <string name="home_search_title">물건 찾기</string>
    <string name="home_search_subtitle">가까워질수록 소리가 빨라져요</string>
    <string name="home_saved_title">저장한 것</string>
    <string name="home_saved_subtitle">알려주신 사람과 물건이에요</string>
    <string name="home_live_title">실시간 안내</string>
    <string name="home_live_subtitle">카메라가 찾는 대로 바로 말해요</string>
    <string name="home_walk_title">걷기 모드</string>
    <string name="home_walk_subtitle">걷는 동안 장애물을 알려드려요</string>
    <string name="home_history">기록</string>
    <string name="home_settings">설정</string>
    <string name="home_mic_turn_on">음성 명령 켜기</string>
    <string name="home_mic_turn_off">음성 명령 끄기</string>

    <string name="hub_headline">어떻게 둘러볼까요?</string>
    <string name="hub_subtitle">휴대폰을 세우고 천천히 돌아 주세요.</string>
    <string name="hub_history_subtitle">지난 결과를 다시 들어요</string>
</resources>
```

Create `app/src/main/res-i/values/home_strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Home and scan menu. -->
<resources>
    <string name="home_greeting">Hello</string>
    <string name="home_headline">What can I help with?</string>
    <string name="home_full_scan_title">Look around</string>
    <string name="home_full_scan_subtitle">Turn once and hear what is around you</string>
    <string name="home_search_title">Find something</string>
    <string name="home_search_subtitle">Beeps get faster as you point at it</string>
    <string name="home_saved_title">Saved</string>
    <string name="home_saved_subtitle">People and things you taught me</string>
    <string name="home_live_title">Live scan</string>
    <string name="home_live_subtitle">Hear things as the camera finds them</string>
    <string name="home_walk_title">Walk mode</string>
    <string name="home_walk_subtitle">Warnings about obstacles while walking</string>
    <string name="home_history">History</string>
    <string name="home_settings">Settings</string>
    <string name="home_mic_turn_on">Turn on voice commands</string>
    <string name="home_mic_turn_off">Turn off voice commands</string>

    <string name="hub_headline">How should I look around?</string>
    <string name="hub_subtitle">Hold the phone upright and turn slowly.</string>
    <string name="hub_history_subtitle">Hear your past scans again</string>
</resources>
```

- [ ] **Step 3: Build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: No unit test: this task is layout and wiring. Before the layouts exist the build fails with `Unresolved reference 'HomeFragmentBinding'`. Then: `assembleDebug` succeeds; all earlier unit tests still pass.

- [ ] **Step 4: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

Light, dark and high contrast, English and Korean: Home shows "무엇을 도와드릴까요?" in large bold type, five full-width cards (주변 둘러보기 highlighted), 기록 and 설정 side by side, and the microphone button at the bottom. Tapping each card opens the right screen; the microphone button switches between "음성 명령 켜기" and "음성 명령 끄기". With TalkBack each card is read as one button with title and subtitle, and the headline is announced as a heading. At the largest font nothing overlaps (the list scrolls, the microphone button stays).

- [ ] **Step 5: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/shell/HomeFragment.kt app/src/main/java/com/nungil/shell/ScanHubFragment.kt app/src/main/res-i/layout/home_fragment.xml app/src/main/res-i/layout/hub_fragment.xml app/src/main/res-i/values-ko/home_strings.xml app/src/main/res-i/values/home_strings.xml
git commit -m "Add the voice-first Home and the scan menu"
git push -u origin i/I6-home
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I7: Settings and history

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/SettingsMath.kt`
- Modify: `app/src/main/java/com/nungil/shell/HistoryFragment.kt`
- Modify: `app/src/main/java/com/nungil/shell/SettingsFragment.kt`
- Create: `app/src/main/res-i/color/ng_toggle_bg.xml`
- Create: `app/src/main/res-i/color/ng_toggle_text.xml`
- Create: `app/src/main/res-i/layout/history_delete_sheet.xml`
- Create: `app/src/main/res-i/layout/history_fragment.xml`
- Create: `app/src/main/res-i/layout/history_item.xml`
- Create: `app/src/main/res-i/layout/settings_fragment.xml`
- Create: `app/src/main/res-i/values-ko/settings_strings.xml`
- Create: `app/src/main/res-i/values/settings_strings.xml`
- Create: `app/src/main/res-i/values/settings_styles.xml`
- Create: `app/src/test/java/com/nungil/core/ui/SettingsMathTest.kt`

**Interfaces:**
- Consumes: `SettingsStore`, `ScanSettings`, `AppDatabase.scans()`, `AppScope` (contract); `MainActivity.setLanguage/setHighContrast/setLearner` (I5); `AppPrefs.voiceGuideOn`.
- Produces: `SettingsMath.scoreToSlider(score)`, `sliderToScore(value)`; `HistoryText.line(mode, coverage, lang)`, `pickFirst`, `deleted`, `empty`; styles `Widget.Nungil.Toggle`, `Widget.Nungil.Switch`, `TextAppearance.Nungil.Section` (A and Y may use them); the real `SettingsFragment` and `HistoryFragment` (a `VoiceHandler` for Start and Delete).

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I7-settings-history
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/nungil/core/ui/SettingsMathTest.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMathTest {
    @Test fun sliderShowsTheSavedScoreOnItsFivePercentSteps() {
        assertEquals(70f, SettingsMath.scoreToSlider(0.7f))
        assertEquals(60f, SettingsMath.scoreToSlider(0.62f))
        assertEquals(65f, SettingsMath.scoreToSlider(0.63f))
    }

    @Test fun sliderStaysInsideThirtyToSeventy() {
        assertEquals(30f, SettingsMath.scoreToSlider(0.1f))
        assertEquals(70f, SettingsMath.scoreToSlider(0.95f))
    }

    @Test fun sliderValueBecomesAScore() = assertEquals(0.55f, SettingsMath.sliderToScore(55f), 1e-6f)

    @Test fun historyLines() {
        assertEquals("Look around · 80%", HistoryText.line("FULL", 80, Lang.EN))
        assertEquals("주변 둘러보기 · 80%", HistoryText.line("FULL", 80, Lang.KO))
        assertEquals("Live scan", HistoryText.line("LIVE", 0, Lang.EN))
        assertEquals("실시간 안내", HistoryText.line("LIVE", 0, Lang.KO))
    }

    @Test fun historySentences() {
        assertEquals("Tap a scan first.", HistoryText.pickFirst(Lang.EN))
        assertEquals("기록을 지웠어요.", HistoryText.deleted(Lang.KO))
        assertEquals("아직 기록이 없어요.", HistoryText.empty(Lang.KO))
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'SettingsMath'` and `'HistoryText'`.

- [ ] **Step 4: Implement**

Create `app/src/main/java/com/nungil/core/ui/SettingsMath.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang
import com.nungil.contract.ScanSettings
import kotlin.math.roundToInt

/**
 * The confidence slider works in whole percent, 30..70 in steps of 5. Material's Slider crashes on a
 * value that is not on a step, so every saved score is snapped before it is shown.
 */
object SettingsMath {
    const val SLIDER_FROM = ScanSettings.MIN_SCORE_LOW * 100f
    const val SLIDER_TO = ScanSettings.MIN_SCORE_HIGH * 100f
    const val SLIDER_STEP = 5f

    fun scoreToSlider(score: Float): Float {
        val percent = (score * 100f).coerceIn(SLIDER_FROM, SLIDER_TO)
        return (percent / SLIDER_STEP).roundToInt() * SLIDER_STEP
    }

    fun sliderToScore(value: Float): Float = value / 100f
}

/** One line under each history entry: the mode, and the coverage of a full scan. */
object HistoryText {
    fun line(mode: String, coveragePercent: Int, lang: Lang): String {
        val ko = lang == Lang.KO
        return when (mode) {
            "FULL" -> (if (ko) "주변 둘러보기" else "Look around") + " · $coveragePercent%"
            "LIVE" -> if (ko) "실시간 안내" else "Live scan"
            else -> mode
        }
    }

    fun pickFirst(lang: Lang): String =
        if (lang == Lang.KO) "먼저 기록을 하나 눌러 주세요." else "Tap a scan first."

    fun deleted(lang: Lang): String = if (lang == Lang.KO) "기록을 지웠어요." else "Scan deleted."

    fun empty(lang: Lang): String = if (lang == Lang.KO) "아직 기록이 없어요." else "No scans yet."
}
```

Replace the whole file `app/src/main/java/com/nungil/shell/HistoryFragment.kt`:

```kotlin
package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.ui.HistoryText
import com.nungil.data.AppDatabase
import com.nungil.data.ScanEntity
import com.nungil.databinding.HistoryDeleteSheetBinding
import com.nungil.databinding.HistoryFragmentBinding
import com.nungil.databinding.HistoryItemBinding
import com.nungil.design.setHeading
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Owner I. Past scans from Room, newest first. Tap = hear it again; touch and hold (or say "delete"
 * after tapping one) = delete it after a confirmation sheet. "Start" replays the newest scan.
 */
class HistoryFragment : Fragment(), VoiceHandler {
    private var _binding: HistoryFragmentBinding? = null
    private val binding get() = _binding!!
    private val adapter = ScanAdapter(onTap = ::replay, onHold = ::confirmDelete)
    private var selected: ScanEntity? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = HistoryFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.historyHeadline.setHeading()
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = adapter
        val dao = AppDatabase.get(requireContext()).scans()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.observeScans().collect { scans ->
                    adapter.submitList(scans)
                    binding.historyEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean {
        val lang = services().lang
        when (command) {
            VoiceCommand.Start -> {
                val newest = adapter.currentList.firstOrNull()
                if (newest == null) services().speaker.sayNow(HistoryText.empty(lang)) else replay(newest)
            }
            VoiceCommand.Delete -> {
                val scan = selected
                if (scan == null) services().speaker.sayNow(HistoryText.pickFirst(lang)) else confirmDelete(scan)
            }
            else -> return false
        }
        return true
    }

    private fun replay(scan: ScanEntity) {
        selected = scan
        services().speaker.sayNow(scan.summaryText)
    }

    private fun confirmDelete(scan: ScanEntity) {
        selected = scan
        val sheet = BottomSheetDialog(requireContext())
        val sheetBinding = HistoryDeleteSheetBinding.inflate(layoutInflater)
        sheetBinding.historyDeleteTitle.setHeading()
        sheetBinding.historyDeleteConfirm.setOnClickListener {
            val dao = AppDatabase.get(requireContext()).scans()
            val speaker = services().speaker
            val text = HistoryText.deleted(services().lang)
            AppScope.launch { dao.deleteScan(scan.id) }
            speaker.say(text)
            selected = null
            sheet.dismiss()
        }
        sheetBinding.historyDeleteCancel.setOnClickListener { sheet.dismiss() }
        sheet.setContentView(sheetBinding.root)
        sheet.show()
    }

    override fun onDestroyView() {
        binding.historyList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private class ScanAdapter(
        private val onTap: (ScanEntity) -> Unit,
        private val onHold: (ScanEntity) -> Unit,
    ) : ListAdapter<ScanEntity, ScanAdapter.Holder>(Diff) {

        class Holder(val binding: HistoryItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(HistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val scan = getItem(position)
            val context = holder.itemView.context
            val lang = (context as? MainActivity)?.lang ?: Lang.EN
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scan.startedAt))
            val meta = "$date · ${HistoryText.line(scan.mode, scan.coveragePercent, lang)}"
            holder.binding.historyItemMeta.text = meta
            holder.binding.historyItemSummary.text = scan.summaryText
            holder.itemView.contentDescription = "$meta. ${scan.summaryText}"
            holder.itemView.setOnClickListener { onTap(scan) }
            holder.itemView.setOnLongClickListener {
                onHold(scan)
                true
            }
        }

        private object Diff : DiffUtil.ItemCallback<ScanEntity>() {
            override fun areItemsTheSame(oldItem: ScanEntity, newItem: ScanEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ScanEntity, newItem: ScanEntity) = oldItem == newItem
        }
    }
}
```

Replace the whole file `app/src/main/java/com/nungil/shell/SettingsFragment.kt`:

```kotlin
package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nungil.R
import com.nungil.contract.Compute
import com.nungil.contract.Facing
import com.nungil.contract.ModelChoice
import com.nungil.contract.ScanSettings
import com.nungil.core.ui.LanguageChoice
import com.nungil.core.ui.SettingsMath
import com.nungil.data.SettingsStore
import com.nungil.databinding.SettingsFragmentBinding
import com.nungil.design.setHeading

/**
 * Owner I. Scan settings are saved through SettingsStore on every change (A reads them when a camera
 * starts); app settings go to AppPrefs and the per-app language. Listeners are attached after the
 * current values are shown, so showing them never counts as a change.
 */
class SettingsFragment : Fragment() {
    private var _binding: SettingsFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: SettingsStore
    private lateinit var prefs: AppPrefs
    private var settings = ScanSettings()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsHeadline.setHeading()
        store = SettingsStore(requireContext())
        prefs = AppPrefs(requireContext())
        settings = store.load()
        show()
        listen()
    }

    private fun show() = with(binding) {
        settingsCamera.check(if (settings.facing == Facing.FRONT) R.id.settings_camera_front else R.id.settings_camera_back)
        settingsCompute.check(if (settings.compute == Compute.CPU) R.id.settings_compute_cpu else R.id.settings_compute_gpu)
        settingsModel.check(
            when (settings.model) {
                ModelChoice.LIGHT -> R.id.settings_model_light
                ModelChoice.FAST -> R.id.settings_model_fast
                ModelChoice.ACCURATE -> R.id.settings_model_accurate
            },
        )
        val slider = SettingsMath.scoreToSlider(settings.minScore)
        settingsScore.value = slider
        settingsScoreValue.text = "${slider.toInt()}%"
        settingsSpeech.isChecked = settings.speechOn
        settingsColors.isChecked = settings.colorsOn

        val main = requireActivity() as MainActivity
        settingsLanguage.check(
            when (main.languageChoice) {
                LanguageChoice.SYSTEM -> R.id.settings_language_system
                LanguageChoice.ENGLISH -> R.id.settings_language_english
                LanguageChoice.KOREAN -> R.id.settings_language_korean
            },
        )
        settingsHighContrast.isChecked = main.highContrast
        settingsVoiceGuide.isChecked = prefs.voiceGuideOn
        settingsLearner.isChecked = main.learnerOn
    }

    private fun listen() = with(binding) {
        settingsCamera.addOnButtonCheckedListener { _, id, checked ->
            if (checked) update { copy(facing = if (id == R.id.settings_camera_front) Facing.FRONT else Facing.BACK) }
        }
        settingsCompute.addOnButtonCheckedListener { _, id, checked ->
            if (checked) update { copy(compute = if (id == R.id.settings_compute_cpu) Compute.CPU else Compute.GPU) }
        }
        settingsModel.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val model = when (id) {
                R.id.settings_model_light -> ModelChoice.LIGHT
                R.id.settings_model_fast -> ModelChoice.FAST
                else -> ModelChoice.ACCURATE
            }
            update { copy(model = model) }
        }
        settingsScore.addOnChangeListener { _, value, fromUser ->
            settingsScoreValue.text = "${value.toInt()}%"
            if (fromUser) update { copy(minScore = SettingsMath.sliderToScore(value)) }
        }
        settingsSpeech.setOnCheckedChangeListener { _, on -> update { copy(speechOn = on) } }
        settingsColors.setOnCheckedChangeListener { _, on -> update { copy(colorsOn = on) } }

        val main = requireActivity() as MainActivity
        settingsLanguage.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            main.setLanguage(
                when (id) {
                    R.id.settings_language_english -> LanguageChoice.ENGLISH
                    R.id.settings_language_korean -> LanguageChoice.KOREAN
                    else -> LanguageChoice.SYSTEM
                },
            )
        }
        settingsHighContrast.setOnCheckedChangeListener { _, on -> main.setHighContrast(on) }
        settingsVoiceGuide.setOnCheckedChangeListener { _, on -> prefs.voiceGuideOn = on }
        settingsLearner.setOnCheckedChangeListener { _, on -> main.setLearner(on) }
    }

    private fun update(change: ScanSettings.() -> ScanSettings) {
        settings = settings.change().normalized()
        store.save(settings)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
```

Create `app/src/main/res-i/color/ng_toggle_bg.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Selected segment of a toggle group is filled with the primary colour. -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="?attr/ngPrimary" android:state_checked="true" />
    <item android:color="@android:color/transparent" />
</selector>
```

Create `app/src/main/res-i/color/ng_toggle_text.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="?attr/ngOnPrimary" android:state_checked="true" />
    <item android:color="?attr/ngText" />
</selector>
```

Create `app/src/main/res-i/layout/history_delete_sheet.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Confirmation before a history entry is deleted (a bottom sheet, not a blocking dialog). -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="@dimen/ng_gutter">

    <TextView
        android:id="@+id/history_delete_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/history_delete_question"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/history_delete_confirm"
        style="@style/Widget.Nungil.Button.Danger"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:text="@string/history_delete" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/history_delete_cancel"
        style="@style/Widget.Nungil.Button.Tonal"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/history_cancel" />
</LinearLayout>
```

Create `app/src/main/res-i/layout/history_fragment.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Past scans; tap one to hear it again. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingEnd="@dimen/ng_gutter">

    <TextView
        android:id="@+id/history_headline"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:text="@string/history_headline"
        android:textAppearance="@style/TextAppearance.Nungil.Title" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/history_subtitle"
        android:textAppearance="@style/TextAppearance.Nungil.Body"
        android:textColor="?attr/ngTextSub" />

    <TextView
        android:id="@+id/history_empty"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap_large"
        android:text="@string/history_empty"
        android:textAppearance="@style/TextAppearance.Nungil.BodyStrong"
        android:visibility="gone" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/history_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="@dimen/ng_gap"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:paddingBottom="@dimen/ng_gap_large" />
</LinearLayout>
```

Create `app/src/main/res-i/layout/history_item.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. One past scan. The card is one TalkBack element: date, mode, then the summary. -->
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/ng_gap"
    android:clickable="true"
    android:focusable="true"
    android:minHeight="@dimen/ng_touch">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:id="@+id/history_item_meta"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:importantForAccessibility="no"
            android:textAppearance="@style/TextAppearance.Nungil.Caption" />

        <TextView
            android:id="@+id/history_item_summary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:importantForAccessibility="no"
            android:textAppearance="@style/TextAppearance.Nungil.Body" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

Create `app/src/main/res-i/layout/settings_fragment.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Scan settings (stored by SettingsStore) and app settings (AppPrefs, per-app language). -->
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="@dimen/ng_gutter"
        android:paddingEnd="@dimen/ng_gutter"
        android:paddingBottom="@dimen/ng_gap_large">

        <TextView
            android:id="@+id/settings_headline"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap"
            android:text="@string/settings_headline"
            android:textAppearance="@style/TextAppearance.Nungil.Title" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap_large"
            android:text="@string/settings_section_scan"
            android:textAppearance="@style/TextAppearance.Nungil.Section" />

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_camera"
                    android:textAppearance="@style/TextAppearance.Nungil.Label" />

                <com.google.android.material.button.MaterialButtonToggleGroup
                    android:id="@+id/settings_camera"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    app:selectionRequired="true"
                    app:singleSelection="true">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_camera_back"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_camera_back" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_camera_front"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_camera_front" />
                </com.google.android.material.button.MaterialButtonToggleGroup>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/ng_gap_large"
                    android:text="@string/settings_compute"
                    android:textAppearance="@style/TextAppearance.Nungil.Label" />

                <com.google.android.material.button.MaterialButtonToggleGroup
                    android:id="@+id/settings_compute"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    app:selectionRequired="true"
                    app:singleSelection="true">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_compute_gpu"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_compute_gpu" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_compute_cpu"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_compute_cpu" />
                </com.google.android.material.button.MaterialButtonToggleGroup>

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/ng_gap_large"
                    android:text="@string/settings_model"
                    android:textAppearance="@style/TextAppearance.Nungil.Label" />

                <com.google.android.material.button.MaterialButtonToggleGroup
                    android:id="@+id/settings_model"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    app:selectionRequired="true"
                    app:singleSelection="true">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_model_light"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_model_light" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_model_fast"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_model_fast" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_model_accurate"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_model_accurate" />
                </com.google.android.material.button.MaterialButtonToggleGroup>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/ng_gap_large"
                    android:orientation="horizontal">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_score"
                        android:textAppearance="@style/TextAppearance.Nungil.Label" />

                    <TextView
                        android:id="@+id/settings_score_value"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:importantForAccessibility="no"
                        android:textAppearance="@style/TextAppearance.Nungil.Label" />
                </LinearLayout>

                <com.google.android.material.slider.Slider
                    android:id="@+id/settings_score"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:contentDescription="@string/settings_score"
                    android:stepSize="5"
                    android:valueFrom="30"
                    android:valueTo="70"
                    app:labelBehavior="gone"
                    app:minTouchTargetSize="@dimen/ng_touch" />

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_score_hint"
                    android:textAppearance="@style/TextAppearance.Nungil.Caption" />

                <com.google.android.material.materialswitch.MaterialSwitch
                    android:id="@+id/settings_speech"
                    style="@style/Widget.Nungil.Switch"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/ng_gap"
                    android:text="@string/settings_speech" />

                <com.google.android.material.materialswitch.MaterialSwitch
                    android:id="@+id/settings_colors"
                    style="@style/Widget.Nungil.Switch"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_colors" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/ng_gap_large"
            android:text="@string/settings_section_app"
            android:textAppearance="@style/TextAppearance.Nungil.Section" />

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_language"
                    android:textAppearance="@style/TextAppearance.Nungil.Label" />

                <com.google.android.material.button.MaterialButtonToggleGroup
                    android:id="@+id/settings_language"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    app:selectionRequired="true"
                    app:singleSelection="true">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_language_system"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_language_system" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_language_english"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_language_english" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/settings_language_korean"
                        style="@style/Widget.Nungil.Toggle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="@string/settings_language_korean" />
                </com.google.android.material.button.MaterialButtonToggleGroup>

                <com.google.android.material.materialswitch.MaterialSwitch
                    android:id="@+id/settings_high_contrast"
                    style="@style/Widget.Nungil.Switch"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="@dimen/ng_gap"
                    android:text="@string/settings_high_contrast" />

                <com.google.android.material.materialswitch.MaterialSwitch
                    android:id="@+id/settings_voice_guide"
                    style="@style/Widget.Nungil.Switch"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_voice_guide" />

                <com.google.android.material.materialswitch.MaterialSwitch
                    android:id="@+id/settings_learner"
                    style="@style/Widget.Nungil.Switch"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_learner" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
    </LinearLayout>
</ScrollView>
```

Create `app/src/main/res-i/values-ko/settings_strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. 설정과 기록. -->
<resources>
    <string name="settings_headline">설정</string>
    <string name="settings_section_scan">둘러보기</string>
    <string name="settings_camera">카메라</string>
    <string name="settings_camera_back">후면</string>
    <string name="settings_camera_front">전면</string>
    <string name="settings_compute">처리 장치</string>
    <string name="settings_compute_gpu">GPU</string>
    <string name="settings_compute_cpu">CPU</string>
    <string name="settings_model">모델</string>
    <string name="settings_model_light">가벼움</string>
    <string name="settings_model_fast">빠름</string>
    <string name="settings_model_accurate">정확함</string>
    <string name="settings_score">얼마나 확실할 때 말할까요</string>
    <string name="settings_score_hint">높을수록 실수는 줄지만 찾는 물건도 줄어요.</string>
    <string name="settings_speech">결과 말하기</string>
    <string name="settings_colors">색깔 알려주기</string>
    <string name="settings_section_app">앱</string>
    <string name="settings_language">언어</string>
    <string name="settings_language_system">휴대폰 설정</string>
    <string name="settings_language_english">English</string>
    <string name="settings_language_korean">한국어</string>
    <string name="settings_high_contrast">고대비 화면</string>
    <string name="settings_voice_guide">터치하면 읽어주기</string>
    <string name="settings_learner">학습 모드</string>

    <string name="history_headline">기록</string>
    <string name="history_subtitle">누르면 다시 들려드려요. 길게 누르면 지울 수 있어요.</string>
    <string name="history_empty">아직 기록이 없어요.</string>
    <string name="history_delete_question">이 기록을 지울까요?</string>
    <string name="history_delete">삭제</string>
    <string name="history_cancel">취소</string>
</resources>
```

Create `app/src/main/res-i/values/settings_strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Settings and history. -->
<resources>
    <string name="settings_headline">Settings</string>
    <string name="settings_section_scan">Looking around</string>
    <string name="settings_camera">Camera</string>
    <string name="settings_camera_back">Back</string>
    <string name="settings_camera_front">Front</string>
    <string name="settings_compute">Processor</string>
    <string name="settings_compute_gpu">GPU</string>
    <string name="settings_compute_cpu">CPU</string>
    <string name="settings_model">Model</string>
    <string name="settings_model_light">Light</string>
    <string name="settings_model_fast">Fast</string>
    <string name="settings_model_accurate">Accurate</string>
    <string name="settings_score">How sure before speaking</string>
    <string name="settings_score_hint">Higher means fewer mistakes but fewer things found.</string>
    <string name="settings_speech">Speak results</string>
    <string name="settings_colors">Say colours</string>
    <string name="settings_section_app">App</string>
    <string name="settings_language">Language</string>
    <string name="settings_language_system">Phone</string>
    <string name="settings_language_english">English</string>
    <string name="settings_language_korean">한국어</string>
    <string name="settings_high_contrast">High contrast</string>
    <string name="settings_voice_guide">Read what I touch</string>
    <string name="settings_learner">Learner mode</string>

    <string name="history_headline">History</string>
    <string name="history_subtitle">Tap a scan to hear it again. Touch and hold to delete it.</string>
    <string name="history_empty">No scans yet.</string>
    <string name="history_delete_question">Delete this scan?</string>
    <string name="history_delete">Delete</string>
    <string name="history_cancel">Cancel</string>
</resources>
```

Create `app/src/main/res-i/values/settings_styles.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. Controls used on settings-like screens. A and Y may use them too. -->
<resources>
    <!-- One segment of a MaterialButtonToggleGroup: 64 dp tall, filled when selected. -->
    <style name="Widget.Nungil.Toggle" parent="Widget.Material3.Button.OutlinedButton">
        <item name="android:minHeight">@dimen/ng_touch</item>
        <item name="android:insetTop">0dp</item>
        <item name="android:insetBottom">0dp</item>
        <item name="backgroundTint">@color/ng_toggle_bg</item>
        <item name="android:textColor">@color/ng_toggle_text</item>
        <item name="strokeColor">?attr/ngLine</item>
        <item name="strokeWidth">2dp</item>
        <item name="android:textAppearance">@style/TextAppearance.Nungil.Label</item>
    </style>

    <style name="Widget.Nungil.Switch" parent="Widget.Material3.CompoundButton.MaterialSwitch">
        <item name="android:minHeight">@dimen/ng_touch</item>
        <item name="android:textAppearance">@style/TextAppearance.Nungil.Body</item>
        <item name="android:textColor">?attr/ngText</item>
    </style>

    <style name="TextAppearance.Nungil.Section" parent="TextAppearance.Nungil.Label">
        <item name="android:textColor">?attr/ngTextSub</item>
    </style>
</resources>
```

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `SettingsMathTest` 5 pass.

- [ ] **Step 6: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

Settings: change camera, processor, model, the slider and both switches, leave and come back: every value is kept, and A's scan uses them (A checks the inference-time log line). Language 한국어/English/휴대폰 설정 recreates the app in that language and says so. High contrast switches theme at once. History: after A saves a scan, it appears at the top with date, mode and coverage; tapping speaks the summary; touch and hold opens the delete sheet; saying "삭제" after tapping one does the same.

- [ ] **Step 7: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/ui/SettingsMath.kt app/src/main/java/com/nungil/shell/HistoryFragment.kt app/src/main/java/com/nungil/shell/SettingsFragment.kt app/src/main/res-i/color/ng_toggle_bg.xml app/src/main/res-i/color/ng_toggle_text.xml app/src/main/res-i/layout/history_delete_sheet.xml app/src/main/res-i/layout/history_fragment.xml app/src/main/res-i/layout/history_item.xml app/src/main/res-i/layout/settings_fragment.xml app/src/main/res-i/values-ko/settings_strings.xml app/src/main/res-i/values/settings_strings.xml app/src/main/res-i/values/settings_styles.xml app/src/test/java/com/nungil/core/ui/SettingsMathTest.kt
git commit -m "Add settings for scanning, language and contrast, and a history that replays past scans"
git push -u origin i/I7-settings-history
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

### Task I10: Help, learner mode, voice guide, onboarding and the TalkBack pass

**Files:**
- Create: `app/src/main/java/com/nungil/core/ui/ScreenGuide.kt`
- Create: `app/src/main/java/com/nungil/core/ui/ScreenHelp.kt`
- Modify: `app/src/main/java/com/nungil/shell/MainActivity.kt`
- Modify: `app/src/main/java/com/nungil/shell/OnboardingFragment.kt`
- Create: `app/src/main/java/com/nungil/speech/VoiceGuide.kt`
- Create: `app/src/main/res-i/layout/onboarding_fragment.xml`
- Create: `app/src/main/res-i/values-ko/onboarding_strings.xml`
- Create: `app/src/main/res-i/values/onboarding_strings.xml`
- Create: `app/src/test/java/com/nungil/core/ui/ScreenGuideTest.kt`
- Create: `app/src/test/java/com/nungil/core/ui/ScreenHelpTest.kt`

**Interfaces:**
- Consumes: the navigation graph ids (contract), `ShellPhrases`, `MainActivity`.
- Produces: `ScreenHelp.forScreen(screen, lang)`, `forTopic(topic, lang)`, `topicOf(text)`, `hasScreen(screen)`; `OnboardingText.intro(lang)`; `ScreenGuide.position/describeControl/summary`, `MAX_ITEMS = 12`, `ControlKind`, `GuideItem`; `speech.VoiceGuide.describeAt(root, rawX, rawY, lang)`; the real `OnboardingFragment`; "help" speaks the current screen, learner mode speaks each screen as it opens, the voice guide speaks what is tapped while TalkBack is off.

- [ ] **Step 1: Branch**

```powershell
git checkout main; git pull
git checkout -b i/I10-help-onboarding
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/nungil/core/ui/ScreenGuideTest.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenGuideTest {
    @Test fun positionsOnAThreeByThreeGrid() {
        assertEquals("top left", ScreenGuide.position(10f, 10f, 300f, 600f, Lang.EN))
        assertEquals("middle", ScreenGuide.position(150f, 300f, 300f, 600f, Lang.EN))
        assertEquals("bottom right", ScreenGuide.position(290f, 590f, 300f, 600f, Lang.EN))
        assertEquals("아래", ScreenGuide.position(150f, 590f, 300f, 600f, Lang.KO))
        assertEquals("왼쪽 위", ScreenGuide.position(10f, 10f, 300f, 600f, Lang.KO))
    }

    @Test fun describesControls() {
        assertEquals("Look around, button", ScreenGuide.describeControl("Look around", ControlKind.BUTTON, null, true, Lang.EN))
        assertEquals("High contrast, switch, on", ScreenGuide.describeControl("High contrast", ControlKind.SWITCH, true, true, Lang.EN))
        assertEquals("고대비 화면, 스위치, 꺼짐", ScreenGuide.describeControl("고대비 화면", ControlKind.SWITCH, false, true, Lang.KO))
        assertEquals("GPU, button, selected", ScreenGuide.describeControl("GPU", ControlKind.BUTTON, true, true, Lang.EN))
        assertEquals("Start, button, unavailable", ScreenGuide.describeControl("Start", ControlKind.BUTTON, null, false, Lang.EN))
        assertEquals("시작, 버튼, 사용할 수 없음", ScreenGuide.describeControl("시작", ControlKind.BUTTON, null, false, Lang.KO))
    }

    @Test fun summaryReadsAtMostTwelveControls() {
        val items = (1..15).map { GuideItem("Item $it", ControlKind.BUTTON, null, true, 100f, it * 10f) }
        val text = ScreenGuide.summary(items, 300f, 600f, Lang.EN)
        assertTrue(text.startsWith("This screen has 15 controls."))
        assertTrue(text.contains("Item 12, button"))
        assertTrue(!text.contains("Item 13,"))
        assertTrue(text.endsWith("And 3 more."))
    }

    @Test fun koreanSummary() {
        val items = listOf(GuideItem("시작", ControlKind.BUTTON, null, true, 150f, 590f))
        assertEquals("이 화면에는 항목이 1개 있어요. 시작, 버튼, 아래.", ScreenGuide.summary(items, 300f, 600f, Lang.KO))
    }

    @Test fun emptyScreen() = assertEquals("Nothing to press on this screen.", ScreenGuide.summary(emptyList(), 300f, 600f, Lang.EN))
}
```

Create `app/src/test/java/com/nungil/core/ui/ScreenHelpTest.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ScreenHelpTest {
    private fun hasHangul(s: String) = s.any { it.code in 0xAC00..0xD7A3 }

    /** Every destination in the (frozen) navigation graph has help in both languages. */
    @Test fun everyNavDestinationHasHelp() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res-i/navigation/nav_graph.xml"))
        val nodes = doc.getElementsByTagName("fragment")
        val ids = (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:id").removePrefix("@+id/") }
        assertEquals(17, ids.size)
        for (id in ids) {
            assertTrue(id, ScreenHelp.hasScreen(id))
            assertTrue(id, !hasHangul(ScreenHelp.forScreen(id, Lang.EN)))
            assertTrue(id, hasHangul(ScreenHelp.forScreen(id, Lang.KO)))
        }
    }

    @Test fun unknownScreenGetsGeneralHelp() =
        assertEquals(ShellPhrases.text(Phrase.HELP_GENERAL, Lang.EN), ScreenHelp.forScreen("nowhere", Lang.EN))

    @Test fun topicsFromSpokenWords() {
        assertEquals("search", ScreenHelp.topicOf("search"))
        assertEquals("walk", ScreenHelp.topicOf("walk mode"))
        assertEquals("live", ScreenHelp.topicOf("live scan"))
        assertEquals("scan", ScreenHelp.topicOf("full scan"))
        assertEquals("walk", ScreenHelp.topicOf("걷기 모드"))
        assertEquals("search", ScreenHelp.topicOf("검색"))
        assertEquals("voice", ScreenHelp.topicOf("음성 명령"))
        assertNull(ScreenHelp.topicOf("banana"))
    }

    @Test fun topicTextInBothLanguages() {
        assertTrue(hasHangul(ScreenHelp.forTopic("walk mode", Lang.KO)!!))
        assertTrue(ScreenHelp.forTopic("language", Lang.EN)!!.contains("Korean"))
        assertNull(ScreenHelp.forTopic("banana", Lang.EN))
    }

    @Test fun introductionNamesTheApp() {
        assertTrue(OnboardingText.intro(Lang.EN).startsWith("Hello, I am Nungil."))
        assertTrue(OnboardingText.intro(Lang.KO).startsWith("안녕하세요, 눈길이에요."))
    }
}
```

- [ ] **Step 3: Run them and watch them fail**

```powershell
.\gradlew.bat testDebugUnitTest -PskipModels
```

Expected: `Unresolved reference 'ScreenHelp'`, `'ScreenGuide'`, `'OnboardingText'`.

- [ ] **Step 4: Implement**

Create `app/src/main/java/com/nungil/core/ui/ScreenGuide.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang

enum class ControlKind { BUTTON, SWITCH, SLIDER, TEXT_FIELD, TEXT }

/** One control on screen; [checked] is null for controls without an on/off state. */
data class GuideItem(
    val label: String,
    val kind: ControlKind,
    val checked: Boolean?,
    val enabled: Boolean,
    val centerX: Float,
    val centerY: Float,
)

/** Words for the voice guide: "tap a control to hear it, tap empty space to hear the whole screen". */
object ScreenGuide {
    const val MAX_ITEMS = 12

    private val positionsEn = arrayOf("top left", "top", "top right", "left", "middle", "right", "bottom left", "bottom", "bottom right")
    private val positionsKo = arrayOf("왼쪽 위", "위", "오른쪽 위", "왼쪽", "가운데", "오른쪽", "왼쪽 아래", "아래", "오른쪽 아래")

    fun position(x: Float, y: Float, width: Float, height: Float, lang: Lang): String {
        fun third(v: Float, size: Float) = when {
            v < size / 3f -> 0
            v < size * 2f / 3f -> 1
            else -> 2
        }
        val i = third(y, height) * 3 + third(x, width)
        return if (lang == Lang.KO) positionsKo[i] else positionsEn[i]
    }

    fun describeControl(label: String, kind: ControlKind, checked: Boolean?, enabled: Boolean, lang: Lang): String {
        val ko = lang == Lang.KO
        val parts = mutableListOf(label)
        when (kind) {
            ControlKind.BUTTON -> parts += if (ko) "버튼" else "button"
            ControlKind.SWITCH -> parts += if (ko) "스위치" else "switch"
            ControlKind.SLIDER -> parts += if (ko) "슬라이더" else "slider"
            ControlKind.TEXT_FIELD -> parts += if (ko) "입력창" else "text box"
            ControlKind.TEXT -> Unit
        }
        if (checked != null) {
            parts += if (kind == ControlKind.SWITCH) {
                if (checked) (if (ko) "켜짐" else "on") else (if (ko) "꺼짐" else "off")
            } else {
                if (checked) (if (ko) "선택됨" else "selected") else (if (ko) "선택 안 됨" else "not selected")
            }
        }
        if (!enabled) parts += if (ko) "사용할 수 없음" else "unavailable"
        return parts.joinToString(", ")
    }

    fun summary(items: List<GuideItem>, width: Float, height: Float, lang: Lang): String {
        val ko = lang == Lang.KO
        if (items.isEmpty()) return if (ko) "이 화면에는 누를 것이 없어요." else "Nothing to press on this screen."
        val head = when {
            ko -> "이 화면에는 항목이 ${items.size}개 있어요."
            items.size == 1 -> "This screen has 1 control."
            else -> "This screen has ${items.size} controls."
        }
        val shown = items.take(MAX_ITEMS).map {
            describeControl(it.label, it.kind, it.checked, it.enabled, lang) + ", " +
                position(it.centerX, it.centerY, width, height, lang) + "."
        }
        val rest = items.size - MAX_ITEMS
        val tail = if (rest > 0) listOf(if (ko) "외 ${rest}개." else "And $rest more.") else emptyList()
        return (listOf(head) + shown + tail).joinToString(" ")
    }
}
```

Create `app/src/main/java/com/nungil/core/ui/ScreenHelp.kt`:

```kotlin
package com.nungil.core.ui

import com.nungil.contract.Lang
import java.util.Locale

/**
 * Spoken help. One entry per navigation destination (keyed by its id name, e.g. "search_camera") plus
 * general topics, in English and Korean. "help" speaks the current screen; "what is walk mode" a topic.
 */
object ScreenHelp {
    private class Text(val en: String, val ko: String)

    private val screens: Map<String, Text> = mapOf(
        "home" to Text(
            "Home. Look around, find something, or open what you saved. The button at the bottom turns voice commands on.",
            "홈 화면이에요. 주변 둘러보기, 물건 찾기, 저장한 것을 열 수 있어요. 맨 아래 버튼으로 음성 명령을 켤 수 있어요.",
        ),
        "onboarding" to Text(
            "Welcome. Listen to the introduction, then press start at the bottom.",
            "시작 화면이에요. 소개를 듣고 아래의 시작하기를 눌러 주세요.",
        ),
        "scan_hub" to Text(
            "Scan menu. Choose look around, live scan, walk mode or history.",
            "둘러보기 메뉴예요. 주변 둘러보기, 실시간 안내, 걷기 모드, 기록 중에서 골라 주세요.",
        ),
        "settings" to Text(
            "Settings. Choose the camera, the model, how sure I must be, the language and high contrast.",
            "설정 화면이에요. 카메라, 모델, 확신도, 언어, 고대비 화면을 바꿀 수 있어요.",
        ),
        "history" to Text(
            "History. Tap a scan to hear it again. Touch and hold to delete it.",
            "기록 화면이에요. 누르면 다시 들려드리고, 길게 누르면 지울 수 있어요.",
        ),
        "scan" to Text(
            "Look around. Hold the phone upright and turn slowly in a full circle. I will tell you what is around you.",
            "주변 둘러보기 화면이에요. 휴대폰을 세우고 천천히 한 바퀴 돌면 주변을 알려드려요.",
        ),
        "walk" to Text(
            "Walk mode. Hold the phone in front of you while walking. I warn about obstacles, steps and walls.",
            "걷기 모드예요. 걸을 때 휴대폰을 앞으로 들면 장애물, 계단, 벽을 알려드려요.",
        ),
        "search" to Text(
            "Find. Say or type what to look for, for example my bag.",
            "찾기 화면이에요. 찾을 것을 말하거나 입력해 주세요. 예를 들어 가방이라고 말해 보세요.",
        ),
        "search_camera" to Text(
            "Finding. Move the phone slowly. The beeps get faster as you point at it, and the phone vibrates when it is straight ahead.",
            "찾는 중이에요. 휴대폰을 천천히 움직여 주세요. 가까워질수록 소리가 빨라지고, 정면에 오면 진동이 울려요.",
        ),
        "saved" to Text(
            "Saved. People, cars and objects you taught me. Add new ones with the button at the bottom.",
            "저장한 것 화면이에요. 알려주신 사람, 자동차, 물건이 있어요. 아래 버튼으로 새로 추가할 수 있어요.",
        ),
        "person" to Text(
            "A saved person. You can rename or delete them.",
            "저장한 사람이에요. 이름을 바꾸거나 지울 수 있어요.",
        ),
        "add_person" to Text(
            "Add a person. Say or type their name, then continue.",
            "사람 추가 화면이에요. 이름을 말하거나 입력한 뒤 다음을 눌러 주세요.",
        ),
        "enroll" to Text(
            "Learn a face. Point the camera at the face and follow the spoken steps: straight, left, right, up and down.",
            "얼굴 등록 화면이에요. 카메라를 얼굴에 맞추고 안내에 따라 정면, 왼쪽, 오른쪽, 위, 아래를 보여 주세요.",
        ),
        "item" to Text(
            "A saved item. You can rename or delete it.",
            "저장한 물건이에요. 이름을 바꾸거나 지울 수 있어요.",
        ),
        "add_item" to Text(
            "Add an item. Say or type its name, then continue.",
            "물건 추가 화면이에요. 이름을 말하거나 입력한 뒤 다음을 눌러 주세요.",
        ),
        "item_enroll" to Text(
            "Learn an item. Hold the item in front of the camera, keep still, then move it left and right.",
            "물건 등록 화면이에요. 물건을 카메라 앞에 두고 가만히 있다가 왼쪽과 오른쪽으로 움직여 주세요.",
        ),
        "reader" to Text(
            "Read text. Point the camera at printed text or a QR code and I will read it.",
            "글자 읽기 화면이에요. 카메라를 글자나 QR 코드에 맞추면 읽어 드려요.",
        ),
    )

    private val topics: Map<String, Text> = mapOf(
        "voice" to Text(
            "Voice commands: press the microphone button on Home, then just speak. Say stop listening to turn it off.",
            "음성 명령: 홈의 마이크 버튼을 누르고 말씀하세요. 끄려면 듣기 중지라고 말해 주세요.",
        ),
        "language" to Text(
            "Say Korean or English to switch the language, or change it in Settings.",
            "한국어나 영어라고 말하면 언어가 바뀌어요. 설정에서도 바꿀 수 있어요.",
        ),
        "learner" to Text(
            "Learner mode explains each screen when it opens.",
            "학습 모드는 화면이 열릴 때마다 설명해 드려요.",
        ),
        "contrast" to Text(
            "High contrast shows white and yellow on black. Turn it on in Settings.",
            "고대비 화면은 검은 바탕에 흰색과 노란색으로 보여 줘요. 설정에서 켤 수 있어요.",
        ),
        "faces" to Text(
            "Save a person's face under Saved, then I say their name when I see them.",
            "저장한 것에서 사람 얼굴을 등록하면, 보일 때 이름을 말해 드려요.",
        ),
        "coverage" to Text(
            "The ring shows how much of the room you have turned through. The scan ends when it is full.",
            "원은 방을 얼마나 돌았는지 보여 줘요. 원이 다 차면 둘러보기가 끝나요.",
        ),
        "live" to Text(
            "Live scan tells you about things as soon as the camera finds them.",
            "실시간 안내는 카메라가 찾는 즉시 알려드려요.",
        ),
    )

    /** Spoken words to topic keys; longer phrases are checked first. Keys may also be screen names. */
    private val words: List<Pair<String, String>> = listOf(
        "live scan" to "live", "live" to "live", "실시간" to "live",
        "walk mode" to "walk", "walk" to "walk", "걷기" to "walk", "보행" to "walk",
        "full scan" to "scan", "look around" to "scan", "scan" to "scan", "둘러보기" to "scan", "스캔" to "scan",
        "search" to "search", "find" to "search", "찾기" to "search", "검색" to "search",
        "saved" to "saved", "저장" to "saved",
        "history" to "history", "기록" to "history",
        "settings" to "settings", "설정" to "settings",
        "voice" to "voice", "microphone" to "voice", "음성" to "voice", "마이크" to "voice",
        "language" to "language", "언어" to "language",
        "learner" to "learner", "학습" to "learner",
        "contrast" to "contrast", "고대비" to "contrast",
        "read" to "reader", "글자" to "reader", "qr" to "reader",
        "face" to "faces", "얼굴" to "faces",
        "ring" to "coverage", "coverage" to "coverage", "percent" to "coverage", "퍼센트" to "coverage",
    ).sortedByDescending { it.first.length }

    fun hasScreen(screen: String): Boolean = screen in screens

    fun forScreen(screen: String, lang: Lang): String =
        screens[screen]?.pick(lang) ?: ShellPhrases.text(Phrase.HELP_GENERAL, lang)

    /** Help for a spoken topic ("walk mode", "검색"), or null when the topic is unknown. */
    fun forTopic(topic: String, lang: Lang): String? {
        val key = topicOf(topic) ?: return null
        return (topics[key] ?: screens[key])?.pick(lang)
    }

    fun topicOf(text: String): String? {
        val t = text.lowercase(Locale.ROOT)
        return words.firstOrNull { t.contains(it.first) }?.second
    }

    private fun Text.pick(lang: Lang) = if (lang == Lang.KO) ko else en
}

/** The twenty-second introduction spoken on first launch. */
object OnboardingText {
    fun intro(lang: Lang): String = if (lang == Lang.KO) {
        "안녕하세요, 눈길이에요. 인터넷 없이 이 휴대폰만으로 주변에 무엇이 있는지 알려드려요. " +
            "주변 둘러보기라고 말하거나 홈의 첫 번째 카드를 누른 뒤 천천히 한 바퀴 돌아 보세요. " +
            "가방 찾아줘처럼 말하면 소리를 따라 찾을 수 있어요. " +
            "저장한 것이라고 말하면 사람과 물건을 알려 줄 수 있어요. " +
            "아래의 시작하기를 눌러 시작해 주세요."
    } else {
        "Hello, I am Nungil. I tell you what is around you, using only this phone, even without internet. " +
            "Say look around, or tap the first card on the home screen, then turn slowly in a circle. " +
            "Say find and a thing, like find my bag, and follow the beeps. " +
            "Say saved to teach me people and things. " +
            "Press start at the bottom to begin."
    }
}
```

Replace the whole file `app/src/main/java/com/nungil/shell/MainActivity.kt`:

```kotlin
package com.nungil.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppNavigator
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.Beeper
import com.nungil.contract.app.Haptics
import com.nungil.contract.app.Speaker
import com.nungil.contract.app.VoiceHandler
import com.nungil.core.ui.AppLanguage
import com.nungil.core.ui.CommandRouter
import com.nungil.core.ui.LanguageChoice
import com.nungil.core.ui.PermissionOutcome
import com.nungil.core.ui.Phrase
import com.nungil.core.ui.Route
import com.nungil.core.ui.ScreenHelp
import com.nungil.core.ui.ShellPhrases
import com.nungil.core.ui.VoiceChoice
import com.nungil.core.voice.VoiceCommandParser
import com.nungil.databinding.ActivityMainBinding
import com.nungil.design.isTalkBackOn
import com.nungil.design.openAppSettings
import com.nungil.items.AddItemFragmentArgs
import com.nungil.people.AddPersonFragmentArgs
import com.nungil.saved.SavedFragmentArgs
import com.nungil.scan.ScanFragmentArgs
import com.nungil.search.SearchFragmentArgs
import com.nungil.speech.ToneBeeper
import com.nungil.speech.TtsSpeaker
import com.nungil.speech.VibratorHaptics
import com.nungil.speech.VoiceGuide
import com.nungil.speech.VoiceInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner I. The single activity: theme (light, dark or high contrast), toolbar, nav host, caption bar,
 * the AppServices every screen uses, the always-on microphone, voice-command routing, spoken help,
 * learner mode and the tap-to-hear voice guide.
 */
class MainActivity : AppCompatActivity(), AppServices, AppNavigator {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var prefs: AppPrefs
    private lateinit var tts: TtsSpeaker
    private lateinit var vibration: VibratorHaptics
    private val tones = ToneBeeper()
    private var voiceChoice: VoiceChoice? = null
    private lateinit var voice: VoiceInput
    private val voiceOnState = MutableStateFlow(false)

    /** A screen waiting for words (askForWords) while the always-on listener runs. */
    private var dictation: ((String) -> Unit)? = null
    private var dictationOwner: LifecycleOwner? = null

    private val guide = VoiceGuide()
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L

    private var afterMicGranted: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onMicPermission(granted)
    }

    /** The language the user chose for the screens (per-app locale, else the phone's language). */
    val uiLang: Lang
        get() {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val tag = if (appLocales.isEmpty) {
                ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language
            } else {
                appLocales[0]?.language
            }
            return Lang.fromTag(tag)
        }

    /** The language of spoken sentences: the UI language, unless its voice is missing (then English). */
    override val lang: Lang
        get() = voiceChoice?.speak ?: uiLang
    override val speaker: Speaker get() = tts
    override val haptics: Haptics get() = vibration
    override val beeper: Beeper get() = tones
    override val navigator: AppNavigator get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = AppPrefs(this)
        val highContrast = prefs.highContrast
        if (highContrast) setTheme(R.style.Theme_Nungil_HighContrast)
        // High contrast is black in light and dark mode, so its bar icons are always light.
        val bars = if (highContrast) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(bars, bars)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 35 draws edge-to-edge on Android 15: keep content out from under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        navController = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        binding.toolbar.setupWithNavController(navController, AppBarConfiguration(setOf(R.id.home)))

        vibration = VibratorHaptics(this)
        tts = TtsSpeaker(
            context = this,
            wanted = { uiLang },
            onCaption = { text -> binding.caption.text = text },
            onVoiceChoice = ::onVoiceChoice,
        )
        voice = VoiceInput(
            context = this,
            language = { uiLang },
            isSpeaking = { tts.isBusy },
            stopSpeaking = { tts.stop() },
            onHeard = ::onHeard,
            onProblem = ::onVoiceProblem,
        )
        prefs.takePendingAnnouncement()?.let { tts.say(it) }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (prefs.learnerOn) tts.say(ScreenHelp.forScreen(resources.getResourceEntryName(destination.id), lang))
        }
        if (savedInstanceState == null && !prefs.onboarded) {
            prefs.onboarded = true // shown once, even if the user backs out of it
            open(Dest.Onboarding)
        }
    }

    override fun onResume() {
        super.onResume()
        // Listen only while the app is in front; the user's choice survives in AppPrefs.
        if (prefs.voiceOn && hasMic()) voice.start()
        voiceOnState.value = voice.isOn
    }

    override fun onPause() {
        voice.stop()
        super.onPause()
    }

    override fun onDestroy() {
        voice.destroy()
        tts.shutdown()
        tones.release()
        super.onDestroy()
    }

    override fun askForWords(owner: LifecycleOwner, onText: (String) -> Unit) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        if (voice.isOn) {
            // One microphone, one owner: the always-on listener hands the next non-command words over.
            dictation = onText
            dictationOwner = owner
            owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (dictationOwner === owner) {
                        dictation = null
                        dictationOwner = null
                    }
                }
            })
            return
        }
        ensureMic {
            haptics.buzz(Buzz.TAP)
            voice.listenOnce { text ->
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) onText(text)
            }
        }
    }

    // ---- Navigation -------------------------------------------------------------------------------

    override fun open(dest: Dest) {
        when (dest) {
            Dest.Home -> navController.popBackStack(R.id.home, false)
            Dest.ScanHub -> go(R.id.scan_hub)
            is Dest.Scan -> go(R.id.scan, ScanFragmentArgs(dest.mode).toBundle())
            Dest.Walk -> go(R.id.walk)
            is Dest.Search -> go(R.id.search, SearchFragmentArgs(dest.query).toBundle())
            is Dest.Saved -> go(R.id.saved, SavedFragmentArgs(dest.tab?.ordinal ?: -1).toBundle())
            Dest.Settings -> go(R.id.settings)
            Dest.History -> go(R.id.history)
            is Dest.AddPerson -> go(R.id.add_person, AddPersonFragmentArgs(dest.name).toBundle())
            is Dest.AddItem -> go(R.id.add_item, AddItemFragmentArgs(dest.kind, dest.name).toBundle())
            Dest.Reader -> go(R.id.reader)
            Dest.Onboarding -> go(R.id.onboarding)
        }
    }

    override fun back() {
        if (!navController.popBackStack()) finish()
    }

    /** popUpTo the same destination, so "full scan" said twice never stacks two scan screens. */
    private fun go(id: Int, args: Bundle? = null) {
        val options = NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(id, true).build()
        navController.navigate(id, args, options)
    }

    /** The screen on top, which gets voice commands first. */
    private fun currentScreen(): Fragment? =
        supportFragmentManager.findFragmentById(R.id.nav_host)?.childFragmentManager?.primaryNavigationFragment

    // ---- Voice commands ---------------------------------------------------------------------------

    private fun onHeard(text: String) {
        val command = VoiceCommandParser.parse(text)
        Log.i(TAG, "Heard \"$text\" -> $command")
        val claim = dictation
        if (claim != null && command is VoiceCommand.Unknown) {
            dictation = null
            dictationOwner = null
            claim(command.text)
            return
        }
        handleCommand(command)
    }

    private fun onVoiceProblem(phrase: Phrase) {
        if (phrase == Phrase.MIC_NEEDED || phrase == Phrase.VOICE_UNAVAILABLE) {
            prefs.voiceOn = false
            voiceOnState.value = false
        }
        say(phrase)
    }

    /** Runs one parsed command: global ones here, the rest on the current screen first. Main thread. */
    fun handleCommand(command: VoiceCommand) {
        if (!CommandRouter.isGlobal(command)) {
            val screen = currentScreen() as? VoiceHandler
            if (screen?.onVoiceCommand(command) == true) return
        }
        perform(CommandRouter.route(command))
    }

    private fun perform(route: Route) {
        when (route) {
            is Route.Open -> open(route.dest)
            Route.GoBack -> back()
            Route.RepeatLast -> if (!tts.repeatLast()) say(Phrase.NOTHING_TO_REPEAT)
            is Route.SpeakHelp -> tts.sayNow(helpText(route.topic))
            is Route.SetLearner -> setLearner(route.on)
            Route.StopListening -> setVoiceOn(false)
            is Route.SwitchLanguage -> setLanguage(AppLanguage.forLang(route.lang))
            is Route.OpenAndSay -> {
                open(route.dest)
                say(route.phrase)
            }
            is Route.Say -> say(route.phrase)
            Route.StopSpeaking -> tts.stop()
        }
    }

    private fun say(phrase: Phrase) = tts.say(ShellPhrases.text(phrase, lang))

    /** Help for a spoken topic, else for the screen on top. */
    private fun helpText(topic: String?): String {
        topic?.let { t -> ScreenHelp.forTopic(t, lang)?.let { return it } }
        val screen = navController.currentDestination?.id?.let { resources.getResourceEntryName(it) } ?: "home"
        return ScreenHelp.forScreen(screen, lang)
    }

    // ---- Voice guide ------------------------------------------------------------------------------

    /**
     * A tap (short, without moving) speaks what is under the finger while TalkBack is off. The event is
     * always passed on, so the tapped control works as usual.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::prefs.isInitialized && prefs.voiceGuideOn && !isTalkBackOn()) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    downAt = ev.eventTime
                }
                MotionEvent.ACTION_UP -> {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    val still = kotlin.math.abs(ev.rawX - downX) < slop && kotlin.math.abs(ev.rawY - downY) < slop
                    if (still && ev.eventTime - downAt < ViewConfiguration.getLongPressTimeout()) {
                        guide.describeAt(binding.navHost, ev.rawX.toInt(), ev.rawY.toInt(), lang)?.let { tts.say(it) }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // ---- App settings -----------------------------------------------------------------------------

    val languageChoice: LanguageChoice
        get() = AppLanguage.choiceOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /** Switches the UI, speech and recognition language; the activity is recreated by AppCompat. */
    fun setLanguage(choice: LanguageChoice) {
        val tag = AppLanguage.tag(choice)
        val target = if (choice == LanguageChoice.SYSTEM) {
            Lang.fromTag(ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language)
        } else {
            Lang.fromTag(tag)
        }
        val sentence = ShellPhrases.text(Phrase.LANGUAGE_SET, target)
        if (choice == languageChoice) {
            tts.say(sentence)
            return
        }
        prefs.setPendingAnnouncement(sentence)
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    val highContrast: Boolean get() = prefs.highContrast

    fun setHighContrast(on: Boolean) {
        if (prefs.highContrast == on) return
        prefs.highContrast = on
        recreate()
    }

    val learnerOn: Boolean get() = prefs.learnerOn

    fun setLearner(on: Boolean) {
        prefs.learnerOn = on
        say(if (on) Phrase.LEARNER_ON else Phrase.LEARNER_OFF)
    }

    /** Always-on voice commands, for the Home microphone button. */
    val voiceOn: StateFlow<Boolean> get() = voiceOnState.asStateFlow()

    fun setVoiceOn(on: Boolean) {
        if (!on) {
            prefs.voiceOn = false
            voice.stop()
            voiceOnState.value = false
            say(Phrase.VOICE_OFF)
            return
        }
        ensureMic {
            prefs.voiceOn = true
            say(Phrase.VOICE_ON)
            voice.start()
            voiceOnState.value = true
        }
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Runs [then] once the microphone permission is granted, asking for it first if needed. */
    private fun ensureMic(then: () -> Unit) {
        if (hasMic()) {
            then()
            return
        }
        afterMicGranted = then
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) say(Phrase.MIC_NEEDED)
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun onMicPermission(granted: Boolean) {
        val then = afterMicGranted
        afterMicGranted = null
        when (PermissionOutcome.of(granted, shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))) {
            PermissionOutcome.GRANTED -> then?.invoke()
            PermissionOutcome.DENIED -> say(Phrase.MIC_NEEDED)
            PermissionOutcome.BLOCKED -> {
                say(Phrase.MIC_BLOCKED)
                Snackbar.make(binding.root, R.string.voice_mic_blocked, Snackbar.LENGTH_LONG)
                    .setAction(R.string.voice_open_settings) { openAppSettings() }
                    .show()
            }
        }
    }

    private fun onVoiceChoice(choice: VoiceChoice) {
        voiceChoice = choice
        if (choice.notice == null) return
        Snackbar.make(binding.root, R.string.voice_korean_voice_missing, Snackbar.LENGTH_LONG)
            .setAction(R.string.voice_install) {
                runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            }
            .show()
    }

    private companion object {
        const val TAG = "Nungil"
    }
}
```

Replace the whole file `app/src/main/java/com/nungil/shell/OnboardingFragment.kt`:

```kotlin
package com.nungil.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.nungil.contract.Dest
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.ui.OnboardingText
import com.nungil.databinding.OnboardingFragmentBinding
import com.nungil.design.setHeading

/** Owner I. Twenty-second spoken introduction on first launch; "Start" (button or voice) goes Home. */
class OnboardingFragment : Fragment(), VoiceHandler {
    private var _binding: OnboardingFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = OnboardingFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.onboardingHeadline.setHeading()
        if (savedInstanceState == null) services().speaker.sayNow(OnboardingText.intro(services().lang))
        binding.onboardingStart.setOnClickListener { finish() }
        binding.onboardingVoice.setOnClickListener { (requireActivity() as MainActivity).setVoiceOn(true) }
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean {
        if (command != VoiceCommand.Start) return false
        finish()
        return true
    }

    private fun finish() {
        services().speaker.stop()
        services().navigator.open(Dest.Home)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
```

Create `app/src/main/java/com/nungil/speech/VoiceGuide.kt`:

```kotlin
package com.nungil.speech

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.nungil.contract.Lang
import com.nungil.core.ui.ControlKind
import com.nungil.core.ui.GuideItem
import com.nungil.core.ui.ScreenGuide

/**
 * The voice guide for users without TalkBack: a tap on a control speaks its name and state, a tap on
 * empty space speaks a summary of the screen. It never consumes the tap, so the control still works.
 */
class VoiceGuide {
    private val rect = Rect()

    /** What to say for a tap at screen coordinates ([rawX], [rawY]); null when the tap is outside [root]. */
    fun describeAt(root: View, rawX: Int, rawY: Int, lang: Lang): String? {
        if (!root.getGlobalVisibleRect(rect) || !rect.contains(rawX, rawY)) return null
        val hit = deepestControlAt(root, rawX, rawY)
        if (hit != null) {
            val (kind, checked) = kindOf(hit)
            return ScreenGuide.describeControl(labelOf(hit), kind, checked, hit.isEnabled, lang)
        }
        val items = mutableListOf<GuideItem>()
        collect(root, items)
        root.getGlobalVisibleRect(rect)
        return ScreenGuide.summary(items, rect.width().toFloat(), rect.height().toFloat(), lang)
    }

    private fun isControl(view: View): Boolean =
        view.isShown && (view.isClickable || view is CompoundButton || view is Slider || view is EditText)

    private fun deepestControlAt(view: View, x: Int, y: Int): View? {
        if (!view.isShown || !view.getGlobalVisibleRect(rect) || !rect.contains(x, y)) return null
        if (view is ViewGroup && view !is Slider) {
            for (i in view.childCount - 1 downTo 0) {
                deepestControlAt(view.getChildAt(i), x, y)?.let { return it }
            }
        }
        return if (isControl(view)) view else null
    }

    private fun collect(view: View, out: MutableList<GuideItem>) {
        if (!view.isShown) return
        if (isControl(view)) {
            view.getGlobalVisibleRect(rect)
            val (kind, checked) = kindOf(view)
            out += GuideItem(labelOf(view), kind, checked, view.isEnabled, rect.exactCenterX(), rect.exactCenterY())
            return
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i), out)
    }

    private fun kindOf(view: View): Pair<ControlKind, Boolean?> = when {
        view is CompoundButton -> ControlKind.SWITCH to view.isChecked
        view is Slider -> ControlKind.SLIDER to null
        view is EditText -> ControlKind.TEXT_FIELD to null
        view is MaterialButton && view.isCheckable -> ControlKind.BUTTON to view.isChecked
        else -> ControlKind.BUTTON to null
    }

    private fun labelOf(view: View): String {
        view.contentDescription?.takeIf { it.isNotBlank() }?.let { described ->
            return if (view is Slider) "$described ${view.value.toInt()}" else described.toString()
        }
        if (view is TextView && view.text.isNotBlank()) return view.text.toString()
        val texts = mutableListOf<String>()
        fun gather(v: View) {
            if (!v.isShown) return
            if (v is TextView && v.text.isNotBlank()) texts += v.text.toString()
            if (v is ViewGroup) for (i in 0 until v.childCount) gather(v.getChildAt(i))
        }
        gather(view)
        return texts.joinToString(", ")
    }
}
```

Create `app/src/main/res-i/layout/onboarding_fragment.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. First-launch introduction: what the app does and the three commands that matter. -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:paddingStart="@dimen/ng_gutter"
    android:paddingEnd="@dimen/ng_gutter">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <TextView
                android:id="@+id/onboarding_headline"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap_large"
                android:text="@string/onboarding_headline"
                android:textAppearance="@style/TextAppearance.Nungil.Display" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap"
                android:text="@string/onboarding_body"
                android:textAppearance="@style/TextAppearance.Nungil.Body"
                android:textColor="?attr/ngTextSub" />

            <com.google.android.material.card.MaterialCardView
                style="@style/Widget.Nungil.Card.Primary"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap_large">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/onboarding_try_saying"
                        android:textAppearance="@style/TextAppearance.Nungil.Section" />

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="@dimen/ng_gap"
                        android:text="@string/onboarding_command_scan"
                        android:textAppearance="@style/TextAppearance.Nungil.Headline" />

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="@string/onboarding_command_find"
                        android:textAppearance="@style/TextAppearance.Nungil.Headline" />

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="@string/onboarding_command_saved"
                        android:textAppearance="@style/TextAppearance.Nungil.Headline" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <com.google.android.material.button.MaterialButton
                android:id="@+id/onboarding_voice"
                style="@style/Widget.Nungil.Button.Tonal"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/ng_gap_large"
                android:text="@string/home_mic_turn_on"
                app:icon="@drawable/ng_ic_mic" />
        </LinearLayout>
    </ScrollView>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/onboarding_start"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/ng_gap"
        android:layout_marginBottom="@dimen/ng_gap"
        android:text="@string/onboarding_start" />
</LinearLayout>
```

Create `app/src/main/res-i/values-ko/onboarding_strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. 첫 실행 소개. -->
<resources>
    <string name="onboarding_headline">안녕하세요, 눈길이에요</string>
    <string name="onboarding_body">인터넷 없이 이 휴대폰만으로 주변에 무엇이 있는지 알려드려요.</string>
    <string name="onboarding_try_saying">이렇게 말해 보세요</string>
    <string name="onboarding_command_scan">“주변 둘러보기”</string>
    <string name="onboarding_command_find">“가방 찾아줘”</string>
    <string name="onboarding_command_saved">“저장한 것”</string>
    <string name="onboarding_start">시작하기</string>
</resources>
```

Create `app/src/main/res-i/values/onboarding_strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Owner I. First-launch introduction. -->
<resources>
    <string name="onboarding_headline">Hello, I am Nungil</string>
    <string name="onboarding_body">I tell you what is around you using only this phone, even without internet.</string>
    <string name="onboarding_try_saying">Try saying</string>
    <string name="onboarding_command_scan">“Look around”</string>
    <string name="onboarding_command_find">“Find my bag”</string>
    <string name="onboarding_command_saved">“Saved”</string>
    <string name="onboarding_start">Start</string>
</resources>
```

- [ ] **Step 5: Run the tests and build**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug -PskipModels
```

Expected: `BUILD SUCCESSFUL`; `ScreenHelpTest` 5 (it reads the real navigation graph: all 17 destinations have help in both languages) and `ScreenGuideTest` 5 pass.

- [ ] **Step 6: Check it on the phone (English and Korean)**

```powershell
.\gradlew.bat installDebug
```

TalkBack pass (write the results in the PR): (1) turn TalkBack on; swipe through Home, Settings, History and Onboarding: the headline is announced as a heading first, every control has a name, the bottom button is last; (2) the voice guide stays silent while TalkBack is on; (3) turn TalkBack off: tapping a card says "주변 둘러보기, 버튼", tapping empty space reads the screen summary; (4) say "help" / "도움말" on three screens: each speaks its own help; "what is walk mode" / "걷기 모드가 뭐예요" speaks the topic; (5) turn learner mode on: every screen explains itself when it opens; (6) clear app data: onboarding speaks the introduction (about 20 seconds) and "시작하기" goes Home.

- [ ] **Step 7: Commit and open the pull request**

```powershell
git add -A app/src/main/java/com/nungil/core/ui/ScreenGuide.kt app/src/main/java/com/nungil/core/ui/ScreenHelp.kt app/src/main/java/com/nungil/shell/MainActivity.kt app/src/main/java/com/nungil/shell/OnboardingFragment.kt app/src/main/java/com/nungil/speech/VoiceGuide.kt app/src/main/res-i/layout/onboarding_fragment.xml app/src/main/res-i/values-ko/onboarding_strings.xml app/src/main/res-i/values/onboarding_strings.xml app/src/test/java/com/nungil/core/ui/ScreenGuideTest.kt app/src/test/java/com/nungil/core/ui/ScreenHelpTest.kt
git commit -m "Speak help for every screen, add learner mode, the tap-to-hear guide and the first-launch introduction"
git push -u origin i/I10-help-onboarding
```

Open a pull request into `main`; wait for CI (`check`) to pass and one review, then merge.

---

## Hand-offs (what I tells A and Y)

| When | Message |
|---|---|
| h1.5 (after I1 step 1) | Figma link and screenshots are in `docs/design/ui-guide.md`; use only `?attr/ng…` tokens and `Widget.Nungil.*` styles. |
| h5 (I3 merged) | `services().speaker` is the real queue: `say` for announcements, `sayNow` to interrupt, `sayFinal(text) { … }` for the summary before closing. Never speak through TalkBack's `announceForAccessibility`. |
| h7 (I4 merged) | `services().haptics.buzz(Buzz.CENTERED)` is the 60 ms "centred" pulse; `services().beeper.pulse(ms)` changes speed immediately when faster; always call `beeper.stop()` in `onDestroyView`. |
| h8 (I2 merged, S1) | A: `CoverageRingView` is real; call `setCoverage(percent, bins)` on the main thread. A and Y: `BigCardView`, `setHeading()`, `openAppSettings()`, `ng_ic_*` icons are available. |
| h10.5 (I5 merged) | Screens implementing `VoiceHandler` now receive commands; return `true` only for what you handle. `Repeat`, language, learner and "stop listening" never reach screens. |
| h15 (I9 merged, S2) | `askForWords(viewLifecycleOwner) { text -> … }` is real. Keep a keyboard field anyway. Watch `adb logcat -s Nungil:V` for `Heard "…" -> …` lines when a command seems ignored. |
| h19 (I7 merged) | Settings writes `SettingsStore` on every change; A reads it on `CameraSession.start()`. `Widget.Nungil.Toggle` and `Widget.Nungil.Switch` are available for any settings-like control. |
| h22 (I10 merged, S3) | Help text for your screens is in `ScreenHelp`; if a screen's behaviour changed, open `REQ → I` with the new sentence in both languages. |

## Design review checklist (I on every A and Y pull request; comments only, never edits)

1. Colours only via `?attr/ng…`; text only via `TextAppearance.Nungil.*`; spacing via `@dimen/ng_*`.
2. One headline per screen, marked with `setHeading()`; one filled button, full width at the bottom; destructive actions use `Widget.Nungil.Button.Danger`.
3. Camera preview sits inside a 24 dp card, not behind text.
4. Every string in `values/` and `values-ko/`; Korean is 해요체; glossary words from the team plan §5; no emoji.
5. Every icon-only control has a content description; every touch target is at least 64 dp.
6. Speech goes through `services().speaker`; no screen creates its own `TextToSpeech` or `SpeechRecognizer`.
7. Screenshot in the PR for light, dark and high contrast, and one at the largest font size.

---

## Changes after execution

Found on real phones (Infinix X6880 on Android 15, Galaxy S10+ on Android 12) after I1–I10 were merged.
Each row names the change, why it was needed and where it lives. Every change has unit tests where the
logic is pure.

| Change | Why | Files | Merged in |
|---|---|---|---|
| **The microphone listens the whole time the app is open**, including while the app talks. It listens again 100–250 ms after each phrase, silence no longer counts as an error, and voice is on by default. | Before, the microphone waited up to 15 s for the app to stop speaking and then cut the app off, so it felt "not always on". | `core/ui/RecognizerPolicy.kt`, `speech/VoiceInput.kt`, `shell/AppPrefs.kt` | commit `5329791` |
| **The app goes quiet when the user talks** (barge-in). Any word the app is not saying stops speech and beeps until the phrase ends. The app's own voice heard by the microphone is ignored (`VoiceBargeIn.isEcho`). | "When the user speaks, every other sound must stop, and the user must never be cut off." | `core/ui/VoiceBargeIn.kt`, `speech/TtsSpeaker.kt` (`holdForUser`, `resumeAfterUser`, `recentSpeech`), `speech/ToneBeeper.kt` | `5329791` |
| **The system beep is replaced by the app's own chime**: two soft notes, rising for on and falling for off. The recognizer's start and end beeps are muted for at most 2 s. | The recognizer beeped on every restart. | `core/ui/ChimeSynth.kt`, `speech/MicChime.kt`, `speech/EarconMuter.kt` | `5329791` |
| "I did not understand" is said **at most once every 10 s**. | An always-on microphone also hears people nearby. | `shell/MainActivity.kt`, `RecognizerPolicy.NOT_UNDERSTOOD_GAP_MS` | `5329791` |
| **Any microphone button silences every sound** and keeps it quiet for up to 6 s while the user talks. The spoken "voice on/off" is replaced by the chime. Home: a tap means "speak", a long press turns voice off. | Pressing the mic did not stop the onboarding instructions. | `MainActivity.talkNow`, `VoiceInput.talkNow`, `shell/HomeFragment.kt`, `home_mic_speak` string | commit `85f90ff` |
| **Wake word**: "Eye" (Korean "눈길" / "눈길아") wakes the app and "Eye stop" puts it to sleep. "Eye, saved" wakes it and runs the command. While asleep only the wake word is answered, and both words work in both languages. | Requested so the app acts only when addressed, like a smart speaker. | `core/voice/WakeWord.kt`, `MainActivity.onHeard`, `home_mic_asleep`, onboarding strings, `OnboardingText.intro` | commit `a1de0ff` |
| **Stop and back also silence the app.** Back (by voice, toolbar or gesture), opening another screen and "stop" all stop speech and beeps. "Eye stop" also sends Stop to the current screen. There are more stop and back words (cancel, exit, close, 조용히 해, 나가, 닫아). | "Stop" or "back" during a scan left announcements running. | `shell/MainActivity.kt`, `core/voice/VoiceCommandParser.kt` | PR #4 |
| **Figma plugin** that builds the `Nungil UI` file from the app's tokens. | I1 step 2 without hand-drawing every screen. | `docs/design/figma-plugin/`, `docs/design/ui-guide.md` | PR #6 |
| **The coverage ring sits on a card-coloured disc.** The caption bar hides when empty and shows at most 3 lines. Home's button row is not baseline-aligned. | The percent was unreadable over the camera image; a long intro pushed the screen content away; a two-line label was cut off. | `design/CoverageRingView.kt`, `res-i/layout/activity_main.xml`, `res-i/layout/home_fragment.xml` | PR #8 |
| **Walk mode** moved from A to I (contract PR #15) and built from build guide §7 plus the street-navigation spec. It covers walls, steps, stairs up and down, and drops from ARCore depth; hazards, signs, codes, traffic lights and saved things; and "save this place" / "take me to X" through openrouteservice, or an offline beacon. Field fixes: the phone height comes from the floor at the feet, a wall is held and brought closer by steps when depth is lost, scene semantics is off (it made ARCore crash natively on pause), and the session restarts if tracking is lost for 5 s. | Requested after the core app was done; tested on an Infinix X6880. | `core/walk/`, `walk/`, `res-i/layout/walking_fragment.xml`, `walking_strings.xml`, `shell/MainActivity.kt` (walk commands) | PRs #15, #16, #18 |
| **Demo script** and `DemoCommandsTest`. | Pins every demo phrase in English and Korean. | `docs/design/demo-script.md`, `app/src/test/java/com/nungil/core/voice/DemoCommandsTest.kt` | PR #10 |

Numbers added after execution, each pinned by a test:

| Name | Value |
|---|---|
| `DELAY_AFTER_ENABLE_MS` | 400 ms |
| `DELAY_AFTER_COMMAND_MS` | 250 ms |
| `DELAY_AFTER_SILENCE_MS` | 100 ms |
| `HOLD_SAFETY_MS` | 10 s |
| `TALK_WINDOW_MS` | 6 s |
| `MUTE_TAIL_MS` | 300 ms |
| `MUTE_MAX_MS` | 2 s |
| `NOT_UNDERSTOOD_GAP_MS` | 10 s |
| `VoiceBargeIn.MIN_ECHO_WORDS` | 3 |
| `VoiceBargeIn.ECHO_SHARE` | 0.7 |
| `ChimeSynth` notes | 660 and 990 Hz, 90 ms each, amplitude 0.35 |
