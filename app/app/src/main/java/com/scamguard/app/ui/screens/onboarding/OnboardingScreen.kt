package com.scamguard.app.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.ui.components.ConsentToggles
import com.scamguard.app.ui.components.ShieldIcon
import com.scamguard.app.ui.navigation.containerViewModel
import com.scamguard.app.util.Permissions

private const val STEPS = 4

/** Four-step onboarding: what it does, privacy, consent, permissions. */
@Composable
fun OnboardingScreen() {
    val vm = containerViewModel { OnboardingViewModel(it.settings) }
    val settings by vm.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LinearProgressIndicator(
            progress = { (step + 1f) / STEPS },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.onboarding_step, step + 1, STEPS),
            style = MaterialTheme.typography.labelLarge,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                0 -> IntroStep()
                1 -> PrivacyStep()
                2 -> ConsentToggles(settings, vm::setVoiceLayer, vm::setTrainingUpload, vm::setCaseSharing)
                else -> PermissionsStep()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step-- },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.action_back)) }
            }
            Button(
                onClick = { if (step < STEPS - 1) step++ else vm.finish() },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Text(stringResource(if (step < STEPS - 1) R.string.action_next else R.string.action_finish))
            }
        }
    }
}

@Composable
private fun IntroStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ShieldIcon(MaterialTheme.colorScheme.primary, contentDescription = null, size = 120.dp)
    }
    StepTitle(stringResource(R.string.onboarding_intro_title))
    Text(stringResource(R.string.onboarding_intro_body), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.onboarding_intro_signals), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PrivacyStep() {
    StepTitle(stringResource(R.string.onboarding_privacy_title))
    Card(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.privacy_promise),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
    Text(stringResource(R.string.onboarding_privacy_body), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    StepTitle(stringResource(R.string.onboarding_permissions_title))
    Text(stringResource(R.string.onboarding_permissions_body), style = MaterialTheme.typography.bodyLarge)
    Button(
        onClick = { permissionLauncher.launch(Permissions.corePermissions().toTypedArray()) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.action_grant_permissions)) }

    if (Permissions.screeningRoleSupported()) {
        OutlinedButton(
            onClick = { Permissions.screeningRoleIntent(context)?.let(activityLauncher::launch) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.action_set_caller_id)) }
    } else {
        Text(stringResource(R.string.caller_id_legacy_instructions), style = MaterialTheme.typography.bodyMedium)
    }

    OutlinedButton(
        onClick = { activityLauncher.launch(Permissions.batteryOptimizationIntent()) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.action_battery_optimisation)) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.oem_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.oem_samsung), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.oem_xiaomi), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.oem_oppo), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.oem_vivo), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(stringResource(R.string.onboarding_permissions_later), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}
