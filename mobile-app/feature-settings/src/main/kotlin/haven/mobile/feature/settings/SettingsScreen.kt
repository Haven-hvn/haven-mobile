package haven.mobile.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavController,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is SettingsUiState.Ready) {
            // Settings loaded, screen renders
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )

        when (uiState) {
            SettingsUiState.Loading -> {
                Text("Loading settings…")
            }
            is SettingsUiState.Ready -> {
                val ready = uiState as SettingsUiState.Ready

                // Cache quota slider
                Text("Cache quota: ${ready.cacheQuotaBytes / (1024 * 1024)} MB")
                Slider(
                    value = ready.cacheQuotaBytes.toFloat(),
                    onValueChange = { viewModel.setCacheQuotaBytes(it.toLong()) },
                    valueRange = 100_000_000f..10_000_000_000f,
                    steps = 10,
                )

                // TTL slider
                Text("Cache TTL: ${ready.cacheTtlDays} days")
                Slider(
                    value = ready.cacheTtlDays.toFloat(),
                    onValueChange = { viewModel.setCacheTtlDays(it.toInt()) },
                    valueRange = 1f..90f,
                    steps = 89,
                )

                // Clear on disconnect toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Clear on disconnect")
                    Switch(
                        checked = ready.clearOnDisconnect,
                        onCheckedChange = { viewModel.setClearOnDisconnect(it) },
                    )
                }

                // Cache space info
                ready.cacheSpaceBytes?.let { space ->
                    Text("Cache used: ${space / (1024 * 1024)} MB")
                }

                // Clear cache button
                Button(onClick = { viewModel.clearCache() }) {
                    Text("Clear cache")
                }

                // Disconnect button
                OutlinedButton(onClick = { viewModel.disconnect() }) {
                    Text("Disconnect")
                }
            }
            is SettingsUiState.Error -> {
                Text((uiState as SettingsUiState.Error).message)
                Button(onClick = { /* retry */ }) {
                    Text("Retry")
                }
            }
        }

        Spacer(modifier = Modifier.padding(8.dp))

        OutlinedButton(onClick = onNavigateBack) {
            Text("Back")
        }
    }
}