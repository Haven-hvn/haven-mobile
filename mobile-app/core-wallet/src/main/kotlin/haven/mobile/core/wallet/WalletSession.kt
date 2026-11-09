package haven.mobile.core.wallet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val NO_IDENTITIES: StateFlow<List<WalletIdentity>> = MutableStateFlow(emptyList())
private val NO_ACTIVE: StateFlow<WalletIdentity?> = MutableStateFlow(null)
private val NOT_LIVE: StateFlow<Boolean> = MutableStateFlow(false)

interface WalletSession {
    /** The active (browsing) identity's address, live or stored. Null when no identity is known. */
    val address: StateFlow<String?>

    /** All known identities, in the order they were added. */
    val identities: StateFlow<List<WalletIdentity>> get() = NO_IDENTITIES

    /** The active identity, or null when none is known. */
    val activeIdentity: StateFlow<WalletIdentity?> get() = NO_ACTIVE

    /**
     * True when a live WalletConnect session exists for the active identity
     * (so it can sign). A stored identity browses instantly but needs
     * [reconnect] before signing — AppKit holds one live session at a time.
     */
    val isLive: StateFlow<Boolean> get() = NOT_LIVE

    /**
     * Recent wallet-stack diagnostic events (init/restore/delegate/connect), newest last.
     * Surfaced in the onboarding UI so connection failures are visible without adb logcat.
     */
    val diagnostics: StateFlow<List<String>>

    /**
     * Latest WalletConnect pairing URI (wc:…) awaiting wallet approval, if any. Surfaced so the
     * UI can offer a QR / copy fallback when no installed wallet picks up the deep link.
     */
    val pairingUri: StateFlow<String?>

    suspend fun connect(): Result<String>

    /**
     * Makes [address] the active identity. Clears nothing: the previous
     * identity's keys, cache, mirror and settings stay on device for when it
     * is switched back to. Ends the previous live WalletConnect session.
     */
    suspend fun switchTo(address: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("switchTo not supported by this session"))

    /** Connects a wallet that is not the active one, adding/activating its identity. */
    suspend fun connectNewIdentity(): Result<String> = connect()

    /** Re-establishes a live session for the active (stored) identity. */
    suspend fun reconnect(): Result<String> = connect()

    suspend fun renameIdentity(address: String, label: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("renameIdentity not supported by this session"))

    /** Removes the identity record only; per-identity data wipe is SecurityCleanup's job. */
    suspend fun forgetIdentity(address: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("forgetIdentity not supported by this session"))

    /** Ends the live WalletConnect session. The identity and its data stay on device. */
    suspend fun disconnect()

    suspend fun signTypedDataV4(json: String, chainId: Long): Result<String>

    /**
     * Submits a contract call through the connected wallet (`eth_sendTransaction`).
     * The wallet signs and broadcasts; [Result] holds the tx hash on success.
     */
    suspend fun sendTransaction(to: String, data: String, chainId: Long, valueHex: String = "0x0"): Result<String>
}
