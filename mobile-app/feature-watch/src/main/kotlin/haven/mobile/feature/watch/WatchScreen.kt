package haven.mobile.feature.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WatchScreen(
    navController: androidx.navigation.NavController,
    itemId: String,
    viewModel: WatchViewModel = androidx.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (uiState) {
        WatchUiState.Loading -> {
            viewModel.loadItem(itemId)
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        WatchUiState.Error -> {
            Text(
                text = (uiState as WatchUiState.Error).message,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        is WatchUiState.Ready -> {
            val ready = uiState as WatchUiState.Ready
            val item = ready.item

            if (ready.isDecrypting) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    text = "Decrypting content…",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else if (ready.decryptError != null) {
                Text(
                    text = ready.decryptError,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                when (item.kind) {
                    haven.mobile.core.domain.MediaKind.VIDEO -> {
                        VideoPlayer(item = item)
                    }
                    haven.mobile.core.domain.MediaKind.AUDIO -> {
                        AudioPlayer(item = item)
                    }
                    haven.mobile.core.domain.MediaKind.IMAGE -> {
                        ImageViewer(item = item)
                    }
                    haven.mobile.core.domain.MediaKind.DOCUMENT -> {
                        DocumentViewer(item = item)
                    }
                    haven.mobile.core.domain.MediaKind.FILE -> {
                        FileViewer(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(item: haven.mobile.core.domain.MediaItem) {
    // Media3 ExoPlayer placeholder — to be implemented with ExoPlayer surface
    Text(
        text = "Video: ${item.title}",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

@Composable
private fun AudioPlayer(item: haven.mobile.core.domain.MediaItem) {
    // Media3 ExoPlayer placeholder — to be implemented with ExoPlayer for audio
    Text(
        text = "Audio: ${item.title}",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

@Composable
private fun ImageViewer(item: haven.mobile.core.domain.MediaItem) {
    // Coil placeholder — to be implemented with Coil image loading
    Text(
        text = "Image: ${item.title}",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

@Composable
private fun DocumentViewer(item: haven.mobile.core.domain.MediaItem) {
    // PdfRenderer placeholder — to be implemented with PdfRenderer
    Text(
        text = "Document: ${item.title}",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

@Composable
private fun FileViewer(item: haven.mobile.core.domain.MediaItem) {
    // Save-to-device + Open-with placeholder
    Text(
        text = "File: ${item.title}",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}
