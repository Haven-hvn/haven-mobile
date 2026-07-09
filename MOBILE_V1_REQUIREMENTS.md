# Haven Mobile — v1 Requirements

**Status:** v1 spec — locked scope for the first Android release.
**Relationship to `FUNCTIONAL_REQUIREMENTS.md`:** that document is the *forward-looking superset* (all media kinds, chat, pay-for-access, treasury, etc.). **This document is the v1 subset** — a straight, minimally-generalized port of `haven-dapp-main`'s core to Android. Everything not in this doc is post-v1.

---

## 1. Goal

Ship an Android app that gives a wallet holder the same core experience as `haven-dapp-main`:

1. Connect a wallet.
2. See a library of gated content they can access.
3. Open an item → decrypt via Haven-AOL → play it back offline-first.
4. Manage the local cache.
5. Disconnect and wipe secrets.

Two intentional generalizations vs the web dApp:

- **Content is a "file", not just a "video".** Same encryption + cache pipeline, just a broader viewer set (video, audio, image, PDF, plus generic "download & open with…" for anything else). No new domain concepts.
- **Storage layer is the shared `foc-local-first-android` cache.** Replaces the web dApp's `synapse` + service-worker + IndexedDB stack with the mobile-native, hedged multi-provider FOC cache.

Everything else is a 1:1 port.

---

## 2. Explicit non-goals for v1

Deferred to post-v1 (see `FUNCTIONAL_REQUIREMENTS.md`):

- Chat / realtime.
- Pay-for-access / Get-access deep-links / DEX-swap flows.
- Treasury dashboard.
- Discovery UI (paste-address, QR, NFC, featured carousel).
- Multi-wallet profiles.
- Guest / preview mode.
- Push notifications & non-custodial push relay.
- Remote wipe.
- Publishing / capture / upload — same as web dApp, authoring stays in `haven-cli`.
- iOS.

If it isn't in the web dApp today, it isn't in mobile v1 either. The one exception is the file-kind viewer table (§5) — required because we're on mobile hardware with different decoders — and even that just enlarges *which files render inline*; it does not add a new authoring flow.

---

## 3. Feature parity with `haven-dapp-main`

Every row is a v1 requirement.

| Web dApp feature (file / route)                                        | Mobile v1                                                                 |
|------------------------------------------------------------------------|---------------------------------------------------------------------------|
| Wallet connect + disconnect (`components/auth/*`, `AuthProvider.tsx`)  | Reown AppKit for Android, WalletConnect v2.                               |
| EIP-712 gate signing (`lib/haven-aol/haven-aol-auth.ts`)               | Same signing done through AppKit's signer.                                |
| Haven-AOL decrypt v1 (`lib/haven-aol/haven-aol-decrypt.ts`)            | `ic-kotlin` call to canister; AES-GCM unwrap on-device.                   |
| Haven-AOL decrypt v3 batch (`haven-aol-decrypt-v3.ts`)                 | Same v3 dispatch — one canister call unlocks a gate's epoch.              |
| AES key + gate-key caches (in-memory)                                  | Same, backed by a plain Kotlin LRU.                                       |
| Attestation verify offline (`lib/attestation.ts`)                     | Ed25519 verify + Merkle proof in `core-attestation`.                      |
| Community discovery (`lib/community-feed.ts::discoverUserCommunities`) | Same Arkiv query.                                                         |
| Library page (`app/library/page.tsx`)                                  | Compose grid + list of `MediaItem`s.                                      |
| Watch page (`app/watch/page.tsx`) + video player                       | Media3 (ExoPlayer) player screen.                                          |
| Community feed page (`app/community/page.tsx`)                         | Compose list, verified badges from attestation checks.                    |
| Settings (`app/settings/page.tsx`)                                     | Compose settings — cache quota, TTL, clear cache, disconnect.             |
| Cache metadata mirror (`services/cacheService.ts`, `lib/cache/db.ts`)  | Room DB + DataStore, per-wallet namespaced.                                |
| Video cache (`lib/video-cache.ts`, `public/haven-sw.js`)               | `foc-local-first-android/foc-cache` (LRU + TTL + quota + hedged race).    |
| Cache integrity + eviction (`lib/cache-integrity.ts`, `cache-errors.ts`)| Same policies, Kotlin implementations.                                    |
| Security cleanup on disconnect (`lib/security-cleanup.ts`)             | Kotlin equivalent, purges Room + FocCache + Keystore for this wallet.     |
| Landing page (`app/page.tsx`)                                          | Simple onboarding screen: wallet-connect CTA + one paragraph.             |

