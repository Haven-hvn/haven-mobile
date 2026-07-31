package haven.mobile.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
    Text(
        text = item.title,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LibraryListItem(item: haven.mobile.core.domain.MediaItem) {
    Text(
        text = item.title,
        style = MaterialTheme.typography.bodyMedium,
    )
}