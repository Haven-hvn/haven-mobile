# Haven Mobile v1 — Tasking Plan

**Source of truth:** `MOBILE_V1_REQUIREMENTS.md` (locked scope).
**Output location:** this folder — sprints are subfolders, tasks are markdown files inside them.

This document is the **project-level brief** every task file inherits from. Specialists working an individual task file do **not** need to read other task files; they must, however, respect the contracts and design decisions declared here.

---

## 1. Project context

Haven Mobile v1 is an Android app that ports the core of `haven-dapp-main` (Next.js web dApp for wallet-gated encrypted media) onto Android. Delivery vehicle: a single Android app module + supporting Gradle library modules, sitting alongside three vendored/in-tree Kotlin projects (`ic-kotlin`, `foc-local-first-android`, `reown-kotlin-develop`). Target: Pixel 6 / `minSdk=26`, `targetSdk=34`.

The app must reach parity with the web dApp on: wallet connect, EIP-712 gate signing, Haven-AOL v1 + v3 decrypt, offline-first content cache, library / watch / community feed / settings screens, and security cleanup on disconnect. The one intentional expansion beyond the web dApp is a broader **file-kind viewer table** (video, audio, image, PDF, plus SAF-based "Save & Open-with" for arbitrary files).

**Non-goals (post-v1, do not plan for):** chat, pay-for-access, treasury, discovery UI, multi-wallet, guest mode, push, remote wipe, publishing, iOS.

---

## 2. Team composition — specialist roles

Every task file below is assigned to exactly one of the following roles. Task briefs must provide the "Inputs they need" column for their assigned role.

| Role | Scope of authority | Inputs a brief must provide | Deliverables |
|------|--------------------|-----------------------------|--------------|
| **Scaffolding Engineer** | Gradle module layout, build configs, DI graph shape, base theme, module boundaries. Owns *skeleton* only — no domain logic. | Module list, package roots, min/target SDK, DI framework choice, Kotlin/AGP versions, dependency composite build wiring. | `settings.gradle.kts`, `build.gradle.kts` files, empty modules with correct namespaces, DI wiring, `Application` class. |
| **API Designer** | Public types, enum shapes, interface signatures, cross-module contracts. No implementation bodies. | Domain-model spec, exact type names, invariants, nullability rules. | `data class` / `enum` / `interface` / `sealed` files under `core-domain`. |
| **Domain Specialist — Wallet** | Reown AppKit integration, connector selection, session persistence, EIP-712 signer usage. | AppKit version, list of connectors, EIP-712 payload spec, persistence rules. | `core-wallet` module: connect/disconnect, address stream, signer surface. |
| **Domain Specialist — ACL / Haven-AOL** | Haven-AOL canister interaction, v1/v3 dispatch, nonce mgmt, AES-key + gate-key + verification-key caches. | Canister interface (Candid), v1 vs v3 metadata shapes, nonce rules, cache lifetimes. | `core-haven-aol` module + `core-crypto` key caches. |
| **Domain Specialist — Crypto** | AES-GCM encrypt/decrypt (chunked, streaming, in-memory only), Ed25519 verify, Merkle proof verify, PieceCID (CommP) verification. | Cipher params, chunk size, framing, plaintext-never-on-disk invariant, PieceCID algorithm reference. | `core-crypto` cipher + `core-attestation` verifiers + PieceCID verifier in `core-cache`. |
| **Domain Specialist — Arkiv** | Arkiv entity queries, payload fetch, community discovery, pagination. | Arkiv binding decision (see §5 Open Q), query shapes, pagination defaults. | `core-arkiv` module. |
| **Data / Schema Engineer** | Room schema, DAOs, migrations, DataStore preference keys, per-wallet namespacing strategy, write-through orchestration to Arkiv. | Table names, column list, indexes, TTL/eviction policy, per-wallet key derivation rule. | `core-cache-mirror` module. |
| **Integration Engineer** | Wiring `foc-cache` into the domain layer, security-cleanup fanout, cross-cutting glue that isn't clearly one specialist's turf. | Module boundaries to bridge, resource lifecycles, cancellation semantics. | `core-cache` FocCache facade, `core-security` cleanup orchestrator. |
| **Product / UI Designer** | Wireframes, design-system tokens (color, typography, spacing, elevation, motion), component specs, empty/error/loading states, accessibility contract, per-screen redlines. Owns *what it looks like* and *how state maps to visuals* — not implementation. | FR list per screen, brand direction (or explicit "no brand — Material-3 defaults"), platform target (Android + Material 3), accessibility target level. | Design-system spec (Figma or Markdown+Mermaid), per-screen wireframes for the five surfaces, component catalog (badges, chips, viewer chrome, error panels), motion/transition spec, a11y checklist. |
| **UX Engineer** | Compose implementation of the designer's specs: screens & ViewModels for library / watch / community / settings / onboarding. Chooses composable structure, state hoisting, navigation wiring — within the design-system constraints. | Designer's wireframes + design-system spec + FR references, state inputs, actions out, viewer type dispatch. | `feature-*` modules + navigation entries. |
| **Unit Test Engineer** | Isolated tests for domain, crypto, cache-mirror, haven-aol modules. Chooses mocking strategy. | List of behaviors to cover, fixture requirements, coverage target. | `*Test.kt` under module `src/test/`. |
| **E2E / QA Engineer** | Maestro flows, device-lab config. | Flow scripts (steps + expected states), device targets, fixtures. | `maestro/*.yaml`, seed data helpers, CI wiring. |
| **Performance Engineer** | Cold-start, first-frame, quota, memory, cellular hedge-race verification. | NFR budgets from §6, measurement methodology, harness location. | Benchmark harness + written measurement report. |
| **DevOps / Platform Engineer** | CI (build, lint, unit tests, coverage gate, Maestro job), release signing config skeleton, dependency locking. | Required workflows, coverage gate rule, secrets that must be provisioned. | `.github/workflows/*.yml`, gradle version catalog wiring, signing config placeholder. |
| **Code Reviewer / QA Gate** | Final sprint-close review pass; verifies acceptance criteria, contracts, no leakage of post-v1 scope. | Sprint scope, contract list, gates from other tasks. | Signed-off checklist. |

