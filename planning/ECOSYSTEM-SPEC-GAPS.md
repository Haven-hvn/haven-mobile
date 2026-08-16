# Ecosystem spec gaps — internal working notes

**Not for publication.** Working notes for a fast-follow across `haven-dapp`, `haven-cli`,
`haven-mobile`, `haven-aol` and the entity spec. Deliberately blunter than anything in `docs/`: it names
things that are wrong rather than describing what exists. Nothing here is scheduled — it is a list to
work from when the ecosystem pass happens.

Lives in the mobile repo because that is where it was found. It belongs in a shared internal space once
one exists.

Found while porting `haven-dapp`'s entity handling to Kotlin. Porting is a good spec audit: every
assumption that was implicit in TypeScript had to be written down again, and the ones that were wrong
stopped compiling.

---

## How these are ranked

By what breaks if nobody fixes it:

- **P0** — the protocol leaks something it exists to protect.
- **P1** — a consumer written against the documented spec gets wrong behaviour or a crash.
- **P2** — a consumer has to invent something, and two consumers will invent differently.
- **P3** — a real improvement, nothing breaks without it.

---

## P0 — metadata privacy is not a capability, and the default leaks

### 0. The content is encrypted. Everything *about* it is public and permanent.

Today the split is fixed by the spec, not chosen by the publisher: **attributes are public and
on-chain, payload is encrypted.** `MEDIA_CONTENT_SPEC.md` states the rule as "No secrets in
attributes" — which is sound advice given the mechanism, and also the source of the problem, because
several required attributes are secrets in practice.

What is currently readable by anyone, forever, for an item nobody can decrypt:

| Public today | What it discloses |
|---|---|
| `title` (**required**) | The subject. "Board call — Q3 restructuring" is legible to the world while the recording is sealed. This alone defeats the premise for a large class of archives. |
| `duration` | Length. Correlates a sealed item with a known event — a 94-minute item published the evening of a 94-minute meeting is that meeting. |
| `creator_handle`, `entity.owner` | Which address published, and how often. Publication *cadence* is signal on its own. |
| `gate_token`, `gate_chain`, `gate_threshold` | Which addresses can open which archive, computable from public chain state alone. |
| `tags`, `category`, `language` | Subject matter, directly. |
| `source_uri` | Where it came from — sometimes an internal or otherwise private URL. |

Two of these deserve to be stated precisely, because one is the actual problem and the other is a
feature that looks like one:

1. **Titles are the leak.** Required, public, and the most descriptive field on the entity. A reader who
   cannot decrypt a byte can still read the table of contents. This is the item to fix first, and there
   is no technical obstacle to fixing it (see below).
2. **The gate attribute is the discovery layer, and it should stay public.** `gate_token` in the clear,
   plus public token balances, means the co-membership graph is computable by anyone from public chain
   state. Read as a disclosure that is a linkability cost. Read as a capability it is the *only*
   mechanism Haven has for discovery once content metadata is encrypted — see below.

   **Addresses are not people.** Haven publishes no identities and this note should not imply otherwise.
   What is public is a pseudonymous graph: which pseudonyms share a gate, and which published when. It
   becomes identifying only through linkage Haven has no part in — a self-claimed address, an ENS name,
   an exchange record.

### The graph is the discovery mechanism. Do not blind it.

An earlier draft of this note proposed replacing the public `gate_token` attribute with
`gate_ref = HMAC(epoch_key, gate_token ‖ gate_chain)` so that non-members could not enumerate gates.
**That was wrong, and wrong in an expensive way** — it would have removed the only discovery path the
protocol has.

The reasoning it missed: if content metadata is encrypted, *nothing about an item is discoverable*. The
gate graph is then the entire surface for "what else is there?" — and it supports exactly the question a
reader wants answered:

> You hold this community's asset. Addresses that hold it also tend to hold that one. You may want to
> look at that community.

