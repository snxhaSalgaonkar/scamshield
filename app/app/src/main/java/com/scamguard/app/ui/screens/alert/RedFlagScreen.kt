package com.scamguard.app.ui.screens.alert

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.actions.CallActions
import com.scamguard.app.actions.GuardDeviceAdmin
import com.scamguard.app.actions.PhoneFreeze
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.navigation.containerViewModel
import com.scamguard.app.ui.theme.AlertRed
import kotlinx.coroutines.launch

/** Full-screen red alert: score, scam type, three reasons and big actions. */
@Composable
fun RedFlagScreen(windowId: String, onClose: () -> Unit) {
    val vm = containerViewModel(key = windowId) { RedFlagViewModel(it, windowId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = state ?: return

    val endCallPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && CallActions.endCall(context)) onClose()
    }
    val endCall = {
        if (s.isTest) {
            onClose()
        } else if (CallActions.canEndCall(context)) {
            if (CallActions.endCall(context)) onClose()
        } else {
            endCallPermission.launch(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }
    val reported = stringResource(R.string.reported_toast)
    val freezeSetup = stringResource(R.string.freeze_phone_setup)
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(AlertRed)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (s.isTest) WhiteText(stringResource(R.string.test_alert_banner), MaterialTheme.typography.labelLarge)
        Text(
            stringResource(R.string.red_flag_heading),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Assertive },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WhiteText("${s.score}", MaterialTheme.typography.displayMedium)
            Column {
                WhiteText(stringResource(R.string.risk_score_label), MaterialTheme.typography.labelLarge)
                WhiteText(stringResource(Labels.scamType(s.scamType)), MaterialTheme.typography.titleLarge)
                if (s.mask.isNotEmpty()) WhiteText(s.mask, MaterialTheme.typography.bodyMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.reasons.take(3).forEach { WhiteText("• " + stringResource(Labels.reason(it)), MaterialTheme.typography.bodyLarge) }
        }
        WhiteText(stringResource(R.string.red_flag_advice), MaterialTheme.typography.bodyLarge)

        val big = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        val whiteButton = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlertRed)
        when {
            s.callEnded -> Button(
                onClick = { context.startActivity(CallActions.dialHelplineIntent()) },
                modifier = big, colors = whiteButton,
            ) { Text(stringResource(R.string.action_call_bank_1930), style = MaterialTheme.typography.titleMedium) }
            CallActions.endCallSupported() -> Button(onClick = endCall, modifier = big, colors = whiteButton) {
                Text(stringResource(R.string.action_end_call), style = MaterialTheme.typography.titleLarge)
            }
            else -> WhiteText(stringResource(R.string.end_call_manually), MaterialTheme.typography.titleMedium)
        }

        val outline = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        val mid = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        // Reversible stand-in for switching the phone off: end the call, lock the screen now.
        OutlinedButton(
            onClick = {
                scope.launch {
                    when (PhoneFreeze.freeze(context, endCall = !s.isTest)) {
                        PhoneFreeze.Result.LOCKED -> onClose()
                        PhoneFreeze.Result.NEEDS_SETUP -> {
                            Toast.makeText(context, freezeSetup, Toast.LENGTH_LONG).show()
                            context.startActivity(GuardDeviceAdmin.activationIntent(context))
                        }
                    }
                }
            },
            modifier = big,
            colors = outline,
            border = BorderStroke(2.dp, Color.White),
        ) { Text(stringResource(R.string.action_freeze_phone), style = MaterialTheme.typography.titleMedium) }
        WhiteText(stringResource(R.string.freeze_phone_hint), MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { vm.thisWasMe(onClose) }, modifier = mid, colors = outline) {
            Text(stringResource(R.string.action_know_caller))
        }
        OutlinedButton(
            onClick = { vm.reportScam { Toast.makeText(context, reported, Toast.LENGTH_SHORT).show(); onClose() } },
            modifier = mid, colors = outline,
        ) { Text(stringResource(R.string.action_report_scam)) }
        TextButton(onClick = onClose, modifier = mid, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
            Text(stringResource(R.string.action_dismiss))
        }
    }
}

@Composable
private fun WhiteText(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(text, style = style, color = Color.White)
}
