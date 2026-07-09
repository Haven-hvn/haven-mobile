# Sprint 1 — Library + Watch MVP

**Milestone parity:** `MOBILE_V1_REQUIREMENTS.md` §9 M1.
**Duration target:** 3 weeks.
**Goal:** A connected wallet can browse their library, open a video, decrypt it via Haven-AOL v1, and watch it. Settings expose cache quota/TTL and disconnect. Disconnect wipes per-wallet state cleanly. Video-only for kinds; audio/image/PDF/FILE land in Sprint 2. v3 batch decrypt also lands in Sprint 2.

## Task list

| # | Task | Role |
|---|------|------|
| 1.1 | `core-arkiv` — entity queries + community discovery | Domain Specialist — Arkiv |
| 1.2 | `core-crypto` — AES-GCM chunked/streaming decrypt + key caches | Domain Specialist — Crypto |
| 1.3 | `core-cache-mirror` — Room + DataStore + per-wallet write-through | Data / Schema Engineer |
| 1.4 | `core-haven-aol` — v1 gate decrypt (real impl) + nonce mgmt | Domain Specialist — ACL |
| 1.5 | `feature-library` — grid/list, search/filter | UX Engineer |
| 1.6 | `feature-settings` v1 — cache quota, TTL, clear, disconnect | UX Engineer |
| 1.7 | `feature-watch` — video viewer (Media3) + kind dispatcher shell | UX Engineer |
| 1.8 | `core-security` — disconnect cleanup orchestrator | Integration Engineer |
| 1.9 | Unit tests: `core-domain`, `core-crypto`, `core-haven-aol`, `core-cache-mirror` — hit 80% floor | Unit Test Engineer |
| 1.10 | Sprint 1 QA gate | Code Reviewer / QA Gate |

## Exit criteria (checked by Task 1.10)

- Connected user's library populates from Arkiv, sorted by recency, offline-usable after first fetch.
- Tapping a `VIDEO` item plays it via Media3 with no plaintext hitting disk.
- Settings shows quota/TTL sliders, clear-cache buttons, disconnect toggle.
- Disconnect purges the current wallet's Room DB, FocCache subdir, in-memory caches; other wallets' state (if any resurface later) is untouched.
- Kover verify passes with `haven.coverage.enforce=true` and 80% thresholds on the four gated modules.
- Open Q #3 (Arkiv binding) has a written decision on file.
