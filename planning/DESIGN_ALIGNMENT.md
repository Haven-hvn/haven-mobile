# Design Alignment — Gap Analysis (from mobile-app/design/*)

Reference: `MOBILE_V1_REQUIREMENTS.md` §5 + `design/tokens.md|components.md|screens/*.md`
Taste: no purple/indigo, no cream, no aurora/glass, no Inter-only, hierarchy via weight/size.

## Tokens → Theme (done)
- `app/ui/theme/Theme.kt` implements `tokens.md`: dark #0B0E14 / #141A26 / #1E2636, amber #C8932A (dark) / #8A6318 (light), outline #2A3447, Verified #2E7D32 etc. Light/dark schemes, typography 32/400 display, 22/600 title, mono 13/400 for 0x…/CIDs. No purple/cream/glass.

## Library (`design/screens/library.md`)
- Required: TopBar 56dp (Haven | Search + View-toggle 48dp), CategoryGrid 2×2 (VIDEO/AUDIO/IMAGE/DOC+All, 12dp radius, count), FolderList LazyColumn 72dp rows (40dp MediaKind icon, title 16/600, subtitle short CID+size, trailing ⋮ 48dp + cache chip), Pull-to-refresh, Empty/Loading (LinearProgress+shimmer 4+6)/Error/Populated, grid↔list crossfade 200ms.
- Current `LibraryScreen.kt`: PullToRefreshBox + TextField + Adaptive grid vs LazyColumn toggle, cache chip color but no CategoryGrid, no TopBar, no 2×2, no folder grouping, no shimmer. **Gap:** add `LibraryCategoryGrid` + `TopBar` + shimmer placeholders, persist view-toggle via DataStore.

## Watch (`design/screens/watch.md`)
- Required: top bar 56dp + content + bottom 72dp auto-fade 150ms, VIDEO Media3 16:9 PiP, AUDIO 160dp artwork+scrubber+lockscreen, IMAGE pinch-zoom, DOCUMENT paginated scrubber, FILE metadata+SAF Save+Open-with+warning modal (FR-FILE-3 destructive). Attestation badge + cache chip in header, states Loading/Error/Ready (decrypting/decryptError).
- Current `WatchScreen.kt`: chrome + `when(kind)` branches exist, Media3/Coil/pdf-renderer deps present, but FILE viewer is placeholder metadata card only — no `ACTION_CREATE_DOCUMENT` launcher, no warning dialog, no exportable gate. PiP handle 4dp not implemented.

## Community (`design/screens/community.md`)
- Required: AppBar 56dp (no search), LazyColumn grouped by day sticky header 12/500+divider, row 72dp identicon 40dp + MediaKind 16dp overlay, title/subtitle mono, trailing timestamp+attestation badge (3 distinct states). Empty/Loading/Error.
- Current `feature-community`: not inspected but likely similar gap to Library — need grouped feed + attestation badge mapping.

## Onboarding (`design/screens/onboarding.md`)
- Required: 320dp max width center, illustration 160dp → Display 32/400 → body 16/400 → primary CTA 48dp amber → secondary link, Terms checkbox, states Disconnected/Connecting (LinearProgress 2dp)/Connected (`0xABCD…1234` mono)/Error inline. **Gap:** `feature-onboarding` currently minimal, need to implement per spec, no purple.

## Settings (`design/screens/settings.md`)
- Required: LazyColumn 16dp, headers 14/500 + divider, Wallet (mono full + Copy + Disconnect destructive), Sliders Storage GiB 0–20 + TTL 1–30 (48dp thumb), Clear cached/expired destructive, Strict verify toggle, About, Recent errors expandable 5 mono `code:message`.
- Current `feature-settings`: exists but sliders not bound to `CacheConfig`/`DataStore`, no destructive dialogs.

## Components (`design/components.md`)
- Chips/Badges: Cache chip 32dp 8dp radius + icon, Attestation 3 states (filled check/outline minus/filled alert) never collapsed, MediaKind badge 28dp container. Controls: TopBar/BottomNav 80dp 5 slots Watch center 56dp FAB-flat amber active tint+2dp indicator, Category Card 1:1, Row 72dp, Sliders 48dp, Dialogs destructive red text. All 48dp min target.

## Next patches (single-thread, after `help` passes)
1. `LibraryCategoryGrid.kt` + shimmer + TopBar, wire to `LibraryViewModel` filter.
2. `FileViewer` SAF + warning modal + attestation gate.
3. `Theme` already done — verify `assembleDebug` no purple/cream regression.
4. Community grouped feed + badge.
