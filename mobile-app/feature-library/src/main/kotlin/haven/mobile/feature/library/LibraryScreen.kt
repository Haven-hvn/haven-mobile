package haven.mobile.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaKind
import haven.mobile.feature.library.LibraryViewModel.LibraryCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: androidx.navigation.NavController,
    viewModel: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top search — 48dp touch target per design/components.md
            TextField(
                value = (uiState as? LibraryUiState.Ready)?.searchQuery ?: "",
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (uiState) {
                LibraryUiState.Loading -> {
                    // LinearProgress at top + shimmer 4 cards + 6 rows per design/screens/library.md
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    LibraryShimmer()
                }
                LibraryUiState.Empty -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No items — check access",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Connect a wallet to see your library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is LibraryUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Couldn\u2019t load library",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = (uiState as LibraryUiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is LibraryUiState.Ready -> {
                    val ready = uiState as LibraryUiState.Ready
                    val filteredItems = viewModel.filteredItems

                    // CategoryGrid 2×2 — filters LazyColumn, “All” selected by default (design/screens/library.md)
                    // Need to collect selectedCategory as state — expose via uiState trigger
                    val selectedCat = viewModel.selectedCategory
                    val counts = viewModel.categoryCounts
                    CategoryGrid(
                        selected = selectedCat,
                        counts = counts,
                        onSelect = { viewModel.selectCategory(it) },
                    )

                    if (ready.isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (filteredItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No items match your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (ready.isGridLayout) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = rememberLazyGridState(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                LibraryGridItem(item = item, onClick = { navController.navigate("watch/${item.id}") })
                            }
                        }
                    } else {
                        LazyColumn(
                            state = rememberLazyListState(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                LibraryListItem(item = item, onClick = { navController.navigate("watch/${item.id}") })
                            }
                        }
                    }
                }
            }
        }
    }
}

// Design tokens — ContentCache chip colors per tokens.md, 8dp radius chips, 12dp cards
private fun cacheChipColor(status: ContentCacheStatus): Color = when (status) {
    ContentCacheStatus.CACHED -> Color(0xFF1B5E20)   // Cached
    ContentCacheStatus.PARTIAL -> Color(0xFF1565C0) // Fetching
    ContentCacheStatus.EXPIRED -> Color(0xFF6D4C41)  // Expired
    ContentCacheStatus.UNCACHED -> Color(0xFF424242) // Missing
}

private fun cacheChipLabel(status: ContentCacheStatus): String = when (status) {
    ContentCacheStatus.CACHED -> "Cached"
    ContentCacheStatus.PARTIAL -> "Fetching"
    ContentCacheStatus.EXPIRED -> "Expired"
    ContentCacheStatus.UNCACHED -> "Missing"
}

@Composable
private fun CategoryGrid(
    selected: LibraryCategory,
    counts: Map<LibraryCategory, Int>,
    onSelect: (LibraryCategory) -> Unit,
) {
    // 2×2 grid, 16dp gutter, 1:1 cards, 12dp radius per components.md
    val categories = listOf(LibraryCategory.ALL, LibraryCategory.VIDEO, LibraryCategory.AUDIO, LibraryCategory.DOCUMENT)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (row in categories.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (cat in row) {
                    CategoryCard(
                        category = cat,
                        count = counts[cat] ?: 0,
                        selected = cat == selected,
                        onClick = { onSelect(cat) },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: LibraryCategory,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (category) {
        LibraryCategory.ALL -> Icons.Default.AllInclusive
        LibraryCategory.VIDEO -> Icons.Default.VideoLibrary
        LibraryCategory.AUDIO -> Icons.Default.AudioFile
        LibraryCategory.DOCUMENT -> Icons.Default.Article
        LibraryCategory.IMAGE -> Icons.Default.Image
        LibraryCategory.FILE -> Icons.Default.InsertDriveFile
    }
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(imageVector = icon, contentDescription = category.name, modifier = Modifier.size(24.dp), tint = content)
            Spacer(Modifier.height(8.dp))
            Text(text = category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(text = "$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryShimmer() {
    // Shimmer 4 cards + 6 rows placeholder (design/screens/library.md) — no aurora/glow, just surfaceVariant blocks
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(2) {
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(2) {
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        repeat(6) {
            Box(modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@Composable
private fun LibraryGridItem(item: haven.mobile.core.domain.MediaItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.pieceRef?.pieceCid?.take(12)?.let { "${it}..." } ?: item.id.take(12),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            val c = cacheChipColor(item.contentCacheStatus)
            AssistChip(
                onClick = {},
                label = { Text(cacheChipLabel(item.contentCacheStatus), fontSize = 10.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = c.copy(alpha = 0.12f), labelColor = c),
                border = null,
                modifier = Modifier.height(32.dp),
            )
        }
    }
}

@Composable
private fun LibraryListItem(item: haven.mobile.core.domain.MediaItem, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = when (item.kind) {
                MediaKind.VIDEO -> Icons.Default.VideoLibrary
                MediaKind.AUDIO -> Icons.Default.AudioFile
                MediaKind.IMAGE -> Icons.Default.Image
                MediaKind.DOCUMENT -> Icons.Default.Article
                MediaKind.FILE -> Icons.Default.InsertDriveFile
            }
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = item.kind.name, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(item.pieceRef?.pieceCid?.take(10)?.let { "$it…" }, item.sizeBytes?.let { "${it / 1024} KB" }).joinToString(" · ").ifBlank { item.mimeType ?: "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            val c = cacheChipColor(item.contentCacheStatus)
            AssistChip(
                onClick = {},
                label = { Text(cacheChipLabel(item.contentCacheStatus), fontSize = 10.sp) },
                colors = AssistChipDefaults.assistChipColors(containerColor = c.copy(alpha = 0.12f), labelColor = c),
                border = null,
                modifier = Modifier.height(32.dp),
            )
        }
    }
}
