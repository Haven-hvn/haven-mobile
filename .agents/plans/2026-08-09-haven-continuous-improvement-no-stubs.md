# Plan: Continuous Improvement — No-Stubs Conformance to haven-dapp / haven-cli

**Date:** 2026-08-09
**Branch:** `main` @ `80000f8` (verified ED25519, `G3F2…`), `foc-cache` @ `11511e0`
**Goal source:** user asked “are we continuously improving the code base to meet all feature requirements making sure there are no stubs and the implementation is in conformance to the haven ecosystem i.e. haven-dapp and haven-cli (whos repos are on directory up)”

## Goal
Bring `haven-mobile` to **no-stub, 1:1 parity** with `haven-dapp-main` for `MOBILE_V1_REQUIREMENTS.md` v1, using the same domain concepts and the mobile-native `foc-local-first-android` + `ic-kotlin` stack. Every `MOBILE_V1_REQUIREMENTS.md` row and `haven-dapp` file not in the exclusions must have a real implementation, not a placeholder that keeps the build green.

## Success Criteria
- `MOBILE_V1_REQUIREMENTS.md` §3 table: every left-column web-dApp feature has a right-column mobile implementation with **no `stub`/`TODO`/`placeholder`/`Not yet wired` in `main`**. Verified by `grep -R "stub|TODO|placeholder|Not yet wired"` returning 0 in `mobile-app/src` (except comments that point to tests).
- `FR-W-1..4`, `FR-ACL-1..3`, `FR-CACHE-1..5`, `FR-UI-1..5`, `FR-SEC-1..2`, `FR-OBS-1..2`, `FR-FILE-1..4` all demonstrable in a single `assembleDebug` build + `test -x :foc-cache:test` + Maestro `connect-library-watch-disconnect.yaml` + `pdf-save-open.yaml` (both `BUILD SUCCESSFUL` and Maestro green).
- `foc-cache` verified via `haven-dapp` `src/lib/video-cache.ts` → `foc-local-first-android` hedged race + `PieceCID` verify path; `ic-kotlin` verified via `haven-dapp/haven-aol` VetKD flow, not SHA-256.
- `reown-kotlin-develop` inclusion no longer `// Reown is temporarily stubbed` — real `includeBuild("../../reown-kotlin-develop")` builds with `wallet.projectId` from `local.properties` per planning/NEXT_DAY_PLAN.md:12.
- Continuous build loop (`/tmp/continuous_build.log`, `768m`, `--offline --no-daemon`) stays green across next 3 cycles with no OOM kills.
- `git log --show-signature` shows ED25519 `G3F2…` on each new slice, pushed to `origin/main` for both `haven-mobile` and `foc-local-first-android`.

## Context And Current Facts
- **Current green build:** `6m19s` `BUILD SUCCESSFUL` `24M APK` at `17:27` with `Xmx768m` after fixing `BuildConfig` wiring (`app` → `core-wallet`/`core-haven-aol` own `BuildConfig`), `HavenAolImpl.getNonce` (was `nextNonce`), `CommunityViewModel` stray `}` (was `Unresolved filteredItems/setSearchQuery`), `core-attestation` test `datetime` dep, `foc-cache/LocalStore.kt:193,200` `Array<Any>` casts. Pushed at `80000f8` / `11511e0`. Continuous loop `PID 72695` `5m00s` `332 lines` at `mergeLibDex`.
- **Verified stubs still present** (`grep` capped at 50 matches, key hits):
  - `mobile-app/core-wallet/build.gradle.kts:31` `// Reown is temporarily stubbed — see com.reown.appkit.AppKitStubs` and `com/reown/appkit/AppKitStubs.kt:5` `connect(): Wallet(address=null, connectorName="stub")`, `signTypedDataV4 → "0x00"*65` (`planning/NEXT_DAY_PLAN.md:12` says keep stub until projectId provided).
  - `mobile-app/core-haven-aol/src/main/kotlin/.../HavenAolImpl.kt:29` `// Until agent wiring lands, return signed payload hash as placeholder … SHA-256` + `32: MessageDigest SHA-256`, `41: // TODO: IcAgent.query(..., "verificationKey") — cached via AesKeyCache`, `72: // stub currently returns in grouped order`.
  - `mobile-app/feature-watch/src/main/kotlin/.../WatchScreen.kt:230` `stub URI until HavenAol decrypt live`, `235: // Stub until canisterId/icHost configured: empty player`, `413: // Attempt PdfRenderer … ; else placeholder`, `463: // FR-FILE-4 … stub check until verifier wired`, `470: // For now: placeholder write (zero bytes)`.
  - `mobile-app/core-cache` `HavenCacheImpl` per `planning/NEXT_DAY_PLAN.md:14` `PieceCidVerifier NoOp until PieceCID (FR-CACHE-5)` — `haven-dapp` `src/lib/haven-aol/haven-aol-decrypt.ts` + `haven-aol-decrypt-v3.ts` is real VetKD, `src/lib/attestation.ts` is Ed25519+Merkle.
  - `LibraryScreen` / `WatchScreen` / `Community` design gaps per `planning/DESIGN_ALIGNMENT.md:11,15` (no `CategoryGrid` 2×2, no `TopBar` 56dp, FILE viewer placeholder, PiP 4dp handle missing, attestation badge 3 states, etc.) and `planning/mobile-v1-tasking/sprint-1…/1.7-feature-watch-video.md:60` placeholder for non-VIDEO kinds.
  - `maestro/*.yaml:2` `FR coverage 80% stubs`, `5` `stubbed wallet`.
