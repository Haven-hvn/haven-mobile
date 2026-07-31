package haven.mobile.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AesKeyCache(private val capacity: Int = 256) {
    private val cache = LinkedHashMap<String, ByteArray>(capacity, 0.75f, true)
    private val mutex = Mutex()

    fun put(pieceCid: String, key: ByteArray) {
        mutex.runBlocking {
            if (cache.size >= capacity) {
                val eldest = cache.entries.first()
                zeroize(eldest.value)
                cache.remove(eldest.key)
            }
            cache[pieceCid] = key.copyOf()
        }
    }

    fun get(pieceCid: String): ByteArray? {
        return mutex.runBlocking {
            cache[pieceCid]?.copyOf()
        }
    }

    fun clearAll() {
        mutex.runBlocking {
            for (entry in cache.entries) {
                zeroize(entry.value)
            }
            cache.clear()
        }
    }

    private fun zeroize(bytes: ByteArray) {
        for (i in bytes.indices) {
            bytes[i] = 0
        }
    }
}