package haven.mobile.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.reown.appkit.client.AppKit
import haven.mobile.app.ui.theme.HavenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register AppKit with this activity for Coinbase and deep-link handling (mirrors sample modal MainActivity)
        try {
            var isRegistered = false
            var counter = 10
            while (!isRegistered && counter-- > 0) {
                try {
                    AppKit.register(this)
                    isRegistered = true
                } catch (e: Exception) {
                    Thread.sleep(100)
                }
            }
        } catch (_: Exception) {
            // AppKit not initialized (blank projectId) — onboarding will show guidance
        }
        handleDeepLink(intent)
        setContent {
            HavenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(
                        navController = navController,
                        onNavigate = {
                            navController.navigate(AppRoute.Debug.route())
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val dataString = intent?.dataString ?: return
        if (dataString.contains("wc_ev") || dataString.contains("wc:")) {
            AppKit.handleDeepLink(dataString) { error ->
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "WalletConnect error: ${error.throwable.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            AppKit.unregister()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}