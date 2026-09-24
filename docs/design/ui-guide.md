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
