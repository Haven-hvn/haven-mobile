package haven.mobile.core.wallet

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * eth_signTypedData_v4 params MUST be [address, typedDataObject].
 * A stringified second element (escaped JSON string) is rejected by strict wallets:
 * Trust Wallet cannot process it, MetaMask answers -32603 "unexpected character '\'".
 * Regression guard: 167b506 flipped the object form (4dfcd30) back to the string form.
 */
class SignRequestShapeTest {
    private val fixtureJson = """{"types":{"EIP712Domain":[]},"primaryType":"X","domain":{},"message":{}}"""

    @Test
    fun `params second element is a JSON object for eth_signTypedData_v4`() {
        val params = JSONArray().apply {
            put("0xabc")
            put(JSONObject(fixtureJson))
        }.toString()

        val parsed = JSONArray(params)
        assertEquals("0xabc", parsed.getString(0))
        assertNotNull(parsed.getJSONObject(1))
        assertEquals("X", parsed.getJSONObject(1).getString("primaryType"))
    }

    @Test
    fun `stringified typed data is not a valid v4 shape`() {
        val params = JSONArray().apply {
            put("0xabc")
            put(fixtureJson)
        }.toString()

        val parsed = JSONArray(params)
        assertEquals("0xabc", parsed.getString(0))
        // The string form parses as a String, not an object — wallets expecting an
        // object cannot process it.
        assertEquals(fixtureJson, parsed.getString(1))
        assertThrows(JSONException::class.java) { parsed.getJSONObject(1) }
    }
}
