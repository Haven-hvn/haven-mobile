package haven.mobile.core.cache

/**
 * Cache defaults, per requirements FR-CACHE-2. All of these are user-adjustable in Settings; these
 * are the values a fresh install starts from.
 */
data class CacheConfig(
    /** Ciphertext budget for `foc-cache`. */
    val quotaBytes: Long = 2L * 1024 * 1024 * 1024,
    val blockTtlDays: Int = 30,
    val maxParallelFetches: Int = 3,
    /**
     * Delay before a hedged second provider request. 200ms splits the difference between the
     * cellular (300ms) and Wi-Fi (150ms) targets until connectivity-aware selection is wired.
     */
    val hedgeDelayMillis: Long = 200,
    val chunkSize: Int = 256 * 1024,
    /**
     * Budget for *decrypted* content staged on disk ([PlaintextSpool]), separate from the ciphertext
     * quota above.
     *
     * Deliberately much smaller: staged plaintext is a convenience copy that can always be rebuilt
     * from the ciphertext cache, and on a 16GB device it is the thing most likely to run the user out
     * of space. 1 GiB holds a couple of feature-length videos, and the oldest is dropped first.
     */
    val plaintextBudgetBytes: Long = 1L * 1024 * 1024 * 1024,
)
