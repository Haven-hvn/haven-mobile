package haven.mobile.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
fun CommunityScreen(
    navController: androidx.navigation.NavController,
    viewModel: CommunityViewModel = androidx.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredItems = viewModel.filteredItems

    PullToRefreshBox(
        isRefreshing = (uiState as? CommunityUiState.Ready)?.isRefreshing ?: false,
        onRefresh = { viewModel.refreshCommunity() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            TextField(
                value = (uiState as? CommunityUiState.Ready)?.searchQuery ?: "",
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search community") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (uiState) {
                CommunityUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                CommunityUiState.Empty -> {
                    Text(
                        text = "No community items yet. Connect a wallet to explore.",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                is CommunityUiState.Error -> {
                    Text(
                        text = (uiState as CommunityUiState.Error).message,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                is CommunityUiState.Ready -> {
                    val ready = uiState as CommunityUiState.Ready

                    if (filteredItems.isEmpty()) {
                        Text(
                            text = "No items match your search.",
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else {
                        LazyColumn(
                            state = rememberLazyListState(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                CommunityListItem(item = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityListItem(item: haven.mobile.core.domain.MediaItem) {
    val attestationBadge = if (item.attestation != null) {
        "Verified"
    } else {
        "Unverified"
    }

    Column {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "${item.kind} • $attestationBadge",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}