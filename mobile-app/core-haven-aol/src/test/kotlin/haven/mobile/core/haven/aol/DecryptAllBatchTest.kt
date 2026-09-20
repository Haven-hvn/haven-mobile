package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.haven.aol.vetkeys.TransportKeypair
import haven.mobile.core.haven.aol.vetkeys.UnwrapParams
import haven.mobile.core.haven.aol.vetkeys.VetKdUnwrap
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * `decryptAll` batch pins: input order is preserved, one item's failure never
 * cancels the rest, and V1 items actually overlap (each still signs and calls
 * once — fan-out, not fewer calls).
 */
class DecryptAllBatchTest {

    private val transportPub = ByteArray(32) { 0x02 }
    private val transportSecret = ByteArray(32) { 0x03 }

    private open class CannedHavenAol(
        session: WalletSession,
        unwrap: VetKdUnwrap,
        private val reply: ByteArray,
    ) : HavenAolImpl(
        HavenAolConfig(canisterId = "gny6k-fqaaa-aaaab-ag3ra-cai", icHost = "https://ic0.app"),
        session,
        AesKeyCache(),
        NonceManager(),
        GateRequestBuilder(),
        unwrap,
    ) {
        override suspend fun callCanister(method: String, candidArg: ByteArray): Result<ByteArray> =
            Result.success(reply)
    }

    private fun cannedOkReply(): ByteArray = dev.ic.kotlin.candid.CandidEncoder.encode(
        listOf(
            dev.ic.kotlin.candid.CandidValue.CandidVariant(
                dev.ic.kotlin.candid.fieldId("ok"),
                dev.ic.kotlin.candid.CandidValue.CandidRecord(
                    mapOf(
                        dev.ic.kotlin.candid.fieldId("encrypted_key") to
                            dev.ic.kotlin.candid.CandidValue.CandidBlob(ByteArray(32) { 0x04 }),
                        dev.ic.kotlin.candid.fieldId("verification_key") to
                            dev.ic.kotlin.candid.CandidValue.CandidBlob(ByteArray(48) { 0x05 }),
                    ),
                ),
            ),
        ),
    )

    /** Unwrap echoes the derivation input, so each cid recovers a distinct 32-byte key. */
    private val echoUnwrap = object : VetKdUnwrap {
        override fun isAvailable(): Boolean = true
        override fun generateTransportKeypair(): Result<TransportKeypair> =
            Result.success(TransportKeypair(transportPub, transportSecret))
        override fun unwrapContentKey(params: UnwrapParams): Result<ByteArray> =
            Result.success(params.derivationInput.copyOf())
    }

    private fun trackingSession(
        current: AtomicInteger,
        maxObserved: AtomicInteger,
        signDelayMs: Long = 100,
    ) = object : WalletSession {
        override val address = MutableStateFlow<String?>("0xabc")
        override val diagnostics = MutableStateFlow<List<String>>(emptyList())
        override val pairingUri = MutableStateFlow<String?>(null)
        override suspend fun connect(): Result<String> = Result.success("0xabc")
        override suspend fun disconnect() = Unit
        override suspend fun signTypedDataV4(json: String, chainId: Long): Result<String> {
            val now = current.incrementAndGet()
            maxObserved.updateAndGet { m -> maxOf(m, now) }
            try {
                delay(signDelayMs)
            } finally {
                current.decrementAndGet()
            }
            return Result.success("0x" + "11".repeat(65))
        }
        override suspend fun sendTransaction(
            to: String,
            data: String,
            chainId: Long,
            valueHex: String,
        ): Result<String> = Result.failure(IllegalStateException("must not be reached"))
    }

    private fun sealedItem(id: String, cid: String) = MediaItem(
        id = id, kind = MediaKind.VIDEO, owner = "0xabc", title = "sealed-$id", description = null,
        mimeType = "video/mp4", fileExtension = ".mp4", filenameHint = null, sizeBytes = null,
        createdAt = Instant.fromEpochMilliseconds(0), createdAtBlock = 100, expiresAtBlock = null,
        pieceRef = null, filecoinCid = null, encryptedCid = null, cidHash = null,
        gate = null,
        isEncrypted = true,
        encryptionMetadata = GateMetadata.Sealed(
            version = 1,
            encryptedAesKey = "U0VBTElORw==",
            cid = cid,
            chain = "EthSepolia",
            tokenAddress = "0xtoken",
            threshold = "1",
        ),
        cidEncryptionMetadata = null,
        attestation = null, arkivStatus = ArkivStatus.FRESH, contentCacheStatus = ContentCacheStatus.UNCACHED,
        lastAccessedAt = null,
    )

    private fun expectedKey(cid: String): ByteArray {
        val preimage = "accessol:EthSepolia:0xtoken:1:$cid".toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.getInstance("SHA-256").digest(preimage)
    }

    @org.junit.Test
    fun `batch preserves order and overlaps signing`() {
        runBlocking {
            val current = AtomicInteger(0)
            val maxObserved = AtomicInteger(0)
            val session = trackingSession(current, maxObserved)
            val impl = CannedHavenAol(session, echoUnwrap, cannedOkReply())
            val cids = listOf("sha256:one", "sha256:two", "sha256:three")
            val items = cids.mapIndexed { i, cid -> sealedItem("item-$i", cid) }

            val results = impl.decryptAll(items, session)

            assertEquals(3, results.size)
            results.forEachIndexed { i, result ->
                assertTrue("item $i should succeed", result.isSuccess)
                assertArrayEquals("item $i key must match its own cid", expectedKey(cids[i]), result.getOrNull())
            }
            // Sequential execution could never overlap; fan-out must be observed.
            assertTrue("signing should overlap, max was ${maxObserved.get()}", maxObserved.get() > 1)
        }
    }

    @org.junit.Test
    fun `one bad item does not cancel the rest`() {
        runBlocking {
            val session = trackingSession(AtomicInteger(0), AtomicInteger(0), signDelayMs = 0)
            val impl = CannedHavenAol(session, echoUnwrap, cannedOkReply())
            val good = sealedItem("good", "sha256:good")
            // Blank seal cid fails closed before signing — the batch must still deliver the good item.
            val bad = sealedItem("bad", "").copy(
                encryptionMetadata = GateMetadata.Sealed(
                    version = 1,
                    encryptedAesKey = "U0VBTElORw==",
                    cid = "",
                    chain = "EthSepolia",
                    tokenAddress = "0xtoken",
                    threshold = "1",
                ),
            )

            val results = impl.decryptAll(listOf(good, bad), session)

            assertEquals(2, results.size)
            assertTrue(results[0].isSuccess)
            assertTrue(results[1].isFailure)
        }
    }

    @org.junit.Test
    fun `empty batch returns empty without signing`() {
        runBlocking {
            var signs = 0
            val session = trackingSession(AtomicInteger(0), AtomicInteger(0), signDelayMs = 0)
            val counting = object : WalletSession by session {
                override suspend fun signTypedDataV4(json: String, chainId: Long): Result<String> {
                    signs++
                    return session.signTypedDataV4(json, chainId)
                }
            }
            val impl = CannedHavenAol(counting, echoUnwrap, cannedOkReply())

            assertTrue(impl.decryptAll(emptyList(), counting).isEmpty())
            assertEquals(0, signs)
        }
    }
}
