package haven.mobile.core.crypto

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * One identity's keys must never be visible to — or evicted by — another.
 * Wallet-scoped keys are `<walletLower>:<rest>`; canister keys stay unprefixed.
 */
class AesKeyCacheWalletScopeTest {

    private val walletA = "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val walletB = "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"

    @Test
    fun `same item key under two wallets does not collide`() = runBlocking {
        val cache = AesKeyCache()
        cache.putSuspend("${walletA.lowercase()}:item-1:gate", ByteArray(32) { 1 })
        cache.putSuspend("${walletB.lowercase()}:item-1:gate", ByteArray(32) { 2 })
        org.junit.jupiter.api.Assertions.assertEquals(1.toByte(), cache.getSuspend("${walletA.lowercase()}:item-1:gate")!![0])
        org.junit.jupiter.api.Assertions.assertEquals(2.toByte(), cache.getSuspend("${walletB.lowercase()}:item-1:gate")!![0])
    }

    @Test
    fun `clearForWallet removes only that wallet, keeps others and canister keys`() = runBlocking {
        val cache = AesKeyCache()
        cache.putSuspend("${walletA.lowercase()}:item-1:gate", ByteArray(32) { 1 })
        cache.putSuspend("${walletA.lowercase()}:piece-1", ByteArray(32) { 1 })
        cache.putSuspend("${walletB.lowercase()}:item-1:gate", ByteArray(32) { 2 })
        cache.putSuspend("verificationKey:canister-1", ByteArray(32) { 3 })

        cache.clearForWallet(walletA)

        assertNull(cache.getSuspend("${walletA.lowercase()}:item-1:gate"))
        assertNull(cache.getSuspend("${walletA.lowercase()}:piece-1"))
        assertNotNull(cache.getSuspend("${walletB.lowercase()}:item-1:gate"))
        assertNotNull(cache.getSuspend("verificationKey:canister-1"))
    }
}
