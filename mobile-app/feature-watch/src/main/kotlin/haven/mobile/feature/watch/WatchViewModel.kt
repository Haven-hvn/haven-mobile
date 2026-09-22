package haven.mobile.feature.watch

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.PlaintextSpool
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.crypto.HavenCipher
import haven.mobile.core.crypto.stripCarContainer
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** What the pipeline is doing, so the UI can say something better than "loading". */
enum class UnlockStage(val label: String) {
    /** "Gate" is Haven's word, not a reader's — they are waiting on an access check. */
    UNLOCKING("Checking your access\u2026"),
    STREAMING("Decrypting\u2026"),
    /** Fetching bytes with nothing to decrypt — ungated items must never claim otherwise. */
    LOADING("Loading\u2026"),
}

/** Content lifecycle for the open item. */
sealed interface ContentState {
    data object Idle : ContentState

    /**
     * The item is gated and no session key exists, so opening it needs a wallet
     * signature. Shown *before* any signing prompt: a cold wallet popup with no
     * explanation is how readers learn to blind-sign. Explicit [unlock] proceeds.
     */
    data object NeedsSignature : ContentState

    /** [progress] is 0f..1f when the size is known, null when it is not. */
    data class Working(val stage: UnlockStage, val progress: Float? = null) : ContentState

    /**
     * Staged, decrypted file in app-private storage. Viewers open it by path, so peak memory is a
     * decoder's own buffers rather than the whole payload.
     */
    data class Ready(val file: File) : ContentState

    data class Failed(val code: String, val message: String) : ContentState
}

sealed interface WatchUiState {
    data object Loading : WatchUiState
    data object NotFound : WatchUiState
    data class Ready(val item: MediaItem, val content: ContentState) : WatchUiState
}

