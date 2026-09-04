package haven.mobile.feature.watch

import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.TokenGate
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Method 4 must surface a notice plus a buy link — never a bare decrypt error.
 * These pin the sheet's pure inputs: V4 detection, token/chain resolution, and
 * the mint.club trade URL shared with the dapp lock screen.
 */
class DripPumpSheetTest {

    @Test
    fun `non-drip items have no pump sheet`() {
        assertNull(item(encryptionMetadata = null).dripPump())
    }

    @Test
    fun `v1 gates have no pump sheet`() {
        val v1 = GateMetadata.V1(wrappedKey = byteArrayOf(1), nonce = "n")
        assertNull(item(encryptionMetadata = v1).dripPump())
    }

    @Test
    fun `v4 resolves token from entity gate attrs`() {
        val v4 = GateMetadata.V4(epochId = 1, marketCapTargetUsd = 5_000_000, wrappedKey = byteArrayOf(1), gateReference = "")
        val gate = TokenGate(chain = "eip155:8453", tokenAddress = "0xabcDEF1234567890abcdef1234567890ABCDEF12", threshold = 1.0)
        val pump = item(encryptionMetadata = v4, gate = gate).dripPump()
        assertNotNull(pump)
        assertEquals(5_000_000, pump!!.targetUsd)
        assertEquals(HavenChain.BASE_MAINNET, pump.chain)
        assertEquals(
            "https://mint.club/token/base/0xabcDEF1234567890abcdef1234567890ABCDEF12",
            pump.tradeUrl,
        )
    }

    @Test
    fun `v4 falls back to gate-json tokenAddress`() {
        val v4 = GateMetadata.V4(
            epochId = 1,
            marketCapTargetUsd = 1_000_000,
            wrappedKey = byteArrayOf(1),
            gateReference = "",
            tokenAddress = "0x1111111111111111111111111111111111111111",
            chain = "BaseMainnet",
        )
        val pump = item(encryptionMetadata = v4, gate = null).dripPump()
        assertNotNull(pump)
        assertEquals("https://mint.club/token/base/0x1111111111111111111111111111111111111111", pump!!.tradeUrl)
    }

    @Test
    fun `v4 without any token still shows the notice, minus the buy link`() {
        val v4 = GateMetadata.V4(epochId = 1, marketCapTargetUsd = 1_000_000, wrappedKey = byteArrayOf(1), gateReference = "")
        val pump = item(encryptionMetadata = v4, gate = null).dripPump()
        assertNotNull(pump)
        assertNull(pump!!.tokenAddress)
        assertNull(pump.tradeUrl)
    }

    @Test
    fun `content gate wins over cid gate`() {
        val content = GateMetadata.V4(epochId = 1, marketCapTargetUsd = 5, wrappedKey = byteArrayOf(1), gateReference = "")
        val cid = GateMetadata.V4(epochId = 2, marketCapTargetUsd = 9, wrappedKey = byteArrayOf(2), gateReference = "")
        assertEquals(content, item(encryptionMetadata = content, cidEncryptionMetadata = cid).dripPumpGate())
    }

    @Test
    fun `mintClubUrl matches the dapp shape`() {
        assertEquals("https://mint.club/token/base/0xabc", mintClubUrl("0xabc", "base"))
        assertEquals("https://mint.club/token/ethereum/0xabc", mintClubUrl("0xabc", "ethereum"))
        assertNull(mintClubUrl("  ", "base"))
    }

    @Test
    fun `shortAddress keeps short tokens intact`() {
        assertEquals("0xabc", shortAddress("0xabc"))
        assertEquals("0x123456…abcdef", shortAddress("0x1234567890abcdef"))
    }

    @Test
    fun `formatUsdCompact mirrors the dapp`() {
        assertEquals("$1.5M", formatUsdCompact(1_500_000))
        assertEquals("$5M", formatUsdCompact(5_000_000))
        assertEquals("$800K", formatUsdCompact(800_000))
        assertEquals("$950", formatUsdCompact(950))
        assertEquals("$2.5B", formatUsdCompact(2_500_000_000))
    }

    private fun item(
        encryptionMetadata: GateMetadata? = null,
        cidEncryptionMetadata: GateMetadata? = null,
        gate: TokenGate? = null,
    ) = MediaItem(
        id = "1",
        kind = MediaKind.VIDEO,
        owner = "0xabc",
        title = "Premiere",
        description = null,
        mimeType = "video/mp4",
        fileExtension = ".mp4",
        filenameHint = "premiere.mp4",
        sizeBytes = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        createdAtBlock = null,
        expiresAtBlock = null,
        pieceRef = null,
        filecoinCid = null,
        encryptedCid = null,
        cidHash = null,
        gate = gate,
        isEncrypted = true,
        encryptionMetadata = encryptionMetadata,
        cidEncryptionMetadata = cidEncryptionMetadata,
        attestation = null,
        arkivStatus = ArkivStatus.FRESH,
        contentCacheStatus = ContentCacheStatus.UNCACHED,
        lastAccessedAt = null,
    )
}
