package haven.mobile.core.domain

import cloud.filecoin.foc.cache.PieceRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Why a paid key acquisition (wallet signature, Haven-AOL canister call) may
 * proceed for an item — or why it must not.
 */
enum class KeyGateVerdict {
    /** A session key is already held, so no probe ran and no spend is possible. */
    CACHED,

    /** No cached key, but the piece can be served right now; the probe ran. */
    AVAILABLE,

    /**
     * No cached key and the piece cannot be served (or the probe itself failed):
     * acquiring a key would burn spend for content that cannot open. Fail closed.
     */
    UNAVAILABLE,
}

/**
 * Decides, per item in input order, whether a paid key acquisition may proceed.
 *
 * Items with a cached key pass as [KeyGateVerdict.CACHED] without probing, and
 * items with no piece reference pass as [KeyGateVerdict.AVAILABLE] — a key fetched
 * now is still usable once the reference refreshes in, so blocking them would
 * strand keys, not save spend. Everything else is probed concurrently (one slow
 * provider must not stall the rest) via [isAvailable]; a `false` — or a throwing
 * probe — is [KeyGateVerdict.UNAVAILABLE].
 *
 * The verdict is point-in-time only: an AVAILABLE piece can still vanish before
 * the fetch that follows, and that fetch keeps throwing as today.
 */
suspend fun gateKeyAcquisition(
    items: List<MediaItem>,
    isKeyCached: suspend (MediaItem) -> Boolean,
    isAvailable: suspend (PieceRef) -> Boolean,
): List<Pair<MediaItem, KeyGateVerdict>> = supervisorScope {
    items.map { item ->
        async {
            val verdict = try {
                when {
                    isKeyCached(item) -> KeyGateVerdict.CACHED
                    else -> {
                        val ref = item.pieceRef
                        if (ref != null && !isAvailable(ref)) KeyGateVerdict.UNAVAILABLE
                        else KeyGateVerdict.AVAILABLE
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                KeyGateVerdict.UNAVAILABLE
            }
            item to verdict
        }
    }.awaitAll()
}
