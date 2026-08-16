package haven.mobile.feature.watch

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.design.component.CacheStatusChip
import haven.mobile.core.design.component.ConfirmDialog
import haven.mobile.core.design.component.ErrorState
import haven.mobile.core.design.component.Explain
import haven.mobile.core.design.component.HavenTopBar
import haven.mobile.core.design.component.MediaKindGlyph
import haven.mobile.core.design.component.byteLabel
import haven.mobile.core.design.component.label
import haven.mobile.core.design.component.summaryLine
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The viewer.
 *
 * One pipeline (see [WatchViewModel]) stages decrypted content to a file; five renderers read that
 * file. Every one of them is written for a device with little RAM to spare: the player streams from
 * disk, images are downsampled to the screen before decoding, PDF pages are rendered one at a time,
 * and export copies stream-to-stream.
 *
 * Inline kinds start as soon as the item resolves — a user who tapped an item has already expressed
 * intent. `FILE` is the exception: it does not render, it exports, and export is gated behind an
 * explicit action plus a one-time warning (FR-FILE-3).
 */
@Composable
fun WatchScreen(
    navController: NavController,
    itemId: String,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(itemId) { viewModel.open(itemId) }

    Column(modifier = Modifier.fillMaxSize()) {
        val item = (uiState as? WatchUiState.Ready)?.item
        HavenTopBar(
            title = item?.title ?: "Loading\u2026",
            subtitle = item?.let { media ->
                listOfNotNull(media.kind.label(), media.byteLabel()).joinToString(" \u00b7 ")
            },
            onBack = { navController.popBackStack() },
        )

        when (val state = uiState) {
            WatchUiState.Loading -> ProgressBlock(label = "Opening\u2026")

            WatchUiState.NotFound -> ErrorState(
                title = "This isn't in your library",
                message = "It may have been removed by its community, or it belongs to a different " +
                    "wallet than the one you have connected.",
            )

            is WatchUiState.Ready -> {
                val media = state.item

                if (media.kind.rendersInline()) {
                    LaunchedEffect(media.id) { viewModel.prepare(media) }
                }

                when (val content = state.content) {
                    ContentState.Idle ->
                        if (media.kind == MediaKind.FILE) {
                            FileViewer(media = media, staged = null, viewModel = viewModel)
                        } else {
                            ProgressBlock(label = "Preparing\u2026")
                        }

                    is ContentState.Working -> ProgressBlock(
                        label = content.stage.label,
                        progress = content.progress,
                    )

                    is ContentState.Failed -> ErrorState(
                        title = "Couldn't open this",
                        message = content.message,
                        onRetry = { viewModel.retry(media) },
                    )

                    is ContentState.Ready -> when (media.kind) {
                        MediaKind.VIDEO -> PlayerViewer(
                            media = media,
                            file = content.file,
                            aspect = 16f / 9f,
                        )
                        MediaKind.AUDIO -> PlayerViewer(
                            media = media,
                            file = content.file,
                            aspect = null,
                        )
                        MediaKind.IMAGE -> ImageViewer(media = media, file = content.file)
                        MediaKind.DOCUMENT -> DocumentViewer(media = media, file = content.file)
                        MediaKind.FILE -> FileViewer(
                            media = media,
                            staged = content.file,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

/* ── Video / audio ─────────────────────────────────────────────────────────────────────────── */

/**
 * Media3, playing the staged file through [HavenPlaybackService].
 *
 * A file URI is the cheapest possible source: ExoPlayer memory-maps and seeks it, so scrubbing a
 * two-hour video costs a few hundred KB of buffers. Feeding the player from memory instead (an earlier
 * `ByteArrayDataSource` approach) needed the whole file resident and made seeking a re-allocation.
 *
 * The player itself lives in the service rather than here, which is what lets audio keep going when
 * the app is backgrounded and gives video somewhere to continue from in picture-in-picture.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PlayerViewer(
    media: MediaItem,
    file: File,
    aspect: Float?,
) {
    val controller by rememberPlaybackController(file)

    // Video floats over whatever the user opens next; audio does not need a window, it just keeps
    // playing through the service.
    EnablePictureInPicture(player = controller, enabled = media.kind == MediaKind.VIDEO)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.md)
                .then(if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.height(80.dp)),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            val player = controller
            if (player == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            controllerShowTimeoutMs = if (aspect != null) 3_000 else 0
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                        }
                    },
                    // A MediaController *is* a Player, so the view is indifferent to the fact that the
                    // real player is in another process. Set in `update` because the controller
                    // arrives asynchronously, after the view has been created.
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        MediaMeta(media = media)
    }
}

/* ── Image ─────────────────────────────────────────────────────────────────────────────────── */

/**
 * Decoded from the staged file, downsampled to the screen.
 *
 * The two-pass `inJustDecodeBounds` + `inSampleSize` dance is the whole point: a 6000×4000 photo is
 * 96MB as ARGB_8888 and will OOM a low-end device outright, but at `inSampleSize=4` it is 6MB and
 * still sharper than the display can show.
 */
@Composable
private fun ImageViewer(media: MediaItem, file: File) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidthPx = remember(configuration.screenWidthDp) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath, targetWidthPx) {
        value = withContext(Dispatchers.Default) { decodeDownsampled(file, targetWidthPx) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.md),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            val decoded = bitmap
            if (decoded == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            } else {
                Image(
                    bitmap = decoded.asImageBitmap(),
                    contentDescription = media.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        MediaMeta(media = media)
    }
}

/** Two-pass decode: measure, pick a power-of-two sample size, then decode at that size. */
private fun decodeDownsampled(file: File, targetWidthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= targetWidthPx) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        // Photos are opaque; RGB_565 halves the footprint again for the common case.
        inPreferredConfig = if (bounds.outMimeType == "image/png") {
            Bitmap.Config.ARGB_8888
        } else {
            Bitmap.Config.RGB_565
        }
    }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
}

/* ── Document ──────────────────────────────────────────────────────────────────────────────── */

/**
 * Pages rendered on demand from the staged file, then released.
 *
 * `PdfRenderer` permits one open page at a time, so rendering is serialised through each page's own
 * `produceState`. Scrolling past a page lets its bitmap go, which is what keeps a long document flat
 * in memory instead of linear in page count.
 */
@Composable
private fun DocumentViewer(media: MediaItem, file: File) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val pageWidthPx = remember(configuration.screenWidthDp) {
        with(density) { (configuration.screenWidthDp.dp - HavenSpacing.gutter * 2).roundToPx() }
            .coerceAtLeast(MIN_PAGE_WIDTH_PX)
    }

    val document = remember(file.absolutePath) { PdfDocument.open(file) }
    DisposableEffect(document) {
        onDispose { document.getOrNull()?.close() }
    }

    val opened = document.getOrNull()
    if (opened == null) {
        ErrorState(
            title = "Couldn't open this document",
            message = document.exceptionOrNull()?.message
                ?: "The file is not a readable PDF.",
            code = "DOCUMENT_RENDER_FAILED",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = HavenSpacing.gutter,
            end = HavenSpacing.gutter,
            top = HavenSpacing.md,
            bottom = HavenSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(HavenSpacing.md),
    ) {
        item { MediaMeta(media = media, inset = false) }

        items(count = opened.pageCount, key = { index -> "page-$index" }) { index ->
            PdfPage(
                document = opened,
                index = index,
                widthPx = pageWidthPx,
                totalPages = opened.pageCount,
            )
        }
    }
}

@Composable
private fun PdfPage(
    document: PdfDocument,
    index: Int,
    widthPx: Int,
    totalPages: Int,
) {
    val page by produceState<Bitmap?>(initialValue = null, index, widthPx) {
        value = withContext(Dispatchers.Default) { document.renderPage(index, widthPx) }
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val rendered = page
        if (rendered == null) {
            // Placeholder keeps the scroll position stable while the page is rasterising.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PAGE_PLACEHOLDER_ASPECT),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            }
        } else {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = "Page ${index + 1} of $totalPages",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* ── Generic file ──────────────────────────────────────────────────────────────────────────── */

/**
 * Save-to-device and Open-with (FR-FILE-1..4): an explicit action, a one-time warning, and a hard
 * block when the item's integrity check did not hold.
 */
@Composable
private fun FileViewer(
    media: MediaItem,
    staged: File?,
    viewModel: WatchViewModel,
) {
    val context = LocalContext.current
    var showWarning by rememberSaveable { mutableStateOf(false) }
    var savedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }

    val exportable = media.isExportable()
    val mimeType = media.mimeType ?: FALLBACK_MIME

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Saving\u2026"
        viewModel.exportTo(media, uri, context.contentResolver) { result ->
            status = if (result.isSuccess) {
                savedUri = uri.toString()
                // Persist the grant so "Open with…" still works after process death.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                "Saved to device"
            } else {
                result.exceptionOrNull()?.message ?: "Save failed"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HavenSpacing.gutter),
    ) {
        Spacer(Modifier.height(HavenSpacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaKindGlyph(kind = media.kind, size = 48.dp)
            Spacer(Modifier.width(HavenSpacing.md))
            Column {
                Text(
                    text = media.filenameHint ?: media.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(media.byteLabel(), media.mimeType).joinToString(" \u00b7 "),
                    style = HavenTheme.text.monoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(HavenSpacing.lg))
        Text(
            text = "Haven can't display this type inline. Saving decrypts it to a location you " +
                "choose, where other apps can read it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(HavenSpacing.lg))
        Button(
            onClick = {
                val seen = context
                    .getSharedPreferences(FILE_PREFS, android.content.Context.MODE_PRIVATE)
                    .getBoolean(KEY_WARNING_SEEN, false)
                if (seen) {
                    createDocument.launch(media.filenameHint ?: media.title)
                } else {
                    showWarning = true
                }
            },
            enabled = exportable,
            modifier = Modifier
                .fillMaxWidth()
                .height(HavenSpacing.touchTarget),
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.width(HavenSpacing.sm))
            Text(if (staged != null) "Save to device" else "Unlock and save")
        }

        Spacer(Modifier.height(HavenSpacing.sm))
        OutlinedButton(
            onClick = {
                val uri = savedUri?.let(Uri::parse) ?: return@OutlinedButton
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(Intent.createChooser(view, "Open with\u2026")) }
                    .onFailure {
                        status = "No app on this device can open ${media.fileExtension ?: "this file"}"
                    }
            },
            enabled = savedUri != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(HavenSpacing.touchTarget),
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(HavenSpacing.sm))
            Text("Open with\u2026")
        }

        if (!exportable) {
            Spacer(Modifier.height(HavenSpacing.md))
            Text(
                text = "Export is blocked: this item's content check did not hold (FR-FILE-4).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        status?.let { message ->
            Spacer(Modifier.height(HavenSpacing.md))
            Text(
                text = message,
                style = HavenTheme.text.monoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(HavenSpacing.lg))
        MediaMeta(media = media, inset = false)
    }

    if (showWarning) {
        ConfirmDialog(
            title = "Save this file?",
            body = "Once saved, the decrypted file is readable by any app on this phone and is no " +
                "longer covered by Haven's cache wipe. This warning is only shown once.",
            confirmLabel = "Save anyway",
            onConfirm = {
                context.getSharedPreferences(FILE_PREFS, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_WARNING_SEEN, true)
                    .apply()
                showWarning = false
                createDocument.launch(media.filenameHint ?: media.title)
            },
            onDismiss = { showWarning = false },
        )
    }
}

/* ── Shared bits ───────────────────────────────────────────────────────────────────────────── */

/**
 * Description, residency, and — behind a disclosure — the storage identifier.
 *
 * The identifier used to sit in the open under every title. It is the right thing to have when
 * comparing against a storage provider and the wrong thing to lead with: a reader cannot act on a
 * hash, and printing one under everything makes an archive look like a console. It stays reachable in
 * one tap for the rare moment somebody needs it.
 */
@Composable
private fun MediaMeta(media: MediaItem, inset: Boolean = true) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (inset) HavenSpacing.gutter else 0.dp),
    ) {
        // Copied to a local: `description` is a val in another module, so Kotlin will not smart-cast
        // it after the null check.
        val description = media.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(HavenSpacing.md))
        }
        HorizontalDivider(
            thickness = HavenSpacing.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(HavenSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CacheStatusChip(status = media.contentCacheStatus, compact = true)
            Spacer(Modifier.width(HavenSpacing.md))
            Text(
                text = media.summaryLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        media.pieceRef?.pieceCid?.let { cid ->
            Spacer(Modifier.height(HavenSpacing.md))
            Explain(
                question = "Where this is stored",
                body = "Haven finds this file by its content, not by a location — the reference below " +
                    "is what identifies it across every provider holding a copy.\n\n$cid",
            )
        }
        Spacer(Modifier.height(HavenSpacing.lg))
    }
}

/** Determinate when the size is known, indeterminate when it is not. */
@Composable
private fun ProgressBlock(label: String, progress: Float? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(HavenSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress == null) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        } else {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
        Spacer(Modifier.height(HavenSpacing.md))
        Text(
            text = if (progress == null) label else "$label ${(progress * 100).toInt()}%",
            style = HavenTheme.text.mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val FILE_PREFS = "haven_file_export"
private const val KEY_WARNING_SEEN = "file_warning_seen"
private const val FALLBACK_MIME = "application/octet-stream"
private const val MIN_PAGE_WIDTH_PX = 320
private const val PAGE_PLACEHOLDER_ASPECT = 1f / 1.414f
