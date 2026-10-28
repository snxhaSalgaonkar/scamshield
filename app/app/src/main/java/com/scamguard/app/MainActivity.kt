package com.scamguard.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.scamguard.app.ui.navigation.AppRoot
import com.scamguard.app.ui.navigation.LocalAppContainer
import com.scamguard.app.ui.theme.ScamGuardTheme

/** The single main activity hosting all Compose screens. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalAppContainer provides appContainer) {
                ScamGuardTheme { AppRoot() }
            }
        }
    }
}