---

## 3. Key deliverables (module surfaces)

| Module | Purpose | Owning role (primary) |
|--------|---------|-----------------------|
| `app` | Android application, nav host, entry point | Scaffolding Engineer + UX Engineer |
| `core-domain` | `MediaKind`, `MediaItem`, `Community`, `TokenGate`, `Attestation`, error taxonomy sealed classes | API Designer |
| `core-wallet` | Reown AppKit wrapper: `WalletSession`, connect/disconnect, EIP-712 signer | Domain Specialist — Wallet |
| `core-haven-aol` | Haven-AOL v1 + v3 dispatch via `ic-kotlin` | Domain Specialist — ACL |
| `core-arkiv` | Arkiv queries: media entities, community discovery | Domain Specialist — Arkiv |
| `core-crypto` | AES-GCM (chunked/streaming), AES key LRU, gate-key LRU, secure random | Domain Specialist — Crypto |
| `core-attestation` | Ed25519 verify + Merkle proof + verification-key cache | Domain Specialist — Crypto |
| `core-cache` | `foc-local-first-android/foc-cache` facade + PieceCID verification hook | Integration Engineer |
| `core-cache-mirror` | Room + DataStore, per-wallet namespaced metadata | Data/Schema Engineer |
| `core-security` | Disconnect cleanup orchestrator | Integration Engineer |
| `feature-library` | Grid/list of `MediaItem` | UX Engineer |
| `feature-watch` | Kind-dispatching viewer host (Media3, Coil, PdfRenderer, SAF export for FILE) | UX Engineer |
| `feature-community` | Community feed w/ attestation badges | UX Engineer |
| `feature-settings` | Cache quota/TTL sliders, clear cache, disconnect, recent-errors, cache diagnostics | UX Engineer |
| `feature-onboarding` | Landing / connect-wallet CTA | UX Engineer |

