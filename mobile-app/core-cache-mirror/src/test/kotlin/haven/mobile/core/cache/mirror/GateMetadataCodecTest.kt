package haven.mobile.core.cache.mirror

import haven.mobile.core.domain.GateMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The gate codec is the mirror's memory of what an item is sealed with: whatever the parser
 * recognized must survive a write/read cycle unchanged, or a sealed item reloads as something
 * else. These pin the round trip per variant, plus that unknown tags still degrade to V1 so
 * rows written by older builds keep parsing.
 */
class GateMetadataCodecTest {

    @Test
    fun `sealed round-trips`() {
        val sealed = GateMetadata.Sealed(version = 1, encryptedAesKey = "SEALEDKEY")

        assertEquals(sealed, parseGateMetadata(jsonFromGateMetadata(sealed)))
    }

    @Test
    fun `legacy variants round-trip`() {
        val v1 = GateMetadata.V1(wrappedKey = "key".toByteArray(Charsets.UTF_8), nonce = "n")
        val v3 = GateMetadata.V3(epochId = 7, wrappedKey = "key".toByteArray(Charsets.UTF_8), gateReference = "g")
        val v4 = GateMetadata.V4(
            epochId = 7,
            marketCapTargetUsd = 1000,
            wrappedKey = "key".toByteArray(Charsets.UTF_8),
            gateReference = "g",
            tokenAddress = "0xtoken",
            chain = "eip155:8453",
        )

        assertEquals(v1, parseGateMetadata(jsonFromGateMetadata(v1)))
        assertEquals(v3, parseGateMetadata(jsonFromGateMetadata(v3)))
        assertEquals(v4, parseGateMetadata(jsonFromGateMetadata(v4)))
    }

    @Test
    fun `unknown type tag degrades to V1`() {
        val parsed = parseGateMetadata("""{"type":"Nope","wrappedKey":"k","nonce":"n"}""")

        assertTrue(parsed is GateMetadata.V1, "expected V1 fallback, got $parsed")
    }
}
