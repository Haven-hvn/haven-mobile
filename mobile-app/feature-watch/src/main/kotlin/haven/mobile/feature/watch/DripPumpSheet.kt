package haven.mobile.feature.watch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
 * up front — with the in-app trade panel — instead of failing later with an inscrutable decrypt
 * error. It never unlocks content itself: sealed v4 stages unlock through the normal decrypt
 * path once the target is reached ([haven.mobile.core.haven.aol.HavenAol] checks the live cap
 * before asking for a signature), and this sheet is what readers see until then. The pump
 * never leaves the app: no browser, no mint.club page.
 */
data class DripPump(
    /**
     * Market-cap target for this chunk. Whole USD for legacy V4 shapes; whole reserve units
     * (ETH) for sealed v4 drips — see [unit].
     */
    val targetUsd: Long,
    /** Gate token contract to pump, best-available spelling. Null when unknown. */
    val tokenAddress: String?,
    /** Chain carrying the gate token, when it resolves. */
    val chain: HavenChain?,
    /** Unit of [targetUsd] and [current]. */
    val unit: DripTargetUnit = DripTargetUnit.USD,
    /** Live market cap in [unit], when the key service reported it (a locked unlock attempt). */
    val current: java.math.BigInteger? = null,
    /** True for sealed v4 drips, which unlock in-app once the target is reached. */
    val unlockable: Boolean = false,
) {
    /** `$5M` for USD targets, `12 ETH` for reserve-unit targets. */
    val targetLabel: String get() = formatTarget(java.math.BigInteger.valueOf(targetUsd), unit)

    /** `3 ETH of 12 ETH`, when the live cap is known. */
    val progressLabel: String? get() = current?.let { "${formatTarget(it, unit)} of $targetLabel" }
}

/**
 * Sealed v4 targets are whole reserve units of the gate token's Bond curve — whole ETH for
 * the native-reserve tokens the canister supports. Legacy V4 shapes carried USD.
 */
enum class DripTargetUnit { USD, RESERVE_ETH }

internal fun formatTarget(amount: java.math.BigInteger, unit: DripTargetUnit): String = when (unit) {
    DripTargetUnit.USD -> formatUsdCompact(amount.toLong())
    DripTargetUnit.RESERVE_ETH -> "${formatCompact(amount)} ETH"
}

private fun formatCompact(amount: java.math.BigInteger): String {
    val n = amount.toLong()
    return when {
        n >= 1_000_000_000 -> "${trimZeros(n / 1_000_000_000.0)}B"
        n >= 1_000_000 -> "${trimZeros(n / 1_000_000.0)}M"
        n >= 1_000 -> "${trimZeros(n / 1_000.0)}K"
        else -> "$n"
    }
}

/** The v4 drip gate on this item, preferring the content gate over the CID-layer gate. */
fun MediaItem.dripPumpGate(): GateMetadata.V4? =
    (encryptionMetadata as? GateMetadata.V4)
        ?: (cidEncryptionMetadata as? GateMetadata.V4)

/** The sealed v4 drip gate on this item (content layer only — that is what unlocks playback). */
fun MediaItem.sealedDripGate(): GateMetadata.Sealed? =
    (encryptionMetadata as? GateMetadata.Sealed)?.takeIf { it.isMarketCapDrip && it.marketCapTarget != null }

/**
 * Collective-unlock facts for the sheet. Null when the item is not a market-cap drip.
 *
 * Token resolution order: entity gate attributes (`gate_token`), then the v4 gate JSON
 * (`tokenAddress`), then `gateReference` when it is address-shaped. Chain resolves the same
 * way via [HavenChain.parse].
 */
fun MediaItem.dripPump(current: java.math.BigInteger? = null): DripPump? {
    sealedDripGate()?.let { sealed ->
        val token = gate?.tokenAddress?.takeIf { it.isAddressShaped() }
            ?: sealed.tokenAddress.takeIf { it.isAddressShaped() }
        return DripPump(
            targetUsd = sealed.marketCapTarget ?: 0L,
            tokenAddress = token,
            chain = gate?.havenChain() ?: HavenChain.parse(sealed.chain.ifBlank { gate?.chain }),
            unit = DripTargetUnit.RESERVE_ETH,
            current = current,
            unlockable = true,
        )
    }
    val v4 = dripPumpGate() ?: return null
    val token = gate?.tokenAddress?.takeIf { it.isAddressShaped() }
        ?: v4.tokenAddress.takeIf { it.isAddressShaped() }
        ?: v4.gateReference.takeIf { it.isAddressShaped() }
    val chain = gate?.havenChain() ?: HavenChain.parse(v4.chain.ifBlank { gate?.chain })
    return DripPump(
        targetUsd = v4.marketCapTargetUsd,
        tokenAddress = token,
        chain = chain,
    )
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
    /** Connected wallet; when null the sheet keeps share/copy and no trade panel renders. */
    wallet: haven.mobile.core.wallet.WalletSession? = null,
    walletAddress: String? = null,
) {
    val context = LocalContext.current
    val target = pump.targetLabel
    val tokenLabel = pump.tokenAddress?.let { shortAddress(it) } ?: "gate token"
    val chainLabel = pump.chain?.label ?: "its chain"
    val shareText = "Help pump $tokenLabel to $target to premiere \"${media.title}\" on Haven"

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
        pump.progressLabel?.let { progress ->
            Spacer(Modifier.height(HavenSpacing.sm))
            Text(
                text = "Now at $progress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(HavenSpacing.md))
        Text(
            text = "$tokenLabel · $chainLabel",
            style = HavenTheme.text.monoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (pump.tokenAddress != null) {
            Spacer(Modifier.height(HavenSpacing.xl))
            if (wallet != null && walletAddress != null) {
                MintClubTradePanel(
                    wallet = wallet,
                    address = walletAddress,
                    pump = pump,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(HavenSpacing.md))
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
                Text(if (pump.unlockable) "Check again and unlock" else "Check again")
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
