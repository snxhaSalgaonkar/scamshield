package com.scamguard.app.ui.screens.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.ml.ModelId
import com.scamguard.app.ml.ModelState
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.SurfacePanel
import com.scamguard.app.ui.components.TagChip
import com.scamguard.app.ui.navigation.LocalAppContainer
import com.scamguard.app.ui.theme.AlertRed
import com.scamguard.app.ui.theme.SafeGreen

/** Which models are loaded, their held-out metrics and limits, and what the app can observe. */
@Composable
fun ModelStatusScreen(onBack: () -> Unit, onOpenHealth: () -> Unit) {
    val predictions = LocalAppContainer.current.predictions
    val health by predictions.health.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { predictions.ensureLoaded() }

    ScreenFrame(stringResource(R.string.models_title), onBack = onBack) {
        Text(stringResource(R.string.models_intro), style = MaterialTheme.typography.bodyMedium)
        ModelId.entries.forEach { id -> ModelCard(id, health.states[id] ?: ModelState.NotLoaded) }

        Text(stringResource(R.string.signals_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        SurfacePanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.signals_can), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.signals_cannot), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenHealth, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.signals_open_health))
                }
            }
        }
    }
}

@Composable
private fun ModelCard(id: ModelId, state: ModelState) {
    val (title, desc, primary) = when (id) {
        ModelId.TEXT -> Triple(R.string.model_text, R.string.model_text_desc, "binary")
        ModelId.URL -> Triple(R.string.model_url, R.string.model_url_desc, "binary")
        ModelId.CALL -> Triple(R.string.model_call, R.string.model_call_desc, "model")
    }
    SurfacePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                if (state is ModelState.Ready && state.info.isExperimental(primary)) TagChip(stringResource(R.string.tag_experimental))
            }
            Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
            when (state) {
                is ModelState.Ready -> {
                    Text(stringResource(R.string.model_state_ready) + " · " + state.info.version, color = SafeGreen, style = MaterialTheme.typography.labelLarge)
                    val m = state.info.metrics
                    fun v(k: String) = (m[k] as? Number)?.let { "%.2f".format(it.toDouble()) } ?: "—"
                    val synthetic = m["synthetic"] == true
                    Text(
                        if (synthetic) stringResource(R.string.model_metrics_synthetic, v("precision"), v("recall"), v("f1"))
                        else stringResource(R.string.model_metrics, v("precision"), v("recall"), v("f1"), v("pr_auc"), (m["n"] ?: "—").toString()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is ModelState.Missing -> StateError(stringResource(R.string.model_state_missing, state.file))
                is ModelState.Failed -> StateError(stringResource(R.string.model_state_failed, state.reason))
                ModelState.NotLoaded -> Text(stringResource(R.string.model_state_loading), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StateError(text: String) {
    Text(text, color = AlertRed, style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.model_unavailable_effect), style = MaterialTheme.typography.bodySmall)
}
