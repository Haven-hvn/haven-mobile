package haven.mobile.app

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import haven.mobile.core.wallet.WalletSession

@Composable
fun DebugRoute(
    viewModel: DebugViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val log by viewModel.log.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Debug Tab",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.padding(8.dp))

        Button(onClick = { viewModel.pingCanister() }) {
            Text("Ping canister")
        }
        Button(onClick = { viewModel.fetchFixturePieceRef() }) {
            Text("Fetch fixture PieceRef")
        }
        Button(onClick = { viewModel.signFixtureEip712() }) {
            Text("Sign fixture EIP-712")
        }
        OutlinedButton(onClick = { viewModel.disconnectWallet() }) {
            Text("Disconnect wallet")
        }

        Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "Log:",
            style = MaterialTheme.typography.titleSmall,
        )

        log.forEach { entry ->
            Text(
                text = entry,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
