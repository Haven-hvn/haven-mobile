# Sprint 2 — File kinds + v3 + PieceCID verify

**Milestone parity:** `MOBILE_V1_REQUIREMENTS.md` §9 M2.
**Duration target:** 2 weeks.
**Goal:** Watch screen now handles all five `MediaKind`s; SAF-based Save-to-device / Open-with lands for `FILE`; Haven-AOL v3 batch unlock replaces per-item unwrap; PieceCID verification is wired into the hedged retrieval path.

## Task list

| # | Task | Role |
|---|------|------|
| 2.1 | Audio viewer (Media3) | UX Engineer |
| 2.2 | Image viewer (Coil) | UX Engineer |
| 2.3 | PDF viewer (PdfRenderer) | UX Engineer |
| 2.4 | `core-haven-aol` — v3 batch decrypt | Domain Specialist — ACL |
| 2.5 | `feature-watch` — SAF export + Open-with for `FILE` | UX Engineer |
| 2.6 | `core-cache` — PieceCID verifier (real impl) | Domain Specialist — Crypto |
| 2.7 | Unit tests: keep 80% floor with new code | Unit Test Engineer |
| 2.8 | Sprint 2 QA gate | Code Reviewer / QA Gate |

## Exit criteria (checked by Task 2.8)

- All five `MediaKind`s route to a working inline viewer (or SAF export for `FILE`).
- Playing multiple items from the same gate epoch results in one canister call, not N.
- PieceCID verification runs on every returned piece; a fault-injected corrupt piece causes eviction + retry via a different provider from the hedged pool.
- SAF export shows the one-time warning on first use; export-failure conditions from FR-FILE-4 are enforced.
- Kover verify still passes with 80% thresholds.
- Open Q #1 (PieceCID implementation path) has a written decision on file.
