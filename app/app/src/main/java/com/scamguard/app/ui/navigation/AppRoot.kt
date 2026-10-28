package com.scamguard.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.screens.onboarding.OnboardingScreen

/** Shows onboarding until it is finished, then the main navigation. */
@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings ?: return
    if (!current.onboardingDone) {
        OnboardingScreen()
    } else {
        Scaffold { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                ScreenFrame(stringResource(R.string.app_name)) { Text(stringResource(R.string.privacy_promise)) }
            }
        }
    }
}
