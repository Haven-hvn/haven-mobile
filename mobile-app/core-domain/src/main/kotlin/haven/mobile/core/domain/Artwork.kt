package haven.mobile.core.domain

/**
 * Cache slot for one item's extracted cover art. Entities carry no thumbnails (there is no
 * `thumbnail_cid` in practice), so art comes out of the media itself — the MP3's embedded
 * picture, extracted once at staging — and is filed here for every surface that renders the
 * item (player bar, library rows, community rows) to share.
 *
 * The id is sanitised — entity ids have produced path-hostile characters before — so the art
 * can never escape [cacheDir]'s artwork corner. Pure so the mapping stays unit-tested
 * without a device.
 */
fun artworkFileFor(cacheDir: java.io.File, itemId: String): java.io.File {
    val safe = itemId.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }.joinToString("")
    return java.io.File(java.io.File(cacheDir, "haven-artwork"), "$safe.jpg")
}
