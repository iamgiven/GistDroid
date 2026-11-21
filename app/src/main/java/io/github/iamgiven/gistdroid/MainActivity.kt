package io.github.iamgiven.gistdroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.iamgiven.gistdroid.data.ThemeMode
import io.github.iamgiven.gistdroid.ui.GistDroidNavHost
import io.github.iamgiven.gistdroid.ui.theme.GistDroidTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as GistDroidApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamic by container.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            GistDroidTheme(themeMode = theme, dynamicColor = dynamic) {
                GistDroidNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        container.pendingShare.value = SharedText(intent.getStringExtra(Intent.EXTRA_SUBJECT), text)
    }
}
