package haven.mobile.core.wallet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SignRequestShapeTest {
    private val fixtureJson = """{"types":{"EIP712Domain":[]},"primaryType":"X","domain":{},"message":{}}"""

    @Test
    fun `params second element is stringified JSON for eth_signTypedData_v4`() {
        val params = JSONArray().apply {
            put("0xabc")
            put(fixtureJson)
        }.toString()

        val parsed = JSONArray(params)
        assertEquals("0xabc", parsed.getString(0))
        assertEquals(fixtureJson, parsed.getString(1))
    }

    @Test
    fun `JSONObject wrapping would change shape and break v4 spec`() {
        val asObject = JSONObject(fixtureJson)
        val params = JSONArray().apply {
            put("0xabc")
            put(asObject)
        }.toString()

        val parsed = JSONArray(params)
        assertEquals("0xabc", parsed.getString(0))
        org.junit.jupiter.api.Assertions.assertNotNull(parsed.getJSONObject(1))
    }
}
