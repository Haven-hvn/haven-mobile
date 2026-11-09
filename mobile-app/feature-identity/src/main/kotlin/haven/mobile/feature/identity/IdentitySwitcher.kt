package haven.mobile.feature.identity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.component.ConfirmDialog
import haven.mobile.core.wallet.WalletIdentity

/** Deterministic colour for an identity, derived from its address — nothing stored. */
@Composable
fun IdentityAvatar(identity: WalletIdentity, modifier: Modifier = Modifier, size: Int = 32) {
    val hue = WalletIdentity.hueFor(identity.address).toFloat()
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.hsl(hue, 0.55f, 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = identity.displayLabel.take(1).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

/** Header chip: who you are right now. Tap opens the switcher. */
@Composable
fun IdentityChip(
    identity: WalletIdentity?,
    isLive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (identity == null) return
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = HavenSpacing.sm, vertical = HavenSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdentityAvatar(identity, size = 24)
        Spacer(Modifier.width(HavenSpacing.sm))
        Column {
            Text(
                text = identity.displayLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isLive) "Connected" else "Reconnect to sign",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun IdentityRow(
    identity: WalletIdentity,
    isActive: Boolean,
    isLive: Boolean,
    onSwitch: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isActive, onClick = onSwitch)
            .padding(vertical = HavenSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdentityAvatar(identity)
        Spacer(Modifier.width(HavenSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = identity.displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = WalletIdentity.shortAddress(identity.address) + when {
                    isActive && isLive -> " · Active · Connected"
                    isActive -> " · Active · Reconnect to sign"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (isActive) {
            Icon(Icons.Default.Check, contentDescription = "Active identity", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { renaming = true }) {
            Icon(Icons.Default.Edit, contentDescription = "Rename ${identity.displayLabel}")
        }
        IconButton(onClick = { confirmingRemove = true }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove ${identity.displayLabel}",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (renaming) {
        var draft by remember { mutableStateOf(identity.label) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename identity") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Label") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(draft); renaming = false }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmingRemove) {
        ConfirmDialog(
            title = "Remove ${identity.displayLabel}?",
            body = "This deletes this wallet's keys, cached content, local library and " +
                "settings from this device. Other identities are not touched. You can " +
                "add the wallet again later, but cached content will need re-fetching.",
            confirmLabel = "Remove",
            onConfirm = { confirmingRemove = false; onRemove() },
            onDismiss = { confirmingRemove = false },
        )
    }
}

/**
 * The switcher sheet: every identity, tap to switch (nothing is cleared),
 * plus add. Rename/remove live on each row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitySwitcherSheet(
    state: IdentityUiState,
    viewModel: IdentityViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.md)) {
            Text("Identities", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(HavenSpacing.xs))
            Text(
                text = "Switching is instant and clears nothing — each wallet keeps its own " +
                    "library, downloads and keys on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(HavenSpacing.md))

            state.identities.forEach { identity ->
                IdentityRow(
                    identity = identity,
                    isActive = identity.id == state.active?.id,
                    isLive = state.isLive,
                    onSwitch = { viewModel.switchTo(identity.address); onDismiss() },
                    onRename = { label -> viewModel.rename(identity.address, label) },
                    onRemove = { viewModel.removeIdentity(identity.address) },
                )
            }

            if (state.active != null && !state.isLive) {
                Spacer(Modifier.height(HavenSpacing.md))
                FilledTonalButton(
                    onClick = { viewModel.reconnect() },
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reconnect ${state.active.displayLabel} to sign")
                }
            }

            Spacer(Modifier.height(HavenSpacing.md))
            FilledTonalButton(
                onClick = { viewModel.addIdentity() },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(HavenSpacing.sm))
                Text(if (state.working) "Waiting for wallet…" else "Add identity")
            }
            Spacer(Modifier.height(HavenSpacing.xl))
        }
    }
}
