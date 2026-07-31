package haven.mobile.core.domain

import cloud.filecoin.foc.cache.PieceRef
import kotlinx.datetime.Instant

// Mirrors haven-dapp-main/src/types/video.ts::Video (generalized to all media kinds)
data class MediaItem(
    val id: String,
    val kind: MediaKind,
    val owner: String,
    val title: String,
    val description: String?,
    val mimeType: String?,
    val fileExtension: String?,
    val filenameHint: String?,
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
    val lastAccessedAt: Instant?
)