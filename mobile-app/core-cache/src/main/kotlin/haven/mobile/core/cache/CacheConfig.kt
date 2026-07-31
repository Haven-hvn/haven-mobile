package haven.mobile.core.cache

data class CacheConfig(
    val quotaBytes: Long = 2L * 1024 * 1024 * 1024,
    val blockTtlDays: Int = 30,
    val maxParallelFetches: Int = 3,
    val hedgeDelayMillis: Long = 200,
    val chunkSize: Int = 256 * 1024,
)