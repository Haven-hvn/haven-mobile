package haven.mobile.feature.watch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.havenChain

/**
 * Method 4 (gate_type 4) pump-to-premiere sheet.
 *
 * A market-cap drip chunk unlocks collectively: the community pumps the gate token until its
 * market cap reaches the chunk target, and only then can holders decrypt. This screen says that
 * up front — with the buy link — instead of failing later with an inscrutable decrypt error.
 * It never unlocks content itself; the decrypt path stays fail-closed ([ haven.mobile.core.haven.aol.HavenAol]).
 */
data class DripPump(
    /** Whole-USD market-cap target for this chunk. */
    val targetUsd: Long,
    /** Gate token contract to pump, best-available spelling. Null when unknown. */
    val tokenAddress: String?,
    /** Chain carrying the gate token, when it resolves. */
    val chain: HavenChain?,
    /** mint.club trade page for the gate token. Null when the token is unknown. */
    val tradeUrl: String?,
)

/** The v4 drip gate on this item, preferring the content gate over the CID-layer gate. */
fun MediaItem.dripPumpGate(): GateMetadata.V4? =
    (encryptionMetadata as? GateMetadata.V4)
        ?: (cidEncryptionMetadata as? GateMetadata.V4)

/**
 * Collective-unlock facts for the sheet. Null when the item is not a market-cap drip.
 *
 * Token resolution order: entity gate attributes (`gate_token`), then the v4 gate JSON
 * (`tokenAddress`), then `gateReference` when it is address-shaped. Chain resolves the same
 * way via [HavenChain.parse].
 */
fun MediaItem.dripPump(): DripPump? {
    val v4 = dripPumpGate() ?: return null
    val token = gate?.tokenAddress?.takeIf { it.isAddressShaped() }
        ?: v4.tokenAddress.takeIf { it.isAddressShaped() }
        ?: v4.gateReference.takeIf { it.isAddressShaped() }
    val chain = gate?.havenChain() ?: HavenChain.parse(v4.chain.ifBlank { gate?.chain })
    val networkKey = chain?.mintClubKey ?: "base"
    return DripPump(
        targetUsd = v4.marketCapTargetUsd,
        tokenAddress = token,
        chain = chain,
        tradeUrl = token?.let { mintClubUrl(it, networkKey) },
    )
}

/** mint.club trade URL for a gate token. Mirrors dapp `buildMintClubUrl`. */
fun mintClubUrl(token: String, networkKey: String): String? {
    val t = token.trim()
    if (t.isEmpty()) return null
    val chain = networkKey.trim().lowercase().ifEmpty { "base" }
    return "https://mint.club/token/$chain/$t"
}

/** `0x1234…abcd` for addresses, passthrough otherwise. Mirrors dapp `shortenAddress`. */
fun shortAddress(token: String): String {
    val t = token.trim()
    if (t.length > 14 && t.startsWith("0x")) return "${t.take(8)}…${t.takeLast(6)}"
    return t
}

/** `$1.5M` / `$800K` / `$950`. Mirrors dapp `formatUsdCompact`. */
fun formatUsdCompact(amount: Long): String = when {
    amount >= 1_000_000_000 -> "$${trimZeros(amount / 1_000_000_000.0)}B"
    amount >= 1_000_000 -> "$${trimZeros(amount / 1_000_000.0)}M"
    amount >= 1_000 -> "$${trimZeros(amount / 1_000.0)}K"
    else -> "$$amount"
}

private fun trimZeros(n: Double): String {
    val one = (kotlin.math.round(n * 10) / 10.0).toString()
    return if (one.endsWith(".0")) one.dropLast(2) else one
}

private fun String.isAddressShaped(): Boolean {
    val t = trim()
    return t.startsWith("0x") && t.length >= 10 && t.drop(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

@Composable
fun DripPumpScreen(
    media: MediaItem,
    pump: DripPump,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val target = formatUsdCompact(pump.targetUsd)
    val tokenLabel = pump.tokenAddress?.let { shortAddress(it) } ?: "gate token"
    val chainLabel = pump.chain?.label ?: "its chain"
    val shareText = "Help pump $tokenLabel to $target to premiere \"${media.title}\" on Haven" +
        (pump.tradeUrl?.let { " — $it" } ?: "")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HavenSpacing.xl, vertical = HavenSpacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(HavenSpacing.md))
        Text(
            text = "This premiere needs a pump",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(HavenSpacing.sm))
        Text(
            text = "Unlocks at $target market cap. " +
                "Every buy moves the bar for everyone — " +
                "once the target hits, holders can watch.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(HavenSpacing.md))
        Text(
            text = "$tokenLabel · $chainLabel",
            style = HavenTheme.text.monoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (pump.tradeUrl != null) {
            Spacer(Modifier.height(HavenSpacing.xl))
            Button(
                onClick = { openUrl(context, pump.tradeUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HavenSpacing.touchTarget),
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(HavenSpacing.sm))
                Text("Pump it on mint.club")
            }
            Spacer(Modifier.height(HavenSpacing.sm))
            OutlinedButton(
                onClick = { shareText(context, shareText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HavenSpacing.touchTarget),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.size(HavenSpacing.sm))
                Text("Share to pump")
            }
            Spacer(Modifier.height(HavenSpacing.sm))
            OutlinedButton(
                onClick = { copyAddress(context, pump.tokenAddress!!) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HavenSpacing.touchTarget),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(HavenSpacing.sm))
                Text("Copy token address")
            }
            Spacer(Modifier.height(HavenSpacing.md))
            Text(
                text = "Pumping raises the market cap; holding lets you decrypt after unlock. " +
                    "Two steps, one crew.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Spacer(Modifier.height(HavenSpacing.md))
            Text(
                text = "No gate token is attached to this chunk yet — " +
                    "the publisher still needs to add one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (onRetry != null) {
            Spacer(Modifier.height(HavenSpacing.sm))
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HavenSpacing.touchTarget),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.size(HavenSpacing.sm))
                Text("Check again")
            }
        }
        Spacer(Modifier.height(HavenSpacing.md))
        Text(
            text = "GATE_TYPE_4",
            style = HavenTheme.text.monoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "No browser found for $url", Toast.LENGTH_SHORT).show()
    }
}

private fun copyAddress(context: Context, address: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("gate token", address))
    Toast.makeText(context, "Token address copied", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share to pump"))
    }.onFailure {
        copyAddress(context, text)
    }
}
