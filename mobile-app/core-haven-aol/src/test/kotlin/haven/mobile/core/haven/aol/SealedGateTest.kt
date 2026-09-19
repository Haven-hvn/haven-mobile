package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A sealed (VetKD) content key needs a device unwrap this build does not have yet, so
 * decrypt fails closed on [GateMetadata.Sealed] — before any signing prompt and without
 * touching the canister. The wallet must never be asked to sign for an unlock that cannot
 * complete.
 */
class SealedGateTest {

    @org.junit.Test
    fun `sealed content gate fails closed before signing`() {
        runBlocking {
            sealedContentGateFailsClosed()
        }
    }

    private suspend fun sealedContentGateFailsClosed() {
        val signingAsked = AtomicBoolean(false)
        val session = fakeSession(signingAsked)
        val impl = HavenAolImpl(
            HavenAolConfig(canisterId = "gny6k-fqaaa-aaaab-ag3ra-cai", icHost = "https://ic0.app"),
            session,
            AesKeyCache(),
            NonceManager(),
            GateRequestBuilder(),
        )

        val result = impl.decrypt(sealedItem(), session)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("expected UnsupportedGateMetadata, got $error", error is HavenError.UnsupportedGateMetadata)
        assertTrue("message must name the seal: ${error?.message}", error?.message?.contains("sealed") == true)
        assertTrue("message must name the version: ${error?.message}", error?.message?.contains("v1") == true)
        assertFalse("wallet must not be asked to sign", signingAsked.get())
    }

    private fun sealedItem() = MediaItem(
        id = "0xabc", kind = MediaKind.VIDEO, owner = "0xabc", title = "sealed", description = null,
        mimeType = "video/mp4", fileExtension = ".mp4", filenameHint = null, sizeBytes = null,
        createdAt = Instant.fromEpochMilliseconds(0), createdAtBlock = 100, expiresAtBlock = null,
        pieceRef = null, filecoinCid = null, encryptedCid = null, cidHash = null,
        gate = TokenGate(
            chain = "eip155:11155111",
            tokenAddress = "0xtoken",
            threshold = 1.0,
            tokenStandard = TokenStandard.ERC20,
        ),
        isEncrypted = true,
        encryptionMetadata = GateMetadata.Sealed(version = 1, encryptedAesKey = "SEALEDKEY"),
        cidEncryptionMetadata = null,
        attestation = null, arkivStatus = ArkivStatus.FRESH, contentCacheStatus = ContentCacheStatus.UNCACHED,
        lastAccessedAt = null,
    )

    private fun fakeSession(signingAsked: AtomicBoolean) = object : WalletSession {
        override val address = MutableStateFlow<String?>("0xabc")
        override val diagnostics = MutableStateFlow<List<String>>(emptyList())
        override val pairingUri = MutableStateFlow<String?>(null)
        override suspend fun connect(): Result<String> = Result.success("0xabc")
        override suspend fun disconnect() = Unit
        override suspend fun signTypedDataV4(json: String, chainId: Long): Result<String> {
            signingAsked.set(true)
            return Result.failure(IllegalStateException("must not be reached"))
        }
        override suspend fun sendTransaction(to: String, data: String, chainId: Long, valueHex: String): Result<String> =
            Result.failure(IllegalStateException("must not be reached"))
    }
}