- **Ecosystem repos discovered:** `/root` contains `haven-dapp` (Next.js + `src/lib/haven-aol` + `src/lib/attestation.ts` + `src/lib/cache` + `haven-aol/packages/typescript` + `haven-aol/src` Candid/Motoko), `foc-local-first-android` (now fixed), `ic-kotlin` (includeBuild), `reown-kotlin-develop` (stubbed). No `/root/haven-cli` checkout — `ls /root` shows `foc-…`, `haven-dapp`, `haven-mobile`, `ic-kotlin`, `reown-kotlin-develop` only. Assumption: `haven-cli` is the publishing/authoring flow that is **explicitly out of mobile v1** per `MOBILE_V1_REQUIREMENTS.md §2` (“Publishing / capture / upload — same as web dApp, authoring stays in `haven-cli`”), so mobile conformance is **read-only** to its Arkiv entities.
- **Domain parity:** `MOBILE_V1_REQUIREMENTS.md:42` `MediaKind`/`MediaItem` is a 1:1 port of `haven-dapp` `Video` plus `kind` discriminator — current `MediaRepositoryImpl.kt` already mirrors `video.ts` via `PieceRef` transitives.

## Constraints And Non-goals
- **Must keep:** `Xmx768m`, `parallel=false`, `workers.max=1`, `--offline --no-daemon` on `1.9Gi` host (OOM at `752M` before); `minSdk 26`, `targetSdk 36`, `Kotlin 2.3.21`, `JDK17` toolchain, `AGP 9.3.1`, version catalog centralization.
- **Deferred per §2:** chat, pay-for-access/DEX, treasury, discovery UI, multi-wallet, guest, push, remote wipe, publishing/upload, iOS. Do not add them even if `haven-dapp` has code behind flags.
- **Non-goal:** replacing `foc-cache` or `ic-kotlin` with raw `HttpAgent`; Decision 5 from `planning/mobile-v1-tasking/sprint-0…/0.5-core-haven-aol-smoke-ping.md:48` says all canister I/O via `ic-kotlin`.
- **Signing/push:** `gpg.format=ssh`, `user.signingkey=/root/.ssh/haven_signing_ed25519.pub`, `GIT_SSH_COMMAND="ssh -i /root/.ssh/haven_signing_ed25519"` — keep ED25519 `G3F2…` on every slice.

