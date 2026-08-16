package haven.mobile.feature.collections

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import haven.mobile.core.collections.Access
import haven.mobile.core.collections.CollectionAccess
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.design.component.ErrorState
import haven.mobile.core.design.component.Explain
import haven.mobile.core.design.component.HavenStatusChip
import haven.mobile.core.design.component.HavenTopBar
import haven.mobile.core.design.component.SectionHeader

/**
 * Collections — the way in.
 *
 * This is the mobile answer to "I connected a wallet and my library is empty". It lists the
 * communities Haven knows about, says plainly whether this wallet is in, and links out to acquire
 * what a community asks for.
 *
 * The vocabulary is the point. The web surface names contracts, chains, thresholds and `balanceOf`
 * because it is written for people building on Haven. Nothing here does: a reader sees a name, a
 * sentence, "You're in" or what to hold, and a button. The mechanics are real and unchanged — they
 * are simply not the reader's problem.
 */
@Composable
fun CollectionsScreen(
    navController: NavController,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val openMarket: (CollectionAccess) -> Unit = { entry ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.collection.marketUrl)))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HavenTopBar(
            title = "Collections",
            subtitle = (uiState as? CollectionsUiState.Ready)?.let { state ->
                when {
                    !state.accessKnown -> null
                    state.joined.isEmpty() -> "None yet"
                    state.joined.size == 1 -> "You're in 1"
                    else -> "You're in ${state.joined.size}"
                }
            },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Check again")
                }
            },
        )

        when (val state = uiState) {
            CollectionsUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading communities\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is CollectionsUiState.Error -> ErrorState(
                title = "Couldn't load communities",
                message = state.message,
                onRetry = { viewModel.refresh() },
            )

            is CollectionsUiState.Ready -> {
                if (state.isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = HavenSpacing.gutter,
                        end = HavenSpacing.gutter,
                        top = HavenSpacing.md,
                        bottom = HavenSpacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(HavenSpacing.md),
                ) {
                    item {
                        Text(
                            text = "Every community here keeps its archive encrypted. Hold what it asks " +
                                "for and it opens for you — there is nothing to sign up for.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (!state.isConnected) {
                        item { Notice(text = "Connect a wallet to see which of these you can already open.") }
                    } else if (!state.accessKnown) {
                        item {
                            Notice(
                                text = "Couldn't check your assets just now, so nothing below is marked. " +
                                    "Opening something will still work if you hold what it asks for.",
                            )
                        }
                    }

                    if (state.joined.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(HavenSpacing.xs))
                            SectionHeader(label = "You're in")
                        }
                        items(state.joined) { entry ->
                            CollectionCard(entry = entry, onGetAccess = openMarket)
                        }
                    }

                    state.available.forEach { (category, entries) ->
                        item {
                            Spacer(Modifier.height(HavenSpacing.xs))
                            SectionHeader(label = category.label)
                        }
                        items(entries) { entry ->
                            CollectionCard(entry = entry, onGetAccess = openMarket)
                        }
                    }

                    item {
                        Spacer(Modifier.height(HavenSpacing.lg))
                        Explain(
                            question = "Why do I need to hold something?",
                            body = "It is how a community can have members without anyone keeping a list " +
                                "of them. What you hold lives in your wallet, anyone can check it, and no " +
                                "company can revoke it. If you pass it on, you stop being a member — " +
                                "automatically, with nobody to ask.",
                        )
                    }
                    item {
                        Explain(
                            question = "Does Haven take a cut?",
                            body = "No. Acquiring happens on the open market, not from us, and Haven is " +
                                "not part of the transaction. There is no subscription and no fee to open " +
                                "an archive you already have access to.",
                        )
                    }
                    item {
                        Explain(
                            question = "Can I lose access?",
                            body = "Yes, and that is the design. Access follows what you hold: pass it on " +
                                "and the next request for a key fails. Anything already saved to this " +
                                "device stays playable offline until the cache is cleared.",
                        )
                    }
                }
            }
        }
    }
}

/**
 * One community. Name, what it keeps, what it takes — and a single action.
 *
 * Members get no "get access" button, because the action for someone already in is to go and read,
 * which is what the Library tab is for.
 */
@Composable
private fun CollectionCard(
    entry: CollectionAccess,
    onGetAccess: (CollectionAccess) -> Unit,
) {
    val collection = entry.collection

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            HavenSpacing.hairline,
            if (entry.access == Access.GRANTED) {
                HavenTheme.accents.sealEdge
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(HavenSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(name = collection.name)
                Spacer(Modifier.width(HavenSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(HavenSpacing.xxs))
                    AccessChip(access = entry.access)
                }
            }

            Spacer(Modifier.height(HavenSpacing.md))
            Text(
                text = collection.premise,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(HavenSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = collection.requirement,
                    style = HavenTheme.text.figure,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (entry.access != Access.GRANTED) {
                    Spacer(Modifier.width(HavenSpacing.sm))
                    OutlinedButton(
                        onClick = { onGetAccess(entry) },
                        modifier = Modifier.height(HavenSpacing.touchTarget),
                    ) {
                        Text("Get access")
                        Spacer(Modifier.width(HavenSpacing.sm))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Opens ${collection.marketName}",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessChip(access: Access) {
    val accents = HavenTheme.accents
    when (access) {
        Access.GRANTED -> HavenStatusChip(
            tint = accents.cacheCached,
            icon = Icons.Default.CheckCircle,
            label = "You're in",
            semantics = "You hold what this community asks for",
            compact = true,
        )

        Access.MISSING -> HavenStatusChip(
            tint = accents.cacheMissing,
            icon = Icons.Default.Lock,
            label = "Not yet",
            semantics = "You do not hold what this community asks for",
            compact = true,
        )

        Access.UNKNOWN -> HavenStatusChip(
            tint = accents.cacheMissing,
            icon = Icons.Default.HelpOutline,
            label = "Not checked",
            semantics = "Your holdings for this community could not be checked",
            compact = true,
        )
    }
}

/** Stands in for collection artwork, which would mean shipping an image loader and 16 downloads. */
@Composable
private fun Monogram(name: String) {
    Surface(
        modifier = Modifier.size(HavenSpacing.glyph),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HavenSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(HavenSpacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
