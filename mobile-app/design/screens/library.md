# Library — vertical-first (file-player influenced)

```
┌ TopBar (56dp) Haven | Search Q View-toggle          fixed
├─ CategoryGrid 2×2  (MediaKind: VIDEO/AUDIO/IMAGE/DOC + All)  16dp gutter, 1:1 cards
├─ FolderList (LazyColumn)  MediaItem rows 72dp, grouped by folder/file semantics
└─ BottomNav (80dp) Lib | Com | ▶Watch(center) | Set | Onb  fixed
```
- Grid: 2 columns, equal width, 12dp radius, count beneath label. Tapping filters LazyColumn (no nav). “All” selected by default.
- Rows: leading MediaKind icon 40dp, title + subtitle (short CID + size), trailing ⋮ (48dp) + cache chip. Tap → Watch with that item.
- Header: Search (TextField 48dp) + View-toggle stateful (grid↔list persists). Search filters both grid counts and rows.
- States: Empty (“No items — check aol”) / Loading (top LinearProgress + shimmer 4 cards + 6 rows) / Error (retry) / Populated. Pull-to-refresh on list.
- Motion: cache chip fade 150ms, grid→list crossfade 200ms.
- File-player nods adapted: fixed header/footer scrollable middle, 2×2 categories above folder list, 5-icon bottom nav with central Play (Watch), video-preferred list density — but mapped to MediaKind/MediaItem, not nPlayer badges/folders/stats.
