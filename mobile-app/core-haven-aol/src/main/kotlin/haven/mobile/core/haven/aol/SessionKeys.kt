package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.domain.MediaItem

/**
 * True when this session already holds the item's key, so opening it needs no
 * signature and no canister call. Checks both key namespaces: per-piece keys
 * cached by the viewer ([AesKeyCache] keyed by piece CID) and the Haven-AOL
 * session cache ([HavenAol.hasCachedKey]). The key gate must skip the
 * availability probe exactly when this is true — and only then.
 */
suspend fun HavenAol.hasSessionKey(item: MediaItem, keys: AesKeyCache): Boolean {
    val cid = item.pieceRef?.pieceCid
    return (cid != null && keys.getSuspend(cid) != null) || hasCachedKey(item)
}
