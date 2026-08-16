# Refactor plan — shared components and a real design system

**Status:** in progress. Phases 1–4 are implemented in this change; phase 5 onward is queued.
**Scope:** `mobile-app/`. No behaviour was removed except where noted as a bug fix.

---

## 1. Why

The five screens were built independently and it showed. Concretely, before this change:

| Symptom | Root cause |
|---|---|
| No way to reach Library, Community or Settings | There was no app shell — only `onboarding` had a navigation edge, pointing at the debug screen |
| Every feature route rendered blank | `NavGraph` called each `NavGraphBuilder.xxxRoute()` *inside* a `composable {}` body, so the destination's only effect was registering another destination while composing |
| Library category chips did nothing | `selectedCategory`/`filteredItems` were plain getters over `MutableStateFlow.value`, and `selectCategory` "notified" by re-emitting the same instance, which `StateFlow` drops |
| Playback could not work at all | Viewers pointed at `file:///cache/<pieceCid>.mp4`, a path nothing ever writes. The unlock produced a key and dropped it |
| "Clear expired" deleted the whole cache | `clearExpiredFor` delegated to `clearFor` |
| Settings dialogs could vanish mid-interaction | `remember { mutableStateOf(false) }` lived inside `LazyColumn` item lambdas, whose composition is discarded on scroll |
| Every Arkiv call failed as a network error | `ArkivConfig.endpointUrl` was hard-coded to `""`, so `buildUrl` threw before any request |
| Release variant could not configure | `proguard-rules.pro` was referenced by two build files and did not exist |
| No app identity, no network access | `app` had no `res/` at all, and the manifest had no `INTERNET` permission, theme, icon, or WalletConnect deep-link filter |
| `build-logic` could not configure | A `gradlePlugin { register(...) }` block declared `implementationClass` values that are not classes, duplicating ids that `kotlin-dsl` already registers from file names |

Underneath all of it: **no shared UI layer**. Styling was inline per screen (three different title
sizes, four spacings, hard-coded hexes), so a "small" visual change meant five edits, and state marks
that encode trust — cache residency, attestation — were drawn differently on each screen.

## 2. Layering

```
app                     shell, nav graph, DI entry
  └── feature-*         one screen each: Composables + ViewModel
        ├── core-design  tokens + shared components      ← new
        └── core-*       domain, wallet, aol, arkiv, cache, mirror, crypto, attestation, security
```

Rules, enforced by module dependencies rather than convention:

1. `core-*` never depends on `feature-*`; nothing depends on `:app`.
2. Feature modules do not declare colour, type, shape, spacing or motion values. If a screen needs a
   new one, it is added to `:core-design` where the other four screens can see it.
3. Anything drawn on more than one screen is a `:core-design` component, not a copy.
4. Pure logic (filtering, formatting, mapping, rules) is a top-level function or object, testable
   without Android, Room, or a coroutine dispatcher.

## 3. What moved into `:core-design`

| Area | Before | After |
|---|---|---|
| Colour | Hexes inline in `app/ui/theme/Theme.kt` plus literals in screens | `color/HavenPalette.kt` — OKLCH tokens, void + paper Material 3 schemes, `HavenAccents` for roles Material has no slot for |
| Type | One `Typography` in `app`, invisible to features | `HavenTypography.kt` — full M3 scale plus `HavenTextStyles` (mono, overline, figure, editorial) |
| Shape / spacing / motion | Ad-hoc `dp` literals | `HavenTokens.kt` — `HavenShapes`, `HavenSpacing`, `HavenElevation`, `HavenMotion` |
| Chrome | None; each screen drew its own header | `component/Chrome.kt` — `HavenTopBar`, `HavenNavigationBar`, `HavenScreen` |
| Media presentation | Row and card duplicated across Library and Community | `component/Media.kt` — `MediaRow`, `MediaCard`, `formatBytes` |
| State marks | Cache chip and attestation badge re-implemented per screen | `component/Marks.kt` — `CacheStatusChip`, `AttestationBadge`, `MediaKindGlyph`, `ProtocolDot`, `MonoIdentifier` |
| Empty / loading / error | Inline, inconsistent, sometimes a bare spinner | `component/States.kt` — `EmptyState`, `ErrorState`, `LibrarySkeleton` |
| Inputs | Search, sliders, switches, dialogs rebuilt per use | `component/Inputs.kt` — `HavenSearchField`, `SectionHeader`, `SettingRow`, `SettingSliderRow`, `SettingSwitchRow`, `ConfirmDialog` |

