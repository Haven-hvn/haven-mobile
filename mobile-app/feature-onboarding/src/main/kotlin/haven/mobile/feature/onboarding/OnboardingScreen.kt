package haven.mobile.feature.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.design.component.Explain
import haven.mobile.core.design.component.MonoIdentifier

/**
 * Absences, not features. Every line is something a reader has been trained to expect from an app
 * that gates content, and does not have to give up here.
 */
private val NEVER_ASKED_FOR = listOf(
    "An email address",
    "A password",
    "A username",
    "A subscription",
    "Custody of anything you hold",
    "Permission to spend",
)

/**
 * The gate.
 *
 * One job: get a wallet connected, and set expectations before the wallet app takes over. The
 * three assurances are here because the moment of highest anxiety in this product is the first
 * time a wallet asks for a signature — saying "no gas, no transaction, here is what you will
 * see" up front removes the reason to bail out.
 *
 * This is also the only place the brand serif appears (`HavenTextStyles.editorial`), which is
 * what keeps it a signature rather than a texture.
 */
@Composable
fun OnboardingScreen(
    navController: NavController,
    onNavigate: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Connected) onNavigate()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HavenSpacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(HavenSpacing.xxxl))

        HavenSeal()

        Spacer(Modifier.height(HavenSpacing.xl))

        Text(
            text = "HAVEN",
            style = HavenTheme.text.overline,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(HavenSpacing.md))
        Text(
            text = "An archive only you can open.",
            style = HavenTheme.text.editorial,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(HavenSpacing.md))
        Text(
            text = "Connect a wallet to see the content it holds the keys to. Everything is " +
                "decrypted on this device and cached for offline viewing.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(HavenSpacing.xl))

        Assurance(
            icon = Icons.Default.Fingerprint,
            title = "A signature, not a payment",
            body = "Haven asks your wallet to sign a message proving the address is yours. It costs " +
                "nothing and moves nothing — like signing your name rather than writing a cheque.",
        )
        Assurance(
            icon = Icons.Default.EnhancedEncryption,
            title = "Only your device can open it",
            body = "Content is decrypted here, on this phone. Haven has no server holding your key " +
                "and no account holding your library.",
        )
        Assurance(
            icon = Icons.Default.CloudOff,
            title = "Works without a signal",
            body = "Anything you have opened stays playable offline until you clear it.",
        )

        Spacer(Modifier.height(HavenSpacing.lg))

        // The web's "what you are never asked for" list, which is the most effective thing on that
        // page: every item is an absence a reader has learned to expect from an app like this.
        Text(
            text = "WHAT HAVEN NEVER ASKS FOR",
            style = HavenTheme.text.overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(HavenSpacing.sm))
        NEVER_ASKED_FOR.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = HavenSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "—",
                    style = HavenTheme.text.mono,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(HavenSpacing.sm))
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(HavenSpacing.lg))

        Explain(
            question = "What is a wallet, and do I need one?",
            body = "A wallet is an app that holds a key and signs messages with it. That is all Haven " +
                "needs it for — it never asks a wallet to spend anything. If you do not have one, the " +
                "connect button lists the ones it works with and where to get them.",
        )
        Spacer(Modifier.height(HavenSpacing.sm))
        Explain(
            question = "Why does a community need me to hold something?",
            body = "It is how membership works without anyone keeping a list of members. What you hold " +
                "lives in your wallet, anyone can verify it, and no company can revoke it. Pass it on " +
                "and you stop being a member, automatically.",
        )

        Spacer(Modifier.height(HavenSpacing.lg))
        HorizontalDivider(
            thickness = HavenSpacing.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(HavenSpacing.xl))

        // Status first, then the control — the control keeps the same position in every state so
        // a failed attempt does not move the button out from under the user's thumb.
        when (val state = uiState) {
            OnboardingUiState.Idle -> Unit

            OnboardingUiState.Connecting -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = HavenSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(HavenSpacing.md))
                Text(
                    text = "Waiting for your wallet\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is OnboardingUiState.Connected -> Column {
                Text(
                    text = "Connected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(HavenSpacing.xs))
                MonoIdentifier(value = state.address, head = 10, tail = 8)
                Spacer(Modifier.height(HavenSpacing.md))
                TextButton(onClick = { viewModel.disconnect() }) {
                    Text("Use a different wallet")
                }
            }

            is OnboardingUiState.Error -> Text(
                text = friendlyConnectError(state.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = HavenSpacing.lg),
            )
        }

        if (uiState !is OnboardingUiState.Connected) {
            if (!viewModel.isWalletConfigured) {
                Spacer(Modifier.height(HavenSpacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = MaterialTheme.shapes.medium)
                        .padding(HavenSpacing.md),
                ) {
                    Text(
                        text = "Wallet connect is not configured in this build. Install a debug build with a valid wallet.projectId to connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.connect() },
                    enabled = uiState !is OnboardingUiState.Connecting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = if (uiState is OnboardingUiState.Connecting) "Connecting…" else "Connect wallet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(HavenSpacing.md))
                Text(
                    text = "MetaMask, Rainbow, Trust, or any WalletConnect v2 wallet. No gas, no transaction — just a signature.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(HavenSpacing.xxxl))
    }
}

/** Ember ring with the Haven monogram — the launcher mark, drawn in type. */
@Composable
private fun HavenSeal(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "H",
            style = HavenTheme.text.editorial.copy(fontSize = 26.sp, lineHeight = 30.sp),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Assurance(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = HavenSpacing.md),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(HavenSpacing.glyph),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(HavenSpacing.md))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(HavenSpacing.xxs))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Wallet plumbing failures are not user-facing language.
 *
 * The unconfigured case in particular used to surface build instructions ("set wallet.projectId in
 * local.properties") to whoever happened to be holding the phone. A reader cannot act on that, and a
 * shipped build should never produce it — so it now reads as a fault on our side, which is what it is.
 */
internal fun friendlyConnectError(message: String): String = when {
    message.contains("AppKitNotInitialized", ignoreCase = true) ||
        message.contains("projectId", ignoreCase = true) ->
        "Wallet connections aren't available in this build of Haven."
    message.contains("NoAddressReturned", ignoreCase = true) ->
        "That wallet didn't return an address. Try another wallet."
    message.contains("InvalidSignatureFormat", ignoreCase = true) ->
        "The signature came back in a form Haven couldn't read. Please try again."
    message.contains("ConnectFailed", ignoreCase = true) ||
        message.contains("not connected", ignoreCase = true) ->
        "Your wallet didn't respond. Open the wallet app and approve the connection."
    else -> "Couldn't connect to your wallet. Please try again."
}
