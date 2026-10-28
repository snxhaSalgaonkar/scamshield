package com.scamguard.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scamguard.app.R
import com.scamguard.app.data.settings.AppSettings

/** A labelled switch row with a description; the whole row is the touch target. */
@Composable
fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** The four consent levels. events_only is mandatory and cannot be switched off. */
@Composable
fun ConsentToggles(
    settings: AppSettings,
    onVoice: (Boolean) -> Unit,
    onTraining: (Boolean) -> Unit,
    onCaseSharing: (Boolean) -> Unit,
) {
    SurfacePanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Column {
            ToggleRow(
                stringResource(R.string.consent_events_title),
                stringResource(R.string.consent_events_desc),
                checked = true, onChange = {}, enabled = false,
            )
            ToggleRow(
                stringResource(R.string.consent_voice_title),
                stringResource(R.string.consent_voice_desc),
                checked = false, onChange = onVoice, enabled = false,
            )
            ToggleRow(
                stringResource(R.string.consent_training_title),
                stringResource(R.string.consent_training_desc),
                settings.trainingUpload, onTraining,
            )
            ToggleRow(
                stringResource(R.string.consent_case_title),
                stringResource(R.string.consent_case_desc),
                settings.caseSharing, onCaseSharing,
            )
        }
    }
}
