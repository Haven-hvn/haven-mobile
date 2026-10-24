# Design Tokens — Haven (Material 3, Jetpack Compose)

Source of truth is code; this file mirrors it. Dark (Void) is the default, light is Paper.
If this file and code disagree, code wins and this file is the bug.

- Shapes: [HavenTokens.kt](../core-design/src/main/kotlin/haven/mobile/core/design/HavenTokens.kt) (`HavenShapes`)
- Spacing/elevation/motion: same file (`HavenSpacing`, `HavenElevation`, `HavenMotion`)
- Type: [HavenTypography.kt](../core-design/src/main/kotlin/haven/mobile/core/design/HavenTypography.kt)
- Color: [HavenPalette.kt](../core-design/src/main/kotlin/haven/mobile/core/design/color/HavenPalette.kt), authored in OKLCH

## Color

Authored in OKLCH so the Void and Paper ramps stay two ends of one ramp. No feature module
hard-codes a hex; semantic roles live in `HavenAccents` (cache state, attestation state,
protocol hues, seal, skeleton) and the Material scheme.

- Grounds (dark): VoidSunk → VoidBase → VoidLow → VoidContainer → VoidHigh → VoidHighest → VoidBright
- Grounds (light): PaperBase with PaperLow/Container/High/Highest steps, PaperRaised on top
- Ink: InkOnVoid / InkOnPaper, with Muted and Faint steps. Running text is full ink, never grey.
- Accent: Ember (amber/ochre, hue 44) — primary actions, active nav, focus. One accent only.
- Meaning hues (never repainted by dynamic color): Arkiv, ICP, EVM, Filecoin; Alert for failure.
- Attestation and cache states use `HavenAccents` roles, not raw colors, so a failed badge
  cannot drift toward a verified one.

## Typography (sp)

Three voices: Institution (sans, UI), Ledger (mono — addresses, CIDs, byte counts, error
codes only), Editorial (serif — the onboarding brand line only).

- Display 44/48, 36/42, 30/36 · Headline 27/33, 23/29, 20/26 · Title 20/26, 16/22, 14/20
- Body 16/24, 14/21, 12/18 · Label 14/18, 12/16, 11/15
- Extra roles in `HavenTextStyles`: `mono` 13/18, `monoSmall` 11/15, `figure` 14/19 (tabular),
  `editorial` 30/38, `overline` 11/14 (sentence case in content; see [taste.md](taste.md))
- Display/headline tracking is tightened (-0.018 to -0.032em). Five sizes do the visible work
  on any one screen.

## Spacing / Shape / Elevation

- Spacing scale (dp): 2, 4, 8, 12, 16, 24, 32, 48. Gutter 16. Touch target min 48.
  Media row height 76, row glyph 40, hairline 1.
- Radius: extraSmall 6, small 10, medium 14, large 20, extraLarge 28. One corner decision
  per component family; no 9–16dp "soft" default drifting between screens.
- Elevation: flat 0, raised 1, floating 3, overlay 6. Surfaces separate by tone step,
  not shadow; shadow only on what truly floats (menus, dialogs, sheets).

## Motion

- Durations: instant 100ms, short 180ms, medium 280ms, long 420ms; skeleton loop 1150ms.
- Easing: Material 3 standard / emphasized curves in `HavenMotion`. Transform and opacity
  only; every animation keeps content visible under reduced motion.

## Iconography

5 MediaKind vectors (VIDEO/AUDIO/IMAGE/DOCUMENT/FILE) + 3 attestation marks.
Stroke 1.5dp, 20dp container. Icons never sit in tinted squares as decoration.
