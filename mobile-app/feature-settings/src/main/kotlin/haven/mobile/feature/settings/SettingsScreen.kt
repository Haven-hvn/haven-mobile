package haven.mobile.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.design.component.ConfirmDialog
import haven.mobile.core.design.component.HavenTopBar
import haven.mobile.core.design.component.MonoIdentifier
import haven.mobile.core.design.component.SectionHeader
import haven.mobile.core.design.component.SettingSliderRow
import haven.mobile.core.design.component.SettingSwitchRow
import haven.mobile.core.design.component.formatBytes
import haven.mobile.core.domain.HavenChain

/** Which destructive confirmation is open, if any. Hoisted so scrolling cannot lose it. */
private enum class PendingAction { NONE, CLEAR_CACHE, DISCONNECT }

/**
 * Settings.
 *
 * Three destructive actions live here (clear cache, clear expired, disconnect-and-wipe), so each
 * one confirms first, and every confirmation says what will actually be destroyed rather than
 * "are you sure?".
 *
 * The dialog flags are `rememberSaveable` at screen level. They previously lived inside
 * `LazyColumn` item lambdas, where `remember` is scoped to a composition that is thrown away when
 * the item scrolls out of view — a dialog could be dismissed by scrolling, and the flag reset
 * behind the user's back.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    var pending by rememberSaveable { mutableStateOf(PendingAction.NONE) }

    // Slider positions are local while dragging so the thumb tracks the finger at 60fps; the
    // value is committed to DataStore once, on release.
    var quotaGiB by rememberSaveable { mutableStateOf<Float?>(null) }
    var ttlDays by rememberSaveable { mutableStateOf<Float?>(null) }

    LaunchedEffect(state.message) {
        // Messages are one-shot; the screen shows them inline and clears them so they do not
        // reappear on the next recomposition.
        if (state.message != null) viewModel.consumeMessage()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HavenTopBar(
            title = "Settings",
            onBack = onNavigateBack,
        )

        if (state.isWorking) {
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
            verticalArrangement = Arrangement.spacedBy(HavenSpacing.lg),
        ) {
            item {
                SectionHeader(label = "Wallet")
                Spacer(Modifier.height(HavenSpacing.md))
                val address = state.walletAddress
                if (address == null) {
                    Text(
                        text = "No wallet connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonoIdentifier(
                            value = address,
                            full = false,
                            head = 10,
                            tail = 8,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy wallet address",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(label = "Storage")
                Spacer(Modifier.height(HavenSpacing.md))

                val usage = state.usage
                if (usage != null) {
                    val fraction = if (usage.quotaBytes > 0) {
                        (usage.usedBytes.toFloat() / usage.quotaBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Text(
                        text = "${formatBytes(usage.usedBytes)} of ${formatBytes(usage.quotaBytes)} used" +
                            " \u00b7 ${usage.itemCount} pieces",
                        style = HavenTheme.text.figure,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(HavenSpacing.sm))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Spacer(Modifier.height(HavenSpacing.lg))
                }

                SettingSliderRow(
                    title = "Cache limit",
                    valueLabel = "${"%.1f".format(quotaGiB ?: state.quotaBytes.toGiB())} GiB",
                    value = quotaGiB ?: state.quotaBytes.toGiB(),
                    range = MIN_QUOTA_GIB..MAX_QUOTA_GIB,
                    supporting = "Hard cap on cached content for this wallet.",
                    onValueChange = { quotaGiB = it },
                    onValueChangeFinished = {
                        quotaGiB?.let { viewModel.setQuotaBytes(it.gibToBytes()) }
                    },
                )
                Spacer(Modifier.height(HavenSpacing.md))
                SettingSliderRow(
                    title = "Keep content for",
                    valueLabel = "${(ttlDays ?: state.ttlDays.toFloat()).toInt()} days",
                    value = ttlDays ?: state.ttlDays.toFloat(),
                    range = MIN_TTL_DAYS..MAX_TTL_DAYS,
                    steps = 0,
                    supporting = "Pieces untouched for longer than this are evicted.",
                    onValueChange = { ttlDays = it },
                    onValueChangeFinished = {
                        ttlDays?.let { viewModel.setTtlDays(it.toInt()) }
                    },
                )
                Spacer(Modifier.height(HavenSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(HavenSpacing.sm)) {
                    OutlinedButton(
                        onClick = { pending = PendingAction.CLEAR_CACHE },
                        enabled = state.walletAddress != null && !state.isWorking,
                        modifier = Modifier.height(HavenSpacing.touchTarget),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Clear cached content")
                    }
                }
                Spacer(Modifier.height(HavenSpacing.sm))
                Text(
                    // No "clear expired" button: foc owns TTL accounting and evicts on its own
                    // read/write path. A button here could only either lie or delete everything —
                    // which is precisely what the previous implementation did.
                    text = "Expired pieces are evicted automatically once past the limit above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SectionHeader(label = "Networks")
                Spacer(Modifier.height(HavenSpacing.sm))
                Text(
                    // Deliberately not a decision anyone has to make. Every live network is on from
                    // first launch — asking a reader to pick a chain before they can see anything is a
                    // configuration step standing in front of the product, and most people could not
                    // answer it. This is here to turn one *off*.
                    text = "Haven looks for the assets that open an archive on all of these. " +
                        "They're on by default — switch one off if you'd rather it wasn't checked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(HavenSpacing.md))
                HavenChain.entries.forEach { chain ->
                    SettingSwitchRow(
                        title = chain.label,
                        supporting = if (chain.isTestnet) {
                            "Test network — off unless you're testing"
                        } else {
                            null
                        },
                        checked = chain in state.enabledChains,
                        onCheckedChange = { viewModel.toggleChain(chain) },
                    )
                }
            }

            item {
                SectionHeader(label = "Security")
                Spacer(Modifier.height(HavenSpacing.sm))
                SettingSwitchRow(
                    title = "Wipe on disconnect",
                    supporting = "Remove this wallet's cached content and metadata when disconnecting.",
                    checked = state.clearOnDisconnect,
                    onCheckedChange = viewModel::setClearOnDisconnect,
                )
                Spacer(Modifier.height(HavenSpacing.md))
                Button(
                    onClick = { pending = PendingAction.DISCONNECT },
                    enabled = state.walletAddress != null && !state.isWorking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HavenSpacing.touchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Disconnect wallet")
                }
            }

            item {
                SectionHeader(label = "Diagnostics") {
                    if (state.recentEvents.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearEvents() }) { Text("Clear") }
                    }
                }
                Spacer(Modifier.height(HavenSpacing.sm))
                Text(
                    // Named for what it is: a support aid, not a feature. It stays last on the screen
                    // and holds nothing that outlives the session.
                    text = if (state.recentEvents.isEmpty()) {
                        "Nothing recorded this session. If something goes wrong, the detail appears " +
                            "here so you can describe it."
                    } else {
                        "Kept in memory for this session only, to help describe a problem."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(items = state.recentEvents.take(MAX_VISIBLE_EVENTS)) { entry ->
                Text(
                    text = entry,
                    style = HavenTheme.text.monoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            item {
                HorizontalDivider(
                    thickness = HavenSpacing.hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.height(HavenSpacing.md))
                Text(
                    text = "Haven Mobile 0.1.0",
                    style = HavenTheme.text.monoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Content keys are unwrapped in memory only. Nothing is backed up off device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (pending) {
        PendingAction.NONE -> Unit

        PendingAction.CLEAR_CACHE -> ConfirmDialog(
            title = "Clear cached content?",
            body = "Every cached piece for this wallet is deleted. Anything you open again will be " +
                "re-fetched and re-decrypted, which needs a connection.",
            confirmLabel = "Clear",
            onConfirm = {
                pending = PendingAction.NONE
                viewModel.clearCache()
            },
            onDismiss = { pending = PendingAction.NONE },
        )

        PendingAction.DISCONNECT -> ConfirmDialog(
            title = "Disconnect wallet?",
            body = if (state.clearOnDisconnect) {
                "In-memory keys are wiped, and this wallet's cached content and local library are " +
                    "deleted. You will need to reconnect and re-fetch."
            } else {
                "In-memory keys are wiped and the session ends. Cached content is kept because " +
                    "\u201cWipe on disconnect\u201d is off."
            },
            confirmLabel = "Disconnect",
            onConfirm = {
                pending = PendingAction.NONE
                viewModel.disconnect()
            },
            onDismiss = { pending = PendingAction.NONE },
        )
    }
}

private const val MAX_VISIBLE_EVENTS = 20
private const val MIN_QUOTA_GIB = 0.5f
private const val MAX_QUOTA_GIB = 20f
private const val MIN_TTL_DAYS = 1f
private const val MAX_TTL_DAYS = 90f
private const val BYTES_PER_GIB = 1024L * 1024L * 1024L

private fun Long.toGiB(): Float = (this.toDouble() / BYTES_PER_GIB).toFloat()
private fun Float.gibToBytes(): Long = (this.toDouble() * BYTES_PER_GIB).toLong()
