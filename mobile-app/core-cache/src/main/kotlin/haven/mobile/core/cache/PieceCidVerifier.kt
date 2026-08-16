/*
 * PieceCID verification was removed from the mobile client.
 *
 * Requirement FR-CACHE-5 originally promoted a local CommP check to mandatory for every fetched
 * piece. That is redundant here: `foc-cache` proves possession and integrity via PDP against the
 * storage providers before returning bytes, so a second check in the app duplicates a guarantee
 * the layer below already holds — and it can only be weaker, because the app does not have the
 * proofs.
 *
 * What used to live in this file:
 *   - `fun interface PieceCidVerifier { suspend fun verify(pieceCid: String, bytes: ByteArray) }`
 *   - a "CommP" implementation that in practice only checked that the CID string started with
 *     `baga`/`baf` and was longer than ten characters, which would pass any corrupted buffer.
 *
 * Nothing references it now (see `HavenCacheImpl.get`, `CacheDiModule`). This file is left as the
 * record of the decision and can be deleted once the change is reviewed.
 *
 * Related: `MOBILE_V1_REQUIREMENTS.md` FR-CACHE-5, `planning/mobile-v1-tasking/sprint-2-…/2.6`.
 */
package haven.mobile.core.cache
