package com.scamguard.app.ui.screens.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.ui.components.ChipGap
import com.scamguard.app.ui.components.LevelChip
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.navigation.containerViewModel
import com.scamguard.app.ui.theme.AlertRed
import com.scamguard.app.ui.theme.SafeGreen

/** Scenario Simulator (Demo Mode): plays scripted events through the real pipeline. */
@Composable
fun SimulatorScreen(onBack: () -> Unit) {
    val vm = containerViewModel { SimulatorViewModel(it) }
    val results by vm.results.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    ScreenFrame(stringResource(R.string.simulator_title), onBack = onBack) {
        Text(stringResource(R.string.simulator_intro), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = vm::runAll,
            enabled = !running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.simulator_run_all)) }
        if (results.size == Scenarios.all.size) {
            val passed = Scenarios.all.count { s -> results[s.id]?.passed(s.expected) == true }
            Text(
                stringResource(R.string.simulator_summary, passed, Scenarios.all.size),
                style = MaterialTheme.typography.titleMedium,
                color = if (passed == Scenarios.all.size) SafeGreen else AlertRed,
            )
        }
        Scenarios.all.forEach { scenario ->
            ScenarioCard(scenario, results[scenario.id], enabled = !running) { vm.play(scenario) }
        }
    }
}

@Composable
private fun ScenarioCard(scenario: Scenario, result: ScenarioResult?, enabled: Boolean, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${scenario.id}. " + stringResource(scenario.title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(scenario.description), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.simulator_expected), style = MaterialTheme.typography.labelLarge)
                ChipGap()
                LevelChip(scenario.expected)
            }
            if (result != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(stringResource(R.string.simulator_actual, result.score), style = MaterialTheme.typography.labelLarge)
                    ChipGap()
                    LevelChip(result.level)
                    ChipGap()
                    val pass = result.passed(scenario.expected)
                    Text(
                        stringResource(if (pass) R.string.simulator_pass else R.string.simulator_fail),
                        color = if (pass) SafeGreen else AlertRed,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            OutlinedButton(onClick = onPlay, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.simulator_play))
            }
        }
    }
}
