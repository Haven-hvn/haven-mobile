# Multi-identity (multi-wallet) support — plan

Status: implemented in this change. Decisions confirmed with the product owner:

- **One wallet per identity.** An identity is one EVM address plus a user label.
- **Full per-identity isolation.** Keys, files, mirror, settings and hidden items
  are partitioned per identity.
- **Header switcher, instant switch.** Switching clears nothing; it only
  re-points the active partition.

## 1. Why

Web3 users routinely hold several wallets (hot/cold, personal/creator, test).
Haven had exactly one active wallet: `WalletDataStore` held a single address,
disconnect wiped it, and several in-memory stores were global. Switching
wallets meant disconnect → wipe → reconnect → re-fetch and re-sign for
everything, and some state (below) would have leaked between wallets if a
second one had ever connected.

## 2. Audit of the single-wallet assumptions (before this change)

| Area | Before | Verdict |
|---|---|---|
| `HavenCacheImpl` (foc ciphertext/unlocked) | Directory per wallet, rebuilt when address changes | Already isolated |
| `PlaintextSpool` | Directory per wallet | Already isolated |
| `MediaRepositoryImpl` (Room mirror) | One DB file per wallet, swapped on address change | Isolated, but `clearFor` opened the *current* wallet's DB even when clearing another wallet |
| `KeystoreEpochStore` | One encrypted file per wallet | Already isolated |
| `HavenAolImpl.epochUnlocks` | Keyed by `EpochGateKey` incl. wallet | Isolated in memory, but `clearFor` wiped *all* wallets' unlocks |
| `AesKeyCache` | Global, keyed `itemId:token:meta` and (in Watch) raw piece CID | **Leaked**: identity B could reuse A's AES keys with no signature |
| `NonceManager` | Keyed by wallet + canister | Already isolated |
| `SettingsRepository` | One global DataStore | **Shared**: quota/TTL/chains/keep-unlocked leaked |
| `HiddenItemsStore` | One global set | **Shared** |
| `WalletDataStore` / `WalletSessionImpl` | Single address; restore *cleared* it if the WalletConnect session was gone | Single-identity only |
| Playback (`NowPlayingRepository`) | Untagged track | Playback of A's content survived into B |

## 3. Model

```
WalletIdentity(address, label, connector, addedAtMs, lastActiveAtMs)
id = address.lowercase()          // every partition key derives from this
```

- Registry + active id persist in the existing `haven_wallet` DataStore
  (`identities_v1`, `active_address`). The legacy single `wallet_address`
  migrates into a one-entry registry on first load; nothing is deleted.
- `WalletSession.address` stays the browsing identity (the active address).
  New surface on `WalletSession` (all with interface defaults so existing
  test fakes keep compiling): `identities`, `activeIdentity`, `isLive`,
  `switchTo`, `connectNewIdentity`, `reconnect`, `renameIdentity`,
  `forgetIdentity`.
- **Live vs stored.** Reown/AppKit holds exactly one WalletConnect session,
  so only the active identity can be *live* (able to sign). Switching ends
  the previous live session through `AppKit.disconnect()` but touches no
  stored data; the switched-to identity browses instantly (mirror, cache,
  files, persisted epoch unlocks) and shows *Reconnect* for signing.
  Signing (`signTypedDataV4`, `sendTransaction`) now refuses unless the live
  AppKit account equals the active address — it can never sign with the
  wrong wallet. Approving a different wallet than the one reconnecting
  simply activates that wallet (known or new); its stored data is intact.
- Wallet-side account changes (`onSessionUpdate` with a new account) upsert
  and activate that identity, mirroring how wallets themselves switch.

## 4. Operations and what they clear

| Operation | WalletConnect | Keys (mem + epoch file) | Cache / plaintext / mirror | Settings / hidden | Registry |
|---|---|---|---|---|---|
| Switch | Ends live session of previous identity | **Kept, per identity** | **Kept** | **Kept** | Active pointer moves |
| Add identity | Disconnects live, connects new | Kept (old) | Kept (old) | Kept (old) | New entry added + activated |
| Reconnect | New session for chosen wallet | Kept | Kept | Kept | Activated |
| Disconnect (Settings → Security) | Ends session | Wiped for that identity (always) | Wiped only if “Wipe on disconnect” | Kept | **Kept** — identity stays in the switcher |
| Remove identity (Settings → Identities) | Ends session if it was live | Wiped for that identity | Wiped for that identity | Wiped for that identity | Entry removed; next identity activates |

`SecurityCleanup.runDisconnect` keeps its step order/report for Disconnect;
`removeIdentity` is the unconditional per-identity wipe. Neither touches any
other identity. Hidden-id residue for a removed wallet is inert (ids are
namespaced per wallet) and is documented rather than chased across modules.

## 5. Isolation changes

- `AesKeyCache`: keys are wallet-prefixed at every call site
  (`<walletLower>:<item…>` in Haven-AOL, `<walletLower>:<pieceCid>` in
  Watch); new `clearForWallet` wipes one identity. Canister verification
  keys stay unprefixed (public, identity-independent).
  `SessionKeys.hasSessionKey` takes the wallet explicitly.
- `HavenAolImpl.clearFor`: clears only the target wallet's AES keys, epoch
  unlocks/locks and persisted epoch file.
- `SettingsRepository`: every preference key is scoped
  `id/<walletLower>/<base>` for the active identity, falling back to the
  legacy global key, then the default. New `clearFor(wallet)`.
- `HiddenItemsStore`: same scoping with legacy fallback.
- `MediaRepositoryImpl.clearFor`: never opens another wallet's DB; only the
  current wallet's DB is drained/closed, files are deleted by name.
- Playback: `NowPlayingTrack` is tagged with its wallet; `NowPlayingViewModel`
  clears the mini-player and playback session when the active identity no
  longer matches (staged plaintext is per-wallet, so continuing would fail
  or, worse, play the previous identity's content).

## 6. UX

- **Library header**: identity chip (deterministic colour avatar from the
  address + label/short address) opens a bottom sheet: all identities with
  Active / Live / Reconnect badges, tap to switch, rename, *Add identity*,
  and a reconnect banner action when the active identity is not live.
- **Settings → Identities**: same list with Switch / Reconnect / Rename /
  Remove (confirmed, says exactly what is wiped) and *Add identity*.
  The existing Wallet section keeps copy-address; Security keeps Disconnect.
- **Onboarding**: stored identities are listed so a returning user can pick
  one and reconnect instead of starting from a blank connect.
- Removing the last identity (or signing out to none) returns to onboarding
  via the shell's active-identity observer.

## 7. Tests

- `core-wallet`: `IdentityRegistryTest` — codec round-trip, case-insensitive
  dedupe, switch changes only the active pointer (no entry/data loss),
  forget activates the next identity, legacy single-address migration,
  rename/label defaults.
- `core-crypto`: `AesKeyCacheWalletScopeTest` — `clearForWallet` removes one
  wallet's keys, keeps the other's and the unprefixed canister keys.
- `core-haven-aol`: `clearFor` scoping pinned alongside the existing epoch
  persistence tests.
- `core-cache-mirror`: scoped-settings key + fallback resolution.

## 8. Known limits

- One live WalletConnect session at a time (AppKit constraint): a stored
  identity always needs one wallet approval before it can sign again.
  Browsing, cached playback and persisted epoch unlocks do not.
- In-flight signing/fetching for the previous identity is not cancelled on
  switch; results land in that identity's partition (keys are namespaced),
  and signing itself is gated on the live account matching the active one.
