package com.example.sipatkilatis

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.sipatkilatis.ui.AppNavHost
import com.example.sipatkilatis.ui.MainViewModel
import com.example.sipatkilatis.ui.theme.SipatKilatisTheme

/**
 * Single activity hosting all Compose screens.
 * AppCompatActivity (not ComponentActivity) so the per-app language switch works on Android 8+.
 */
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShareIntent(intent)   // not again after rotation / language change
        setContent {
            SipatKilatisTheme {
                AppNavHost(vm)
            }
        }
    }

    // Activity is singleTop: a share while the app is open arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** "Share → Sipat Kilatis" from any messaging app puts the text into the Check screen. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let(vm::onSharedText)
        }
    }
}
