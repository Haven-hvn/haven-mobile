# Sprint 3 — Feed + polish + E2E

**Milestone parity:** `MOBILE_V1_REQUIREMENTS.md` §9 M3.
**Duration target:** 1 week.
**Goal:** Community feed with attestation badges lands; observability (recent errors + cache diagnostics) fills out Settings; Maestro E2E flows cover the four v1 acceptance paths; NFR budgets from §6 are measured and verified. Sprint 3 QA gate closes v1.

## Task list

| # | Task | Role |
|---|------|------|
| 3.1 | `core-attestation` — Ed25519 + Merkle offline verify | Domain Specialist — Crypto |
| 3.2 | `feature-community` — feed w/ verified badges | UX Engineer |
| 3.3 | `feature-settings` v2 — cache diagnostics + recent errors + redacted log export | UX Engineer |
| 3.4 | Performance measurement + NFR verification | Performance Engineer |
| 3.5 | Maestro E2E flows + CI wiring | E2E / QA Engineer |
| 3.6 | Documentation pass (README, docs/, migration notes for haven-cli parity) | Documentation Writer |
| 3.7 | Sprint 3 + v1 release QA gate | Code Reviewer / QA Gate |

## Exit criteria (checked by Task 3.7)

- Community feed renders items with verified/unverified badges from attestation checks.
- Settings shows recent errors (last 100), quota/used bytes, cache-hit-ratio, and an "Export log (redacted)" action.
- Maestro flows run in CI on every PR: connect → library → watch video → disconnect; connect → library → open PDF → disconnect.
- NFR §6 budgets are measured on a Pixel 6 profile and documented in `docs/perf.md`. Any regression from budget is a v1 blocker.
- Debug tab from Sprint 0 is removed from all build variants.
- Open Q #2 (feed pagination default) has a written decision on file.