**Explicitly NOT present in v1:** `feature-chat`, `feature-treasury`, `feature-discover`, `feature-publish`, `core-access`, `core-chat-transport`, `core-handoff`. Any task brief that references these is a bug — reject during review.

---

## 4. Interface contracts (coordination artifacts — exact)

These names, shapes, and paths are **shared language** across multiple tasks. Any change to them must be a coordinated project-level decision, not a specialist call.

### 4.1 Repo / directory layout

```
mobile-app/
├── app/                       # Android app module
├── core-domain/
├── core-wallet/
├── core-haven-aol/
├── core-arkiv/
├── core-crypto/
├── core-attestation/
├── core-cache/
├── core-cache-mirror/
├── core-security/
├── feature-onboarding/
├── feature-library/
├── feature-watch/
├── feature-community/
├── feature-settings/
├── maestro/                   # E2E flows
├── build-logic/               # Gradle convention plugins
├── settings.gradle.kts        # composite-includes foc-local-first-android, ic-kotlin, reown-kotlin-develop
└── gradle/libs.versions.toml
```

### 4.2 Kotlin package roots

- App: `haven.mobile.app`
- Core modules: `haven.mobile.core.<name>` — e.g. `haven.mobile.core.domain`, `haven.mobile.core.haven.aol`, `haven.mobile.core.cache.mirror`
- Feature modules: `haven.mobile.feature.<name>` — e.g. `haven.mobile.feature.library`

### 4.3 Core-domain exact type names

The following symbols MUST exist with these exact names in `core-domain`:

- `haven.mobile.core.domain.MediaKind` (`enum class`) — variants `VIDEO`, `AUDIO`, `IMAGE`, `DOCUMENT`, `FILE`.
- `haven.mobile.core.domain.MediaItem` (`data class`) — fields exactly as listed in `MOBILE_V1_REQUIREMENTS.md` §4.
- `haven.mobile.core.domain.TokenGate` (`data class`).
- `haven.mobile.core.domain.TokenStandard` (`enum class`) — at least `ERC20`, `ERC721`, `ERC1155`.
- `haven.mobile.core.domain.Community` (`data class`).
- `haven.mobile.core.domain.ArkivStatus` (`enum class`) — must cover the same states the web dApp uses (`FRESH`, `EXPIRED`, `NOT_FOUND` at minimum).
- `haven.mobile.core.domain.ContentCacheStatus` (`enum class`) — must cover `UNCACHED`, `PARTIAL`, `CACHED`, `EXPIRED`.
- `haven.mobile.core.domain.GateMetadata` (`sealed interface`) with `V1` and `V3` variants (matches `haven-aol-metadata.ts` split).
- `haven.mobile.core.domain.Attestation` (`data class`).
- Error taxonomy: `haven.mobile.core.domain.error.HavenError` (`sealed class`) with subclasses that map 1:1 to the stable codes in `lib/cache-errors.ts` and `lib/playback-errors.ts`.

Import path from `PieceRef`: reuse `cloud.filecoin.foc.cache.PieceRef` from `foc-local-first-android` — do NOT duplicate this type.

### 4.4 Wallet contract

- `haven.mobile.core.wallet.WalletSession` (`interface`) exposes:
  - `val address: StateFlow<String?>` — null when disconnected.
  - `suspend fun connect(): Result<String>`
  - `suspend fun disconnect()`
  - `suspend fun signTypedDataV4(json: String): Result<String>` — hex-encoded 65-byte signature.
- Persisted keys (DataStore, in `core-wallet`): `wallet.address`, `wallet.last_connector`. Nothing else.

### 4.5 Haven-AOL contract

- `haven.mobile.core.haven.aol.HavenAol` (`interface`) exposes:
  - `suspend fun decrypt(item: MediaItem, session: WalletSession): Result<ByteArray>` — returns unwrapped AES key material (NOT plaintext content).
  - `suspend fun verificationKey(): Result<ByteArray>` — cached per session.
