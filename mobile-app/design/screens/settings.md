# Settings

Wireframe: single LazyColumn, 16dp padding, sections with 14/500 headers + 1dp dividers.

- Wallet: address mono full, Copy + Disconnect (destructive dialog: “Disconnect wallet?” Cancel/Disconnect).
- Cache quotas: two Sliders (Storage GiB 0–20, TTL days 1–30) + “Clear cached files” (destructive confirm, shows freed size) + “Clear expired” secondary.
- Attestation: toggle “Strict verify” (warns on Failed).
- About: app version, build, link to haven-dapp-main parity note.
- Recent errors (Sprint 3): expandable panel under quotas, last 5 HavenError `code:message` mono 12/400, Clear.

Empty/Loading/Error states: Loading covers sliders with LinearProgress; Error inline under affected section with Retry.

Bottom nav same 5-icon; top bar title “Settings”.

