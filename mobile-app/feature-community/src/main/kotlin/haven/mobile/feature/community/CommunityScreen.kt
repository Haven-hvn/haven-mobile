package haven.mobile.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
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
import haven.mobile.core.domain.Attestation
import haven.mobile.core.domain.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    navController: androidx.navigation.NavController,
    viewModel: CommunityViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredItems = viewModel.filteredItems

    Box(
        modifier = Modifier.fillMaxSize(),
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
                                val failedIds = (uiState as? CommunityUiState.Ready)?.failedVerificationIds ?: emptySet()
                                CommunityListItem(item = item, isFailed = item.id in failedIds)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityListItem(item: MediaItem, isFailed: Boolean = false) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                // Attestation badge — 3 distinct states per design/components.md (never collapse latter two)
                // Tokens: Verified #2E7D32 / Unverified #616161 / Failed #C62828 ; 8dp chips, 32dp height
                val badgeColor = when {
                    isFailed -> Color(0xFFC62828) // FailedVerification
                    item.attestation == null -> Color(0xFF616161) // Unverified
                    else -> Color(0xFF2E7D32) // Verified
                }
                val badgeLabel = when {
                    isFailed -> "Failed"
                    item.attestation == null -> "Unverified"
                    else -> "Verified"
                }
                androidx.compose.material3.AssistChip(
                    onClick = {},
                    label = { Text(badgeLabel, fontSize = 10.sp) },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = badgeColor.copy(alpha = 0.12f),
                        labelColor = badgeColor,
                    ),
                    border = null,
                    modifier = Modifier.height(32.dp),
                )
            }

            Spacer(modifier = Modifier.padding(2.dp))

            Text(
                text = "${item.kind}  ${item.description ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )

            // Gate info
            item.gate?.let { gate ->
                Spacer(modifier = Modifier.padding(2.dp))
                Text(
                    text = "Gate: ${gate.chain} / ${gate.tokenStandard.name} (threshold: ${gate.threshold})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                )
            }
        }
    }
}
