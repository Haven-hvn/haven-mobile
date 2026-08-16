/*
 * Superseded by GateAccessChecker.kt.
 *
 * This held a balance reader that was single-chain (one `evm.rpc.ethereum` key), keyed to the bundled
 * roster's `internal GateSpec`, and therefore unusable by the layer that actually needs it — the mirror,
 * which has to intersect Arkiv's stored gate conditions with the wallet's holdings.
 *
 * `GateAccessChecker` replaces it: a public interface over `core-domain.TokenGate`, batched per chain
 * and parallel across the five chains Haven-AOL supports, with per-chain endpoints in `EvmEndpoints`.
 *
 * Safe to delete after review.
 */
package haven.mobile.core.collections
