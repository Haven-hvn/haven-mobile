package haven.mobile.core.arkiv

import cloud.filecoin.foc.cache.FocChain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Retrieval scoping for Arkiv-sourced items: the 2.0 record carries only the
 * `piece` locator. The ref supplies the Beam candidate (keyed by the uploader
 * wallet, on the gate's network) and the network for foc-cache's writer-independent
 * on-chain provider resolution. The network comes from the gate chain when present,
 * otherwise from the Arkiv endpoint the app is pointed at.
 */
class PieceRefScopingTest {

    private val client = ArkivClientImpl(
        ArkivConfig(endpointUrl = "https://example.com", focChain = FocChain.CALIBRATION),
    )

    private fun hexPayload(json: String): String =
        "0x" + json.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun attr(name: String, type: String, value: Any) =
        JSONObject().put("name", name).put("type", type).put("value", value)

    private fun row(owner: String, gateChain: Int?, payloadJson: String): JSONObject {
        val attrs = JSONArray()
            .put(attr("title", "str", "scoped"))
            .put(attr("mime", "i32", 1))
        if (gateChain != null) {
            attrs
                .put(attr("gate_chain", "i32", gateChain))
                .put(attr("gate_token", "str", "0xF23a728b55BE576c75D98A8032982F85cBAD493E"))
                .put(attr("gate_threshold", "i32", 1))
        }
        return JSONObject()
            .put("key", "0x" + "ab".repeat(32))
            .put("owner", owner)
            .put("creator", owner)
            .put("createdAt", "0x9b877")
            .put("expiresAt", "0x1c2d77")
            .put("contentType", "application/json")
            .put("payload", hexPayload(payloadJson))
            .put("attributes", attrs)
    }

    private fun payload() = JSONObject()
        .put("piece", "bafkzcibscoped")
        .put("size", 10888)
        .toString()

    private fun refOf(owner: String, gateChain: Int?) = with(client) {
        normalizeRpcEntity(row(owner, gateChain, payload())).toMediaItem().pieceRef!!
    }

    @Test
    fun `sepolia gate resolves to calibration beam with uploader wallet`() {
        val ref = refOf("0x5C32469325d4093AB142dDDC1305F91d76e45141", 11155111)
        assertEquals(FocChain.CALIBRATION, ref.chain)
        assertTrue(ref.cdnEnabled)
        assertEquals("0x5c32469325d4093ab142dddc1305f91d76e45141", ref.walletAddress)
        assertEquals("https://0x5C32469325d4093aB142DDdC1305F91d76e45141.calibration.filbeam.io/bafkzcibscoped", ref.filBeamUrl)
    }

    @Test
    fun `ref carries no provider list so foc resolves providers from chain`() {
        // The 2.0 record has only the piece locator. The ref must leave
        // providerServiceUrls empty (writer-independent) and set the network, so
        // foc-cache resolves the SPs from the on-chain registry at fetch/probe time.
        val ref = refOf("0x5C32469325d4093AB142dDDC1305F91d76e45141", 11155111)
        assertTrue(ref.providerServiceUrls.isEmpty())
        assertEquals(FocChain.CALIBRATION, ref.chain)
    }

    @Test
    fun `mainnet gate resolves to mainnet beam`() {
        val ref = refOf("0x5C32469325d4093AB142dDDC1305F91d76e45141", 1)
        assertEquals(FocChain.MAINNET, ref.chain)
        assertTrue(ref.cdnEnabled)
        assertEquals("https://0x5C32469325d4093aB142DDdC1305F91d76e45141.filbeam.io/bafkzcibscoped", ref.filBeamUrl)
    }

    @Test
    fun `non-evm owner disables cdn but keeps chain scoping`() {
        val ref = refOf("0xOwner", 11155111)
        assertEquals(FocChain.CALIBRATION, ref.chain)
        assertFalse(ref.cdnEnabled)
        assertNull(ref.walletAddress)
        assertNull(ref.filBeamUrl)
    }

    @Test
    fun `missing gate infers calibration from a testnet arkiv endpoint`() {
        // Ungated records carry no gate signal. The network follows the Arkiv
        // endpoint: Tiramisu (testnet) means Filecoin calibration.
        val testnetClient = ArkivClientImpl(
            ArkivConfig(endpointUrl = "https://rpc.tiramisu.db-chain.testnet.arkiv.network"),
        )
        val ref = with(testnetClient) {
            normalizeRpcEntity(row("0x5C32469325d4093AB142dDDC1305F91d76e45141", null, payload()))
                .toMediaItem().pieceRef!!
        }
        assertEquals(FocChain.CALIBRATION, ref.chain)
        assertTrue(ref.cdnEnabled)
    }

    @Test
    fun `mainnet arkiv endpoint scopes ungated records to mainnet`() {
        val mainnetClient = ArkivClientImpl(
            ArkivConfig(endpointUrl = "https://rpc.mainnet.arkiv.network"),
        )
        val ref = with(mainnetClient) {
            normalizeRpcEntity(row("0x5C32469325d4093AB142dDDC1305F91d76e45141", null, payload()))
                .toMediaItem().pieceRef!!
        }
        assertEquals(FocChain.MAINNET, ref.chain)
    }

    @Test
    fun `gate chain overrides the endpoint network`() {
        // A Sepolia gate on a mainnet endpoint still resolves to calibration.
        val mainnetClient = ArkivClientImpl(
            ArkivConfig(endpointUrl = "https://rpc.mainnet.arkiv.network"),
        )
        val ref = with(mainnetClient) {
            normalizeRpcEntity(row("0x5C32469325d4093AB142dDDC1305F91d76e45141", 11155111, payload()))
                .toMediaItem().pieceRef!!
        }
        assertEquals(FocChain.CALIBRATION, ref.chain)
    }
}
