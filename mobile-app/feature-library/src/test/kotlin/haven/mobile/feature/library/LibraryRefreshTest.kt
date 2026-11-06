package haven.mobile.feature.library

import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.mirror.CachedChapter
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.crypto.HavenCipher
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.wallet.WalletSession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Local-first contract: the mirrored list is the screen. An automatic sync
 * revalidates it silently (no pull-spinner state, items never leave), and
 * only a refresh the user asked for reports itself as refreshing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRefreshTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeMediaRepository
    private lateinit var viewModel: LibraryViewModel
    private lateinit var collectScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = FakeMediaRepository()
        val wallet = mockk<WalletSession> {
            every { address } returns MutableStateFlow("0xabc")
        }
        val hidden = mockk<HiddenItemsStore> {
            every { hiddenIds } returns flowOf(emptySet())
        }
        viewModel = LibraryViewModel(
            mediaRepository = repository,
            walletSession = wallet,
            havenAol = mockk<HavenAol>(relaxed = true),
            havenCache = mockk<HavenCache>(relaxed = true),
            havenCipher = mockk<HavenCipher>(relaxed = true),
            aesKeyCache = AesKeyCache(),
            hiddenItems = hidden,
        )
        collectScope = CoroutineScope(mainDispatcher)
        collectScope.launch { viewModel.uiState.collect {} }
    }

    @AfterEach
    fun tearDown() {
        collectScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `automatic sync is silent and the mirrored items stay on screen`() {
        // Construction already started the automatic sync; it is held open here.
        val state = viewModel.uiState.value as LibraryUiState.Ready
        assertEquals(listOf("1"), state.items.map { it.id })
        assertTrue(state.isSyncing)
        assertFalse(state.isRefreshing)

        repository.gate.complete(Result.success(Unit))
        val settled = viewModel.uiState.value as LibraryUiState.Ready
        assertFalse(settled.isSyncing)
        assertFalse(settled.isRefreshing)
        assertEquals(listOf("1"), settled.items.map { it.id })
    }

    @Test
    fun `user-initiated refresh reports refreshing and absorbs duplicate syncs`() {
        repository.gate.complete(Result.success(Unit))
        assertEquals(1, repository.refreshCalls)

        repository.gate = CompletableDeferred()
        viewModel.refresh(userInitiated = true)
        viewModel.refresh()
        viewModel.refresh(userInitiated = true)

        val state = viewModel.uiState.value as LibraryUiState.Ready
        assertTrue(state.isRefreshing)
        assertTrue(state.isSyncing)
        assertEquals(2, repository.refreshCalls)

        repository.gate.complete(Result.success(Unit))
        assertFalse((viewModel.uiState.value as LibraryUiState.Ready).isRefreshing)
    }

    private class FakeMediaRepository : MediaRepository {
        var gate = CompletableDeferred<Result<Unit>>()
        var refreshCalls = 0
        private val items = MutableStateFlow(listOf(item()))

        override fun observeAccessible(): Flow<List<MediaItem>> = items
        override suspend fun refreshAccessible(): Result<Unit> {
            refreshCalls++
            return gate.await()
        }

        override fun observeLibrary(owner: String): Flow<List<MediaItem>> = items
        override suspend fun refreshLibrary(owner: String): Result<Unit> = Result.success(Unit)
        override fun observeItem(id: String): Flow<MediaItem?> = flowOf(null)
        override suspend fun refreshItem(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun noteAccessed(id: String) = Unit
        override suspend fun clearFor(walletAddress: String) = Unit
        override suspend fun cachedChapters(item: MediaItem, fileSizeBytes: Long): List<CachedChapter>? = null
        override suspend fun cacheChapters(item: MediaItem, fileSizeBytes: Long, chapters: List<CachedChapter>) = Unit
    }

    private companion object {
        fun item() = MediaItem(
            id = "1",
            kind = MediaKind.VIDEO,
            owner = "0xabc",
            title = "Founding interview",
            description = null,
            mimeType = null,
            fileExtension = null,
            filenameHint = null,
            sizeBytes = 1_024,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdAtBlock = null,
            expiresAtBlock = null,
            pieceRef = null,
            filecoinCid = null,
            encryptedCid = null,
            cidHash = null,
            gate = null,
            isEncrypted = false,
            encryptionMetadata = null,
            cidEncryptionMetadata = null,
            attestation = null,
            arkivStatus = ArkivStatus.FRESH,
            contentCacheStatus = ContentCacheStatus.CACHED,
            lastAccessedAt = null,
        )
    }
}