## Key Decisions
- **D1 — Reown wiring vs stub:** Recommended: un-stub `includeBuild("../../reown-kotlin-develop")` in `settings.gradle.kts` (currently commented) and replace `AppKitStubs` with real `AppKit(projectId, metadata)` using `local.properties: wallet.projectId` (already wired to `BuildConfig`). Alternative rejected: keep stub — violates `FR-W-1` and `maestro` “stubbed wallet” is Sprint 3 tech debt, not v1.
- **D2 — VetKD vs SHA-256:** Recommended: implement `HavenAolImpl.decrypt` via `ic-kotlin` `IcAgent.query/call(canisterId, "requestDecryptionKey", candidEncode)` + `HavenAolConfig(canisterId, icHost)` + `AesKeyCache` LRU, and `verificationKey()` via `IcAgent.query("verificationKey")` cached. SHA-256 placeholder kept only for `local.properties` empty (i.e., `canisterId.isBlank()` fast-path returns `HavenError.CanisterCallFailed`). Rejected: keep SHA-256 — breaks `FR-ACL-1` and diverges from `haven-dapp` `haven-aol-decrypt.ts`/`v3.ts`.
- **D3 — PieceCID verifier:** Recommended: replace `PieceCidVerifier NoOp` with real `foc-cache` `PieceCID` verify on `FocCache.get/stream/fetch` per `planning/mobile-v1-tasking/sprint-2…/2.6-core-cache-piececid-verify.md:7` (FR-CACHE-5, web dApp queued TODO). Rejected: leave NoOp — fails §6 coverage gate on `core-cache-mirror`.
- **D4 — Watch kind routing:** Recommended: finish `FILE` SAF `ACTION_CREATE_DOCUMENT` + warning dialog (FR-FILE-1..3), wire `decrypt→stream→decryptStream→tempFile→ExoPlayer/Coil/PdfRenderer`, enforce `FR-FILE-4` export gate, and replace PiP stub with `PlayerView` 4dp handle. Keep `haven-dapp` `src/components/player/VideoPlayer.tsx` parity (no plaintext on disk, FR-UI-5).
- **D5 — Library/Community design:** Recommended: implement `planning/DESIGN_ALIGNMENT.md` deltas (TopBar 56dp, CategoryGrid 2×2, shimmer, FolderList 72dp, view-toggle DataStore) as the next UI slice; verify via `Maestro` already expects `Search`/`All`/`Video`.
- **D6 — haven-cli conformance:** Recommended: treat as **no mobile code** — verify mobile reads Arkiv entities produced by `haven-cli` (authoring stays there) and document the boundary in `docs/ecosystem.md`; do not clone `haven-cli` into `mobile-app`.

## Recommended Approach
Invent nothing — follow `haven-dapp` as the spec and `MOBILE_V1_REQUIREMENTS.md` §3 mapping:

1. **Wire Reown** using `reown-kotlin-develop/sample/` as the Maestro pattern, matching `haven-dapp/src/components/auth/*` + `AuthProvider.tsx`.
2. **Wire IcAgent** using `haven-dapp/haven-aol/packages/typescript/src` + `haven-dapp/src/lib/haven-aol/haven-aol-decrypt*.ts` + `ic-kotlin` `IcAgent` as the transport; keep `local.properties` empty → error path, not crash.
3. **Wire PieceCID** using `foc-local-first-android/README.md` caveat + Task `2.6` spec; implement the seam already present in `core-cache`.
4. **Finish Watch FILE** using `haven-dapp/src/app/watch/page.tsx` + `FR-FILE-1..4` + `SAF ACTION_CREATE_DOCUMENT` already stubbed in `WatchScreen.kt:470`.
5. **Close design gaps** using `planning/DESIGN_ALIGNMENT.md` as the checklist.

Sequence respects DI and build order: `core-wallet` (Reown) → `core-haven-aol` (IcAgent) → `core-cache` (PieceCID) → `feature-watch` (FILE) → `feature-library/community` polish.

## Work Plan
**Unit 1 — `core-wallet` Reown real (`FR-W-1..4`) — owns `feature-onboarding` CTA**
- Files: `settings.gradle.kts` (uncomment `includeBuild("../../reown-kotlin-develop")`), `mobile-app/core-wallet/build.gradle.kts` (remove stub comment, add `implementation(project(":reown"))` or `api`), `core-wallet/src/main/kotlin/com/reown/appkit/AppKitStubs.kt` → delete after replacement, `WalletDataStore.kt` (already per-wallet namespaced, verify), `WalletSessionImpl.kt` (use real `AppKit` + `signTypedDataV4`), `HavenAolDiModule.kt`/`WalletDiModule.kt` already fixed for `BuildConfig`.
- Depends: `local.properties: wallet.projectId` (exists `02760a…`).
- Why first: all `haven-aol` decrypts depend on `WalletSession`.

