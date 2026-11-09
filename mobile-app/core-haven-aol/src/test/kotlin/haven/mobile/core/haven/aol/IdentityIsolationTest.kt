package haven.mobile.core.haven.aol

import haven.mobile.core.crypto.AesKeyCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-identity isolation at the unlock layer: a key one wallet paid a
 * signature for must not open content for another wallet, and clearing one
 * wallet must not evict another's unlocks (switching clears nothing).
 */
class IdentityIsolationTest {

    private val walletA = "0x00000000000000000000000000000000000000ab"
    private val walletB = "0x00000000000000000000000000000000000000cd"

    @Test
    fun `unlock for one wallet is invisible to another, and clearFor is scoped`() =
        kotlinx.coroutines.runBlocking {
            val store = InMemoryEpochKeyStore()
            val aes = AesKeyCache()
            val session = MutableSession(walletA)
            val aol = Recorder(session, store, aes)
            aol.reply = okReply()
            val item = v3Item("a")

            assertTrue(aol.decrypt(item, session).isSuccess)
            assertEquals(1, session.signed.size)
            assertTrue("A holds its unlock", aol.hasCachedKey(item))

            // Switch to B: same item, same device, no key visible, signing required again.
            session.address.value = walletB
            assertFalse("B must not reuse A's key", aol.hasCachedKey(item))

            // Clearing B (e.g. B disconnects) must not evict A.
            aol.clearFor(walletB)
            session.address.value = walletA
            assertTrue("A's unlock survives B's clear", aol.hasCachedKey(item))
            assertTrue(store.loadFor(walletA).isNotEmpty())

            // Clearing A wipes memory and disk for A only.
            aol.clearFor(walletA)
            assertFalse(aol.hasCachedKey(item))
            assertTrue(store.loadFor(walletA).isEmpty())
        }

    private fun okReply(): ByteArray = dev.ic.kotlin.candid.CandidEncoder.encode(
        listOf(
            dev.ic.kotlin.candid.CandidValue.CandidVariant(
                dev.ic.kotlin.candid.fieldId("ok"),
                dev.ic.kotlin.candid.CandidValue.CandidRecord(
                    mapOf(
                        dev.ic.kotlin.candid.fieldId("encrypted_key") to
                            dev.ic.kotlin.candid.CandidValue.CandidBlob(ByteArray(192) { (it + 3).toByte() }),
                        dev.ic.kotlin.candid.fieldId("verification_key") to
                            dev.ic.kotlin.candid.CandidValue.CandidBlob(ByteArray(96) { (it + 4).toByte() }),
                    ),
                ),
            ),
        ),
    )

    private class Recorder(session: MutableSession, store: EpochKeyStore, aes: AesKeyCache) : HavenAolImpl(
        HavenAolConfig(canisterId = "gny6k-fqaaa-aaaab-ag3ra-cai", icHost = "https://ic0.app"),
        session,
        aes,
        NonceManager(),
        GateRequestBuilder(),
        FakeUnwrap(),
        store,
    ) {
        var reply: ByteArray = ByteArray(0)
        override suspend fun callCanister(method: String, candidArg: ByteArray): Result<ByteArray> =
            Result.success(reply)
    }

    private class FakeUnwrap : haven.mobile.core.haven.aol.vetkeys.VetKdUnwrap {
        override fun isAvailable() = true
        override fun generateTransportKeypair(): Result<haven.mobile.core.haven.aol.vetkeys.TransportKeypair> =
            Result.success(haven.mobile.core.haven.aol.vetkeys.TransportKeypair(ByteArray(48) { (it + 1).toByte() }, ByteArray(32) { 0x07 }))
        override fun unwrapContentKey(params: haven.mobile.core.haven.aol.vetkeys.UnwrapParams): Result<ByteArray> {
            params.transportSecret.fill(0)
            return Result.success(java.security.MessageDigest.getInstance("SHA-256").digest(params.sealedKeyUtf8))
        }
    }

    private class MutableSession(initial: String) : haven.mobile.core.wallet.WalletSession {
        val signed = mutableListOf<Pair<String, Long>>()
        override val address = kotlinx.coroutines.flow.MutableStateFlow<String?>(initial)
        override val diagnostics = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
        override val pairingUri = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        override suspend fun connect(): Result<String> = Result.success(address.value!!)
        override suspend fun disconnect() = Unit
        override suspend fun signTypedDataV4(json: String, chainId: Long): Result<String> {
            signed += json to chainId
            return Result.success("0x" + "11".repeat(64) + "1b")
        }
        override suspend fun sendTransaction(to: String, data: String, chainId: Long, valueHex: String): Result<String> =
            Result.failure(IllegalStateException("must not be reached"))
    }

    private fun v3Item(id: String) = haven.mobile.core.domain.MediaItem(
        id = id, kind = haven.mobile.core.domain.MediaKind.AUDIO, owner = "0xowner", title = id, description = null,
        mimeType = "audio/mpeg", fileExtension = ".mp3", filenameHint = null, sizeBytes = null,
        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0), createdAtBlock = 123, expiresAtBlock = null,
        pieceRef = null, filecoinCid = null, encryptedCid = null, cidHash = null,
        gate = null, isEncrypted = true,
        encryptionMetadata = haven.mobile.core.domain.GateMetadata.Sealed(
            version = 3, encryptedAesKey = "SEALED-$id", cid = "bafk-$id",
            chain = "EthSepolia", tokenAddress = "0xAbCdEf0000000000000000000000000000000001",
            threshold = "1", epoch = 680,
        ),
        cidEncryptionMetadata = null, attestation = null,
        arkivStatus = haven.mobile.core.domain.ArkivStatus.FRESH,
        contentCacheStatus = haven.mobile.core.domain.ContentCacheStatus.UNCACHED,
        lastAccessedAt = null,
    )
}