- Dispatch v1 vs v3 is an internal detail; callers stay unaware.

### 4.6 FocCache contract

- Consumed as-is from `foc-local-first-android`. Facade wrapper name: `haven.mobile.core.cache.HavenCache` — thin adapter that (a) injects PieceCID verification, (b) resolves `Config` from user-adjustable `SettingsRepository`, (c) namespaces `cacheDir` per wallet (`<cacheDir>/foc/<walletAddress>/`).

### 4.7 Cache mirror contract

- Room database name: `haven-mirror-<walletAddress>.db` (per-wallet file — namespacing invariant).
- DataStore file name for user prefs: `haven-settings.preferences_pb` (global — settings are per-install, not per-wallet).
- DataStore keys (exact strings): `cache.quota_bytes`, `cache.ttl_days`, `cache.clear_on_disconnect`.
- Room DAO must expose Flow-based observation on the media table so ViewModels can react to write-through updates.

### 4.8 Navigation route ids (feature modules must agree)

- `onboarding` → `feature-onboarding`
- `library` → `feature-library`
- `watch/{mediaId}` → `feature-watch`
- `community` → `feature-community`
- `settings` → `feature-settings`

Route strings above are the contract; any deviation breaks deep-linking and Maestro flows.

### 4.9 DataStore & Room per-wallet reset

The single rule: on `disconnect`, every module that persists state per wallet MUST expose a `suspend fun clearFor(walletAddress: String)` and `core-security` calls each one in a documented order.

---

## 5. Key design decisions (non-negotiable across all tasks)

1. **Local-first via `foc-cache`.** All content bytes flow through `FocCache.get / stream / fetch`. No parallel HTTP fetch path in the app. — Rationale: matches web dApp's cache model; single verified retrieval path.
2. **Plaintext never on disk.** Decrypted media bytes only exist in memory (parity with `lib/chunked-decrypt.ts`). The **only** exception is `MediaKind.FILE` + user-initiated SAF export (FR-FILE-1..4), which is gated on the one-time warning. — Rationale: FR-UI-5, FR-SEC.
3. **Per-wallet namespacing.** Room DB file, FocCache subdir, WorkManager tags, and any keystore aliases must include the connected wallet address (or a stable hash of it). Disconnect wipes only the current wallet's slice. — Rationale: FR-SEC-1.
4. **Reown AppKit is the only wallet path.** No custom WalletConnect wiring. No raw eth-json-rpc. — Rationale: FR-W-1 + maintenance surface.
5. **`ic-kotlin` is the only canister transport.** No parallel HTTP-agent-like implementation. — Rationale: shared vetKD/certification trust chain lives there.
6. **Composite build for vendored libs.** `foc-local-first-android`, `ic-kotlin`, and `reown-kotlin-develop` are consumed via Gradle composite builds (`includeBuild`), not via published artifacts. — Rationale: they are co-developed sibling projects in this repo.
7. **v3 batch unlock uses ONE canister call per gate epoch.** No fan-out of per-item calls when v3 metadata is available. — Rationale: parity with `haven-aol-batch-decrypt-v3.ts`; latency budget.
8. **PieceCID verification is mandatory in v1** for every fetched piece. Failure → evict + retry via a different provider from the hedged pool. — Rationale: FR-CACHE-5 explicitly promotes this from web dApp's queued TODO.
9. **Offline-first UX.** Library, cached content, and settings must be fully usable in airplane mode. Any code path that assumes network is a bug. — Rationale: NFR §6.
10. **No third-party crash reporter, no analytics SDK, no push SDK in v1.** — Rationale: §2 non-goals + FR-OBS scope.
11. **Coverage floor.** `core-domain`, `core-haven-aol`, `core-crypto`, `core-cache-mirror` must hit ≥80% line coverage. CI enforces. — Rationale: NFR §6.
12. **AppKit's signer performs all EIP-712 signing.** No local key material for signing lives outside AppKit. — Rationale: FR-W-3, security model.
13. **DI framework: Hilt.** Consistent across all modules. Scaffolding Engineer wires it once; every module picks it up. — Rationale: reduces per-module bikeshedding; Android-native.
14. **Kotlin 2.x, JDK 17 toolchain, AGP current-stable.** Version catalog centralizes all versions. — Rationale: matches sibling projects.
15. **No `application/octet-stream`-as-primary-mime hack** — SAF export prefers the item's declared `mimeType` and only falls back to `application/octet-stream` when `mimeType` is null. — Rationale: OS chooser accuracy (FR-FILE-2).

