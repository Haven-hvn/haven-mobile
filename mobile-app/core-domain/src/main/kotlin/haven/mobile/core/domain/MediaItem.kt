package haven.mobile.core.domain

import cloud.filecoin.foc.cache.PieceRef
import kotlinx.datetime.Instant

/**
 * One item in an archive.
 *
 * Generalised from `haven-dapp`'s `Video` (requirements §4): same fields, plus a `kind` discriminator so
 * the viewer can dispatch. **`haven-dapp` is the spec** — specifically `lib/parse-arkiv-video.ts`, which
 * is the only code that turns a real Arkiv entity into a domain object. If a field is not read there, it
 * does not exist in practice, whatever a document says it could contain.
 *
 * Two fields were removed again after checking against it:
 *  - `thumbnailCid` — `MEDIA_CONTENT_SPEC.md` lists `thumbnail_cid` as an optional payload key, and the
 *    dapp never reads it. Nothing writes it either, so it would have been a permanently null column.
 *  - a size read — no entity carries one. [PieceRef.size] is the size of record, filled in when foc
 *    resolves the piece, which is why [sizeBytes] is nullable and often null until then.
 */
data class MediaItem(
    val id: String,
    val kind: MediaKind,
    /** Publishing wallet, lowercased — `entity.owner`. */
    val owner: String,
    val title: String,
    val description: String?,
    /** `content_mime_type`. */
    val mimeType: String?,
    /** Derived from the MIME type or source URI; entities carry no filename. */
    val fileExtension: String?,
    val filenameHint: String?,
    /** From `PieceRef.size` once foc has resolved the piece; null before that. */
    val sizeBytes: Long?,
    val createdAt: Instant,
    val createdAtBlock: Long?,
    val expiresAtBlock: Long?,
    val pieceRef: PieceRef?,
    val filecoinCid: String?,
    val encryptedCid: String?,
    val cidHash: String?,
    val gate: TokenGate?,
    val isEncrypted: Boolean,
    val encryptionMetadata: GateMetadata?,
    val cidEncryptionMetadata: GateMetadata?,
    val attestation: Attestation?,
    val arkivStatus: ArkivStatus,
    val contentCacheStatus: ContentCacheStatus,
    val lastAccessedAt: Instant?,

    /**
     * Runtime in seconds. `duration` is both an attribute and a payload key, and the dapp reads it —
     * this model used to drop it, so a media library could not say how long anything was.
     */
    val durationSeconds: Long? = null,

    /**
     * The publisher's own name for themselves (`creator_handle`).
     *
     * Read by the dapp and worth having here: the feed can credit "moth.eth" instead of a hex address,
     * which is both friendlier and the only human identity an entity carries.
     */
    val creatorHandle: String? = null,
)
