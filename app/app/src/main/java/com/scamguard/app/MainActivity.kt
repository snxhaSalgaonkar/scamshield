package com.scamguard.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.scamguard.app.alerts.FullScreenAlertActivity
import com.scamguard.app.ui.navigation.AppRoot
import com.scamguard.app.ui.navigation.LocalAppContainer
import com.scamguard.app.ui.theme.ScamGuardTheme
import kotlinx.coroutines.launch

/** The single main activity hosting all Compose screens. */
class MainActivity : AppCompatActivity() {
    private val openWindowId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openWindowId.value = intent.getStringExtra(EXTRA_WINDOW_ID)

        // While the app is on screen, open red-flag alerts directly instead of relying on the
        // full-screen intent (which Android shows as a heads-up when the phone is in use).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.alertDispatcher.redFlags.collect { id ->
                    startActivity(FullScreenAlertActivity.intent(this@MainActivity, id))
                }
            }
        }

        setContent {
            CompositionLocalProvider(LocalAppContainer provides appContainer) {
                ScamGuardTheme {
                    AppRoot(openWindowId.value, onWindowOpened = { openWindowId.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openWindowId.value = intent.getStringExtra(EXTRA_WINDOW_ID)
    }

    companion object {
        const val EXTRA_WINDOW_ID = "open_window_id"
    }
}
