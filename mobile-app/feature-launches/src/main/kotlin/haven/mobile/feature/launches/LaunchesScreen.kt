package haven.mobile.feature.launches

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
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
import androidx.compose.ui.text.style.TextOverflow
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
import haven.mobile.core.design.component.MonoIdentifier
import haven.mobile.core.domain.LaunchStage

/**
 * Global launches.
 *
 * Parity with `haven-dapp`'s `UpcomingDrops`: every live drip stage, not just what this wallet
 * owns or can open. Each row opens the stage in watch (pump sheet until unlock, premiere after),
 * where the pump itself happens in-app — rows never link out to a mint.club page.
 *
 * No demo rows: when Arkiv holds no drips the screen says so, rather than showing sample content
 * a reader could mistake for something real to buy.
 */
@Composable
fun LaunchesScreen(
    navController: NavController,
    viewModel: LaunchesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        HavenTopBar(
            title = "Launches",
            subtitle = (uiState as? LaunchesUiState.Ready)?.let { state ->
                if (state.totalCount == 0) null else "${state.totalCount} stages live"
            },
            actions = {
                IconButton(
                    onClick = { viewModel.refresh() },
                    enabled = uiState !is LaunchesUiState.Loading,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh launches")
                }
            },
        )

        when (val state = uiState) {
            LaunchesUiState.Loading -> LibrarySkeleton()

            is LaunchesUiState.Error -> ErrorState(
                title = "Couldn't load launches",
                message = state.message,
                onRetry = { viewModel.retry() },
            )

            is LaunchesUiState.Ready -> {
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
                        placeholder = "Search launches, tokens or creators",
                    )
                    Spacer(Modifier.height(HavenSpacing.md))
                }
                state.refreshError?.let { message ->
                    LaunchesNotice(
                        title = "Showing cached launches",
                        detail = message,
                        onDismiss = { viewModel.dismissRefreshError() },
                    )
                }

                when {
                    state.totalCount == 0 -> EmptyState(
                        icon = Icons.Default.RocketLaunch,
                        title = "No launches yet",
                        body = "Token-gated premieres appear here as creators publish them — " +
                            "no gate membership needed to browse.",
                        actionLabel = "Check again",
                        onAction = { viewModel.refresh() },
                    )

                    state.items.isEmpty() -> EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No matches",
                        body = "Nothing launching matches \u201c${state.query}\u201d.",
                        actionLabel = "Clear search",
                        onAction = { viewModel.setQuery("") },
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = HavenSpacing.xxl),
                    ) {
                        items(items = state.items, key = { it.id }) { stage ->
                            LaunchRow(
                                stage = stage,
                                onOpen = { navController.navigate("watch/${stage.id}") },
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

@Composable
private fun LaunchRow(
    stage: LaunchStage,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stage.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stageLabel(stage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stage.gateToken.isNotBlank()) {
                    MonoIdentifier(value = stage.gateToken, head = 6, tail = 4)
                    stage.gateChain?.let { chain ->
                        Text(
                            text = " · ${chain.label}",
                            style = HavenTheme.text.monoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (!stage.creatorHandle.isNullOrBlank()) {
                    Text(
                        text = "by ${stage.creatorHandle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "Stage 2/3 · unlocks at $5M". Mirrors the dapp `UpcomingDrops` row line. */
fun stageLabel(stage: LaunchStage): String =
    "Stage ${stage.dripIndex + 1}/${stage.dripTotal} · unlocks at ${formatUsdCompact(stage.marketCapTargetUsd)}"

/** `$1.5M` / `$800K` / `$950`. Mirrors dapp `formatUsdCompact`. */
fun formatUsdCompact(amount: Long): String = when {
    amount >= 1_000_000_000 -> "$${trimLaunchZeros(amount / 1_000_000_000.0)}B"
    amount >= 1_000_000 -> "$${trimLaunchZeros(amount / 1_000_000.0)}M"
    amount >= 1_000 -> "$${trimLaunchZeros(amount / 1_000.0)}K"
    else -> "$$amount"
}

private fun trimLaunchZeros(n: Double): String {
    val one = (kotlin.math.round(n * 10) / 10.0).toString()
    return if (one.endsWith(".0")) one.dropLast(2) else one
}

@Composable
private fun LaunchesNotice(
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
