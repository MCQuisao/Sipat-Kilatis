package com.example.sipatkilatis

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import com.example.sipatkilatis.capture.MessageNotificationListener
import com.example.sipatkilatis.capture.ScamAlerts
import com.example.sipatkilatis.ui.AppNavHost
import com.example.sipatkilatis.ui.MainViewModel
import com.example.sipatkilatis.ui.theme.SipatKilatisTheme

/**
 * Single activity hosting all Compose screens.
 * AppCompatActivity (not ComponentActivity) so the per-app language switch works on Android 8+.
 */
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    // Set by the "link hidden" notice; the clipboard can only be read once our window has focus
    private var pasteClipboardOnFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShareIntent(intent)   // not again after rotation / language change
        setContent {
            val appearance by vm.appearance.collectAsStateWithLifecycle()
            SipatKilatisTheme(appearance) {
                AppNavHost(vm)
            }
        }
    }

    // Coming back to the app (e.g. from Settings): make sure chat-app screening is really connected
    override fun onResume() {
        super.onResume()
        MessageNotificationListener.ensureConnected(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !pasteClipboardOnFocus) return
        pasteClipboardOnFocus = false
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        vm.onSharedText(text.trim())   // empty clipboard: the Check screen opens empty, ready to paste
    }

    // Activity is singleTop: a share while the app is open arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * "Share → Sipat Kilatis" from any messaging app puts the text into the Check screen.
     * Tapping a scam alert ("View details") opens that scan's Result screen.
     * Tapping a "link hidden" notice opens the Check screen filled with what the user copied.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let(vm::onSharedText)
        }
        if (intent?.getBooleanExtra(ScamAlerts.EXTRA_CHECK_CLIPBOARD, false) == true) pasteClipboardOnFocus = true
        if (intent?.getBooleanExtra("open_demo", false) == true) vm.requestDemo()   // adb: --ez open_demo true
        val scanId = intent?.getLongExtra(ScamAlerts.EXTRA_SCAN_ID, -1L) ?: -1L
        if (scanId >= 0) {
            vm.openScan(scanId, fromAlert = true)
            NotificationManagerCompat.from(this).cancel(scanId.toInt())
        }
    }
}