That is a recommendation system with a property almost nothing else has: **it needs no surveillance.**
The graph is public chain state, so any client — or any third party — can compute it locally. Nobody has
to observe what a reader opens, because the recommendation is derived from what addresses *hold*, not
from what anyone *watched*. Compare a platform, where the equivalent feature requires a server that
records every view and owns the resulting graph.

So the split falls out naturally, and it is a better split than "encrypt more":

| Dimension | Tier | Why |
|---|---|---|
| **Gate / membership structure** (`gate_token`, `gate_chain`, `gate_threshold`) | **Public, by design** | The join key for discovery and recommendation. Blinding it buys marginal privacy and costs the feature. |
| **What the content is about** (`title`, `tags`, `category`, `language`, `source_uri`, thumbnail) | **Member** or **Private** | This is the part that leaks the archive's substance, and the part with no discovery role. |
| **Shape** (`duration`, any `size_bytes`) | **Public, bucketed** | Useful for browsing and estimates; exact values fingerprint. |
| **Identity** (`encrypted_cid`) | **Public, unchanged** | Uniqueness is a functional requirement. |

Stated as a single rule: **Haven publishes the structure of who can read what, and encrypts what it is
about.** The graph says "these addresses share a gate". It never says "this address opened this item" —
and that distinction is what keeps a public graph from becoming behavioural surveillance.

This also answers the open question the earlier draft raised — how a prospective member discovers a
community they cannot yet decrypt. They discover it *through the graph*, at community level, without any
item's metadata being legible. Communities are findable; their contents are not.

**The honest flip side.** A public graph is equally available to anyone building a profile rather than a
recommendation, and that cannot be prevented without destroying the feature. What limits the damage is
that Haven contributes no behavioural signal to it: no view events, no dwell time, no per-item access
log. Worth stating in the spec, because "your reading is not recorded anywhere" is both true and the more
meaningful guarantee.

### A stable unique identifier is required, and `encrypted_cid` is the right one

An earlier draft also proposed rotating or dropping `encrypted_cid` because a stable public identifier is
a correlation handle. **Also wrong** — uniqueness is a functional requirement, not an accident: dedup,
idempotent republication, and lookup-without-revealing-the-plaintext-CID all need one identifier that
does not move. `encrypted_cid` already provides it and does not disclose the underlying Filecoin CID.

The correlation property is an accepted cost of having uniqueness at all, and the right response is to say
so in the spec rather than to churn the identifier.

Encrypting a payload while publishing an index of its subject, length, author, cadence and audience is
a partial guarantee described as a whole one.

### What "metadata-level privacy" should mean

Not "encrypt everything" — that leaves nothing discoverable, and the gate graph above is why. Three tiers,
and the publisher chooses per key:

| Tier | Mechanism | Who can read | Queryable |
|---|---|---|---|
| **Public** | attribute, as today | anyone | yes, on-chain |
| **Member** | attribute encrypted under the **gate epoch key** | anyone holding the gate | equality only, by members who can derive the key |
| **Private** | payload, as today | anyone holding the gate, after a per-item unlock | no |

The **Member** tier is the missing one, and it is nearly free because the machinery exists: Haven-AOL
v3 already derives one key per gate epoch and unlocks a whole epoch in a single canister call
(`haven-aol-batch-decrypt-v3.ts`). Metadata encrypted under that epoch key costs a member **one** unlock
to browse an entire community, while a non-member sees opaque bytes. That is exactly the property a
browse list needs, and it is why this is a spec change rather than a research project.

Concretely:

- **Encrypt `title`.** There is no reason it cannot be: it is a string in an attribute slot, and an
  attribute slot holds bytes. Default it to Member tier, with `title_public` as a separate optional
  attribute for archives that want to be discoverable — so a public title is something a publisher opts
  into rather than something they get by default without being told.

  The one real constraint is length, and it needs designing around rather than discovering later.
  `ATTR_STRING` is a 128-byte value with no embedded nulls, so the ciphertext has to be text-encoded:

  ```
  AES-GCM overhead      = 12 (nonce) + 16 (tag)   = 28 bytes
  base64 expansion      = ceil(n / 3) * 4
  budget                = 128 bytes
  → plaintext title     ≤ (128 * 3 / 4) - 28      ≈ 68 bytes
  ```

  So an encrypted title fits ~68 characters against 128 in the clear. Three ways out, in preference
  order: accept 68 and say so in the spec; keep the attribute for the optional public title and put the
  Member-tier display title in the epoch-encrypted payload where length is unconstrained; or widen the
  attribute value, which is a container change and much more expensive than the other two.
