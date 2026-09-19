package haven.mobile.core.haven.aol

import org.json.JSONObject
import org.junit.Assert.*

/**
 * The v1 `GateRequest` typed data must be byte-equivalent to the dapp's
 * `buildGateRequestTypedData` — the canister verifies against pinned typehashes, so any drift
 * (a `version` domain field, an `itemId` message, an unquoted 256-bit nonce) signs a digest
 * the canister rejects. These pin the canonical shape field by field.
 */
class GateRequestBuilderTest {

    private val builder = GateRequestBuilder()

    private fun built() = JSONObject(
        builder.buildV1Request(
            evmAddress = "0xabc",
            transportPublicKeyHex = "0x" + "ab".repeat(48),
            nonceDecimal = "12345678901234567890",
        ),
    )

    @org.junit.Test
    fun `domain is HavenAOL without a version field`() {
        val domain = built().getJSONObject("domain")

        assertEquals("HavenAOL", domain.getString("name"))
        assertEquals(1, domain.getLong("chainId"))
        assertEquals("0x0000000000000000000000000000000000000000", domain.getString("verifyingContract"))
        assertTrue("version field breaks the pinned domain typehash", !domain.has("version"))
    }

    @org.junit.Test
    fun `message binds wallet transport key and nonce`() {
        val message = built().getJSONObject("message")

        assertEquals("GateRequest", built().getString("primaryType"))
        assertEquals("0xabc", message.getString("evmAddress"))
        assertEquals("0x" + "ab".repeat(48), message.getString("transportPublicKey"))
        // Quoted decimal: a 256-bit nonce loses precision as a JSON number.
        assertEquals("12345678901234567890", message.getString("nonce"))
    }

    @org.junit.Test
    fun `type table matches the canister typehashes`() {
        val types = built().getJSONObject("types")
        val domainFields = types.getJSONArray("EIP712Domain")
        val requestFields = types.getJSONArray("GateRequest")

        assertEquals(3, domainFields.length())
        assertEquals("name", domainFields.getJSONObject(0).getString("name"))
        assertEquals("chainId", domainFields.getJSONObject(1).getString("name"))
        assertEquals("verifyingContract", domainFields.getJSONObject(2).getString("name"))
        assertEquals(3, requestFields.length())
        assertEquals("evmAddress", requestFields.getJSONObject(0).getString("name"))
        assertEquals("address", requestFields.getJSONObject(0).getString("type"))
        assertEquals("transportPublicKey", requestFields.getJSONObject(1).getString("name"))
        assertEquals("bytes", requestFields.getJSONObject(1).getString("type"))
        assertEquals("nonce", requestFields.getJSONObject(2).getString("name"))
        assertEquals("uint256", requestFields.getJSONObject(2).getString("type"))
    }

    @org.junit.Test
    fun `domain constants match the dapp deployment defaults`() {
        assertEquals(1L, GateRequestBuilder.EIP712_CHAIN_ID)
        assertEquals(
            "0x0000000000000000000000000000000000000000",
            GateRequestBuilder.EIP712_VERIFYING_CONTRACT,
        )
    }
}
