package com.scamguard.app.ui.screens.detail

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.data.db.CallEventEntity
import com.scamguard.app.data.db.SignalEventEntity
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.components.maskOrUnknown
import com.scamguard.app.ui.components.ChipGap
import com.scamguard.app.ui.components.LayerBars
import com.scamguard.app.ui.components.LevelChip
import com.scamguard.app.ui.components.ScoreGauge
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.TagChip
import com.scamguard.app.ui.components.formatClock
import com.scamguard.app.ui.components.formatRupees
import com.scamguard.app.ui.components.formatTime
import com.scamguard.app.ui.navigation.containerViewModel
import com.scamguard.app.ui.screens.history.feedbackLabel

/** Alert detail: score gauge, layer bars, reasons, event timeline and actions. */
@Composable
fun AlertDetailScreen(windowId: String, onBack: () -> Unit) {
    val vm = containerViewModel(key = windowId) { AlertDetailViewModel(it, windowId) }
    val detail by vm.detail.collectAsStateWithLifecycle()

    ScreenFrame(stringResource(R.string.detail_title), onBack = onBack) {
        val data = detail ?: return@ScreenFrame
        val d = data.decision
        val level = Labels.parseLevel(d.level)

        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelChip(Labels.parseLevel(d.peakLevel))
            if (d.simulated) { ChipGap(); TagChip(stringResource(R.string.tag_simulated)) }
            if (d.feedback != null) { ChipGap(); TagChip(stringResource(feedbackLabel(d.feedback))) }
        }
        Text(stringResource(Labels.scamType(Labels.parseScamType(d.scamType))), style = MaterialTheme.typography.headlineMedium)
        Text("${maskOrUnknown(d.mask)} · ${formatTime(d.startedAt)}", style = MaterialTheme.typography.bodyMedium)

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreGauge(d.score, level)
            if (d.peakScore > d.score) {
                Text(stringResource(R.string.detail_peak, d.peakScore), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { LayerBars(d.l1, d.l2, d.l3) }
        }

        SectionTitle(stringResource(R.string.detail_reasons))
        val reasons = Labels.parseReasons(d.reasonCodes)
        if (reasons.isEmpty()) Text(stringResource(R.string.detail_no_reasons))
        reasons.forEach { Text("• " + stringResource(Labels.reason(it)), style = MaterialTheme.typography.bodyLarge) }

        SectionTitle(stringResource(R.string.detail_timeline))
        Timeline(data.callEvents, data.signalEvents)

        SectionTitle(stringResource(R.string.detail_actions))
        val wide = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        OutlinedButton(onClick = { vm.feedback(FeedbackKind.THIS_WAS_ME) }, modifier = wide) {
            Text(stringResource(R.string.action_this_was_me))
        }
        Button(onClick = { vm.feedback(FeedbackKind.CONFIRMED_SCAM) }, modifier = wide) {
            Text(stringResource(R.string.action_report_scam))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** Event types, amounts and times only; there is no text to show because none is stored. */
@Composable
private fun Timeline(calls: List<CallEventEntity>, signals: List<SignalEventEntity>) {
    val rows = calls.map { it.ts to callLine(it) } + signals.map { it.ts to signalLine(it) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows.isEmpty()) Text(stringResource(R.string.detail_no_events))
            rows.sortedBy { it.first }.forEach { (ts, line) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatClock(ts), style = MaterialTheme.typography.labelLarge)
                    Text(line(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun callLine(e: CallEventEntity): @Composable () -> String = {
    when (e.type) {
        "RINGING" -> stringResource(if (e.isHidden) R.string.ev_ringing_hidden else R.string.ev_ringing)
        "ACTIVE" -> stringResource(R.string.ev_active)
        "ENDED" -> stringResource(R.string.ev_ended, ((e.durationMs ?: 0) / 1000).toInt())
        else -> stringResource(R.string.ev_second_call)
    }
}

private fun signalLine(e: SignalEventEntity): @Composable () -> String = {
    when (e.type) {
        "OTP" -> stringResource(if (e.flag == true) R.string.ev_otp_trusted else R.string.ev_otp_unknown)
        "DEBIT" -> {
            val amount = formatRupees(e.amountPaise ?: 0)
            val balance = e.balancePaise
            if (balance != null) stringResource(R.string.ev_debit_balance, amount, formatRupees(balance))
            else stringResource(R.string.ev_debit, amount)
        }
        "LINK" -> stringResource(R.string.ev_link)
        "FORWARDING" -> stringResource(if (e.flag == true) R.string.ev_forwarding_on else R.string.ev_forwarding_off)
        "REMOTE_APP" -> stringResource(R.string.ev_remote_app, e.pkg.orEmpty())
        "APK_INSTALLED" -> stringResource(
            if (e.flag == true) R.string.ev_apk_sideloaded else R.string.ev_apk_store, e.pkg.orEmpty(),
        )
        "NETWORK_LOST" -> stringResource(R.string.ev_network_lost)
        "FEEDBACK" -> stringResource(if (e.detail == "THIS_WAS_ME") R.string.ev_feedback_me else R.string.ev_feedback_scam)
        "VOICE_KEYWORD" -> stringResource(R.string.ev_voice, stringResource(Labels.voiceCategory(e.detail)))
        else -> e.type
    }
}
