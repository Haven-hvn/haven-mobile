package haven.mobile.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
fun LibraryScreen(
    navController: androidx.navigation.NavController,
    viewModel: LibraryViewModel = androidx.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredItems = viewModel.filteredItems

    PullToRefreshBox(
        isRefreshing = (uiState as? LibraryUiState.Ready)?.isRefreshing ?: false,
        onRefresh = { viewModel.refreshLibrary() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            TextField(
                value = (uiState as? LibraryUiState.Ready)?.searchQuery ?: "",
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (uiState) {
                LibraryUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                LibraryUiState.Empty -> {
                    Text(
                        text = "No items yet. Connect a wallet to see your library.",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                is LibraryUiState.Error -> {
                    Text(
                        text = (uiState as LibraryUiState.Error).message,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                is LibraryUiState.Ready -> {
                    val ready = uiState as LibraryUiState.Ready

                    if (filteredItems.isEmpty()) {
                        Text(
                            text = "No items match your search.",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else if (ready.isGridLayout) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            state = rememberLazyGridState(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                LibraryGridItem(item = item)
                            }
                        }
                    } else {
                        LazyColumn(
                            state = rememberLazyListState(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                LibraryListItem(item = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGridItem(item: haven.mobile.core.domain.MediaItem) {
    Card(
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            // MediaKind icon
            val icon = when (item.kind) {
                MediaKind.VIDEO -> Icons.Default.VideoLibrary
                MediaKind.AUDIO -> Icons.Default.AudioFile
                MediaKind.IMAGE -> Icons.Default.Image
                MediaKind.DOCUMENT -> Icons.Default.Article
                MediaKind.FILE -> Icons.Default.InsertDriveFile
            }
            Icon(
                imageVector = icon,
                contentDescription = item.kind.name,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.padding(4.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )

            Spacer(modifier = Modifier.padding(2.dp))

            // Cache status chip
            val cacheChipColor = when (item.contentCacheStatus) {
                ContentCacheStatus.CACHED -> Color(0xFF4CAF50)
                ContentCacheStatus.PARTIAL -> Color(0xFFFFC107)
                ContentCacheStatus.UNCACHED -> Color(0xFFF44336)
                ContentCacheStatus.EXPIRED -> Color(0xFF9E9E9E)
            }
            AssistChip(
                onClick = { },
                label = { Text(item.contentCacheStatus.name, fontSize = 10.sp) },
                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = cacheChipColor.copy(alpha = 0.15f),
                    labelColor = cacheChipColor,
                ),
            )
        }
    }
}

@Composable
private fun LibraryListItem(item: haven.mobile.core.domain.MediaItem) {
    Card(
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // MediaKind icon
            val icon = when (item.kind) {
                MediaKind.VIDEO -> Icons.Default.VideoLibrary
                MediaKind.AUDIO -> Icons.Default.AudioFile
                MediaKind.IMAGE -> Icons.Default.Image
                MediaKind.DOCUMENT -> Icons.Default.Article
                MediaKind.FILE -> Icons.Default.InsertDriveFile
            }
            Icon(
                imageVector = icon,
                contentDescription = item.kind.name,
                modifier = Modifier.size(32.dp),
            )

            Spacer(modifier = Modifier.padding(horizontal = 8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = item.description ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }

            // Cache status chip
            val cacheChipColor = when (item.contentCacheStatus) {
                ContentCacheStatus.CACHED -> Color(0xFF4CAF50)
                ContentCacheStatus.PARTIAL -> Color(0xFFFFC107)
                ContentCacheStatus.UNCACHED -> Color(0xFFF44336)
                ContentCacheStatus.EXPIRED -> Color(0xFF9E9E9E)
            }
            AssistChip(
                onClick = { },
                label = { Text(item.contentCacheStatus.name, fontSize = 10.sp) },
                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                    containerColor = cacheChipColor.copy(alpha = 0.15f),
                    labelColor = cacheChipColor,
                ),
            )
        }
    }
}
