package haven.mobile.core.haven.aol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * Epoch-key persistence codec + store semantics: encode/decode round-trips
 * without a device, and a fresh holder sharing one store (how a restart is
 * simulated) sees the same unlocks without re-signing.
 */
class EpochKeyPersistenceTest {

    private fun key(
        wallet: String = "0x00000000000000000000000000000000000000ab",
        epoch: Long = 680,
        target: Long? = null,
    ) = EpochGateKey(
        protocol = if (target == null) 3L else 4L,
        chainVariant = "EthSepolia",
        tokenAddress = "0xAbCdEf0000000000000000000000000000000001",
        threshold = BigInteger.ONE,
        epoch = BigInteger.valueOf(epoch),
        marketCapTarget = target?.let { BigInteger.valueOf(it) },
        walletLower = wallet.lowercase(),
    )

    private fun data(seed: Int = 1) = EpochUnlockData(
        encryptedVetKey = ByteArray(192) { (it + seed).toByte() },
        transportSecret = ByteArray(32) { (it + seed + 1).toByte() },
        verificationKey = ByteArray(96) { (it + seed + 2).toByte() },
    )

    @Test
    fun `v3 and v4 encodings round-trip`() {
        val v3 = mapOf(key(epoch = 680) to data(1))
        val decodedV3 = KeystoreEpochStore.decodeEntries(key().walletLower, KeystoreEpochStore.encodeEntries(v3))
        assertEquals(v3.keys, decodedV3.keys)
        assertArrayEquals(v3.values.first().encryptedVetKey, decodedV3.values.first().encryptedVetKey)
        assertArrayEquals(v3.values.first().transportSecret, decodedV3.values.first().transportSecret)
        assertArrayEquals(v3.values.first().verificationKey, decodedV3.values.first().verificationKey)

        val v4 = mapOf(key(epoch = 691, target = 100) to data(9))
        val decodedV4 = KeystoreEpochStore.decodeEntries(key().walletLower, KeystoreEpochStore.encodeEntries(v4))
        assertEquals(v4.keys, decodedV4.keys)
        assertEquals(BigInteger.valueOf(100), decodedV4.keys.first().marketCapTarget)
    }

    @Test
    fun `malformed entries are skipped, not fatal`() {
        assertEquals(emptyMap<EpochGateKey, EpochUnlockData>(), KeystoreEpochStore.decodeEntries("0xw", "[]".toByteArray()))
        assertEquals(emptyMap<EpochGateKey, EpochUnlockData>(), KeystoreEpochStore.decodeEntries("0xw", "[{}".toByteArray()))
    }

    @Test
    fun `store keeps wallets isolated and clearFor wipes only one`() {
        val store = InMemoryEpochKeyStore()
        kotlinx.coroutines.runBlocking {
            store.saveFor("0xAAA", mapOf(key(wallet = "0xaaa") to data(1)))
            store.saveFor("0xBBB", mapOf(key(wallet = "0xbbb") to data(2)))
            assertTrue(store.loadFor("0xaaa").isNotEmpty())
            assertTrue(store.loadFor("0xBBB").keys.first().walletLower == "0xbbb")

            store.clearFor("0xaaa")
            assertTrue(store.loadFor("0xaaa").isEmpty())
            assertTrue(store.loadFor("0xbbb").isNotEmpty())
        }
    }

    @Test
    fun `a fresh holder sharing the store needs no new signature`() = kotlinx.coroutines.runBlocking {
        val store = InMemoryEpochKeyStore()
        val session1 = MutableSession()
        val first = RestartRecorder(session1, store)
        first.reply = okReply()
        val item = v3Item("a")
        assertTrue(first.decrypt(item, session1).isSuccess)
        assertEquals(1, session1.signed.size)

        // Simulate process restart: brand-new holder, same store, same wallet.
        val session2 = MutableSession()
        val afterRestart = RestartRecorder(session2, store)
        afterRestart.reply = okReply()
        assertTrue(afterRestart.hasCachedKey(item))
        assertTrue(afterRestart.decrypt(item, session2).isSuccess)
        assertEquals("no re-sign after restart", 0, session2.signed.size)
        assertEquals("no canister call after restart", 0, afterRestart.calls.size)

        // Disconnect wipes memory AND disk: fresh holder must sign again.
        afterRestart.clearFor(session2.address.value!!)
        assertTrue(store.loadFor(session2.address.value!!).isEmpty())
        val session3 = MutableSession()
        val afterDisconnect = RestartRecorder(session3, store)
        afterDisconnect.reply = okReply()
        assertTrue(afterDisconnect.decrypt(item, session3).isSuccess)
        assertEquals(1, session3.signed.size)
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

    private class RestartRecorder(session: MutableSession, store: EpochKeyStore) : HavenAolImpl(
        HavenAolConfig(canisterId = "gny6k-fqaaa-aaaab-ag3ra-cai", icHost = "https://ic0.app"),
        session,
        haven.mobile.core.crypto.AesKeyCache(),
        NonceManager(),
        GateRequestBuilder(),
        FakeUnwrap(),
        store,
    ) {
        var reply: ByteArray = ByteArray(0)
        val calls = mutableListOf<Pair<String, ByteArray>>()
        override suspend fun callCanister(method: String, candidArg: ByteArray): Result<ByteArray> {
            calls += method to candidArg
            return Result.success(reply)
        }
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

    private class MutableSession : haven.mobile.core.wallet.WalletSession {
        val signed = mutableListOf<Pair<String, Long>>()
        override val address = kotlinx.coroutines.flow.MutableStateFlow<String?>("0x00000000000000000000000000000000000000ab")
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
