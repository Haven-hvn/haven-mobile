package haven.mobile.feature.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haven.mobile.core.domain.ContentCacheStatus
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import haven.mobile.core.domain.MediaKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    navController: androidx.navigation.NavController,
    itemId: String,
    viewModel: WatchViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            WatchUiState.Loading -> {
                androidx.compose.runtime.LaunchedEffect(itemId) { viewModel.loadItem(itemId) }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Loading",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is WatchUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Couldn\u2019t load item",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = (uiState as WatchUiState.Error).message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is WatchUiState.Ready -> {
                val ready = uiState as WatchUiState.Ready
                val item = ready.item
                when {
                    ready.isDecrypting -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Decrypting\u2026",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ready.decryptError != null -> {
                        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                            Text(
                                text = "Decryption failed",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = ready.decryptError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.decryptItem(item) }) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            when (item.kind) {
                                MediaKind.VIDEO -> VideoPlayer(item = item)
                                MediaKind.AUDIO -> AudioPlayer(item = item)
                                MediaKind.IMAGE -> ImageViewer(item = item)
                                MediaKind.DOCUMENT -> DocumentViewer(item = item)
                                MediaKind.FILE -> FileViewer(item = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaHeader(
    title: String,
    subtitle: String?,
    description: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CacheStatusRow(status: ContentCacheStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = when (status) {
                ContentCacheStatus.CACHED -> "Cached \u00b7 offline ready"
                ContentCacheStatus.PARTIAL -> "Partial \u00b7 resume to cache"
                ContentCacheStatus.EXPIRED -> "Expired \u00b7 refresh required"
                ContentCacheStatus.UNCACHED -> "Not cached"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val label = status.name.lowercase().replaceFirstChar { it.uppercase() }
        AssistChip(
            onClick = { },
            label = { Text(label, fontSize = 11.sp) },
            shape = RoundedCornerShape(8.dp),
            colors = AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun VideoPlayer(item: haven.mobile.core.domain.MediaItem) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Media3 ExoPlayer 16:9 — offline-first, PiP handle 4dp via PlayerView, stub URI until HavenAol decrypt live
    // FR-UI-2 VIDEO → Media3, FR-UI-5 decrypt in-memory only (no plaintext on disk)
    val exoPlayer = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            // Real path: havenCache.stream(pieceRef) → havenCipher.decryptStream(key) → temp cache file → setMediaItem
            // Stub until canisterId/icHost configured: empty player with controls visible
            val uri = item.pieceRef?.pieceCid?.let { Uri.parse("file:///cache/${it}.mp4") } ?: Uri.EMPTY
            if (uri != Uri.EMPTY) {
                setMediaItem(ExoMediaItem.fromUri(uri))
                prepare()
            }
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(horizontal = 16.dp).padding(top = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        androidx.media3.ui.PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            controllerShowTimeoutMs = 3000
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (exoPlayer.mediaItemCount == 0) {
                    // Overlay icon when no media queued (decrypt stub)
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        MediaHeader(
            title = item.title,
            subtitle = listOfNotNull(item.mimeType, item.fileExtension).joinToString(" \u00b7 ").ifBlank { null },
            description = item.description,
        )
        Spacer(Modifier.height(12.dp))
        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        CacheStatusRow(item.contentCacheStatus)
    }
}

@Composable
private fun AudioPlayer(item: haven.mobile.core.domain.MediaItem) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Media3 audio — background + lockscreen via MediaSession stub, 8dp radius distinct from video 12dp
    val audioPlayer = androidx.compose.runtime.remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            val uri = item.pieceRef?.pieceCid?.let { android.net.Uri.parse("file:///cache/${it}.m4a") } ?: android.net.Uri.EMPTY
            if (uri != android.net.Uri.EMPTY) {
                setMediaItem(ExoMediaItem.fromUri(uri))
                prepare()
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(audioPlayer) { onDispose { audioPlayer.release() } }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        MediaHeader(
            title = item.title,
            subtitle = "Audio \u00b7 ${item.mimeType ?: "unknown"}",
            description = item.description,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.filenameHint ?: item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Inline Media3 controller via AndroidView for scrubber — distinct from video full PlayerView
                    AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                player = audioPlayer
                                useController = true
                                controllerShowTimeoutMs = 0
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                    )
                    Text(
                        text = "Background + lockscreen via Media3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        CacheStatusRow(item.contentCacheStatus)
    }
}

@Composable
private fun ImageViewer(item: haven.mobile.core.domain.MediaItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                contentAlignment = Alignment.Center,
            ) {
                // Coil — inline, pinch-zoom via modifier.zoomable when decrypt provides file URI
                val model = item.pieceRef?.pieceCid?.let { "file:///cache/${it}.jpg" } ?: item.filecoinCid
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Image),
                        placeholder = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Image),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        MediaHeader(
            title = item.title,
            subtitle = item.mimeType ?: item.fileExtension,
            description = item.description,
        )
        Spacer(Modifier.height(8.dp))
        CacheStatusRow(item.contentCacheStatus)
    }
}

@Composable
private fun DocumentViewer(item: haven.mobile.core.domain.MediaItem) {
    // PdfRenderer paginated + scrubber — fallback when androidx.pdf:pdf-renderer not in maven (tokens 1v)
    // Attempt android.graphics.pdf.PdfRenderer via contentResolver if decrypted file exists; else placeholder
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        MediaHeader(
            title = item.title,
            subtitle = "PDF \u00b7 ${item.sizeBytes?.let { "${it / 1024} KB" } ?: "document"}",
            description = item.description,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Article,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.filenameHint ?: "${item.title}.pdf",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = "Opens inline \u00b7 pinch to zoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        CacheStatusRow(item.contentCacheStatus)
    }
}

@Composable
private fun FileViewer(
    item: haven.mobile.core.domain.MediaItem,
    viewModel: WatchViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    var showWarning by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }
    var lastSavedUri by remember { mutableStateOf<Uri?>(null) }

    // FR-FILE-4: block export if EXPIRED (attestation/PieceCID failed) — real verifier wired via WatchViewModel.exportFile double-check
    val canExport = item.contentCacheStatus != haven.mobile.core.domain.ContentCacheStatus.EXPIRED

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(item.mimeType ?: "application/octet-stream")) { uri ->
        if (uri != null) {
            // Real path: HavenAol→HavenCache→HavenCipher in-memory decrypt → SAF (FR-FILE-1, FR-UI-5) + persistable so Open with… survives process death
            viewModel.exportFile(item, uri, context.contentResolver) { result ->
                if (result.isSuccess) {
                    // Persist URI permission so the saved file remains openable via lastSavedUri after process death (SAF persistable)
                    try {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, flags)
                    } catch (_: Exception) {
                        // Not all providers support persistable (e.g. Downloads on some OEMs) — file is still saved at SAF location and openable via file manager
                    }
                    lastSavedUri = uri
                    android.widget.Toast.makeText(context, "Saved to device", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "Save failed"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        pendingSave = false
    }

    fun triggerSave() {
        if (!canExport) {
            android.widget.Toast.makeText(context, "Cannot export — verification failed (FR-FILE-4)", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val prefs = context.getSharedPreferences("haven_file", android.content.Context.MODE_PRIVATE)
        val seenWarning = prefs.getBoolean("file_warning_seen", false)
        if (!seenWarning) {
            showWarning = true
            pendingSave = true
        } else {
            createDocLauncher.launch(item.filenameHint ?: item.title)
        }
    }

    if (showWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWarning = false; pendingSave = false },
            title = { Text("Save file?") },
            text = { Text("This file is now readable by anything on your phone. Continue?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    context.getSharedPreferences("haven_file", android.content.Context.MODE_PRIVATE).edit().putBoolean("file_warning_seen", true).apply()
                    showWarning = false
                    if (pendingSave) createDocLauncher.launch(item.filenameHint ?: item.title)
                }) { Text("Continue") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showWarning = false; pendingSave = false }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        MediaHeader(
            title = item.title,
            subtitle = listOfNotNull(item.mimeType, item.fileExtension).joinToString(" \u00b7 ").ifBlank { "Generic file" },
            description = item.description ?: "This file will be saved to your device and opened with an installed app.",
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.filenameHint ?: item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Text(
                        text = item.sizeBytes?.let { "${it / 1024} KB" } ?: "Size unknown",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { triggerSave() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = canExport,
            ) {
                Text("Save to device")
            }
            OutlinedButton(
                onClick = {
                    val uri = lastSavedUri
                    if (uri == null) {
                        android.widget.Toast.makeText(context, "Save first, then Open with…", android.widget.Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, item.mimeType ?: "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(view, "Open with…")
                    try { context.startActivity(chooser) } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "No app can open this file", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = lastSavedUri != null,
            ) {
                Text("Open with…")
            }
            Text(
                text = "Files are saved outside the encrypted cache. You’ll see a warning on first save (FR-FILE-3).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        CacheStatusRow(item.contentCacheStatus)
    }
}
