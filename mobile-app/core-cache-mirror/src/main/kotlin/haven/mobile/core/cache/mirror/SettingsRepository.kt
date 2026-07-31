package haven.mobile.core.cache.mirror

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val cacheQuotaBytes: Flow<Long>
    val cacheTtlDays: Flow<Int>
    val clearOnDisconnect: Flow<Boolean>

    suspend fun setCacheQuotaBytes(bytes: Long)
    suspend fun setCacheTtlDays(days: Int)
    suspend fun setClearOnDisconnect(enabled: Boolean)
}