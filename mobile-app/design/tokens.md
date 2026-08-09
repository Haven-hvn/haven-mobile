# Design Tokens — Haven v1 (Material 3)

Concrete values. Dark = default.

## Color (WCAG AA checked)
- Background: dark #0B0E14 / light #F6F7F9
- Surface: dark #141A26 / light #FFFFFF; Surface-variant dark #1E2636
- Primary (amber): #C8932A (dark) / #8A6318 (light) — buttons, active nav, focus ring. Contrast 4.8:1 on surface.
- Verified: #2E7D32 / Unverified: #616161 / Failed: #C62828
- ContentCache chip: Cached #1B5E20, Fetching #1565C0, Expired #6D4C41, Missing #424242
- Text primary dark #E6E8EC / light #0B0E14; secondary 70% opacity (not gray-400)
- Outline: dark #2A3447

## Typography (sp, weights)
- Display: 32/400 (onboarding headline only)
- Title: 22/600, 16/600 (card titles)
- Body: 16/400, 14/400 (metadata)
- Label: 14/500 (chips), 12/400 (timestamps)
- Mono: 13/400 for `0x…` / CIDs / piece_ref

## Spacing / Shape / Elevation
- Scale: 4,8,12,16,24,32 dp. Touch target min 48dp (a11y).
- Radius: cards 12dp, chips 8dp, sheets 16dp top — not single radius.
- Elevation: card 1dp, app bar 0dp (flat), bottom nav 2dp.

## Motion
Transitions ≤250ms, viewer chrome fade 150ms, respect `prefers-reduced-motion`.

## Iconography
5 MediaKind vectors (VIDEO/AUDIO/IMAGE/DOCUMENT/FILE) + 3 attestation. Stroke 1.5dp, 20dp container.
