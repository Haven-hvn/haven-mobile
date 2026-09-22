package haven.mobile.core.wallet

import com.reown.appkit.presets.AppKitChainsPresets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Haven gates live on Sepolia but the SDK presets ship mainnet chains only.
 * Sessions approve exactly the proposed chains, so Sepolia must be appended
 * at connect or every Sepolia signature dies instantly at request time.
 */
class WithSepoliaTest {

    private fun refs(chains: List<com.reown.appkit.client.Modal.Model.Chain>) =
        chains.map { "${it.chainNamespace}:${it.chainReference}" }

    @Test
    fun `sepolia appended exactly once`() {
        val preset = AppKitChainsPresets.ethChains.values.toList()
        val result = withSepolia(preset)
        assertEquals(1, refs(result).count { it == "eip155:11155111" })
        assertTrue(refs(result).contains("eip155:1"), "mainnet must be retained")
    }

    @Test
    fun `sepolia entry points at sepolia`() {
        val preset = AppKitChainsPresets.ethChains.values.toList()
        val mainnet = preset.first { it.chainReference == "1" }
        val sepolia = withSepolia(preset).first { it.chainReference == "11155111" }
        assertEquals("Sepolia", sepolia.chainName)
        assertEquals("eip155", sepolia.chainNamespace)
        assertEquals("https://rpc.sepolia.org", sepolia.rpcUrl)
        assertEquals("https://sepolia.etherscan.io", sepolia.blockExplorerUrl)
        // Same signing surface as mainnet; the proposal adds v4 for every chain.
        assertEquals(mainnet.requiredMethods, sepolia.requiredMethods)
        assertEquals(mainnet.token, sepolia.token)
    }

    @Test
    fun `idempotent and safe on empty`() {
        val once = withSepolia(AppKitChainsPresets.ethChains.values.toList())
        assertEquals(refs(once), refs(withSepolia(once)))
        assertEquals(emptyList<String>(), refs(withSepolia(emptyList())))
    }
}
