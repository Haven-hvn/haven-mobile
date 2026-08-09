# Component Catalog

Reusable primitives — built once, used in Library/Community/Watch/Settings.

## Chips & Badges
- **ContentCacheStatus chip:** Cached/Fetching/Expired/Missing. Color per tokens.md, icon left (check/spinner/clock/dash). 32dp height, 8dp radius.
- **Attestation badge (3 states, distinct):** Verified (filled check, green #2E7D32) / Unverified (outline minus, gray) / FailedVerification (filled alert, red #C62828). Never collapse latter two.
- **MediaKind badge:** 20dp icon in 28dp container, per-kind shape. Used in Library grid + Community feed + Watch header.

## Controls
- **Top Bar:** 56dp fixed, title left (16/600), right Search + View-toggle (grid/list) 48dp. Full-width, no shadow.
- **Bottom Nav:** 80dp fixed, 5 slots Library|Community|Watch(center)|Settings|Onboarding. Watch 56dp FAB-like but flat amber, 48dp others. Active = amber tint + 2dp indicator.
- **Category Card (Library):** 1:1, 12dp radius, icon 24dp top, label 14/500, count 12/400 below. Equal width in 2×2 grid.
- **Folder/Media Row:** 72dp, leading 40dp MediaKind icon, title 16/600, subtitle 12/400 (piece_cid short + size), trailing 48dp ⋮ menu + cache chip.
- **Sliders:** Material 3 Slider, 48dp thumb, value label above. Quota (GiB) + TTL (days).
- **Transport:** Media3 controls, PiP handle 4dp, lockscreen integration (audio).
- **Dialogs:** Standard + Destructive (disconnect/clear). Destructive red text, not red fill.

## States
Empty (illustration + CTA) ≠ Loading (linear at top, shimmer cards) ≠ Error (retry + code). Every screen enumerates all three.

## Viewer Chrome (Watch)
Top bar 56dp (back + title + ⋮), bottom controls 72dp, auto-fade 150ms. Image: pinch-zoom. PDF: paginated + scrubber. FILE: Save-to-device (SAF) + Open-with + one-time warning modal (FR-FILE-3).

All components 48dp min target, 8/16dp padding, no hover:scale, no left-border accent cards.
