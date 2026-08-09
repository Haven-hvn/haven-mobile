# Watch — per-kind viewer

Layout per kind, shared chrome: top bar (back + title + ⋮ 48dp) + content + bottom controls (72dp) auto-fade 150ms. Bottom nav hidden while viewing video; shown for doc/image.

- VIDEO (root/haven-mobile/mobile-app/feature-watch): Exo/Media3 16:9, PiP button, fullscreen 4dp handle. Error overlay Retry + code.
- AUDIO: 160dp artwork + scrubber + play/pause/next, lockscreen controls via Media3.
- IMAGE: pinch-zoom/pan, 1:1 or aspect-fit toggle.
- DOCUMENT (PDF): paginated, scrubber with page N/M, zoom.
- FILE (opaque): metadata card (name/size/CID) + Save to device (SAF) + Open with (intent chooser). One-time warning modal (FR-FILE-3) before first Save/Open — destructive style, checkbox “Don’t show again”.

Attestation badge in header (Verified/Unverified/Failed — three distinct). Cache chip in header.

States: Loading (spinner) / Empty (no media) / Error (HavenError code + Retry).

Motion: PiP 250ms, page turn 150ms.

