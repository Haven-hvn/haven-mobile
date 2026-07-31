package haven.mobile.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DebugRoute() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Button(onClick = { }) {
            Text("Ping canister")
        }
        Button(onClick = { }) {
            Text("Fetch fixture PieceRef")
        }
        Button(onClick = { }) {
            Text("Sign fixture EIP-712")
        }
        Button(onClick = { }) {
            Text("Disconnect wallet")
        }
    }
}