package haven.mobile.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.reown.appkit.client.AppKit
import dagger.hilt.android.AndroidEntryPoint
import haven.mobile.app.ui.theme.HavenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try { StartupTracer.log(this, "MainActivity.onCreate start") } catch (_: Exception) {}
        // Draw behind the status and navigation bars; `Scaffold` in HavenScreen consumes the
        // insets, so content stays clear of them while the background runs edge to edge.
        try { enableEdgeToEdge() } catch (e: Throwable) { try { StartupTracer.log(this, "enableEdgeToEdge failed", e.stackTraceToString().take(600)) } catch (_: Exception) {} }
        super.onCreate(savedInstanceState)
        try { StartupTracer.log(this, "MainActivity super.onCreate done") } catch (_: Exception) {}

        try { registerAppKit() } catch (e: Throwable) { try { StartupTracer.log(this, "registerAppKit Throwable", e.stackTraceToString().take(600)) } catch (_: Exception) {} }
        try { handleDeepLink(intent) } catch (e: Throwable) { try { StartupTracer.log(this, "handleDeepLink Throwable", e.stackTraceToString().take(600)) } catch (_: Exception) {} }
        try { maybeShowCrashDialog() } catch (e: Throwable) { try { StartupTracer.log(this, "maybeShowCrashDialog Throwable", e.stackTraceToString().take(600)) } catch (_: Exception) {} }

        try { StartupTracer.log(this, "MainActivity before setContent") } catch (_: Exception) {}
        setContent {
            HavenTheme {
                val navController = rememberNavController()
                androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    // Error boundary: show startup trace instead of blank logo if HavenApp throws
                    val startupTrace = try { StartupTracer.read(this@MainActivity) } catch (_: Exception) { null }
                    try {
                        HavenApp(
                            navController = navController,
                            isDebugBuild = BuildConfig.DEBUG,
                        )
                    } catch (e: Throwable) {
                        val msg = "HavenApp compose failed:\n${e.stackTraceToString().take(4000)}\n\nStartup:\n${startupTrace?.take(4000) ?: "no trace"}"
                        try { StartupTracer.log(this@MainActivity, "HavenApp compose Throwable", e.stackTraceToString().take(600)) } catch (_: Exception) {}
                        androidx.compose.material3.Text(
                            text = msg,
                            modifier = androidx.compose.ui.Modifier.padding(androidx.compose.ui.unit.dp(16)),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        try { StartupTracer.log(this, "MainActivity.onCreate end") } catch (_: Exception) {}
    }

    /**
     * AppKit needs the activity for Coinbase Wallet hand-off and deep-link return. It throws if
     * `AppKit.initialize` has not completed yet (blank `wallet.projectId`, or initialisation
     * still in flight), which is expected on a fresh install without configuration — onboarding
     * explains what to do, so this is logged rather than fatal.
     */
    private fun registerAppKit() {
        try {
            AppKit.register(this)
        } catch (e: Throwable) {
            try { StartupTracer.log(this, "AppKit.register Throwable", e.stackTraceToString().take(600)) } catch (_: Exception) {}
            Timber.w(e as? Exception ?: Exception(e.message, e), "AppKit.register skipped — wallet connect unavailable until configured")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /** WalletConnect return leg: `haven://connect?wc_ev=…`. */
    private fun handleDeepLink(intent: Intent?) {
        val dataString = intent?.dataString ?: return
        if (!dataString.contains("wc_ev") && !dataString.contains("wc:")) return
        try {
            AppKit.handleDeepLink(dataString) { error ->
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Wallet connection failed: ${error.throwable.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } catch (e: Throwable) {
            try { StartupTracer.log(this, "handleDeepLink Throwable", e.stackTraceToString().take(600)) } catch (_: Exception) {}
            Timber.w(e as? Exception ?: Exception(e.message, e), "Deep link ignored — AppKit not initialised")
        }
    }

    private fun maybeShowCrashDialog() {
        try {
            val crashLog = (getExternalFilesDir(null)?.resolve("haven_crash.log")
                ?: filesDir.resolve("haven_crash.log")).takeIf { it.exists() } ?: return
            val text = crashLog.readText().take(4000)
            crashLog.delete()
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Haven — previous crash")
                .setMessage(text)
                .setPositiveButton("Share") { _, _ ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(intent, "Share crash log"))
                }
                .setNegativeButton("Dismiss", null)
                .show()
        } catch (e: Throwable) {
            try { StartupTracer.log(this, "maybeShowCrashDialog Throwable", e.stackTraceToString().take(600)) } catch (_: Exception) {}
            Timber.w(e as? Exception ?: Exception(e.message, e), "Crash dialog skipped")
        }
    }

    override fun onDestroy() {
        try {
            AppKit.unregister()
        } catch (e: Throwable) {
            try { StartupTracer.log(this, "AppKit.unregister Throwable", e.stackTraceToString().take(600)) } catch (_: Exception) {}
            Timber.v(e as? Exception ?: Exception(e.message, e), "AppKit.unregister skipped")
        }
        super.onDestroy()
    }
}