- **Keep `gate_token`, `gate_chain` and `gate_threshold` public**, and say in the spec that this is a
  deliberate choice serving discovery — not an oversight, and not something a future revision should
  quietly "fix". A publisher who cannot accept a public gate does not want a gate; they want a private
  distribution list, which is a different product.
- **Encrypt the descriptive set** — `tags`, `category`, `language`, `source_uri` alongside `title`. These
  carry the archive's substance and contribute nothing to discovery. `source_uri` in particular is
  sometimes an internal URL and has no business being public.
- **Bucket `duration` and any future `size_bytes`** — powers of two, or 5-minute buckets. Exact values
  fingerprint against a known file or a known meeting; buckets still sort, filter and estimate.
- **Keep `encrypted_cid` exactly as it is.** Uniqueness is required and it already provides it. Document
  the correlation property as an accepted cost rather than attempting to hide it.
- **State the residual disclosure, and that it is intentional.** After all of the above, the following
  stay public: that an entity exists, the publishing address, the gate it belongs to, its size class and
  its timing. Everything except size class and timing is load-bearing for discovery. Say so, rather than
  letting a reader infer that encryption covers more than it does.
- **No view events, ever.** The guarantee that makes a public graph acceptable is that Haven adds no
  behavioural layer on top of it: no access log, no read receipts, no per-item analytics, on any surface.
  That is currently true by omission; it should be written down as a constraint so it stays true.

### Recommendation — what it needs that does not exist yet

The graph is public, but it is not *cheap*. Co-membership needs holder sets, and a client cannot
enumerate the holders of an ERC-721 contract over `eth_call` — it can only ask "does this address hold
it?" (which is all `GateAccessChecker` does today). So recommendation needs an index, and the interesting
part is who gets to build it:

- **Anyone can.** The inputs are public chain state, so an index is a derived artifact, not a moat. A
  client can spot-check any recommendation it is given by verifying a handful of balances directly — which
  means an index can be *untrusted* and still useful, an unusual and good property.
- **Arkiv could publish aggregates** — per-gate holder counts and pairwise co-membership — as ordinary
  entities. That keeps the whole thing inside the protocol with no new service.
- **Mobile's shape today** is one balance read per gate for the connected wallet
  (`GateAccessChecker.satisfied`). Recommendation is the reverse query and does not fit that path; it
  wants an index, whichever way one arrives.

Worth writing up separately as a proposal once tiered metadata is settled, because the tiers determine
what a recommendation is allowed to say. "Members of X also hold Y" is fine at every tier. "Members of X
also read *this item*" must never be possible — and with no view events, it is not.

### Why this is P0 rather than a nice-to-have

It is not a missing feature; it is a **stated guarantee that does not hold**. `haven-web`'s reader path
says "nobody can revoke it" and "no company controls the record" — both true, and both read by a
newcomer as "private". The threshold page's absence list ("what you are never asked for") builds exactly
that expectation. A publisher reading the current spec has no way to keep a title private, and no
warning that they cannot.

Everything else on this list produces a bug. This one produces a false sense of confidentiality, which
is the failure mode with consequences outside the software.

### Mobile's own exposure, for completeness

Worth fixing alongside, because it is the same guarantee at a different layer:

- The metadata mirror (`haven-mirror-<wallet>.db`) stores titles, descriptions and CIDs **in plaintext**
  in app-private storage. App-private is not encrypted — a rooted device, a backup, or a forensic image
  reads it. If metadata is sensitive, the mirror needs SQLCipher (or per-column encryption under a
  Keystore key), and requirements §6 already asks for Keystore use that nothing currently does.
