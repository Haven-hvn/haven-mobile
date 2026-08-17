package haven.mobile.app

import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout
import android.widget.Button
import android.view.Gravity
import android.graphics.Typeface
import android.util.TypedValue

/**
 * Shown directly from the uncaught handler when the app dies before MainActivity can draw.
 * No Hilt, no Compose, no Reown — just a TextView so the trace is visible without adb.
 */
class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashText = intent.getStringExtra(EXTRA_CRASH) ?: readCrashFile() ?: "No crash log available."
        val startupText = StartupTracer.read(this)

        val title = TextView(this).apply {
            text = "Haven — startup crash"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(32, 48, 32, 16)
        }
        val combined = buildString {
            append(crashText.take(12000))
            if (startupText != null) {
                append("\n\n--- Startup trace ---\n")
                append(startupText.take(6000))
            }
        }
        val body = TextView(this).apply {
            text = combined
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setPadding(32, 16, 32, 16)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        val share = Button(this).apply {
            text = "Share crash log"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Haven crash")
                    putExtra(Intent.EXTRA_TEXT, combined)
                }
                startActivity(Intent.createChooser(send, "Share crash log"))
            }
        }
        val dismiss = Button(this).apply {
            text = "Close"
            setOnClickListener { finishAffinity() }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(32, 16, 32, 32)
            addView(share)
            addView(dismiss)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        }
        setContentView(root)
    }

    private fun readCrashFile(): String? = try {
        val f = getExternalFilesDir(null)?.resolve("haven_crash.log")?.takeIf { it.exists() }
            ?: filesDir.resolve("haven_crash.log").takeIf { it.exists() }
        f?.readText()
    } catch (_: Exception) { null }

    companion object {
        const val EXTRA_CRASH = "haven_crash_text"
    }
}