**Unit 2 — `core-haven-aol` VetKD (`FR-ACL-1..3`, `FR-OBS`) — owns `HavenAolImpl` + `NonceManager` + `GateRequestBuilder`**
- Files: `core-haven-aol/src/main/kotlin/.../HavenAolImpl.kt` (replace SHA-256 with `IcAgent.query/call`, `candidEncode`, `AesKeyCache` LRU, `verificationKey()` cache, `decryptAll` epoch grouping fix per `HavenAolBatchGroupingTest.kt:18` comment — group by `epochId+gateReference` not full `V3` object), `NonceManager.kt` (`getNonce` already correct), `HavenAolDiModule.kt` (`provideNonceManager`, `provideHavenAolConfig` already fixed).
- Depends: Unit 1 (wallet), `ic-kotlin` `ic-agent`/`ic-candid` (already `includeBuild`).
- Cite: `haven-dapp/src/lib/haven-aol/haven-aol-decrypt.ts`, `haven-aol-decrypt-v3.ts`, `haven-aol/packages/typescript/src`.

**Unit 3 — `core-cache` PieceCID + HavenCache (`FR-CACHE-1..5`, `FR-SEC-1..2`)**
- Files: `core-cache/src/main/kotlin/.../HavenCacheImpl.kt` (`PieceCidVerifier` seam → real `verify`), `core-cache-mirror/MediaRepositoryImpl.kt` (already fixed `Instant` deprecated warnings, keep `foc-cache` hedged race), `core-cache-mirror/SettingsRepositoryImpl.kt`, `core-security/SecurityCleanupImpl.kt`.
- Depends: Unit 2 (key cache), `foc-local-first-android` (already `11511e0`).
- Cite: `haven-dapp/docs/video-cache/architecture.md:338` + `2.6-core-cache-piececid-verify.md`.

**Unit 4 — `feature-watch` FILE + MediaKind completeness (`FR-UI-2`, `FR-UI-5`, `FR-FILE-1..4`)**
- Files: `feature-watch/src/main/kotlin/.../WatchScreen.kt` (replace zero-byte SAF placeholder with `decrypt→openOutputStream` + warning `AlertDialog` `FR-FILE-3` + `Open with` `Intent.ACTION_VIEW` + `FR-FILE-4` gate, wire Media3 audio background + `PdfRenderer` + `Coil` pinch-zoom per `design/screens/watch.md`).
- Depends: Units 1-3.

**Unit 5 — `feature-library` / `feature-community` / `feature-settings` / `feature-onboarding` polish (`FR-UI-1`, `FR-UI-3`, `FR-UI-4`, `FR-SEC`)**
- Files: `feature-library/LibraryScreen.kt` (`TopBar` 56dp, `CategoryGrid` 2×2, `FolderList` 72dp, `LibraryShimmer` 4+6, `ViewToggle` DataStore), `feature-community/CommunityScreen.kt` (already `filteredItems` fix, verify `failedVerificationIds` badge `Verified #2E7D32/Unverified #616161/Failed #C62828`), `feature-settings/SettingsScreen.kt` (quota/TTL sliders, clear cache), `feature-onboarding/OnboardingScreen.kt`.
- Depends: Units 1-4.

**Unit 6 — CI / Maestro / coverage (`§6`, `0.7-ci-pipeline.md`)**
- Files: `.github/workflows/*.yml` ( `haven.coverage.enforce=true` + `koverVerify` 0.80 on `core-domain`, `core-haven-aol`, `core-crypto`, `core-cache-mirror`), `maestro/*.yaml` (replace `stubbed wallet` with `reown-kotlin-develop/sample` pattern, add `waitForAnimationToEnd`), `README`/`docs/ci.md`.
- Depends: Units 1-5.

