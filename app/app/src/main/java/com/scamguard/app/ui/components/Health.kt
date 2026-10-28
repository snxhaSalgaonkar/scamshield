package com.scamguard.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.scamguard.app.util.HealthCheck
import com.scamguard.app.util.HealthSnapshot

/** A [HealthSnapshot] that refreshes whenever the screen resumes (e.g. back from Settings). */
@Composable
fun rememberHealth(): HealthSnapshot {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(HealthCheck.snapshot(context)) }
    LifecycleResumeEffect(Unit) {
        snapshot = HealthCheck.snapshot(context)
        onPauseOrDispose { }
    }
    return snapshot
}
