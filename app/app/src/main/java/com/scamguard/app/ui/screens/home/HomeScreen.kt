package com.scamguard.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.service.LiveWindow
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.components.LevelChip
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.ShieldIcon
import com.scamguard.app.ui.components.ToggleRow
import com.scamguard.app.ui.components.formatTime
import com.scamguard.app.ui.components.levelColor
import com.scamguard.app.ui.components.rememberHealth
import com.scamguard.app.ui.navigation.containerViewModel
import com.scamguard.app.ui.theme.SafeGreen
import com.scamguard.app.ui.theme.WarningAmber
import com.scamguard.app.util.ProtectionStatus

/** Home: protection status, live warning banner, counters and the last alert. */
@Composable
fun HomeScreen(
    onOpenAlert: (String) -> Unit,
    onOpenHealth: () -> Unit,
    onOpenSimulator: () -> Unit,
) {
    val vm = containerViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val health = rememberHealth()
    val status = health.status(state.settings.protectionEnabled)

    ScreenFrame(stringResource(R.string.app_name)) {
        state.live?.takeIf { it.decision.level != RiskLevel.SAFE }?.let { WarningBanner(it) { onOpenAlert(it.window.id) } }

        StatusCard(status, onToggle = vm::setProtection, enabled = state.settings.protectionEnabled, onOpenHealth = onOpenHealth)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Counter(stringResource(R.string.home_calls_checked), state.callsChecked, Modifier.weight(1f))
            Counter(stringResource(R.string.home_alerts_week), state.alertsThisWeek, Modifier.weight(1f))
        }

        Text(stringResource(R.string.home_last_alert), style = MaterialTheme.typography.titleMedium)
        val last = state.lastAlert
        if (last == null) {
            Card(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_no_alerts), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Card(Modifier.fillMaxWidth().clickable { onOpenAlert(last.windowId) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LevelChip(Labels.parseLevel(last.peakLevel))
                    Text(stringResource(Labels.scamType(Labels.parseScamType(last.scamType))), style = MaterialTheme.typography.titleMedium)
                    Text("${last.mask} · ${formatTime(last.startedAt)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.settings.demoMode) {
            OutlinedButton(onClick = onOpenSimulator, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.simulator_title))
            }
        }
        Text(stringResource(R.string.privacy_promise), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusCard(status: ProtectionStatus, enabled: Boolean, onToggle: (Boolean) -> Unit, onOpenHealth: () -> Unit) {
    val (color, title, body) = when (status) {
        ProtectionStatus.ON -> Triple(SafeGreen, R.string.status_on, R.string.status_on_body)
        ProtectionStatus.LIMITED -> Triple(WarningAmber, R.string.status_limited, R.string.status_limited_body)
        ProtectionStatus.OFF -> Triple(MaterialTheme.colorScheme.outline, R.string.status_off, R.string.status_off_body)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShieldIcon(color, contentDescription = stringResource(title))
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, color = color)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            ToggleRow(stringResource(R.string.home_protection_toggle), stringResource(R.string.home_protection_toggle_desc), enabled, onToggle)
            if (status == ProtectionStatus.LIMITED) {
                OutlinedButton(onClick = onOpenHealth, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_fix_permissions))
                }
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, modifier: Modifier) {
    Card(modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp)) {
            Text("$value", style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WarningBanner(live: LiveWindow, onClick: () -> Unit) {
    val d = live.decision
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = levelColor(d.level), contentColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.warning_title, stringResource(Labels.scamType(d.scamType))),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.risk_score_value, d.score), style = MaterialTheme.typography.bodyMedium)
            d.reasonCodes.take(3).forEach { Text("• " + stringResource(Labels.reason(it)), style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.banner_tap), style = MaterialTheme.typography.labelLarge)
        }
    }
}