## Validation Plan
- **Unit 1:** `export JAVA_HOME=17; ./gradlew :core-wallet:assembleDebug --offline --no-daemon --max-workers=1 --no-parallel` + `adb` manual connect via `local.properties` `wallet.projectId` → `WalletDataStore` persists `saveAddress`/`saveLastConnector` (inspect `datastore/preferences`); `grep -R AppKitStubs` → 0.
- **Unit 2:** `./gradlew :core-haven-aol:assembleDebug :core-haven-aol:testDebugUnitTest --offline --no-daemon --max-workers=1` (expect `HavenAolBatchGroupingTest` to pass after grouping by `epochId+gateReference`), `grep -R "SHA-256.*placeholder|Not yet wired"` → 0 in `HavenAolImpl.kt`, `Debug` tab ping `HavenAol.ping()` returns `PingResult`.
- **Unit 3:** `./gradlew :core-cache:assembleDebug :core-cache-mirror:assembleDebug --offline …` + `PieceCidVerifier` test that bad `pieceCid` → `evict + retry` (per Task `2.6`); `./gradlew koverVerify -Phaven.coverage.enforce=true` (threshold 0.80).
- **Unit 4:** `./gradlew :feature-watch:assembleDebug --offline …` + manual `Watch` for `VIDEO`/`AUDIO`/`IMAGE`/`DOCUMENT`/`FILE` (airplane-mode cached playback ≤400ms, SAF save writes non-zero bytes, `FR-FILE-4` blocks export on failed verification).
- **Unit 5:** `Maestro` `maestro/*.yaml` headless on emulator (requires `maestro` binary + stub removal), `Library` `TopBar` 56dp + `CategoryGrid` 2×2 assertion.
- **Unit 6:** `GHA` run `build + lint + unit + koverVerify` green, `haven.coverage.enforce=true` enforced, release signing reads `HAVEN_KEYSTORE_*` env (unsigned warning only).
- **Global:** `./gradlew :app:assembleDebug --offline --no-daemon --max-workers=1 --no-parallel` `BUILD SUCCESSFUL` `24-25M` APK (currently `768m` `6m19s` / `5m24s` UP-TO-DATE), `continuous` loop `/tmp/continuous_build.log` stays green, `git log --show-signature` `G3F2…` on each unit commit (push `GIT_SSH_COMMAND="ssh -i /root/.ssh/haven_signing_ed25519"`).

Highest-risk validation: **Unit 2 VetKD** — requires live `canisterId`/`icHost` from `local.properties` and `ic-kotlin` transport; mis-wired Candid encoding will fail `BUILD SUCCESSFUL` but crash at runtime `HavenError.CanisterCallFailed`.

## Risks / Rollback
- **Overscope:** Adding `haven-cli` publishing into `mobile-app` would violate `§2` and bloat APK — keep it read-only, document boundary.
- **Memory:** VetKD + Media3 + Coil together pushes `768m` heap near OOM (`752M` kill at `Xmx1024m` before) — keep `parallel=false`, `workers.max=1`, `--offline --no-daemon`, monitor `dmesg OOM`.
- **Rollback:** Each unit is independently revertible via `git revert <unit-commit>` (cross-repo: `haven-mobile` + `foc-local-first-android` separate). `continuous` loop can be stopped with `pkill -f continuous_build`.

## Open Questions
- **haven-cli location:** No `/root/haven-cli` checkout found — confirm whether `haven-cli` lives at `https://github.com/Haven-hvn/haven-cli` and should be cloned to `/root/haven-cli` for authoring conformance checks, or whether `MOBILE_V1_REQUIREMENTS.md §2` authoring exclusion makes a local clone unnecessary (Assumption: unnecessary for v1 read-only, document only).
- **Reown projectId gating:** Confirm whether `reown-kotlin-develop` can build `--offline` with `wallet.projectId` or needs a one-time online fetch for its catalog (if online, CI first run must be online).
- **Canister method names:** Confirm exact Candid method for `verificationKey()` / `requestDecryptionKey` from `haven-aol` `.did` (use `__get_candid_interface_tmp_hack` fallback per `0.5-core-haven-aol-smoke-ping.md:49` if ambiguous).

