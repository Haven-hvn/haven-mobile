package haven.mobile.core.haven.aol

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

internal class NonceManager {
    private val nonceCache = mutableMapOf<String, String>()
    private val mutex = Mutex()

    suspend fun getNonce(walletAddress: String, canisterId: String): String {
        val key = nonceKey(walletAddress, canisterId)
        return mutex.withLock {
            nonceCache[key] ?: generateNonce()
        }
    }

    suspend fun refreshNonce(walletAddress: String, canisterId: String) {
        val key = nonceKey(walletAddress, canisterId)
        mutex.withLock {
            nonceCache[key] = generateNonce()
        }
    }

    suspend fun clearFor(walletAddress: String, canisterId: String) {
        val key = nonceKey(walletAddress, canisterId)
        mutex.withLock {
            nonceCache.remove(key)
        }
    }

    private fun nonceKey(walletAddress: String, canisterId: String): String {
        return walletAddress + ":" + canisterId
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}