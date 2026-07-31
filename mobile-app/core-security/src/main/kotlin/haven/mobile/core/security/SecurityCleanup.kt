package haven.mobile.core.security

interface SecurityCleanup {
    suspend fun runDisconnect(walletAddress: String): DisconnectReport
}