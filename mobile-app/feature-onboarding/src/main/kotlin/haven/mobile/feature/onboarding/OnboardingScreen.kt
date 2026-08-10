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
import com.reown.appkit.ui.components.button.ConnectButton
import com.reown.appkit.ui.components.button.ConnectButtonSize
import com.reown.appkit.ui.components.button.rememberAppKitState

@Composable
fun OnboardingScreen(
    navController: androidx.navigation.NavController,
    onNavigate: () -> Unit,
    viewModel: OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val appKitState = rememberAppKitState(navController = navController)

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
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = "Haven will ask your wallet to sign a message to prove you own this address — no gas, no transaction. You’ll see \"Haven-AOL\" in your wallet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.padding(24.dp))

        val state = uiState
        when (state) {
            OnboardingUiState.Idle,
            OnboardingUiState.Connecting -> {
                // Primary Reown connect via modal — handles WalletConnect, MetaMask, Rainbow, Trust
                ConnectButton(state = appKitState, buttonSize = ConnectButtonSize.NORMAL)
                Spacer(modifier = Modifier.padding(8.dp))
                Text(
                    text = "Or",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.padding(8.dp))
                Button(
                    onClick = { viewModel.connect() },
                    enabled = state !is OnboardingUiState.Connecting,
                ) {
                    Text("Connect wallet (legacy)")
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
                val friendly = when {
                    state.message.contains("AppKitNotInitialized", ignoreCase = true) -> "Wallet not ready — set wallet.projectId in local.properties and rebuild."
                    state.message.contains("NoAddressReturned", ignoreCase = true) -> "Wallet didn’t return an address — try another wallet."
                    state.message.contains("InvalidSignatureFormat", ignoreCase = true) -> "Signature was rejected — please try again."
                    state.message.contains("ConnectFailed", ignoreCase = true) -> "Wallet didn’t respond — check your wallet app and try again."
                    else -> state.message
                }
                Text(
                    text = friendly,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.padding(16.dp))
                ConnectButton(state = appKitState, buttonSize = ConnectButtonSize.NORMAL)
                Spacer(modifier = Modifier.padding(16.dp))
                Button(onClick = { viewModel.connect() }) {
                    Text("Retry (legacy)")
                }
            }
        }
    }
}