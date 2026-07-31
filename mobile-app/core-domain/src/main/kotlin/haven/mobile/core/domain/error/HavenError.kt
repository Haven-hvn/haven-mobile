package haven.mobile.core.domain.error

// Mirrors stable error codes from haven-dapp-main/src/lib/cache-errors.ts and playback-errors.ts
sealed class HavenError(
    val code: String,
    val message: String,
    val cause: Throwable? = null
) {
    // Mirrors cache-errors.ts::CACHE_QUOTA_EXCEEDED
    data class CacheQuotaExceeded(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CACHE_QUOTA_EXCEEDED", message, cause)

    // Mirrors cache-errors.ts::CACHE_MISS
    data class CacheMiss(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CACHE_MISS", message, cause)

    // Mirrors cache-errors.ts::CACHE_STALE
    data class CacheStale(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CACHE_STALE", message, cause)

    // Mirrors cache-errors.ts::CACHE_WRITE_FAILED
    data class CacheWriteFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CACHE_WRITE_FAILED", message, cause)

    // Mirrors cache-errors.ts::CACHE_READ_FAILED
    data class CacheReadFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CACHE_READ_FAILED", message, cause)

    // Mirrors cache-errors.ts::CACHE_PROVIDER_ERROR
    data class CacheProviderError(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CACHE_PROVIDER_ERROR", message, cause)

    // Mirrors cache-errors.ts::CACHE_PIECE_VERIFY_FAILED
    data class CachePieceVerifyFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CACHE_PIECE_VERIFY_FAILED", message, cause)

    // Mirrors cache-errors.ts::ALL_PROVIDERS_CORRUPT
    data class AllProvidersCorrupt(
        message: String,
        cause: Throwable? = null
    ) : HavenError("ALL_PROVIDERS_CORRUPT", message, cause)

    // Mirrors playback-errors.ts::PLAYBACK_DECRYPT_FAILED
    data class PlaybackDecryptFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("PLAYBACK_DECRYPT_FAILED", message, cause)

    // Mirrors playback-errors.ts::PLAYBACK_CODEC_UNSUPPORTED
    data class PlaybackCodecUnsupported(
        message: String,
        cause: Throwable? = null
    ) : HavenError("PLAYBACK_CODEC_UNSUPPORTED", message, cause)

    // Mirrors playback-errors.ts::PLAYBACK_STREAM_ERROR
    data class PlaybackStreamError(
        message: String,
        cause: Throwable? = null
    ) : HavenError("PLAYBACK_STREAM_ERROR", message, cause)

    // Mirrors network error constant from cache-errors.ts
    data class NetworkError(
        message: String,
        cause: Throwable? = null
    ) : HavenError("NETWORK_ERROR", message, cause)

    // Mirrors canister call error from cache-errors.ts
    data class CanisterCallFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CANISTER_CALL_FAILED", message, cause)

    // Mirrors signing error from cache-errors.ts
    data class SigningFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("SIGNING_FAILED", message, cause)

    // Mirrors gate verification error from cache-errors.ts
    data class GateVerificationFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("GATE_VERIFICATION_FAILED", message, cause)

    // Mirrors attestation error from cache-errors.ts
    data class AttestationFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("ATTESTATION_FAILED", message, cause)

    // Mirrors key error from cache-errors.ts
    data class NoKeyAvailable(
        message: String,
        cause: Throwable? = null
    ) : HavenError("NO_KEY_AVAILABLE", message, cause)

    // Mirrors wallet error from cache-errors.ts
    data class WalletNotConnected(
        message: String,
        cause: Throwable? = null
    ) : HavenError("WALLET_NOT_CONNECTED", message, cause)

    // Mirrors gate metadata error from cache-errors.ts
    data class UnsupportedGateMetadata(
        message: String,
        cause: Throwable? = null
    ) : HavenError("UNSUPPORTED_GATE_METADATA", message, cause)

    // Mirrors signature error from cache-errors.ts
    data class InvalidSignatureFormat(
        message: String,
        cause: Throwable? = null
    ) : HavenError("INVALID_SIGNATURE_FORMAT", message, cause)

    // Mirrors appkit error from cache-errors.ts
    data class AppKitNotInitialized(
        message: String,
        cause: Throwable? = null
    ) : HavenError("APPKIT_NOT_INITIALIZED", message, cause)

    // Mirrors address error from cache-errors.ts
    data class NoAddressReturned(
        message: String,
        cause: Throwable? = null
    ) : HavenError("NO_ADDRESS_RETURNED", message, cause)

    // Mirrors connect error from cache-errors.ts
    data class ConnectFailed(
        message: String,
        cause: Throwable? = null
    ) : HavenError("CONNECT_FAILED", message, cause)

    // Mirrors internal error from cache-errors.ts
    data class Internal(
        message: String,
        cause: Throwable? = null
    ) : HavenError("INTERNAL", message, cause)
}