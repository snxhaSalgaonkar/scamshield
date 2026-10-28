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
import com.scamguard.app.ui.Labels
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

/** Lists every scored call window, newest first. */
class HistoryViewModel(dao: DecisionDao) : ViewModel() {
    val items: StateFlow<List<DecisionEntity>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Alert history: one card per call from an unknown number. */
@Composable
fun HistoryScreen(onOpen: (String) -> Unit) {
    val vm = containerViewModel { HistoryViewModel(it.database.decisions()) }
    val items by vm.items.collectAsStateWithLifecycle()
    ScreenFrame(stringResource(R.string.history_title), scrollable = false) {
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
            Text(stringResource(R.string.history_row_detail, d.mask, d.peakScore), style = MaterialTheme.typography.bodyMedium)
            Text(formatTime(d.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Label for a stored feedback value. */
fun feedbackLabel(feedback: String): Int =
    if (feedback == "THIS_WAS_ME") R.string.tag_this_was_me else R.string.tag_reported
