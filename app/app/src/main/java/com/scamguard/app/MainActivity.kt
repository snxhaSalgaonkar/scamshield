package com.scamguard.app

import android.content.Intent
import android.net.Uri
import android.os.Build
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
    private val openMessageId = mutableStateOf<Long?>(null)
    private val openListen = mutableStateOf(false)
    private val sharedImage = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readExtras(intent)

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
                    AppRoot(
                        openWindowId = openWindowId.value,
                        onWindowOpened = { openWindowId.value = null },
                        openMessageId = openMessageId.value,
                        onMessageOpened = { openMessageId.value = null },
                        openListen = openListen.value,
                        onListenOpened = { openListen.value = false },
                        sharedImage = sharedImage.value,
                        onSharedImageOpened = { sharedImage.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent) {
        openWindowId.value = intent.getStringExtra(EXTRA_WINDOW_ID)
        openMessageId.value = intent.getLongExtra(EXTRA_MESSAGE_ID, -1).takeIf { it >= 0 }
        openListen.value = intent.getBooleanExtra(EXTRA_OPEN_LISTEN, false)
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            sharedImage.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        }
    }

    companion object {
        const val EXTRA_WINDOW_ID = "open_window_id"
        const val EXTRA_MESSAGE_ID = "open_message_id"
        const val EXTRA_OPEN_LISTEN = "open_listen"
    }
}
