package haven.mobile.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavController,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is SettingsUiState.Ready) {
            // Settings loaded, screen renders
        }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            SettingsUiState.Loading -> {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Loading settings…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is SettingsUiState.Error -> {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((uiState as SettingsUiState.Error).message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(8.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                }
            }
            is SettingsUiState.Ready -> {
                val ready = uiState as SettingsUiState.Ready
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    // Wallet — mono full, Copy + Disconnect destructive dialog
                    item {
                        Text("Wallet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.padding(4.dp))
                        val addr = ready.walletAddress ?: "Not connected"
                        Text(text = addr, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.padding(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val cm = viewModel.copyAddressToClipboard()
                            }, modifier = Modifier.height(48.dp)) { Text("Copy") }
                            var showDisc by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            if (showDisc) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showDisc = false },
                                    title = { Text("Disconnect wallet?") },
                                    text = { Text("This will wipe cached keys and per-wallet data (FR-SEC-1).") },
                                    confirmButton = { androidx.compose.material3.TextButton(onClick = { showDisc = false; viewModel.disconnect() }) { Text("Disconnect", color = MaterialTheme.colorScheme.error) } },
                                    dismissButton = { androidx.compose.material3.TextButton(onClick = { showDisc = false }) { Text("Cancel") } },
                                )
                            }
                            OutlinedButton(onClick = { showDisc = true }, modifier = Modifier.height(48.dp)) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
                        }
                        androidx.compose.material3.Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                    // Cache quotas — two Sliders 48dp thumb, value label above, GiB 0–20 / days 1–30 per design
                    item {
                        Text("Cache", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.padding(4.dp))
                        Text("Storage — ${(ready.cacheQuotaBytes / (1024 * 1024 * 1024.0)).let { String.format("%.1f GiB", it) }}", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = ready.cacheQuotaBytes.toFloat(),
                            onValueChange = { viewModel.setCacheQuotaBytes(it.toLong()) },
                            valueRange = 0f..20_000_000_000f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text("Keep for — ${ready.cacheTtlDays} days", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = ready.cacheTtlDays.toFloat(),
                            onValueChange = { viewModel.setCacheTtlDays(it.toInt()) },
                            valueRange = 1f..30f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ready.cacheSpaceBytes?.let { space ->
                            Text("Used: ${space / (1024 * 1024)} MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.padding(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var showClear by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            if (showClear) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showClear = false },
                                    title = { Text("Clear cached files?") },
                                    text = { Text("This frees space per quota/TTL (FR-CACHE-4).") },
                                    confirmButton = { androidx.compose.material3.TextButton(onClick = { showClear = false; viewModel.clearCache() }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
                                    dismissButton = { androidx.compose.material3.TextButton(onClick = { showClear = false }) { Text("Cancel") } },
                                )
                            }
                            Button(onClick = { showClear = true }, modifier = Modifier.height(48.dp)) { Text("Clear cached files") }
                            OutlinedButton(onClick = { viewModel.clearExpired() }, modifier = Modifier.height(48.dp)) { Text("Clear expired") }
                        }
                        Spacer(Modifier.padding(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Clear on disconnect", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = ready.clearOnDisconnect, onCheckedChange = { viewModel.setClearOnDisconnect(it) })
                        }
                        androidx.compose.material3.Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                    // Attestation toggle — Strict verify warns on Failed per design/settings.md
                    item {
                        Text("Attestation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column { Text("Strict verify", style = MaterialTheme.typography.bodyMedium); Text("Warn on Failed attestation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            var strict by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                            Switch(checked = strict, onCheckedChange = { strict = it })
                        }
                        androidx.compose.material3.Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                    // Recent errors — expandable last 5 HavenError mono 12/400 per design/settings.md + FR-OBS-2
                    item {
                        Text("Recent errors", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.padding(4.dp))
                        val errors = viewModel.recentErrors
                        if (errors.isEmpty()) {
                            Text("No recent errors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                errors.take(5).forEach { e ->
                                    Text(text = e, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                            }
                            Spacer(Modifier.padding(4.dp))
                            OutlinedButton(onClick = { viewModel.clearRecentErrors() }, modifier = Modifier.height(40.dp)) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
                        }
                        androidx.compose.material3.Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                    // About
                    item {
                        Text("About", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Haven Mobile v0.1.0 — parity with haven-dapp-main", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.padding(8.dp))
                        OutlinedButton(onClick = onNavigateBack, modifier = Modifier.height(48.dp)) { Text("Back") }
                    }
                }
            }
        }
    }
}