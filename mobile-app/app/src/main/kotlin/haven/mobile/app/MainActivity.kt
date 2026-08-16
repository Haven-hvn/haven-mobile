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
        // Draw behind the status and navigation bars; `Scaffold` in HavenScreen consumes the
        // insets, so content stays clear of them while the background runs edge to edge.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        registerAppKit()
        handleDeepLink(intent)
        maybeShowCrashDialog()

        setContent {
            try {
                HavenTheme {
                    val navController = rememberNavController()
                    HavenApp(
                        navController = navController,
                        isDebugBuild = BuildConfig.DEBUG,
                    )
                }
            } catch (e: Throwable) {
                Timber.e(e, "Compose startup failed — showing fallback")
                try {
                    val crashLog = getExternalFilesDir(null)?.resolve("haven_crash.log")
                        ?: filesDir.resolve("haven_crash.log")
                    crashLog.writeText("startup=${e.stackTraceToString()}\n")
                } catch (_: Exception) {}
                // Fallback: let the system handler terminate so the crash dialog appears on next launch
                throw e
            }
        }
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
        } catch (e: Exception) {
            Timber.w(e, "AppKit.register skipped — wallet connect unavailable until configured")
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
        } catch (e: Exception) {
            Timber.w(e, "Deep link ignored — AppKit not initialised")
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
        } catch (e: Exception) {
            Timber.w(e, "Crash dialog skipped")
        }
    }

    override fun onDestroy() {
        try {
            AppKit.unregister()
        } catch (e: Exception) {
            Timber.v(e, "AppKit.unregister skipped")
        }
        super.onDestroy()
    }
}