If a `haven-dapp-main` file is not in the left column, it's either upload-side (not shipped on mobile — same as web) or captured by one of these rows.

---

## 4. Domain model

Minimally generalized from the web dApp's `Video` type — same fields, plus a `kind` discriminator and file-type hints for viewer selection.

```kotlin
enum class MediaKind {
    VIDEO,      // .mp4 .mkv .webm .mov — inline player (Media3)
    AUDIO,      // .mp3 .flac .ogg .wav .m4a — inline player (Media3)
    IMAGE,      // .jpg .png .webp .gif .heic — inline (Coil)
    DOCUMENT,   // .pdf — inline (PdfRenderer)
    FILE        // anything else — download & Open-with (§7)
}

data class MediaItem(
    val id: String,                     // Arkiv entity key (parity with Video.id)
    val kind: MediaKind,
    val owner: String,                  // wallet address of author
    val title: String,
    val description: String?,
    val mimeType: String?,
    val fileExtension: String?,         // ".mp3", ".pdf", ".zip" — drives Open-with
    val filenameHint: String?,          // suggested filename on Save
    val sizeBytes: Long?,
    val createdAt: Instant,
    val createdAtBlock: Long?,
    val expiresAtBlock: Long?,

    // Same content-addressing as the web dApp
    val pieceRef: PieceRef?,            // FOC handle (foc-local-first-android)
    val filecoinCid: String?,
    val encryptedCid: String?,
    val cidHash: String?,

    // Same access control
    val gate: TokenGate?,
    val isEncrypted: Boolean,
    val encryptionMetadata: GateMetadata?,       // v1
    val cidEncryptionMetadata: GateMetadata?,    // v1
    val attestation: Attestation?,               // for feed verification

    // Same cache-state fields the web dApp already carries
    val arkivStatus: ArkivStatus,
    val contentCacheStatus: ContentCacheStatus,
    val lastAccessedAt: Instant?
)

data class TokenGate(
    val chain: String,
    val tokenAddress: String,
    val threshold: Double,
    val tokenStandard: TokenStandard = TokenStandard.ERC20
)

data class Community(val gate: TokenGate)
```

Everything else is a view over `MediaItem` and `Community`, exactly as in the web dApp.

---

## 5. Functional requirements

Kept intentionally short. Each is a one-liner mapped to an existing web-dApp behavior.

### 5.1 Wallet (FR-W)
- **FR-W-1** Connect via Reown AppKit; supported connectors: MetaMask, Rainbow, Trust, generic WC v2.
- **FR-W-2** Persist only the wallet address and last-used connector.
- **FR-W-3** Sign EIP-712 `GateRequest` / `GateRequestV3` through AppKit; nonce management per `haven-aol-nonce.ts`.
- **FR-W-4** On disconnect run FR-SEC.

### 5.2 Access control (FR-ACL)
- **FR-ACL-1** Use `ic-kotlin` to call Haven-AOL; support **both v1 and v3** metadata, dispatched the same way as `haven-aol-decrypt-dispatch.ts`.
- **FR-ACL-2** Keep unwrapped AES keys in memory-only LRU; wipe on disconnect and process death.
- **FR-ACL-3** Cache the canister's Ed25519 verification key for the session; verify feed attestations offline (single-CID + Merkle-batch).