- Decrypted content is staged to disk by design (accepted deviation, `REFACTOR_PLAN.md` §7) — a separate
  and already-documented trade, but the same conversation.
- The Settings diagnostics log holds gate and item identifiers in memory for the session. Currently
  never persisted, which is correct; it should stay that way.

---

## P1 — the document disagrees with the implementation

### 1. `thumbnail_cid` is specified, unwritten, unread

`MEDIA_CONTENT_SPEC.md` lists it as an optional payload key. `parse-arkiv-video.ts` does not read it.
No pipeline writes it. `Video.thumbnailUrl` exists on the type and is never populated.

Mobile added a `thumbnailCid` field on the strength of the document, then removed it — it would have
been a permanently null column with a Room migration attached.

**Proposal.** Decide it, do not leave it specified-but-absent:
- if thumbnails are wanted, `haven-cli`'s existing thumbnail pipeline should write `thumbnail_cid` **as
  its own piece with its own `PieceRef`**, not a bare CID. A bare CID cannot be fetched through FOC (no
  provider list), and the only fallback is a public IPFS gateway — which tells that gateway exactly
  which gated item a reader is looking at. That is a privacy regression bought for a poster frame.
- if not, strike it from the spec.

Also worth deciding: a thumbnail in the *private* payload is only readable after unlock, so it cannot
be shown in a browse list. This is item 0 in miniature — the thumbnail wants the **Member** tier
(encrypted under the gate epoch key, so members can browse and nobody else can), and making it a public
attribute to get browsability would publish a preview frame of sealed content. Decide the tier before
the pipeline writes anything.

### 2. Nothing carries a byte size

Not in the attribute table, not in the payload table, not read by the parser. `VideoSourceInfo.fileSize`
exists but is upload-time state, not entity data.

Consequence: no consumer can show a size, or a download estimate, or check a size against a quota,
before resolving the piece through FOC. Mobile shows no size at all in lists as a result.

**Proposal.** Add `size_bytes` (`UINT`) as an attribute — **bucketed**, per item 0: an exact byte count
fingerprints an item against a known file, while a bucket still sorts, filters and estimates. It is not
otherwise secret, it is useful before decrypt, and `haven-cli` already computes it while chunking.

### 3. Three fields consumers reasonably expect are not entity data, and the spec does not say so

`arkiv_status` (hard-coded `'active'` by the parser), residency/cache state, last-accessed. All are
local or derived. The mobile parser had been reading all three with a throwing accessor — it would have
failed on every real entity, and that was not caught because there is no real entity to test against
while the index is down.

**Proposal.** The spec now has a "not entity data" table (added 2026-08-15). Keep it accurate, and add a
`corbell spec lint` rule that fails when a documented key has no reader in any surface.

### 4. Gate attributes are undocumented

`gate_token`, `gate_chain`, `gate_threshold` are read from `entity.attributes` by
`community-feed.ts::discoverUserCommunities` and appear in **neither** table in the spec. They are the
only thing that makes community discovery possible.

**Proposal.** Document them as required attributes for any gated entity, with `gate_chain` constrained
to the Haven canonical names in `haven-aol-client.ts` (`EthMainnet`, `BaseMainnet`, `ArbitrumOne`,
`OptimismMainnet`, `EthSepolia`). Right now a publisher could write `"ethereum"` or `"eip155:1"` and
every consumer would need its own normaliser — mobile had to write one (`core-domain/HavenChain.parse`).

### 5. Gate chain parsing is guesswork in more than one place

`haven-aol-metadata.ts::normalizeChain` maps ~27 aliases. `lib/nft.ts::chainToRpc` does its own string
matching. `CommunityAccessNotice.tsx` does a third. Mobile's original port did a fourth, and it was
wrong: it tested `chain.contains("10") && chain.contains("Optimism")`, so a bare `"10"` fell through to
an `EthMainnet` default — a balance checked on the wrong chain, answered confidently.

