package com.scamguard.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.ml.MessageCategory
import com.scamguard.app.ml.ModelId
import com.scamguard.app.service.LiveWindow
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.components.DataPoint
import com.scamguard.app.ui.components.Eyebrow
import com.scamguard.app.ui.components.GradientPanel
import com.scamguard.app.ui.components.LevelChip
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.SectionDivider
import com.scamguard.app.ui.components.SurfacePanel
import com.scamguard.app.ui.components.ToggleRow
import com.scamguard.app.ui.components.formatTime
import com.scamguard.app.ui.components.maskOrUnknown
import com.scamguard.app.ui.components.rememberHealth
import com.scamguard.app.ui.components.ShieldIcon
import com.scamguard.app.ui.navigation.containerViewModel
import com.scamguard.app.ui.theme.AlertRed
import com.scamguard.app.ui.theme.AlertRedContainer
import com.scamguard.app.ui.theme.AppBurgundyDeep
import com.scamguard.app.ui.theme.SafeGreen
import com.scamguard.app.ui.theme.WarningAmber
import com.scamguard.app.ui.theme.WarningAmberLight
import com.scamguard.app.util.ProtectionStatus

/** Home: current coverage, a live warning, recent results, and model readiness. */
@Composable
fun HomeScreen(
    onOpenAlert: (String) -> Unit,
    onOpenHealth: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenMessage: (Long) -> Unit,
    onOpenCheck: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val vm = containerViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val health = rememberHealth()
    val status = health.status(state.settings.protectionEnabled)

    ScreenFrame(stringResource(R.string.app_name)) {
        state.live?.takeIf { it.decision.level != RiskLevel.SAFE }?.let { live ->
            WarningBanner(live) { onOpenAlert(live.window.id) }
        }

        ProtectionPanel(
            status = status,
            enabled = state.settings.protectionEnabled,
            onToggle = vm::setProtection,
            onOpenHealth = onOpenHealth,
        )

        SurfacePanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                DataPoint(stringResource(R.string.home_calls_checked), state.callsChecked.toString(), Modifier.weight(1f))
                DataPoint(stringResource(R.string.home_alerts_week), state.alertsThisWeek.toString(), Modifier.weight(1f))
            }
        }

        Eyebrow(stringResource(R.string.home_last_alert))
        val last = state.lastAlert
        if (last == null) {
            Text(stringResource(R.string.home_no_alerts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SurfacePanel(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenAlert(last.windowId) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LevelChip(Labels.parseLevel(last.peakLevel))
                    Text(stringResource(Labels.scamType(Labels.parseScamType(last.scamType))), style = MaterialTheme.typography.titleMedium)
                }
                Text("${maskOrUnknown(last.mask)} · ${formatTime(last.startedAt)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        state.lastMessage?.let { message ->
            Eyebrow(stringResource(R.string.home_last_message))
            SurfacePanel(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenMessage(message.id) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                Text(stringResource(Labels.messageVerdict(Labels.parseMessageVerdict(message.verdict))), style = MaterialTheme.typography.titleMedium)
                MessageCategory.parse(message.category)?.let { Text(stringResource(Labels.category(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(formatTime(message.ts), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Button(onClick = onOpenCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text(stringResource(R.string.home_check_message), style = MaterialTheme.typography.titleMedium)
        }

        SurfacePanel(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenModels),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.home_models), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_models_ready, state.modelsReady, ModelId.entries.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${state.modelsReady}/${ModelId.entries.size}",
                    color = if (state.modelsReady == ModelId.entries.size) SafeGreen else WarningAmber,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (state.settings.demoMode) {
            OutlinedButton(onClick = onOpenSimulator, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.simulator_title))
            }
        }
        Text(stringResource(R.string.privacy_promise), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProtectionPanel(status: ProtectionStatus, enabled: Boolean, onToggle: (Boolean) -> Unit, onOpenHealth: () -> Unit) {
    val (color, title, body) = when (status) {
        ProtectionStatus.ON -> Triple(SafeGreen, R.string.status_on, R.string.status_on_body)
        ProtectionStatus.LIMITED -> Triple(WarningAmber, R.string.status_limited, R.string.status_limited_body)
        ProtectionStatus.OFF -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, R.string.status_off, R.string.status_off_body)
    }
    GradientPanel(start = AppBurgundyDeep, end = MaterialTheme.colorScheme.surface) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            ShieldIcon(color, contentDescription = stringResource(title), size = 54.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(stringResource(R.string.app_name))
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, color = color)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionDivider(Modifier.padding(vertical = 4.dp))
        ToggleRow(stringResource(R.string.home_protection_toggle), stringResource(R.string.home_protection_toggle_desc), enabled, onToggle)
        if (status == ProtectionStatus.LIMITED) {
            OutlinedButton(onClick = onOpenHealth, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_fix_permissions))
            }
        }
    }
}

@Composable
private fun WarningBanner(live: LiveWindow, onClick: () -> Unit) {
    val d = live.decision
    val isRed = d.level == RiskLevel.RED_FLAG
    val tint = if (isRed) AlertRed else WarningAmber
    GradientPanel(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.clickable(onClick = onClick),
        start = if (isRed) AlertRedContainer else WarningAmberLight,
        end = AppBurgundyDeep,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LevelChip(d.level)
            Text(stringResource(R.string.warning_title, stringResource(Labels.scamType(d.scamType))), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(stringResource(R.string.risk_score_value, d.score), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.detail_scored_by, stringResource(Labels.scoreSource(d.source.name))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        d.reasonCodes.take(3).forEach { Text("• " + stringResource(Labels.reason(it)), style = MaterialTheme.typography.bodyMedium) }
        Text(stringResource(R.string.banner_tap), style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
