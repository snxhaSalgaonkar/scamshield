package com.scamguard.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.ui.components.ConsentToggles
import com.scamguard.app.ui.components.SectionDivider
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.SurfacePanel
import com.scamguard.app.ui.components.ToggleRow
import com.scamguard.app.ui.navigation.containerViewModel

/** Settings: links to health, models, numbers and data; consent levels, language and Demo Mode. */
@Composable
fun SettingsScreen(
    onOpenSimulator: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenNumbers: () -> Unit,
    onOpenData: () -> Unit,
) {
    val vm = containerViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return

    ScreenFrame(stringResource(R.string.settings_title)) {
        SurfacePanel(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            listOf(
                R.string.health_title to onOpenHealth,
                R.string.settings_models to onOpenModels,
                R.string.settings_numbers to onOpenNumbers,
                R.string.settings_data to onOpenData,
            ).forEachIndexed { index, (label, onClick) ->
                if (index > 0) SectionDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SectionTitle(stringResource(R.string.settings_consent))
        ConsentToggles(s, vm::setVoiceLayer, vm::setTrainingUpload, vm::setCaseSharing)

        SectionTitle(stringResource(R.string.settings_language))
        SurfacePanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
            Column(Modifier.padding(8.dp).selectableGroup()) {
                listOf(
                    "system" to R.string.language_system,
                    "en" to R.string.language_english,
                    "hi" to R.string.language_hindi,
                ).forEach { (tag, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = s.language == tag, role = Role.RadioButton) { vm.setLanguage(tag) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.language == tag, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.settings_demo))
        SurfacePanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Column {
                ToggleRow(stringResource(R.string.demo_mode_title), stringResource(R.string.demo_mode_desc), s.demoMode, vm::setDemoMode)
                if (s.demoMode) {
                    OutlinedButton(onClick = onOpenSimulator, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.simulator_title))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}
