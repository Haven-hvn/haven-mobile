package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.domain.MediaItem

/**
 * Wallet-scoped AesKeyCache key for the viewer's per-piece key. One formula
 * for the viewer (which stores by piece CID) and this check, prefixed so one
 * identity's key is never visible to another.
 */
fun walletPieceKey(walletAddress: String?, pieceCid: String): String =
    "${walletAddress?.trim()?.lowercase() ?: "none"}:$pieceCid"

/**
 * True when this session already holds the item's key, so opening it needs no
 * signature and no canister call. Checks both key namespaces: per-piece keys
 * cached by the viewer ([AesKeyCache] keyed by wallet + piece CID) and the
 * Haven-AOL session cache ([HavenAol.hasCachedKey]). The key gate must skip
 * the availability probe exactly when this is true — and only then.
 */
suspend fun HavenAol.hasSessionKey(item: MediaItem, keys: AesKeyCache, walletAddress: String?): Boolean {
    val cid = item.pieceRef?.pieceCid
    return (cid != null && keys.getSuspend(walletPieceKey(walletAddress, cid)) != null) || hasCachedKey(item)
}
