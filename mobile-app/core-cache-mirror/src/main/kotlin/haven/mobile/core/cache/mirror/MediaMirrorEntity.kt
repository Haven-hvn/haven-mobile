package haven.mobile.core.cache.mirror

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaKind
import kotlinx.datetime.Instant

@Entity(tableName = "media_items")
data class MediaMirrorEntity(
    @PrimaryKey
    val id: String,
    val kind: String,
    val owner: String,
    val title: String,
    val description: String?,
    val mimeType: String?,
    val fileExtension: String?,
    val filenameHint: String?,
    val sizeBytes: Long?,
    val createdAt: String,
    val createdAtBlock: Long?,
    val expiresAtBlock: Long?,
    val pieceCid: String?,
    val pieceSize: Long?,
    val providerServiceUrls: String?,
    val walletAddress: String?,
    val cdnEnabled: Boolean,
    val chain: String?,
    val ipfsIndexed: Boolean,
    val unixFsRoot: String?,
    val trustlessGateways: String?,
    val filecoinCid: String?,
    val encryptedCid: String?,
    val cidHash: String?,
    val gateChain: String?,
    val gateTokenAddress: String?,
    val gateThreshold: Double?,
    val gateTokenStandard: String?,
    val isEncrypted: Boolean,
    val encryptionMetadata: String?,
    val cidEncryptionMetadata: String?,
    val attestation: String?,
    val arkivStatus: String,
    val contentCacheStatus: String,
    val lastAccessedAt: String?,
    // Added in schema v2. Defaulted so the mapper and the fixtures keep compiling; the mirror is a
    // cache, so v1 rows are dropped rather than migrated (see HavenMirrorDatabase).
    val durationSeconds: Long? = null,
    val creatorHandle: String? = null,
)