`app/ui/theme/Theme.kt` is now a delegate, kept so `MainActivity` and previews have one entry point.

## 4. Phases

**Phase 1 — unblock the build.** *(done)*
`build-logic` plugin registration; `app/proguard-rules.pro`; `res/` (strings, themes, adaptive icon,
data-extraction rules); manifest permissions, theme, icon, `singleTask`, PiP, `haven://connect`
filter, `<queries>` for the export chooser.

**Phase 2 — design system.** *(done)*
New `:core-design` module, registered in `settings.gradle.kts` and added to `app` + all five feature
modules. Tokens, then components, then a `HavenTheme` that wraps `MaterialTheme` so stock components
inherit the brand.

**Phase 3 — navigation and shell.** *(done)*
`AppNavGraph` registers destinations at builder level; `HavenApp` provides one `Scaffold` with bottom
navigation and correct inset consumption (screens draw their own top bar inside it, the
Now-in-Android pattern); `Routes.kt` centralises route strings and the destination list;
onboarding → library promotion pops the gate off the back stack.

**Phase 4 — screens and state.** *(done)*
All five screens rebuilt on shared components. `LibraryViewModel` and `CommunityViewModel` re-shaped
so derived values are part of the emitted state; `SettingsViewModel` reads preferences as flows
instead of re-reading after every write; `WatchViewModel` runs the real
unlock → fetch → decrypt pipeline and zeroes the plaintext in `onCleared`.

**Phase 5 — streaming decrypt for low-end devices.** *(done)*
The pipeline no longer materialises payloads. `HavenCipher.decryptStream` was rewritten from
"collect the entire flow into one buffer, then decrypt" into a real incremental parser: a compacting
`FrameBuffer` re-assembles chunk framing across arbitrary transport boundaries and each encryption
chunk is decrypted and emitted the moment its last byte lands. `PlaintextSpool` (`:core-cache`) drains
those emissions to `cacheDir/plaintext/<wallet>/<cid>.bin`, writing `.part` and renaming on success.

Consequences, all of them the point:

- Peak memory is one chunk, not one file. The 192 MiB ceiling is gone.
- `ContentState.Ready` carries a `File`, so viewers stream: Media3 seeks the file directly (no
  `ByteArrayDataSource`), images are measured then decoded with `inSampleSize` and RGB_565, PDF pages
  render one at a time via `PdfDocument` (replacing the eager 40-page `PdfPages`), and SAF export
  copies stream-to-stream instead of `write(ByteArray)`.
- Progress is determinate: the spool reports bytes written, so the UI shows a percentage rather than
  an indefinite spinner.
- Re-opening an item costs nothing — the staged file is reused, skipping unlock, fetch and decrypt.

The trade is that decrypted content now exists on disk. It is app-private, per wallet, trimmed
oldest-first to a 1 GiB budget, and wiped unconditionally on disconnect — including when "keep cached
content" is on, since that preference is about ciphertext. FR-UI-5 is amended in the requirements with
the reasoning.

**Phase 6 — the reader path, without the protocol.** *(done)*
`/threshold/read` on the web is the onboarding this app lacked. Ported as two new modules —
`:core-collections` (bundled roster bridged from `haven-web/src/lib/chains.ts`, plus a batched
read-only `balanceOf` to answer access) and `:feature-collections` (the screen) — with a permanent
bottom-nav slot, because the question it answers arrives at the worst possible moment.

