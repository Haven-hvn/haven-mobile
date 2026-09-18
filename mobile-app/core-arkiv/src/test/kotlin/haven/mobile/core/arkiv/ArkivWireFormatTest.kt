package haven.mobile.core.arkiv

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Wire-format parity for `arkiv_query` rows.
 *
 * The node returns SDK 0.8 `RpcEntity` rows — flat `key`/`owner`/`creator`/`createdAt`/
 * `expiresAt`/`contentType` fields plus `attributes` as `[{name, type, value}]` with
 * type-tagged values (see SDK `entityFromRpcResult` and CLI `decode_rpc_entity`, which this
 * decoder mirrors). No Haven entities exist on-chain to capture from, so the fixture below is
 * transcribed from those two decoders rather than from a live response.
 *
 * This pins the decode the whole read path depends on: gate extraction, block heights, the
 * MIME enum, and the skip-corrupt-rows rule.
 */
class ArkivWireFormatTest {

    private val client = ArkivClientImpl(ArkivConfig(endpointUrl = "https://example.com"))

    private fun normalized(json: String): JSONObject = client.normalizeRpcEntity(JSONObject(json))

    @Test
    fun `row identity and block heights land under reader keys`() {
        val out = normalized(RPC_ROW)
        assertEquals("0x1234", out.optString("id"))
        assertEquals("0x1234", out.optString("key"))
        assertEquals("0xbeef", out.optString("owner"))
        assertEquals("video/mp4", out.optString("contentType"))
        assertEquals(256L, out.optLong("createdAtBlock"))
        assertEquals(512L, out.optLong("expiresAtBlock"))
    }

    @Test
    fun `gate triple decodes through to a token gate`() {
        val gate = with(client) { normalized(RPC_ROW).toTokenGate() }!!
        assertEquals("eip155:8453", gate.chain)
        assertEquals("0xabcDEF1234567890abcdef1234567890ABCDEF12", gate.tokenAddress)
        assertEquals(75.0, gate.threshold)
    }

    @Test
    fun `attribute tags decode to flat values`() {
        val out = normalized(RPC_ROW)
        assertEquals("haven.video.full", out.optString("grp"))
        assertEquals(1L, out.optLong("mime"))
        assertEquals(60L, out.optLong("dur_s"))
        assertEquals(true, out.optBoolean("flag"))
        assertEquals("12.5", out.optString("ratio"))
    }

    @Test
    fun `oversized u256 degrades to double, never null`() {
        val out = normalized(RPC_ROW)
        assertTrue(out.opt("whale") is Double)
    }

    @Test
    fun `unknown tags and malformed values are skipped`() {
        val out = normalized(RPC_ROW)
        assertFalse(out.has("weird"))
        assertFalse(out.has("broken"))
    }

    private companion object {
        /** One `arkiv_query` row in the SDK 0.8 `RpcEntity` shape (see class KDoc). */
        const val RPC_ROW = """
            {
              "key": "0x1234",
              "owner": "0xbeef",
              "creator": "0xbeef",
              "createdAt": "0x100",
              "expiresAt": "0x200",
              "contentType": "video/mp4",
              "attributes": [
                {"name": "grp", "type": "str", "value": "haven.video.full"},
                {"name": "title", "type": "str", "value": "Test video"},
                {"name": "mime", "type": "i32", "value": 1},
                {"name": "gate_token", "type": "str", "value": "0xabcDEF1234567890abcdef1234567890ABCDEF12"},
                {"name": "gate_chain", "type": "i32", "value": "0x2105"},
                {"name": "gate_threshold", "type": "i32", "value": 75},
                {"name": "dur_s", "type": "u64", "value": "0x3c"},
                {"name": "whale", "type": "u256", "value": "0xffffffffffffffffffffffffffffffff"},
                {"name": "flag", "type": "bool", "value": true},
                {"name": "ratio", "type": "dec", "value": "12.5"},
                {"name": "blob", "type": "bytes", "value": "0xdeadbeef"},
                {"name": "weird", "type": "nope", "value": "x"},
                {"name": "broken", "type": "i32", "value": "not-a-number"}
              ]
            }
        """
    }
}
