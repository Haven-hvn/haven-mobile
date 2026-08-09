package haven.mobile.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun OnboardingScreen(
    navController: androidx.navigation.NavController,
    onNavigate: () -> Unit,
    viewModel: OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Connected) {
            onNavigate()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome to Haven. Connect your wallet to access gated content.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.padding(24.dp))

        val state = uiState
        when (state) {
            OnboardingUiState.Idle,
            OnboardingUiState.Connecting -> {
                Button(
                    onClick = { viewModel.connect() },
                    enabled = state !is OnboardingUiState.Connecting,
                ) {
                    Text("Connect wallet")
                }
            }
            is OnboardingUiState.Connected -> {
                Text(
                    text = "Connected: ${state.address}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.padding(16.dp))
                OutlinedButton(onClick = { viewModel.disconnect() }) {
                    Text("Disconnect")
                }
            }
            is OnboardingUiState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.padding(16.dp))
                Button(onClick = { viewModel.connect() }) {
                    Text("Retry")
                }
            }
        }
    }
}