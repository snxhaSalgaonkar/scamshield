package com.scamguard.app.ui.screens.history

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.scamguard.app.service.InboxScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import androidx.compose.ui.text.style.TextOverflow
import com.scamguard.app.ui.components.highlightScamPhrases
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamguard.app.R
import com.scamguard.app.data.db.DecisionDao
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.data.db.MessageCheckDao
import com.scamguard.app.data.db.MessageCheckEntity
import com.scamguard.app.ml.MessageCategory
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.components.maskOrUnknown
import com.scamguard.app.ui.components.ChipGap
import com.scamguard.app.ui.components.EmptyState
import com.scamguard.app.ui.components.LevelChip
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.TagChip
import com.scamguard.app.ui.components.formatTime
import com.scamguard.app.ui.navigation.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Lists every scored call window and every flagged message, newest first. */
class HistoryViewModel(dao: DecisionDao, messages: MessageCheckDao, private val scanner: InboxScanner) : ViewModel() {
    val items: StateFlow<List<DecisionEntity>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val messages: StateFlow<List<MessageCheckEntity>?> =
        messages.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed interface ScanState {
        data object Idle : ScanState
        data class Running(val done: Int, val total: Int) : ScanState
        data class Done(val result: InboxScanner.Result) : ScanState
        data object Denied : ScanState
    }

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    fun scanInbox() {
        if (_scan.value is ScanState.Running) return
        _scan.value = ScanState.Running(0, 0)
        viewModelScope.launch {
            _scan.value = try {
                ScanState.Done(scanner.scan { done, total -> _scan.value = ScanState.Running(done, total) })
            } catch (_: SecurityException) {
                ScanState.Denied
            }
        }
    }

    fun denied() { _scan.value = ScanState.Denied }
}

/** History: calls from unknown numbers, and messages the model flagged. */
@Composable
fun HistoryScreen(onOpen: (String) -> Unit, onOpenMessage: (Long) -> Unit) {
    val vm = containerViewModel { HistoryViewModel(it.database.decisions(), it.database.messageChecks(), it.inboxScanner) }
    val items by vm.items.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenFrame(stringResource(R.string.history_title), scrollable = false) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.history_tab_calls)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_tab_messages)) })
        }
        if (tab == 1) {
            InboxScanBar(vm)
            val list = messages ?: return@ScreenFrame
            if (list.isEmpty()) {
                EmptyState(stringResource(R.string.history_messages_empty_title), stringResource(R.string.history_messages_empty_body))
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(list, key = { it.id }) { MessageRow(it) { onOpenMessage(it.id) } }
                }
            }
            return@ScreenFrame
        }
        val list = items ?: return@ScreenFrame
        if (list.isEmpty()) {
            EmptyState(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body))
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(list, key = { it.windowId }) { HistoryRow(it) { onOpen(it.windowId) } }
            }
        }
    }
}

@Composable
private fun HistoryRow(d: DecisionEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelChip(Labels.parseLevel(d.peakLevel))
                if (d.simulated) { ChipGap(); TagChip(stringResource(R.string.tag_simulated)) }
                if (d.feedback != null) { ChipGap(); TagChip(stringResource(feedbackLabel(d.feedback))) }
            }
            Text(stringResource(Labels.scamType(Labels.parseScamType(d.scamType))), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.history_row_detail, maskOrUnknown(d.mask), d.peakScore), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Labels.scoreSource(d.scoreSource)), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(d.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Scan my SMS inbox": asks for READ_SMS once, then classifies past messages into this list. */
@Composable
private fun InboxScanBar(vm: HistoryViewModel) {
    val context = LocalContext.current
    val scan by vm.scan.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.scanInbox() else vm.denied()
    }
    val running = scan is HistoryViewModel.ScanState.Running
    Button(
        onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) vm.scanInbox()
            else ask.launch(Manifest.permission.READ_SMS)
        },
        enabled = !running,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(stringResource(R.string.history_scan_inbox)) }
    when (val s = scan) {
        is HistoryViewModel.ScanState.Running ->
            Text(stringResource(R.string.history_scan_running, s.done, s.total), style = MaterialTheme.typography.bodyMedium)
        is HistoryViewModel.ScanState.Done ->
            Text(stringResource(R.string.history_scan_done, s.result.scanned, s.result.flagged), style = MaterialTheme.typography.bodyMedium)
        HistoryViewModel.ScanState.Denied ->
            Text(stringResource(R.string.history_scan_denied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        HistoryViewModel.ScanState.Idle -> Unit
    }
}

@Composable
private fun MessageRow(m: MessageCheckEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Labels.messageVerdict(Labels.parseMessageVerdict(m.verdict))), style = MaterialTheme.typography.titleMedium)
                if (m.simulated) { ChipGap(); TagChip(stringResource(R.string.tag_simulated)) }
            }
            MessageCategory.parse(m.category)?.let { Text(stringResource(Labels.category(it)), style = MaterialTheme.typography.bodyMedium) }
            m.reasons.split(",").firstOrNull { it.isNotBlank() }?.let { TagChip(it) }
            m.body?.let { Text(highlightScamPhrases(it), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Text(listOfNotNull(m.sender, formatTime(m.ts)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Label for a stored feedback value. */
fun feedbackLabel(feedback: String): Int =
    if (feedback == "THIS_WAS_ME") R.string.tag_this_was_me else R.string.tag_reported
