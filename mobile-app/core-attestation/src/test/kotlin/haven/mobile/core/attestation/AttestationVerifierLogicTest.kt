package haven.mobile.core.attestation

import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.MerkleProofStep
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard
import haven.mobile.core.haven.aol.HavenAol
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Dapp-parity attestation checks (see `haven-dapp` `lib/attestation` + `types/attestation`).
 *
 * Crypto vectors were produced independently with `@noble/curves` + `@noble/hashes` — the
 * same libraries the dapp verifies with — so a passing suite means this port agrees with an
 * outside implementation, not just with itself. The test key is a fixture, never a real
 * canister key. The preimage strings pin byte-exactness against the canister form.
 */
class AttestationVerifierLogicTest {

    private val pubKey = decodeEd25519PublicKey(hexToBytesOrNull(PUBKEY_HEX)!!)

    private fun single(): Attestation.Single = Attestation.Single(
        evmAddress = EVM,
        chain = CHAIN,
        tokenAddress = TOKEN,
        threshold = 75.0,
        balanceAtCheck = 120.0,
        cidHash = CID_HASH,
        timestamp = STAMP,
        signature = SINGLE_SIG,
    )

    private fun merkle(): Attestation.Merkle = Attestation.Merkle(
        evmAddress = EVM,
        chain = CHAIN,
        tokenAddress = TOKEN,
        threshold = 75.0,
        balanceAtCheck = 120.0,
        cidHash = LEAF_A_CID_HASH,
        timestamp = STAMP,
        cidCount = 2,
        merkleProof = listOf(MerkleProofStep(side = "right", hash = LEAF_B_HASH)),
        merkleRoot = MERKLE_ROOT,
        rootSignature = ROOT_SIG,
    )

    private fun item() = MediaItem(
        id = "e1",
        kind = MediaKind.VIDEO,
        owner = "0xbeef",
        title = "t",
        description = null,
        mimeType = "video/mp4",
        fileExtension = ".mp4",
        filenameHint = "t.mp4",
        sizeBytes = null,
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        createdAtBlock = null,
        expiresAtBlock = null,
        pieceRef = null,
        filecoinCid = null,
        encryptedCid = null,
        cidHash = CID_HASH,
        gate = TokenGate(chain = "eip155:8453", tokenAddress = TOKEN, threshold = 75.0, tokenStandard = TokenStandard.ERC20),
        isEncrypted = true,
        encryptionMetadata = null,
        cidEncryptionMetadata = null,
        attestation = null,
        arkivStatus = ArkivStatus.FRESH,
        contentCacheStatus = ContentCacheStatus.UNCACHED,
        lastAccessedAt = null,
        creatorAddress = EVM,
    )

    @Test
    fun `single preimage is byte-exact`() {
        assertEquals(SINGLE_PREIMAGE, encodeSinglePreimage(single()).toString(Charsets.UTF_8))
    }

    @Test
    fun `batch preimage is byte-exact`() {
        assertEquals(BATCH_PREIMAGE, encodeBatchPreimage(merkle()).toString(Charsets.UTF_8))
    }

    @Test
    fun `valid single verifies`() {
        assertTrue(verifySingleOffline(single(), pubKey, NOW))
    }

    @Test
    fun `tampered single signature fails`() {
        val bad = single().copy(signature = SINGLE_SIG.dropLast(1) + if (SINGLE_SIG.last() == '0') '1' else '0')
        assertFalse(verifySingleOffline(bad, pubKey, NOW))
    }

    @Test
    fun `expired single fails, boundary passes`() {
        assertFalse(verifySingleOffline(single(), pubKey, STAMP + ATTESTATION_TTL_SECONDS + 1))
        assertTrue(verifySingleOffline(single(), pubKey, STAMP + ATTESTATION_TTL_SECONDS))
    }

    @Test
    fun `valid merkle verifies`() {
        assertTrue(verifyMerkleOffline(merkle(), pubKey, NOW))
    }

    @Test
    fun `merkle wrong side fails`() {
        val bad = merkle().copy(merkleProof = listOf(MerkleProofStep(side = "left", hash = LEAF_B_HASH)))
        assertFalse(verifyMerkleOffline(bad, pubKey, NOW))
    }

    @Test
    fun `merkle wrong root fails`() {
        val bad = merkle().copy(merkleRoot = "00".repeat(32))
        assertFalse(verifyMerkleOffline(bad, pubKey, NOW))
    }

