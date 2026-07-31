package haven.mobile.feature.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaKind

@OptIn(ExperimentalMaterial3Api::class)
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                ) {
                    Text(
                        text = ready.decryptError,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Button(onClick = { viewModel.decryptItem(item) }) {
                        Text("Retry Decryption")
                    }
                }
            } else {
                when (item.kind) {
                    MediaKind.VIDEO -> {
                        VideoPlayer(item = item)
                    }
                    MediaKind.AUDIO -> {
                        AudioPlayer(item = item)
                    }
                    MediaKind.IMAGE -> {
                        ImageViewer(item = item)
                    }
                    MediaKind.DOCUMENT -> {
                        DocumentViewer(item = item)
                    }
                    MediaKind.FILE -> {
                        FileViewer(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(item: haven.mobile.core.domain.MediaItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = "Video player — Media3 ExoPlayer",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .size(300.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video",
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray,
                )
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        AssistChip(
            onClick = { },
            label = { Text(item.contentCacheStatus.name, fontSize = 10.sp) },
        )
    }
}

@Composable
private fun AudioPlayer(item: haven.mobile.core.domain.MediaItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = "Audio player — Media3 ExoPlayer (background audio + PiP)",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .size(200.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "Audio",
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray,
                )
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        AssistChip(
            onClick = { },
            label = { Text(item.contentCacheStatus.name, fontSize = 10.sp) },
        )
    }
}

@Composable
private fun ImageViewer(item: haven.mobile.core.domain.MediaItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = "Image viewer — Coil",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .size(300.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Image",
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray,
                )
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        AssistChip(
            onClick = { },
            label = { Text(item.contentCacheStatus.name, fontSize = 10.sp) },
        )
    }
}

@Composable
private fun DocumentViewer(item: haven.mobile.core.domain.MediaItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = "PDF viewer — PdfRenderer",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .size(300.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.Article,
                    contentDescription = "Document",
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray,
                )
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        AssistChip(
            onClick = { },
            label = { Text(item.contentCacheStatus.name, fontSize = 10.sp) },
        )
    }
}

@Composable
private fun FileViewer(item: haven.mobile.core.domain.MediaItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = "Generic file — Save to device + Open with",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .size(200.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = "File",
                    modifier = Modifier.size(64.dp),
                    tint = Color.LightGray,
                )
            }
        }
        Spacer(modifier = Modifier.padding(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { /* Save to device via ACTION_CREATE_DOCUMENT */ }) {
                Text("Save to device")
            }
            OutlinedButton(onClick = { /* Open with via Intent.ACTION_VIEW */ }) {
                Text("Open with…")
            }
        }
    }
}