### 5.3 Local-first storage (FR-CACHE)
- **FR-CACHE-1** Depend on `foc-local-first-android/foc-cache`. All content bytes flow through `FocCache.get / stream / fetch`.
- **FR-CACHE-2** Defaults: 2 GiB quota, 30-day TTL, 256 KiB chunk, `maxParallelFetches = 3`, hedge delay 300 ms cellular / 150 ms Wi-Fi. All user-adjustable in Settings.
- **FR-CACHE-3** Metadata mirror in Room, per-wallet namespaced. Write-through on Arkiv fetch. Expired-on-Arkiv entities remain in cache (parity with web dApp).
- **FR-CACHE-4** Automatic eviction on quota / TTL; explicit "Clear cache" in Settings.
- **FR-CACHE-5** **PieceCID verification** for every fetched piece (implements the TODO the web dApp queued). Failed verify → evict + retry from a different provider.

### 5.4 Library, watch, community, settings (FR-UI)
Direct parity with the web dApp's four screens.

- **FR-UI-1** **Library** — grid/list of the wallet's `MediaItem`s from Arkiv, sorted by recency. Same search/filter surface as `haven-dapp-main/src/components/library/*`.
- **FR-UI-2** **Watch/View** — opens one `MediaItem`, routes to the right inline viewer by `MediaKind`:
  - `VIDEO`, `AUDIO` → Media3 (ExoPlayer). Background audio + PiP for video.
  - `IMAGE` → Coil.
  - `DOCUMENT` → PdfRenderer.
  - `FILE` → §7 Save-to-device + Open-with (mobile has no browser download shelf; this is the only true mobile-specific addition).
- **FR-UI-3** **Community feed** — same query + verified-attestation badges as `haven-dapp-main/src/app/community/page.tsx`. No chat, no discovery UI in v1.
- **FR-UI-4** **Settings** — cache quota slider, TTL slider, clear cache (per-community and global), disconnect, "Clear on disconnect" toggle.
- **FR-UI-5** Video/audio: encrypted bytes SHALL be decrypted in memory only; no plaintext on disk (parity with `lib/chunked-decrypt.ts`).

### 5.5 Security cleanup (FR-SEC)
Direct port of `lib/security-cleanup.ts`:

- **FR-SEC-1** On disconnect: wipe AES + gate-key caches, purge Room DB for this wallet, purge FocCache dir for this wallet, cancel WorkManager jobs tagged with this wallet.
- **FR-SEC-2** Fail-safe: log per-step failures, show one confirmation.

### 5.6 Errors & diagnostics (FR-OBS)
- **FR-OBS-1** Same error taxonomy as `lib/cache-errors.ts` / `lib/playback-errors.ts`, classified into stable codes.
- **FR-OBS-2** Rolling in-memory log (last 100 events); "Recent errors" panel in Settings; optional redacted log export.

---

## 6. Non-functional requirements

- **Cold start → library:** ≤ 2.0 s with warm cache (Pixel 6).
- **Cached playback first frame:** ≤ 400 ms after tap.
- **Miss-path first frame:** ≤ 3.0 s p50 on cellular via `foc-cache` hedged race.
- **Offline:** library, cached content, and settings SHALL be fully usable in airplane mode.
- **Quota:** hard cap on FocCache — test-enforced.
- **Security:** wallet-derived material lives in Android Keystore. No plaintext gated content on disk. No third-party crash reporter in v1.
- **`minSdk = 26`, `targetSdk = 34`.**
- **Coverage:** ≥ 80 % unit-test line coverage on `core-domain`, `core-haven-aol`, `core-crypto`, `core-cache-mirror`. Maestro E2E for: connect → library → watch (video, PDF) → disconnect.

---

## 7. Mobile-specific concession: Save-to-device + Open-with