    @Test
    fun `merkle malformed proof hash fails`() {
        val bad = merkle().copy(merkleProof = listOf(MerkleProofStep(side = "right", hash = "zz")))
        assertFalse(verifyMerkleOffline(bad, pubKey, NOW))
    }

    @Test
    fun `binding matches coherent item`() {
        assertTrue(attestationMatchesEntity(single(), item()))
    }

    @Test
    fun `binding rejects every mismatch`() {
        val att = single()
        val base = item()
        assertFalse(attestationMatchesEntity(att.copy(evmAddress = "0x0000000000000000000000000000000000000001"), base))
        assertFalse(attestationMatchesEntity(att, base.copy(creatorAddress = null)))
        assertFalse(attestationMatchesEntity(att.copy(tokenAddress = "0x0000000000000000000000000000000000000001"), base))
        assertFalse(attestationMatchesEntity(att.copy(chain = "EthMainnet"), base))
        assertFalse(attestationMatchesEntity(att.copy(threshold = 76.0), base))
        assertFalse(attestationMatchesEntity(att.copy(cidHash = "bb".repeat(32)), base))
        assertFalse(attestationMatchesEntity(att, base.copy(cidHash = null)))
        assertFalse(attestationMatchesEntity(att, base.copy(gate = null)))
    }

    @Test
    fun `verify composes key fetch, signature, and binding`() {
        val havenAol = mockk<HavenAol>()
        coEvery { havenAol.attestationPublicKey() } returns Result.success(hexToBytesOrNull(PUBKEY_HEX)!!)
        val verifier = AttestationVerifierImpl(havenAol)
        runBlocking {
            assertTrue(verifier.verify(single(), item(), NOW).isSuccess)
            assertTrue(verifier.verify(single(), item().copy(cidHash = "bb".repeat(32)), NOW).isFailure)
        }
    }

    @Test
    fun `verify fails closed when the key fetch fails`() {
        val havenAol = mockk<HavenAol>()
        coEvery { havenAol.attestationPublicKey() } returns Result.failure(RuntimeException("down"))
        val verifier = AttestationVerifierImpl(havenAol)
        runBlocking {
            assertTrue(verifier.verify(single(), item(), NOW).isFailure)
        }
    }

    private companion object {
        const val EVM = "0x5C32469325d4093aB142DDdC1305F91d76e45141"
        const val CHAIN = "BaseMainnet"
        const val TOKEN = "0xabcDEF1234567890abcdef1234567890ABCDEF12"
        const val CID_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val LEAF_A_CID_HASH = "1111111111111111111111111111111111111111111111111111111111111111"
        const val LEAF_B_HASH = "afc5d61084e793ef27cfe099d020fd8ed51e16aade479d6ff7a891d76149cc11"
        const val MERKLE_ROOT = "3027e1b9b7805f562f9bfaa4abaf9d5a9987b36828d0eca7049bd2f3595b3d8a"
        const val STAMP = 1781760000L
        const val NOW = STAMP + 100
        const val PUBKEY_HEX = "bd167e83746f3dc7b763adbf114b7e5f91c56e9c958e7a4aa0ea8cc73a0fbc2a"
        const val SINGLE_SIG = "873f55d81aae0e6bd86c36b14f558d8ca063e3f8867e49b179dc576b34805534c93b7d93970098b44751e8f7fff4b5814ff0bc092d0c86c4ebf3a9ed520e000a"
        const val ROOT_SIG = "c9b781ada8686ca8f65887fa59428d21ad41662641c6cdd8f77dc4389caad104de2254270ad4021d0cb38f429f6f6315373cdcb62f682ac2be2b74cc911d5b08"
        const val SINGLE_PREIMAGE = "HAVEN_ATTEST_V1:BaseMainnet:0xabcDEF1234567890abcdef1234567890ABCDEF12:75:0x5C32469325d4093aB142DDdC1305F91d76e45141:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa:1781760000:120"
        const val BATCH_PREIMAGE = "HAVEN_BATCH_ATTEST_V1:BaseMainnet:0xabcDEF1234567890abcdef1234567890ABCDEF12:75:0x5C32469325d4093aB142DDdC1305F91d76e45141:3027e1b9b7805f562f9bfaa4abaf9d5a9987b36828d0eca7049bd2f3595b3d8a:2:1781760000:120"
    }
}
