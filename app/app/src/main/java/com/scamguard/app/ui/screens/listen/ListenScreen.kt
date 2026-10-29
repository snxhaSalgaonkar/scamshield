package com.scamguard.app.ui.screens.listen

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.scamguard.app.voice.ShizukuAudio
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.components.ChipGap
import com.scamguard.app.ui.components.LevelChip
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.TagChip
import com.scamguard.app.ui.components.formatClock
import com.scamguard.app.ui.components.highlightScamPhrases
import com.scamguard.app.ui.components.levelColor
import com.scamguard.app.ui.navigation.LocalAppContainer
import com.scamguard.app.voice.CallAudioHelper
import com.scamguard.app.voice.CallListenService
import com.scamguard.app.voice.ListenActivity
import com.scamguard.app.voice.ListenLogLine

/**
 * Live call check: what the speech service is hearing, which scam tactics were found (with
 * the words), the call's risk, and a log of every listening event so problems such as a
 * silent microphone during calls are visible. Everything here is in memory only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListenScreen(onBack: () -> Unit, onOpenCall: (String) -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val s by CallListenService.state.collectAsStateWithLifecycle()
    val live by container.sessionManager.live.collectAsStateWithLifecycle()
    val sim by container.sessionManager.simulated.collectAsStateWithLifecycle()
    val decision = (if (s.test) sim else live)?.decision
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var shizuku by remember { mutableStateOf(ShizukuAudio.status()) }
    var inCall by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            shizuku = ShizukuAudio.status()
            inCall = CallListenService.isInCall(context)
            delay(1_500)
        }
    }

    ScreenFrame(stringResource(R.string.listen_screen_title), onBack = onBack) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            when {
                                !s.active -> R.string.listen_status_off
                                s.test -> R.string.listen_title_test
                                else -> R.string.listen_title
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (decision != null) LevelChip(decision.level)
                }
                if (s.active) {
                    Text(stringResource(if (s.viaShizuku) R.string.listen_level_shizuku else R.string.listen_level), style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress = { ((s.level + 2f) / 12f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    CallListenService.riskLine(context, decision, s),
                    style = MaterialTheme.typography.bodyLarge,
                    color = decision?.let { levelColor(it.level) } ?: MaterialTheme.colorScheme.onSurface,
                )
                ShizukuRow()
                LanguageRow(settings?.listenLanguage ?: CallListenService.LANGUAGE, active = s.active) { tag ->
                    scope.launch { container.settings.setListenLanguage(tag) }
                }
                if (shizuku != ShizukuAudio.Status.READY && !CallAudioHelper.isEnabled(context)) {
                    Text(stringResource(R.string.listen_helper_off), color = levelColor(RiskLevel.WARNING), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.listen_helper_steps), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { context.startActivity(CallAudioHelper.settingsIntent()) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.listen_helper_button)) }
                }
                s.problem?.let {
                    Text(stringResource(it), color = levelColor(RiskLevel.RED_FLAG), style = MaterialTheme.typography.bodyMedium)
                }
                // Why nothing is heard when listening never started: the consent, or how listening starts.
                if (!s.active && settings?.voiceLayer == false) {
                    Text(stringResource(R.string.listen_consent_off), color = levelColor(RiskLevel.WARNING), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { scope.launch { container.settings.setVoiceLayer(true) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.listen_consent_on_button)) }
                } else if (!s.active && !inCall) {
                    Text(stringResource(R.string.listen_how_it_starts), style = MaterialTheme.typography.bodyMedium)
                }
                if (!s.active && inCall) {
                    Button(
                        onClick = { context.startActivity(ListenActivity.intent(context, ListenActivity.Mode.CALL)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.listen_now_button)) }
                    Text(stringResource(R.string.listen_now_desc), style = MaterialTheme.typography.bodySmall)
                }
                if (s.active) {
                    OutlinedButton(onClick = { CallListenService.stop(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.listen_stop))
                    }
                } else {
                    Button(
                        onClick = { context.startActivity(ListenActivity.intent(context, ListenActivity.Mode.TEST)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.listen_test_button)) }
                    Text(stringResource(R.string.listen_test_desc), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (s.found.isNotEmpty()) {
            Text(stringResource(R.string.listen_tactics), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.found.forEach { (category, phrase) ->
                    TagChip("“$phrase” · " + stringResource(Labels.voiceCategory(category.name)))
                }
            }
        }
        val windowId = s.windowId
        if (windowId != null && !s.active) {
            OutlinedButton(onClick = { onOpenCall(windowId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.listen_open_history))
            }
        }

        Text(stringResource(R.string.listen_transcript), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.lines.isEmpty() && s.partial.isBlank() && !s.active) {
                    Text(stringResource(R.string.listen_transcript_not_listening), style = MaterialTheme.typography.bodyMedium)
                } else if (s.lines.isEmpty() && s.partial.isBlank()) {
                    Text(stringResource(if (s.viaShizuku) R.string.listen_transcript_empty_shizuku else R.string.listen_transcript_empty), style = MaterialTheme.typography.bodyMedium)
                }
                s.lines.forEach { Text(highlightScamPhrases(it), style = MaterialTheme.typography.bodyLarge) }
                if (s.partial.isNotBlank()) {
                    Text(highlightScamPhrases(s.partial), style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic))
                }
            }
        }

        Text(stringResource(R.string.listen_log), style = MaterialTheme.typography.titleMedium)
        if (s.log.isEmpty()) Text(stringResource(R.string.listen_log_empty), style = MaterialTheme.typography.bodyMedium)
        s.log.asReversed().forEach { line ->
            Row {
                Text(formatClock(line.ts), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp, top = 2.dp))
                ChipGap()
                Text(
                    when (line.kind) {
                        ListenLogLine.Kind.HEARD -> highlightScamPhrases("“${line.text}”")
                        else -> AnnotatedString(line.text)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (line.kind) {
                        ListenLogLine.Kind.PROBLEM -> levelColor(RiskLevel.RED_FLAG)
                        ListenLogLine.Kind.TACTIC -> levelColor(RiskLevel.WARNING)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (line.kind == ListenLogLine.Kind.TACTIC) FontWeight.SemiBold else null,
                )
            }
        }
    }
}

/** The transcription language: English, Hindi, or both (Hindi with switching to English). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageRow(selected: String, active: Boolean, onSelect: (String) -> Unit) {
    Text(stringResource(R.string.listen_language_title), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            CallListenService.LANGUAGE to R.string.listen_language_english,
            CallListenService.LANGUAGE_HINDI to R.string.listen_language_hindi,
            CallListenService.LANGUAGE_AUTO to R.string.listen_language_auto,
        ).forEach { (tag, label) ->
            FilterChip(
                selected = selected == tag,
                onClick = { onSelect(tag) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
    Text(
        stringResource(if (active) R.string.listen_language_next_time else R.string.listen_language_desc),
        style = MaterialTheme.typography.bodySmall,
    )
}

/** Whether Shizuku can record the call itself, with the button to allow it. */
@Composable
private fun ShizukuRow() {
    var status by remember { mutableStateOf(ShizukuAudio.status()) }
    LaunchedEffect(Unit) {
        while (true) {
            status = ShizukuAudio.status()
            delay(1_500)
        }
    }
    Text(stringResource(R.string.shizuku_title), style = MaterialTheme.typography.labelLarge)
    Text(
        stringResource(
            when (status) {
                ShizukuAudio.Status.READY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) R.string.shizuku_ready else R.string.shizuku_old_android
                ShizukuAudio.Status.NO_PERMISSION -> R.string.shizuku_no_permission
                ShizukuAudio.Status.NOT_RUNNING -> R.string.shizuku_not_running
            },
        ),
        color = if (status == ShizukuAudio.Status.READY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MaterialTheme.colorScheme.onSurface else levelColor(RiskLevel.WARNING),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (status == ShizukuAudio.Status.NO_PERMISSION) {
        OutlinedButton(onClick = ShizukuAudio::requestPermission, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.shizuku_allow))
        }
    }
}
