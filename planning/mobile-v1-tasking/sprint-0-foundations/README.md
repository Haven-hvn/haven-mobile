# Sprint 0 — Foundations

**Milestone parity:** `MOBILE_V1_REQUIREMENTS.md` §9 M0.
**Duration target:** 1 week (design and engineering in parallel).
**Goal:** Ship an Android app skeleton that (a) builds green in CI, (b) connects & disconnects a wallet via Reown AppKit, (c) can smoke-ping the Haven-AOL canister via `ic-kotlin`, (d) round-trips one fixture `PieceRef` through `foc-cache`, **and (e) has a locked design system + wireframes for all five v1 screens ready for Sprint 1 UX to consume**. No product UI beyond onboarding + a debug screen exposing the smoke ping.

## Task list

| # | Task | Role |
|---|------|------|
| 0.0 | Design system + v1 wireframes | Product / UI Designer |
| 0.1 | Gradle skeleton + composite builds + version catalog | Scaffolding Engineer |
| 0.2 | Hilt DI bootstrap + `HavenApplication` | Scaffolding Engineer |
| 0.3 | `core-domain` type surface | API Designer |
| 0.4 | `core-wallet` — Reown AppKit connect/disconnect + signer | Domain Specialist — Wallet |
| 0.5 | `core-haven-aol` smoke-ping over `ic-kotlin` | Domain Specialist — ACL |
| 0.6 | `core-cache` — `HavenCache` facade over `foc-cache` w/ fixture PieceRef round-trip | Integration Engineer |
| 0.7 | CI pipeline: build + lint + unit + coverage gate stub | DevOps / Platform Engineer |
| 0.8 | Onboarding screen + Debug tab (smoke ping trigger) | UX Engineer |
| 0.9 | Sprint 0 QA gate | Code Reviewer / QA Gate |

## Parallelism note

Task 0.0 (Design) runs in parallel with all engineering tasks. It has no dependency on 0.1–0.8. It **must land before Sprint 1** because Tasks 1.5 / 1.6 / 1.7 (Library, Settings, Watch UX) consume its outputs. Task 0.8 (Onboarding + Debug tab) also consumes 0.0's onboarding-screen wireframe if available — but since Onboarding is deliberately spartan in Sprint 0 (one paragraph + one CTA), Task 0.8 can proceed with Material-3 defaults if 0.0 hasn't finalized Onboarding yet, and re-skin during Sprint 1 polish.

## Exit criteria (checked by Task 0.9)

- All 15 modules from the project-level layout exist and compile.
- `./gradlew build` green from a clean checkout.
- Connecting a wallet in the sample APK sets `WalletSession.address` non-null; disconnect nulls it.
- Debug tab's "Ping canister" succeeds against a testnet Haven-AOL canister ID.
- Debug tab's "Fetch fixture" round-trips a `PieceRef` through `HavenCache.get` and displays byte length.
- CI runs on push and blocks merge on failure.
- **All design artifacts from Task 0.0 exist under `mobile-app/design/`; project PM has signed off; Sprint 1 UX Engineers confirm they can implement without follow-up design questions.**
