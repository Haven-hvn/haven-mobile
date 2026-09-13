package haven.mobile.feature.watch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.wallet.WalletSession
import java.math.BigInteger
import kotlinx.coroutines.launch

/**
 * In-app buy/sell for the gate token on a [DripPump] sheet.
 *
 * Rendered only when a wallet is connected; otherwise the sheet keeps its
 * existing mint.club link-out and this panel is absent. Quotes are read-only;
 * every state-changing step is a separate wallet confirmation.
 */
@Composable
fun MintClubTradePanel(
    wallet: WalletSession,
    address: String,
    pump: DripPump,
    modifier: Modifier = Modifier,
) {
    val token = pump.tokenAddress
    val chain = pump.chain
    if (token == null || chain == null) return

    var isBuy by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var quoteText by remember { mutableStateOf<String?>(null) }
    var quoting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val amount = MintClubTrade.parseAmount(amountText)

    // Live read-only quote as the amount changes.
    LaunchedEffect(amountText, isBuy) {
        quoteText = null
        status = null
        val a = MintClubTrade.parseAmount(amountText) ?: return@LaunchedEffect
        quoting = true
        quoteText = try {
            val raw = if (isBuy) MintClubTrade.quoteBuy(chain, token, a)
            else MintClubTrade.quoteSell(chain, token, a)
            raw?.let { MintClubTrade.formatUnits(it) }
        } catch (_: Exception) {
            null
        }
        quoting = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = isBuy, onClick = { isBuy = true }, label = { Text("Buy") })
            Spacer(Modifier.width(HavenSpacing.sm))
            FilterChip(selected = !isBuy, onClick = { isBuy = false }, label = { Text("Sell") })
            Spacer(Modifier.width(HavenSpacing.sm))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(HavenSpacing.sm))
        when {
            quoting -> Text(
                text = "Quoting…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quoteText != null -> Text(
                text = if (isBuy) "≈ $quoteText reserve to mint" else "≈ $quoteText reserve back",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            amount != null -> Text(
                text = "No quote — is this token live on Mint Club?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(HavenSpacing.sm))
        Button(
            onClick = {
                val a: BigInteger = amount ?: return@Button
                scope.launch {
                    busy = true
                    status = if (isBuy) "Confirm in wallet (approve + mint)…" else "Confirm in wallet (approve + burn)…"
                    val result = try {
                        if (isBuy) MintClubTrade.executeBuy(wallet, chain, token, address, a)
                        else MintClubTrade.executeSell(wallet, chain, token, address, a)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    status = result.fold(
                        onSuccess = { "Sent: ${it.take(10)}…${it.takeLast(6)}" },
                        onFailure = { "Failed: ${it.message ?: "wallet rejected"}" },
                    )
                    busy = false
                }
            },
            enabled = amount != null && quoteText != null && !busy && !quoting,
            modifier = Modifier.fillMaxWidth().height(HavenSpacing.touchTarget),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(HavenSpacing.md))
                Spacer(Modifier.width(HavenSpacing.sm))
            }
            Text(if (isBuy) "Buy in app" else "Sell in app")
        }
        status?.let {
            Spacer(Modifier.height(HavenSpacing.sm))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