**Proposal.** One exported normaliser in `haven-aol`, used by every surface, that **returns null on
unknown input** rather than defaulting. A default here is a correctness bug that cannot be observed
locally. Mobile's `HavenChain` is a reference implementation with tests, including the `"10"` case.

---

## P2 — every consumer has to invent the same thing

### 6. No filename or extension

Not stored. Mobile derives an extension from `content_mime_type` and falls back to the tail of
`source_uri`, then synthesises a filename from the title for its "save to device" dialog. The dapp has
`VideoSourceInfo.fileName` from upload state, which a reader never sees.

**Proposal.** Optional payload `file_name`. Publishers know it, readers want it when exporting, and
every consumer currently guesses differently.

### 7. Provider resolution is implicit

`piece_cid` is a bare string; provider service URLs, CDN flags and trustless gateways live in FOC's
`PieceRef`. That is the right split — but it is nowhere in the spec, so mobile's first port invented
`providerServiceUrls`/`cdnEnabled`/`trustlessGateways` as entity fields and would have published a
stale provider list into fetch requests.

**Proposal.** One paragraph in the spec: *the index publishes `piece_cid` only; FOC resolves everything
else, and an index must never publish provider lists.*

### 8. No documented gateway/HTTP surface

The dapp talks to Arkiv through the SDK. Mobile cannot (no Kotlin SDK), so it talks to an HTTP gateway
whose endpoints were invented by the mobile code: `/api/arkiv/media`, `/api/arkiv/media/{id}`,
`/api/arkiv/communities`, and now `/api/arkiv/gates`. **None of these are specified anywhere**, and
their response shape is likewise assumed (camelCase, reshaped from the entity).

This is the largest unwritten dependency in the mobile client. If the gateway that eventually appears
serves a different shape, the parser is wrong in a way nothing catches until runtime.

**Proposal.** Either specify the gateway (an OpenAPI document under `docs/api/`, alongside the existing
`contracts.md`) or ship a Kotlin Arkiv binding so mobile uses the same path as everyone else. The second
is more work and removes the whole class of problem — and the tasking plan already lists "which Arkiv
Kotlin binding" as an open question that was never answered.

### 9. `gate_threshold` has no units

An ERC-20 threshold is quoted in whole tokens while `balanceOf` answers in base units, so a consumer
must read `decimals` and scale. Nothing says so. Get it wrong and a gate requiring 25,000 tokens is
cleared by dust — a false positive on the only question the protocol asks.

Mobile handles it (`GateAccessChecker.requiredUnits`) and refuses when `decimals` cannot be read, rather
than guessing 18.

**Proposal.** State the unit in the spec, and state that an unreadable `decimals` means *undetermined*,
not *granted*.

### 10. Gate metadata versioning is by content, not by key

v1 and v3 both arrive as `encryption_metadata`; the version is inferred (v3 carries an epoch). The
dapp's `parseAnyGateMetadata` handles it. Mobile's original port looked for `encryptionMetadataV1` /
`encryptionMetadataV3` keys that do not exist, so **every gate lost its metadata on the way in** — an
unlock could never have succeeded.

**Proposal.** Document the dispatch rule explicitly ("v3 iff `epoch` present"), or add an explicit
`version` field. The current scheme works but is discoverable only by reading the dapp.

---

## P3 — worth doing

### 11. `creator_handle` is underused

It exists, it is read, and it is the only human identity an entity carries. Mobile now credits it in the
feed instead of a hex address. Nothing validates or resolves it, and two publishers can claim the same
handle.

**Proposal.** Note in the spec that handles are unverified and must be presented as self-asserted.
Anything stronger needs a naming layer (ENS resolution would be the obvious one) — worth a separate
proposal.

### 12. The revocation promise does not match any implementation

`haven-web`'s reader path says access "is re-checked against your balance rather than cached forever —
so it follows ownership for as long as you hold the asset, and stops when you do not."

