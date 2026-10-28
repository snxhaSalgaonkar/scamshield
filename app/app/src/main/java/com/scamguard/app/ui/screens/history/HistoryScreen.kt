package com.scamguard.app.ui.screens.history

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
class HistoryViewModel(dao: DecisionDao, messages: MessageCheckDao) : ViewModel() {
    val items: StateFlow<List<DecisionEntity>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val messages: StateFlow<List<MessageCheckEntity>?> =
        messages.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** History: calls from unknown numbers, and messages the model flagged. */
@Composable
fun HistoryScreen(onOpen: (String) -> Unit, onOpenMessage: (Long) -> Unit) {
    val vm = containerViewModel { HistoryViewModel(it.database.decisions(), it.database.messageChecks()) }
    val items by vm.items.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenFrame(stringResource(R.string.history_title), scrollable = false) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.history_tab_calls)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_tab_messages)) })
        }
        if (tab == 1) {
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

@Composable
private fun MessageRow(m: MessageCheckEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Labels.messageVerdict(Labels.parseMessageVerdict(m.verdict))), style = MaterialTheme.typography.titleMedium)
                if (m.simulated) { ChipGap(); TagChip(stringResource(R.string.tag_simulated)) }
            }
            MessageCategory.parse(m.category)?.let { Text(stringResource(Labels.category(it)), style = MaterialTheme.typography.bodyMedium) }
            Text(formatTime(m.ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Label for a stored feedback value. */
fun feedbackLabel(feedback: String): Int =
    if (feedback == "THIS_WAS_ME") R.string.tag_this_was_me else R.string.tag_reported
