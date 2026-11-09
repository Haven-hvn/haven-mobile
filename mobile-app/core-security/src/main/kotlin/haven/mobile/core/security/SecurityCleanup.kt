package haven.mobile.core.security

interface SecurityCleanup {
    /** Disconnects [walletAddress]: keys always wiped; content per the wipe-on-disconnect preference. Identity record kept. */
    suspend fun runDisconnect(walletAddress: String): DisconnectReport

    /** Removes [walletAddress] entirely: unconditional per-identity wipe, then the registry entry is forgotten. Other identities untouched. */
    suspend fun removeIdentity(walletAddress: String): DisconnectReport
}
