# Next Day Autonomous Plan — Haven Mobile v1

**Host:** 1.9Gi RAM, single-thread only (`-Xmx1024m parallel=false workers.max=1`)
**Branch:** `main` @ `c0b671b` (verified ED25519)
**Deps:** `../../foc-local-first-android/foc-cache` + `../../ic-kotlin` (includeBuild), `reown-kotlin-develop` stubbed

## Sequence (single-thread, no overlapping Gradle daemons)

### Today — Stabilize (M0)
1. `help --no-daemon --max-workers=1` must pass (in-flight session 25). If fails, fix `settings.gradle.kts`/`build-logic` first, no other changes.
2. Theme: `app/ui/theme/Theme.kt` (amber #C8932A, dark #0B0E14) wired in `MainActivity` — verify `assembleDebug` single-thread after help.
3. `core-wallet`: keep `AppKitStubs` until reown included; document FR-W-1..4 via `WalletSession` interface + DataStore per-wallet namespacing. No real AppKit until user provides projectId.
4. `core-haven-aol`: `HavenAolConfig(canisterId, icHost)` defaults from `local.properties` (empty = stub). `verificationKey()` cache + offline Ed25519 ready for `core-attestation`.
5. `core-cache`: `HavenCacheImpl` per-wallet `foc/<address>` + `PieceCidVerifier` NoOp until PieceCID (FR-CACHE-5). `DebugViewModel` fixture `baga6ea4seaqfixture` → `fetch/get/stream` E2E.

### Next — M1 (Library+Watch MVP, 3w)
- Arkiv `listMediaForOwner` + `discoverUserCommunities` pagination 20 → Room mirror `core-cache-mirror/MediaRepositoryImpl` write-through, per-wallet namespaced.
- Library UI already has grid/list + search + cache chip; bind to `LibraryViewModel` + `MediaRepository`.
- Watch `VIDEO` via Media3 ExoPlayer, `HavenCipher` chunked decrypt (done) + `HavenAol` v1 gate, Room TTL/quota eviction.
- Settings v1 + disconnect `core-security` purge (Room+FocCache+Keystore+WorkManager).

### Then — M2 (File kinds + v3 + PieceCID)
- Audio/Image/PDF viewers (Coil, PdfRenderer already in catalog) + `FILE` Save-to-device `ACTION_CREATE_DOCUMENT` + `ACTION_VIEW` + warning (FR-FILE-1..4) in `feature-watch/FileViewer`.
- v3 batch `decryptAll` single canister call per epoch.
- PieceCID verify port from synapse-sdk / Rust FFI, `haven.piececid.verify.enabled` flag.

### Then — M3 (Feed+polish)
- Community feed `core-attestation` badges, `feature-community` list.
- `FR-OBS-2` rolling 100-event log in Settings, Maestro E2E `connect→library→watch(video,PDF)→disconnect`.

## Rules for this host
- Never run two Gradle builds concurrently. `help` → `assembleDebug` → `:core-*:test` each `--no-daemon --max-workers=1` sequential.
- Before each build: `./gradlew --stop` then `free -h` to ensure >700Mi available.
- All commits via `user.signingkey=/root/.ssh/haven_signing_ed25519.pub` `gpg.format=ssh` → Verified. Push `origin/main` after each milestone slice.

## Check-in in a day — deliverables to show
- `help` + `assembleDebug` BUILD SUCCESSFUL logs single-thread 1G
- Theme + onboarding → library → watch → settings navigation (screenshots or Maestro)
- Unit coverage ≥80% on core-domain/crypto/haven-aol/attestation (where applicable)