Wallet connect improves on the web flow rather than copying it: pasting an address to check eligibility
exists on the web only because a page cannot hold a wallet, so on mobile it is not a step at all.

The constraint that shaped every decision here: **no leaked internals.** Enforced structurally where
possible — `GateSpec` (address, standard, threshold) is `internal` to `:core-collections`, so no screen
can render it — and by hand elsewhere:

- list rows show *kind · size · age* (`RelativeTime`) instead of a piece CID;
- the storage reference moved behind a "Where this is stored" disclosure on the viewer;
- error codes left the reader's error message and stay in Settings' *Diagnostics* section;
- `HavenAolImpl` no longer returns `"HavenAol <canisterId> unreachable: … (signed 412 chars with
  0x1a2b…)"` as a user-facing string — that goes to the log, the reader gets a sentence;
- onboarding no longer tells whoever is holding the phone to "set wallet.projectId in
  local.properties";
- "Unverified" became "Unsigned", "Unlocking gate…" became "Checking your access…", and "Haven-AOL
  access request" became "a message proving the address is yours".

Added alongside: `Explain` (the mobile analogue of the web's in-place explainer) and the "what Haven
never asks for" list, which is the most effective trust device on the web page.

**Phase 7 — close the known functional gaps.** *(done)*

- **The Feed had no query of its own.** It read `observeLibrary(address)` — the owner-scoped list — so
  it showed the same rows as the Library with badges added. `ArkivClient.listMediaForCommunity(gate)`
  now exists, and the Feed narrows the accessible set to items this wallet did not publish, which is
  what `haven-dapp`'s community page shows ("entities from all creators who gated content with this
  token").
- **The Library asked the wrong question.** It queried items *published by* the wallet, mirroring
  `haven-dapp`'s `fetchAllVideos(ownerAddress)`. That works on the web because the dapp is
  publisher-centric; on mobile, publishing lives in `haven-cli`, so it is empty forever. Reading comes
  from *joining a community*, so the library is now every archive the wallet has access to —
  `MediaRepository.refreshAccessible()` unions the gates it **holds** (the collections balance check)
  with the gates it has **published under** (Arkiv's `discoverUserCommunities`) and pages each archive
  into the mirror.

  An intermediate revision filtered the library to on-device content only. That was also wrong: the
  dapp merges Arkiv with the cache and *badges* residency per item rather than hiding rows. Residency
  is now an "Offline" filter chip the reader can apply, with its own count.
- **Cache settings were written and never read.** foc takes its `Config` at construction, so
  `HavenCacheImpl` now keys its instance on (wallet, quota, TTL) and rebuilds when any of them change.
  Reaching `SettingsRepository` directly would have been a module cycle, so `:core-cache` declares
  `CacheSettingsSource` and `:core-cache-mirror` supplies it.
- **Fields the dapp reads and this model dropped.** `duration` (an attribute, mirrored in the payload)
  and `creator_handle` are now parsed, mirrored (schema v2) and used — runtime in every row and card,
  and the feed credits a handle instead of a hex address when the publisher set one.
- **Audio stopped when backgrounded.** Playback moved into `HavenPlaybackService`
  (`MediaSessionService`), so the player outlives the composable and the system gets its notification
  and lockscreen controls. The UI drives it through a `MediaController`, which *is* a `Player`, so
  `PlayerView` is indifferent to where it lives.
- **PiP was declared and never entered.** Video now sets `autoEnterEnabled` on API 31+ and falls back
  to `onUserLeaveHint` below it, only when something is actually playing.

**Phase 9 — access as an intersection, across every chain.** *(done)*

The reading model, stated properly: **assets held at the address ∩ gate conditions stored on Arkiv**,
plus the author's own uploads. Earlier revisions had each half but never the intersection — one asked
Arkiv what *you* published, another checked a hard-coded roster on a single hard-coded chain.

- `core-domain/HavenChain` is now the supported set, taken from the canister's own `GateChain` variant
  (`EthMainnet`, `BaseMainnet`, `ArbitrumOne`, `OptimismMainnet`, `EthSepolia`) with a `parse` that
  accepts Haven names, CAIP-2, bare chain ids and human names — and returns **null** rather than
  defaulting, because a gate checked on the wrong chain answers confidently and wrongly.
- `HavenAolImpl` used to sniff substrings for the Candid variant ("10" and "Optimism" both had to appear,
  so a bare `10` fell through) and default to `EthMainnet`. It now resolves the chain or refuses.
- `GateAccessChecker` replaces the roster-bound, single-chain `EvmAccessSource`: public, over
  `TokenGate`, batched per chain and parallel across chains, de-duplicating by contract and keeping the
  lowest threshold. Endpoints per chain in `EvmEndpoints`, defaulting to the same publicnode URLs
  `haven-dapp` uses so nothing hinges on a config key.
- `ArkivClient.discoverGates(chains)` supplies the other half of the intersection — the gate conditions
  Arkiv holds, independent of who published them.
- `MediaRepository.refreshAccessible()` performs the intersection and only pages gates that passed.

**Multi-chain is the default and there is no chain picker.** All live networks are checked from first
launch; Settings has per-network switches to turn one off and refuses to empty the set. A selection step
in onboarding was considered and rejected: it is configuration in front of the product, and a reader who
does not know which chain their NFT is on cannot answer it.

**Phase 7 — coverage.** *(next)*
Raise `core-domain`, `core-haven-aol`, `core-crypto`, `core-cache-mirror` to the 80% floor and flip
`haven.coverage.enforce=true`. `MediaRepositoryImpl` needs its Room and Arkiv edges behind
interfaces it can be tested against first.

**Phase 8 — device tests.** *(next)*
The composite-build modules cannot run unit tests in CI without the sibling checkouts, so
`:core-cache`, `:core-cache-mirror` and the viewer paths need instrumentation tests in the assemble
job's environment.

## 5. Bug fixes folded in

These were fixed as part of the refactor rather than deferred, because leaving them would have meant
building the new UI on top of them:

- Navigation double-registration (blank screens).
- Non-observable derived state in Library (dead category filter).
- `clearExpiredFor` wiping the entire cache. Eviction is foc's, so the method is now a documented
  no-op and the button that promised otherwise is gone.
- Dialog state discarded on scroll in Settings.
- Arkiv endpoint hard-coded empty; now `BuildConfig.ARKIV_ENDPOINT_URL` with an explicit
  "not configured" failure.
- `core-domain` exposing `kotlinx.datetime.Instant` through `implementation`, so any consumer
  touching `MediaItem.createdAt` failed to compile.
- Watch pipeline never assembled; unlock key discarded, viewers pointed at a fictional path.
- `HavenCipher.decryptStream` buffering its entire input before decrypting anything, which made the
  "streaming" API a whole-file API with extra steps.

## 6. Removed

**Client-side PieceCID verification** (FR-CACHE-5). `foc-cache` proves integrity via PDP before
returning bytes, so the app-side check duplicated a guarantee held one layer down — and the shipped
implementation only checked that the CID string started with `baga`/`baf` and exceeded ten
characters, which passes any corrupt buffer. Removed from `HavenCacheImpl`, the DI module, and
`gradle.properties`. `core-cache/PieceCidVerifier.kt` is now a comment recording the decision and can
be deleted after review; `HavenError.CachePieceVerifyFailed` is kept for taxonomy parity with the web
dApp's `cache-errors.ts`.

## 7. Accepted deviations

**Access does not revoke instantly.** `haven-web` tells readers access "is re-checked against your
balance rather than cached forever — so it follows ownership for as long as you hold the asset, and
stops when you do not." The app caches the unwrapped key for the session and keeps staged plaintext
until disconnect or budget trim, so someone who passes on the gating asset keeps whatever they had
already opened. Accepted deliberately: it is the same property that makes the archive work in airplane
mode, which is this client's reason to exist. New opens still fail once the balance no longer clears,
and the Collections screen says so in "Can I lose access?". Worth aligning the web copy with.

**No metadata is invented. `haven-dapp` is the spec.** Specifically `lib/parse-arkiv-video.ts`, which is
the only code that turns a real Arkiv entity into a domain object — if a field is not read there, it does
not exist in practice, whatever a document says it could contain. Checking the mobile parser against it
found it reading five things that are not entity data:

| Field | Reality |
|---|---|
| `thumbnail_cid` | In `MEDIA_CONTENT_SPEC.md`, not in the dapp's read path, written by nothing. Removed — it would have been a permanently null column, and a poster frame fetched from a public gateway would leak which gated item is being viewed. Grid cards show the kind glyph. |
| `size_bytes` | No entity carries a size. `PieceRef.size` is the size of record once foc resolves the piece, so `sizeBytes` is null until then and byte labels are simply absent. |
| `file_extension`, `filename_hint` | Mobile-only ideas from requirements §4. Derived from the MIME type or the source URI's tail instead of read. |
| provider list, CDN flag, gateways, `unixFsRoot` | foc's own resolution. The `PieceRef` is built from the CID alone; a provider list baked in from an index would send fetches at the wrong hosts. |
| `arkivStatus`, `contentCacheStatus`, `lastAccessedAt` | The dapp hard-codes status to "active"; the other two are local state. All three were read with `getString`, which **throws** — so the parser would have failed on every real entity. |

Also fixed while checking: gate metadata was read from `encryptionMetadataV1`/`…V3` keys that do not
exist, so every gate lost its metadata on the way in. It now reads `encryption_metadata` /
`cid_encryption_metadata` and picks the version by content (an epoch means v3), as the dapp's
`parseAnyGateMetadata` does.

Everything this audit turned up that is **not** mobile's to fix — undocumented gate attributes, no size
key anywhere, an unwritten HTTP gateway contract, three duplicate chain normalisers, `thumbnail_cid`
specified but written by nothing — is written up in
[`ECOSYSTEM-SPEC-GAPS.md`](ECOSYSTEM-SPEC-GAPS.md) (internal) for the ecosystem fast-follow.
`docs/entities/MEDIA_CONTENT_SPEC.md` was trued up at the same time: it now names its implementation of
record, lists the seven keys the code reads that it had omitted, and marks `thumbnail_cid` unread.

**Prices are not shown.** The web reads live floor prices per collection. A price frozen into an APK
is wrong within a day, and a wrong price is a trust problem, so the roster carries none — "Get access"
opens the marketplace, where the real number is.

**Collection artwork is not shown.** Sixteen remote images would mean shipping an image loader and
sixteen network fetches on a screen that must work offline. A monogram tile stands in.

## 8. Not done

- **Copy is still inline Kotlin.** Only `app_name` is in `strings.xml`, so the app cannot be localised.
  This is a mechanical pass across roughly forty files — every `Text("…")` to a `stringResource`, and
  `RelativeTime`'s "3 days ago" to `getQuantityString` plurals, which also means giving that object a
  `Context` and rewriting its test. Deliberately not started here: done halfway it leaves two
  conventions in the codebase, which is worse than one honest single-locale state. It wants its own
  pass, with the plurals decision made first.
- Pull-to-refresh. Both lists use an explicit refresh action plus a 2dp activity line. The Material 3
  gesture container is the better affordance and should replace it.
- Per-community cache clearing (FR-UI-4 mentions it; there is no per-gate index to clear by).
- Brand typefaces are not bundled; the system sans/mono/serif stand in. Swapping them is three
  values in `HavenFaces`.
- `PlaintextSpool`'s budget is still a constant rather than a user-visible setting, now that quota and
  TTL are wired through.
