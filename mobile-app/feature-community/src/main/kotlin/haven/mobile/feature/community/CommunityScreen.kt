package haven.mobile.feature.community

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
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
import haven.mobile.core.design.component.AttestationBadge
import haven.mobile.core.design.component.AttestationState
import haven.mobile.core.design.component.EmptyState
import haven.mobile.core.design.component.ErrorState
import haven.mobile.core.design.component.HavenSearchField
import haven.mobile.core.design.component.HavenTopBar
import haven.mobile.core.design.component.LibrarySkeleton
import haven.mobile.core.design.component.MediaRow
import haven.mobile.core.design.component.MonoIdentifier

/**
 * Community feed.
 *
 * Same rows as the library, one addition: every item carries its attestation verdict. That badge
 * is the entire point of this screen — a feed of content published by other wallets is only
 * useful if you can tell what has been signed by the canister and what has not.
 *
 * A failed verdict is called out with a banner above the list rather than being left to the
 * individual row, because "one of these items is lying to you" deserves more than a small chip.
 */
@Composable
fun CommunityScreen(
    navController: NavController,
    viewModel: CommunityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HavenTopBar(
            title = "Community",
            subtitle = (uiState as? CommunityUiState.Ready)?.let { state ->
                val verified = state.attestations.values.count { it == AttestationState.VERIFIED }
                if (state.totalCount == 0) null else "$verified of ${state.totalCount} verified"
            },
            actions = {
                IconButton(
                    onClick = { viewModel.refresh() },
                    enabled = uiState !is CommunityUiState.Loading,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh feed")
                }
            },
        )

        when (val state = uiState) {
            CommunityUiState.Loading -> LibrarySkeleton()

            CommunityUiState.Disconnected -> EmptyState(
                icon = Icons.Default.Groups,
                title = "No wallet connected",
                body = "Connect a wallet to see what the communities it belongs to have published.",
            )

            is CommunityUiState.Error -> ErrorState(
                title = "Couldn't load the feed",
                message = state.message,
                onRetry = { viewModel.retry() },
            )

            is CommunityUiState.Ready -> {
                if (state.isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }

                Column(modifier = Modifier.padding(horizontal = HavenSpacing.gutter)) {
                    HavenSearchField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = "Search the feed or a publisher",
                    )
                    Spacer(Modifier.height(HavenSpacing.md))
                }

                val failedCount = state.attestations.values.count { it == AttestationState.FAILED }
                if (failedCount > 0) {
                    UntrustedBanner(count = failedCount)
                }
                state.refreshError?.let { message ->
                    FeedNotice(
                        title = "Showing cached feed",
                        detail = message,
                        onDismiss = { viewModel.dismissRefreshError() },
                    )
                }

                when {
                    state.totalCount == 0 -> EmptyState(
                        icon = Icons.Default.Groups,
                        title = "No community content yet",
                        body = "Items published to the gates this wallet can open will show up here.",
                        actionLabel = "Check again",
                        onAction = { viewModel.refresh() },
                    )

                    state.items.isEmpty() -> EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        body = "Nothing in the feed matches \u201c${state.query}\u201d.",
                        actionLabel = "Clear search",
                        onAction = { viewModel.setQuery("") },
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = HavenSpacing.xxl),
                    ) {
                        items(items = state.items, key = { it.id }) { item ->
                            Column {
                                MediaRow(
                                    item = item,
                                    onClick = { navController.navigate("watch/${item.id}") },
                                    attestation = state.attestations[item.id],
                                )
                                Row(
                                    modifier = Modifier.padding(
                                        start = HavenSpacing.gutter + HavenSpacing.glyph + HavenSpacing.md,
                                        end = HavenSpacing.gutter,
                                        bottom = HavenSpacing.sm,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Published by ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    // `creator_handle` is the only human identity an entity carries, so
                                    // it is preferred over the address whenever the publisher set one.
                                    val handle = item.creatorHandle
                                    if (!handle.isNullOrBlank()) {
                                        Text(
                                            text = handle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    } else {
                                        MonoIdentifier(value = item.owner, head = 6, tail = 4)
                                    }
                                }
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
}

/** Attestation failures are a trust event, not a formatting detail. */
@Composable
private fun UntrustedBanner(count: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.sm),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(HavenSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttestationBadge(state = AttestationState.FAILED, compact = true)
            Spacer(Modifier.width(HavenSpacing.md))
            Text(
                text = if (count == 1) {
                    "1 item failed its attestation check. Treat it as untrusted."
                } else {
                    "$count items failed their attestation check. Treat them as untrusted."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun FeedNotice(
    title: String,
    detail: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.sm),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = HavenSpacing.md, end = HavenSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = HavenSpacing.sm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail,
                    style = HavenTheme.text.monoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(HavenSpacing.sm))
            IconButton(onClick = onDismiss, modifier = Modifier.size(HavenSpacing.touchTarget)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