No client does that. The dapp caches gate keys per session; mobile caches keys per session **and** keeps
decrypted content staged on disk until disconnect. Both are deliberate (offline-first), and both mean a
reader who sells the asset keeps whatever they already opened.

**Proposal.** Change the copy, not the clients — the caching is the feature. Say access is re-checked
when online and that already-opened content stays available offline. Currently the website promises
something stricter than the protocol delivers, which is the wrong direction for a claim about access
control.

### 13. Test vectors exist for attestation but not for entities

`haven-dapp/src/lib/__tests__/fixtures/merkle-attest-vector-n*.json` are exactly the right idea. There
is no equivalent for entities, so every surface's parser is tested against fixtures that surface wrote —
which is how mobile's parser could read five non-existent fields and still pass its own tests.

**Proposal.** A `docs/entities/fixtures/` set of canonical entity JSON: one clear, one v1-gated, one
v3-gated, one expired, one with every optional key present, one with only the required keys. Every
surface parses the same six. This would have caught items 1, 2, 3, 4 and 10 on day one, and it is the
cheapest item on this list.

---

## Suggested order

**Decide item 0 first.** It is the only item that changes what the protocol *is*, every publisher-facing
change downstream of it depends on the answer, and the two items that need a publisher change anyway
(`thumbnail_cid`, `size_bytes`) should be decided in the same pass — a thumbnail and a size are both
metadata with a privacy tier, and adding them as public attributes now would be adding to the leak.

Then:

1. **Entity fixtures** (13) — cheap, and it makes every other item verifiable rather than argued. Should
   include a Member-tier example once item 0 is settled.
2. **Metadata privacy tiers** (0) — the epoch-key Member tier for `title` and the descriptive set. Largest
   piece of work here, and the only one that closes a disclosure rather than a bug. Note what it is *not*:
   the gate attributes stay public, because that graph is the discovery layer.
3. **Gateway spec or Kotlin binding** (8) — largest unwritten dependency; blocks mobile being
   trustworthy. Also the natural place to enforce that a gateway cannot serve Member-tier metadata to an
   unauthenticated caller.
4. **Write down "no view events"** (0) — currently true by omission across every surface. One paragraph in
   the spec, and it is the constraint that makes a public co-membership graph defensible.
5. **Document what exists** (3, 4, 7, 9, 10) — pure documentation, no code, unblocks new consumers. Item 4
   becomes "document the gate attributes as public by design" rather than a straight omission fix.
6. **One chain normaliser in `haven-aol`** (5) — deletes three duplicate implementations and a class of
   silent wrong-chain bugs.
7. **`thumbnail_cid` and `size_bytes`** (1, 2) — with a tier each, per item 0.
8. **Recommendation proposal** (0, separate doc) — after the tiers are settled, since they bound what a
   recommendation may say. Needs the index question answered: Arkiv-published aggregates, or a third-party
   index a client can spot-check against chain state.
9. **Copy fix for revocation** (12) — five minutes, and it is a claim about access control. Worth auditing
   the rest of the site's privacy copy against item 0 at the same time, including whether to say plainly
   that reading is never recorded.
10. The rest as they come up.

---

## What mobile already did about these

Reference implementations, tested, ready to lift:

| Item | Where |
|---|---|
| Chain normaliser that fails closed | `core-domain/HavenChain.kt` + `HavenChainTest.kt` |
| Threshold/decimals handling, undetermined ≠ granted | `core-collections/GateAccessChecker.kt` |
| Version-by-content gate metadata parsing | `core-arkiv/ArkivClientImpl.parseGateMetadata` |
| Tolerant entity parsing (nothing required, no invented fields) | `core-arkiv/ArkivClientImpl.toMediaItem` |
| Extension/filename derivation | `core-arkiv/ArkivClientImpl.deriveExtension` |
| MIME→viewer mapping with tests | `core-arkiv/MediaKindResolver.kt` + test |
