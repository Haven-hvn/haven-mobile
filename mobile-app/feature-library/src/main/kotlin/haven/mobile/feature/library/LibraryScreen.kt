package haven.mobile.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.design.component.EmptyState
import haven.mobile.core.design.component.ErrorState
import haven.mobile.core.design.component.HavenSearchField
import haven.mobile.core.design.component.HavenTopBar
import haven.mobile.core.design.component.LibrarySkeleton
import haven.mobile.core.design.component.MediaCard
import haven.mobile.core.design.component.MediaRow

/**
 * The library.
 *
 * Reads one stream (the Room mirror), so it behaves the same on a train as on wifi. The header
 * is fixed and only the collection scrolls, which is what keeps search and the kind filters
 * reachable in a list of hundreds.
 *
 * Grid is the default: these are media items, and a 16:9 plate tells you more at a glance than a
 * row of text. List is one tap away for people who want density and full CIDs.
 */
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HavenTopBar(
            title = "Library",
            subtitle = (uiState as? LibraryUiState.Ready)?.let { state ->
                when {
                    state.totalCount == 0 -> null
                    // Residency is the fact worth surfacing at a glance; the dapp badges it per row,
                    // and a phone benefits from the total too.
                    else -> "${state.totalCount} items \u00b7 ${state.offlineCount} offline"
                }
            },
            actions = {
                val ready = uiState as? LibraryUiState.Ready
                if (ready != null) {
                    IconButton(onClick = { viewModel.toggleLayout() }) {
                        Icon(
                            imageVector = if (ready.layout == LibraryLayout.GRID) {
                                Icons.Default.ViewList
                            } else {
                                Icons.Default.GridView
                            },
                            contentDescription = if (ready.layout == LibraryLayout.GRID) {
                                "Switch to list view"
                            } else {
                                "Switch to grid view"
                            },
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.refresh() },
                    enabled = uiState !is LibraryUiState.Loading,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh library",
                    )
                }
            },
        )

        when (val state = uiState) {
            LibraryUiState.Loading -> LibrarySkeleton()

            LibraryUiState.Disconnected -> EmptyState(
                icon = Icons.Default.LibraryAddCheck,
                title = "No wallet connected",
                body = "Connect a wallet to load the content it has access to.",
            )

            is LibraryUiState.Error -> ErrorState(
                title = "Couldn't open your library",
                message = state.message,
                onRetry = { viewModel.retry() },
            )

            is LibraryUiState.Ready -> {
                if (state.isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
                state.refreshError?.let { message ->
                    RefreshErrorBanner(message = message, onDismiss = { viewModel.dismissRefreshError() })
                }

                LibraryHeader(
                    query = state.query,
                    counts = state.counts,
                    selected = state.category,
                    offlineOnly = state.offlineOnly,
                    offlineCount = state.offlineCount,
                    onQueryChange = viewModel::setQuery,
                    onCategoryChange = viewModel::selectCategory,
                    onToggleOffline = viewModel::toggleOfflineOnly,
                )

                when {
                    state.totalCount == 0 -> EmptyState(
                        icon = Icons.Default.LibraryAddCheck,
                        title = "Nothing to read yet",
                        // You join a community and that is how you read — so the fix for an empty
                        // library is a community, not a retry button.
                        body = "Your library is everything the communities you belong to have " +
                            "published. Join one and its archive appears here.",
                        actionLabel = "Find a community",
                        onAction = { navController.navigate("collections") },
                    )

                    state.items.isEmpty() -> EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        body = when {
                            state.offlineOnly && state.query.isBlank() ->
                                "Nothing in ${state.category.label.lowercase()} is available offline yet. " +
                                    "Open something once and it stays."
                            state.query.isBlank() ->
                                "Nothing in ${state.category.label.lowercase()} in this library."
                            else ->
                                "Nothing matches \u201c${state.query}\u201d in ${state.category.label.lowercase()}."
                        },
                        actionLabel = "Clear filters",
                        onAction = {
                            viewModel.setQuery("")
                            viewModel.selectCategory(LibraryCategory.ALL)
                            if (state.offlineOnly) viewModel.toggleOfflineOnly()
                        },
                    )

                    state.layout == LibraryLayout.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = HavenSpacing.gutter,
                            end = HavenSpacing.gutter,
                            top = HavenSpacing.md,
                            bottom = HavenSpacing.xxl,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(HavenSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(HavenSpacing.md),
                    ) {
                        items(items = state.items, key = { it.id }) { item ->
                            MediaCard(
                                item = item,
                                onClick = { navController.navigate("watch/${item.id}") },
                            )
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = HavenSpacing.xxl),
                    ) {
                        items(items = state.items, key = { it.id }) { item ->
                            MediaRow(
                                item = item,
                                onClick = { navController.navigate("watch/${item.id}") },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = HavenSpacing.gutter),
                                thickness = HavenSpacing.hairline,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search, an offline filter, and kind filters.
 *
 * Counts live on the chips so an empty category is visible before tapping it, and the offline chip
 * carries its own count — the answer to "how much of this works on the train" without leaving.
 */
@Composable
private fun LibraryHeader(
    query: String,
    counts: Map<LibraryCategory, Int>,
    selected: LibraryCategory,
    offlineOnly: Boolean,
    offlineCount: Int,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (LibraryCategory) -> Unit,
    onToggleOffline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HavenSpacing.gutter),
    ) {
        HavenSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search this library",
        )
        Spacer(Modifier.height(HavenSpacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(HavenSpacing.sm),
        ) {
            // Residency first: it is a different axis from kind, and the one a reader reaches for
            // when the signal drops.
            FilterChip(
                selected = offlineOnly,
                onClick = onToggleOffline,
                label = {
                    Text(
                        text = if (offlineCount > 0) "Offline $offlineCount" else "Offline",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )

            LibraryCategory.entries.forEach { category ->
                val count = counts[category] ?: 0
                FilterChip(
                    selected = category == selected,
                    onClick = { onCategoryChange(category) },
                    enabled = count > 0 || category == LibraryCategory.ALL,
                    label = {
                        Text(
                            text = if (count > 0) "${category.label} $count" else category.label,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        Spacer(Modifier.height(HavenSpacing.md))
    }
}

/**
 * A failed refresh is not a failed screen: the mirror is still valid, so this is a dismissible
 * banner rather than an error state that throws the list away.
 */
@Composable
private fun RefreshErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.sm),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = HavenSpacing.md, end = HavenSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = HavenSpacing.sm)) {
                Text(
                    text = "Showing cached library",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = HavenTheme.text.monoSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(HavenSpacing.sm))
            IconButton(onClick = onDismiss, modifier = Modifier.size(HavenSpacing.touchTarget)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