/**
 * The unlock pipeline.
 *
 * ```
 * AesKeyCache ─┐
 *              ├─ key ─┐
 * HavenAol ────┘       │
 *                      ▼
 * HavenCache.stream ──▶ HavenCipher.decryptStream ──▶ PlaintextSpool ──▶ File ──▶ viewer / export
 *   (ciphertext chunks)   (plaintext chunks)            (disk)
 * ```
 *
 * Everything is a flow from end to end: cache chunks in, plaintext chunks out, straight to a staging
 * file. Peak memory is one chunk, so a 2GB video works on a device that could never hold it — the
 * previous whole-file approach needed the ciphertext *and* the plaintext resident at once and had to
 * refuse anything over 192MB.
 *
 * Re-opening an item is free: the staged file is reused, so there is no second unlock, no second
 * fetch and no second decrypt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchViewModel @Inject constructor(
    private val havenAol: HavenAol,
    private val havenCache: HavenCache,
    private val havenCipher: HavenCipher,
    private val plaintextSpool: PlaintextSpool,
    private val aesKeyCache: AesKeyCache,
    private val mediaRepository: MediaRepository,
    /** Exposed so the drip sheet can offer in-app trading when a wallet is connected. */
    val walletSession: WalletSession,
) : ViewModel() {

    private val itemId = MutableStateFlow<String?>(null)
    private val content = MutableStateFlow<ContentState>(ContentState.Idle)

    /**
     * In-app diagnostics trail: every pipeline transition, newest last, capped.
     * Rendered under the failure screen so a report needs no adb — logcat
     * gets the same lines via [logStage].
     */
    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
    val diagnostics: StateFlow<List<String>> = _diagnostics

    private val item: StateFlow<MediaItem?> = itemId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else mediaRepository.observeItem(id).catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<WatchUiState> = combine(itemId, item, content) { id, media, contentState ->
        when {
            id == null -> WatchUiState.Loading
            media == null -> WatchUiState.NotFound
            else -> WatchUiState.Ready(item = media, content = contentState)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WatchUiState.Loading)

    /** Called once per screen entry; also refreshes the entity from Arkiv in the background. */
    fun open(id: String) {
        if (itemId.value == id) return
        itemId.value = id
        content.value = ContentState.Idle
        viewModelScope.launch { mediaRepository.refreshItem(id) }
    }

    /**
     * Idempotent, so it is safe to drive from a `LaunchedEffect`.
     *
     * Never signs on entry: when the item is gated and no session key exists the
     * screen stops at [ContentState.NeedsSignature] and explains the coming wallet
     * prompt first. Cached keys and ungated items flow straight through to staging.
     */
    fun prepare(media: MediaItem) {
        val current = content.value
        if (current is ContentState.Working || current is ContentState.Ready ||
            current is ContentState.NeedsSignature
        ) {
            return
        }
        viewModelScope.launch {
            if (media.isEncrypted && !hasUnlockKey(media)) {
                content.value = ContentState.NeedsSignature
            } else {
                stage(media)
            }
        }
    }

    /** Explicit consent from the disclosure — the only path that may pop the wallet. */
    fun unlock(media: MediaItem) {
        if (content.value !is ContentState.NeedsSignature) return
        viewModelScope.launch { stage(media) }
    }

    /** Staged file, piece cache, or session gate key — any hit means no signature needed. */
    private suspend fun hasUnlockKey(media: MediaItem): Boolean {
        val cid = media.pieceRef?.pieceCid ?: return false
        if (plaintextSpool.find(cid) != null) return true
        if (aesKeyCache.getSuspend(cid) != null) return true
        return havenAol.hasCachedKey(media)
    }

    fun retry(media: MediaItem) {
        content.value = ContentState.Idle
        prepare(media)
    }

    /**
     * Runs the pipeline and returns the staged file, publishing each stage as it goes. Both the
     * viewer and SAF export await this same call rather than starting parallel copies of the work.
     */
    private suspend fun stage(media: MediaItem): Result<File> {
        (content.value as? ContentState.Ready)?.let { return Result.success(it.file) }
        logStage("start id=${media.id} encrypted=${media.isEncrypted}")

        val piece = media.pieceRef
            ?: return fail("NO_PIECE_REF", "This item has no stored content reference.")
        if (walletSession.address.value == null) {
            return fail("WALLET_NOT_CONNECTED", "Connect a wallet to open this item.")
        }

        // Already staged from an earlier open in this session.
        plaintextSpool.find(piece.pieceCid)?.let { staged ->
            content.value = ContentState.Ready(staged)
            return Result.success(staged)
        }

        // 1. Key, if the item is gated. Cached per piece CID for the session (FR-ACL-2), so a repeat
        //    open never re-signs or re-hits the canister.
        //
        //    The suspend accessors matter: `AesKeyCache.get`/`put` wrap a Mutex in `runBlocking`, and
        //    this runs on `viewModelScope` (main dispatcher), where that would block the UI thread.
        var key: ByteArray? = null
        if (media.isEncrypted) {
            content.value = ContentState.Working(UnlockStage.UNLOCKING)
            key = aesKeyCache.getSuspend(piece.pieceCid)
            logStage("key id=${media.id} cached=${key != null}")
            if (key == null) {
                val unlocked = havenAol.decrypt(media, walletSession)
                unlocked.exceptionOrNull()?.let { throwable ->
                    if (throwable.isOutOfCycles()) {
                        // The community's key service is dry — not a wallet problem.
                        // Say who can fix it (anyone holding ICP) and where it goes.
                        val canister = havenAol.canisterId.ifBlank { "the key-service canister" }
                        val failure = ContentState.Failed(
                            code = "CANISTER_OUT_OF_CYCLES",
                            message = "This community's key service is out of cycles, so it " +
                                "can't unlock anything right now. Topping up $canister keeps " +
                                "this archive readable for everyone.",
                        )
                        logStage("unlock failed id=${media.id} code=${failure.code} (canister dry)")
                        content.value = failure
                        return Result.failure(throwable)
                    }
                    val failure = throwable.toFailure("Could not unlock this item.")
                    logStage("unlock failed id=${media.id} code=${failure.code} msg=${failure.message}")
                    content.value = failure
                    return Result.failure(throwable)
                }
                key = unlocked.getOrNull()
                    ?: return fail("NO_KEY_AVAILABLE", "The gate returned no key material.")
                aesKeyCache.putSuspend(piece.pieceCid, key)
            }
        }

        // 2. Stream: ciphertext chunks from foc (which owns provider selection, the hedged race and
        //    PDP proofs) through the cipher, into the staging file. Nothing here holds the payload.
        val expectedBytes = media.sizeBytes ?: piece.size.takeIf { it > 0 }
        val contentKey = key
        // Ungated items have no key and nothing to decrypt — label the fetch
        // honestly instead of borrowing the decrypting copy.
        val fetchStage = if (contentKey != null) UnlockStage.STREAMING else UnlockStage.LOADING
        content.value = ContentState.Working(fetchStage, progress = 0f)

        // Byte counters survive failure: a GCM bad_decrypt on full-length bytes means
        // the key is wrong, on short bytes means the fetch truncated — the trail must
        // tell those apart instead of printing one opaque cipher error.
        var cipherBytes = 0L
        var plainBytes = 0L
        val ciphertext = havenCache.stream(piece)
        val plaintext = if (contentKey == null) {
            ciphertext
        } else {
            // Retrieval serves the stored CAR file; the cipher expects the raw chunked
            // ciphertext inside it. Non-CAR bytes pass through untouched.
            havenCipher.decryptStream(
                contentKey,
                ciphertext.onEach { cipherBytes += it.size }.stripCarContainer(),
                null,
            )
        }

        val staged = plaintextSpool.write(piece.pieceCid, plaintext) { written ->
            plainBytes = written
            // Progress is against the *declared* size. Plaintext is slightly smaller than
            // ciphertext (GCM tags), so this can reach 100% a beat early — better than a bar that
            // stalls at 97% because it was measuring the wrong thing.
            val fraction = expectedBytes
                ?.takeIf { it > 0 }
                ?.let { (written.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
            content.value = ContentState.Working(fetchStage, fraction)
        }

        staged.exceptionOrNull()?.let { throwable ->
            val failure = throwable.toFailure("This item could not be decrypted.")
            logStage(
                "fetch failed id=${media.id} code=${failure.code} " +
                    "msg=${failure.message} ctBytes=$cipherBytes ptBytes=$plainBytes",
            )
            content.value = failure
            return Result.failure(throwable)
        }
        val file = staged.getOrNull()
            ?: return fail("CACHE_WRITE_FAILED", "Decrypted content could not be staged.")

        logStage("ready id=${media.id} bytes=${file.length()} ctBytes=$cipherBytes")
        content.value = ContentState.Ready(file)
        // Playing is caching: the stream just filled the piece cache, so touch
        // the mirror row and every residency label (here, library, community)
        // recomputes live instead of going stale until the next refresh.
        runCatching { mediaRepository.noteAccessed(media.id) }
        return Result.success(file)
    }

    /**
     * FR-FILE-1/2: copy the staged file to a user-chosen SAF location.
     *
     * Copied stream-to-stream rather than read-then-write: a 2GB export must not become a 2GB
     * allocation, which is exactly what the previous `write(bytes)` did.
     */
    fun exportTo(
        media: MediaItem,
        uri: Uri,
        contentResolver: ContentResolver,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (!media.isExportable()) {
            onResult(
                Result.failure(
                    HavenError.AttestationFailed("Export blocked: this item failed verification"),
                ),
            )
            return
        }
        viewModelScope.launch {
            val prepared = stage(media)
            val file = prepared.getOrNull()
            if (file == null) {
                onResult(
                    Result.failure(prepared.exceptionOrNull() ?: HavenError.Internal("No content")),
                )
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { sink ->
                        file.inputStream().buffered(COPY_BUFFER_BYTES).use { source ->
                            source.copyTo(sink, COPY_BUFFER_BYTES)
                        }
                        sink.flush()
                    } ?: error("Could not open $uri for writing")
                }
            }
            onResult(result.map { })
        }
    }

    /** Publish a terminal failure and hand the same reason back to the caller. */
    private fun fail(code: String, message: String): Result<File> {
        logStage("failed code=$code msg=$message")
        content.value = ContentState.Failed(code = code, message = message)
        return Result.failure(HavenError.Internal("$code: $message"))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val COPY_BUFFER_BYTES = 64 * 1024
        /** How many pipeline lines the failure screen can show. */
        const val DIAGNOSTICS_CAP = 12
    }

    /**
     * One line per pipeline transition, to logcat (`adb logcat -s HavenWatch`)
     * and to the in-app [diagnostics] trail. Timber is a no-op on the JVM
     * without a planted tree, so unit tests stay green.
     */
    private fun logStage(msg: String) {
        timber.log.Timber.tag("HavenWatch").i(msg)
        _diagnostics.value = (_diagnostics.value + msg).takeLast(DIAGNOSTICS_CAP)
    }
}

/**
 * True when the key service itself is dry (out of cycles) rather than the
 * wallet or gate being at fault — the cue for the donation wording instead
 * of the signing wording.
 */
internal fun Throwable.isOutOfCycles(): Boolean {
    val haystack = ((message ?: "") + " " + (cause?.message ?: "")).lowercase()
    return "out of cycles" in haystack ||
        "out-of-cycles" in haystack ||
        "insufficient cycles" in haystack
}

/** Map a pipeline throwable onto a stable code the Settings event log can also show. */
internal fun Throwable.toFailure(fallbackMessage: String): ContentState.Failed = when (this) {
    is HavenError -> ContentState.Failed(code = code, message = message.ifBlank { fallbackMessage })
    else -> ContentState.Failed(code = "INTERNAL", message = message ?: fallbackMessage)
}

/**
 * FR-FILE-4. `EXPIRED` is the mirror's marker for a piece whose freshness or integrity check did not
 * hold, so it is not exportable. A function rather than an inline expression because the ViewModel
 * re-checks it after the button has already been enabled.
 */
internal fun MediaItem.isExportable(): Boolean =
    contentCacheStatus != ContentCacheStatus.EXPIRED

/** Only `FILE` needs the save/open-with path; every other kind renders inline. */
internal fun MediaKind.rendersInline(): Boolean = this != MediaKind.FILE