---

## 6. Sprint sequencing

Sprints map to the milestones in `MOBILE_V1_REQUIREMENTS.md` §9 but re-shape tasks along role boundaries.

1. **Sprint 0 — Foundations** (Scaffolding Engineer, DevOps, API Designer, Wallet, ACL, Integration, Product/UI Designer) — Gradle skeleton, DI, core-domain types, AppKit connect/disconnect, `ic-kotlin` smoke ping, `foc-cache` wired end-to-end against a fixture PieceRef, CI green, **design system + all five screen wireframes locked before Sprint 1 UX starts**.
2. **Sprint 1 — Library + Watch MVP** (Arkiv, Data/Schema, Crypto, ACL, UX, Integration, Unit Test) — Arkiv query, Room mirror, v1 decrypt path, AES-GCM chunked decrypt, Library grid, Watch/Video, Settings v1, disconnect cleanup, unit tests.
3. **Sprint 2 — File kinds + v3 + PieceCID** (UX, ACL, Crypto, Unit Test) — Audio/Image/PDF viewers, SAF export for `FILE`, v3 batch decrypt, PieceCID verification wiring, tests.
4. **Sprint 3 — Feed + polish + E2E** (Crypto, UX, Observability, E2E, Performance, Reviewer) — Attestation verify, Community feed with badges, cache diagnostics + recent errors, Maestro E2E, NFR verification, final QA gate.

---

## 7. Reference material

- `MOBILE_V1_REQUIREMENTS.md` — locked scope.
- `haven-dapp-main/src/lib/haven-aol/*` — TS reference for AOL v1/v3, nonce, batch, dispatch, gate/verification-key caches.
- `haven-dapp-main/src/lib/cache/*` + `haven-dapp-main/src/services/cacheService.ts` + `haven-dapp-main/src/lib/cache-integrity.ts` + `cache-errors.ts` — cache mirror behavior + error taxonomy.
- `haven-dapp-main/src/lib/attestation.ts` — Ed25519 + Merkle offline verify semantics.
- `haven-dapp-main/src/lib/chunked-decrypt.ts` — in-memory-only chunked AES-GCM decrypt reference.
- `haven-dapp-main/src/lib/security-cleanup.ts` — disconnect sequence.
- `haven-dapp-main/src/lib/community-feed.ts` — `discoverUserCommunities` shape.
- `haven-dapp-main/src/app/{library,watch,community,settings}/page.tsx` — UX parity source.
- `foc-local-first-android/README.md` — cache API, hedged retrieval, PieceRef.
- `ic-kotlin/README.md` — canister transport + vetKD decrypt example.
- `reown-kotlin-develop/product/appkit/*` and `reown-kotlin-develop/sample/dapp/*` — AppKit integration reference.

---

## 8. Open questions (unblock before affected sprint)

Copied verbatim from `MOBILE_V1_REQUIREMENTS.md` §10 for visibility. Decisions must be recorded here before the affected sprint starts.

1. **PieceCID verification implementation** — decide before Sprint 2 (Task 2.6).
2. **Community-feed pagination default** — decide before Sprint 3 (Task 3.2). Cosmetic.
3. **Arkiv Kotlin binding** — decide before Sprint 1 (Task 1.1). Blocks the Arkiv specialist.
