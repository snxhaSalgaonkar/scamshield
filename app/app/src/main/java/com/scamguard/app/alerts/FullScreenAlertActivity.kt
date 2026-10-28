package com.scamguard.app.alerts

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.scamguard.app.appContainer
import com.scamguard.app.ui.navigation.LocalAppContainer
import com.scamguard.app.ui.screens.alert.RedFlagScreen
import com.scamguard.app.ui.theme.ScamGuardTheme

/**
 * Lock-screen red-flag alert opened by a full-screen intent. Shows the score, scam type,
 * three plain reasons and the actions the user can take.
 */
class FullScreenAlertActivity : AppCompatActivity() {
    private val windowId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        windowId.value = intent.getStringExtra(EXTRA_WINDOW_ID)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides appContainer) {
                ScamGuardTheme {
                    val id = windowId.value
                    if (id == null) finish() else RedFlagScreen(windowId = id, onClose = ::finish)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        windowId.value = intent.getStringExtra(EXTRA_WINDOW_ID)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_WINDOW_ID = "window_id"
        /** Pseudo window id used by "Send test alert". */
        const val TEST_WINDOW = "test-alert"

        fun intent(context: Context, windowId: String): Intent =
            Intent(context, FullScreenAlertActivity::class.java)
                .putExtra(EXTRA_WINDOW_ID, windowId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun testIntent(context: Context): Intent = intent(context, TEST_WINDOW)
    }
}