The **only new capability** relative to web: since Android has no default "download and open with the emulator/reader/etc. of your choice" chrome, `MediaKind.FILE` items get a **Save to device** action.

- **FR-FILE-1** After successful decrypt, "Save to device" writes plaintext to a user-chosen location via `ACTION_CREATE_DOCUMENT` (Storage Access Framework), with `filenameHint` prefilled.
- **FR-FILE-2** "Open with…" fires `Intent.ACTION_VIEW` (or `application/octet-stream` fallback + filename) on the saved URI so Android resolves the right handler.
- **FR-FILE-3** First save shows a one-time warning: "This file is now readable by anything on your phone. Continue?"
- **FR-FILE-4** Items with failed PieceCID or attestation verification SHALL NOT be exportable.

That's the whole file-out story for v1. No `suggested_apps` maps, no companion registries, no per-DAO overrides — the OS's normal chooser handles it.

---

## 8. Architecture (short version)

```
UI (Compose)
  └── ViewModels
        └── Domain (MediaItem, Community, TokenGate, ACL)
              ├── core-haven-aol   (ic-kotlin → canister)
              ├── core-arkiv       (entity + payload fetch)
              ├── core-cache-mirror (Room, per-wallet write-through)
              ├── core-cache        (foc-local-first-android:foc-cache)
              ├── core-crypto       (AES-GCM + key caches)
              ├── core-attestation  (Ed25519 + Merkle)
              └── core-wallet       (Reown AppKit)
```

Every module above corresponds 1:1 to a chunk of the web dApp (`lib/haven-aol/`, `lib/arkiv.ts`, `services/cacheService.ts`, `lib/cache/*`, `lib/attestation.ts`, `lib/crypto.ts`, wallet stack).

**No** `:feature-chat`, `:feature-treasury`, `:feature-discover`, `:feature-publish`, `:feature-onboarding-carousel`, `:core-access`, `:core-chat-transport`, `:core-handoff` (SAF export lives in `:feature-watch` for v1). Those are post-v1.

---

## 9. Milestones (v1)

**M0 — Foundations (1 week)**
- Gradle modules, DI, theme.
- AppKit connect/disconnect (FR-W-1..4).
- `ic-kotlin` smoke ping to Haven-AOL canister.
- `foc-cache` wired end-to-end against a fixture `PieceRef`.

**M1 — Library + Watch MVP (3 weeks)**
- Community discovery (Arkiv query).
- Library grid + list (FR-UI-1).
- Video watch (FR-UI-2 for `VIDEO`).
- v1 gate decrypt (FR-ACL-1..2).
- Room mirror + write-through (FR-CACHE-3..4).
- Settings v1 + disconnect (FR-SEC).

**M2 — File kinds + v3 + PieceCID verify (2 weeks)**
- Audio + Image + PDF viewers (FR-UI-2 for other kinds).
- Save-to-device + Open-with for `FILE` (FR-FILE-1..4).
- v3 gate + batch unlock (FR-ACL-1).
- PieceCID verification (FR-CACHE-5).

**M3 — Feed + polish (1 week)**
- Community feed with attestation badges (FR-UI-3).
- Cache diagnostics in Settings (FR-OBS-2).
- Maestro E2E flows.

**Total: ~7 engineer-weeks / 1 SDE to a shippable v1.**

---

## 10. Open questions

Kept short — decide before starting the affected milestone.

1. **PieceCID verify implementation** — port from `synapse-sdk` (recommended), Rust FFI, or defer to v1.1. Blocks M2.
2. **Community-feed pagination default** — same 20-per-page as web, or larger for phone screens. Cosmetic.
3. **Which Arkiv Kotlin binding** — official SDK, or a thin HTTP client we generate from Candid/GraphQL. Blocks M1.

Everything else (chat transport, DEX partner, iOS strategy, featured-communities curation, etc.) is post-v1 and lives in `FUNCTIONAL_REQUIREMENTS.md`.
