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
        // Field-wise: ByteArray has no value equality, so data-class equals would fail on
        // identical bytes. The codec round-trips; only the comparison must be content-based.
        val v1 = parseGateMetadata(
            jsonFromGateMetadata(GateMetadata.V1(wrappedKey = "key".toByteArray(Charsets.UTF_8), nonce = "n")),
        ) as? GateMetadata.V1 ?: throw AssertionError("V1 mistyped")
        assertTrue("key".toByteArray(Charsets.UTF_8).contentEquals(v1.wrappedKey))
        assertEquals("n", v1.nonce)

        val v3 = parseGateMetadata(
            jsonFromGateMetadata(GateMetadata.V3(epochId = 7, wrappedKey = "key".toByteArray(Charsets.UTF_8), gateReference = "g")),
        ) as? GateMetadata.V3 ?: throw AssertionError("V3 mistyped")
        assertEquals(7L, v3.epochId)
        assertTrue("key".toByteArray(Charsets.UTF_8).contentEquals(v3.wrappedKey))
        assertEquals("g", v3.gateReference)

        val v4 = parseGateMetadata(
            jsonFromGateMetadata(
                GateMetadata.V4(
                    epochId = 7,
                    marketCapTargetUsd = 1000,
                    wrappedKey = "key".toByteArray(Charsets.UTF_8),
                    gateReference = "g",
                    tokenAddress = "0xtoken",
                    chain = "eip155:8453",
                ),
            ),
        ) as? GateMetadata.V4 ?: throw AssertionError("V4 mistyped")
        assertEquals(7L, v4.epochId)
        assertEquals(1000L, v4.marketCapTargetUsd)
        assertTrue("key".toByteArray(Charsets.UTF_8).contentEquals(v4.wrappedKey))
        assertEquals("g", v4.gateReference)
        assertEquals("0xtoken", v4.tokenAddress)
        assertEquals("eip155:8453", v4.chain)
    }

    @Test
    fun `unknown type tag degrades to V1`() {
        val parsed = parseGateMetadata("""{"type":"Nope","wrappedKey":"k","nonce":"n"}""")

        assertTrue(parsed is GateMetadata.V1, "expected V1 fallback, got $parsed")
    }
}
