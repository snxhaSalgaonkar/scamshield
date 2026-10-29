package com.scamguard.app.ui.screens.check

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamguard.app.AppContainer
import com.scamguard.app.R
import com.scamguard.app.data.ModelFeedback
import com.scamguard.app.data.db.MessageCheckEntity
import com.scamguard.app.data.db.PredictionFeedbackEntity
import com.scamguard.app.ml.MessageCategory
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.components.FeedbackButtons
import com.scamguard.app.ui.components.MessageVerdictCard
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.formatTime
import com.scamguard.app.ui.components.highlightScamPhrases
import com.scamguard.app.ui.components.TagChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scamguard.app.ui.navigation.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One stored message verdict and the user's latest feedback on it. */
class MessageDetailViewModel(private val container: AppContainer, id: Long) : ViewModel() {
    val check: StateFlow<MessageCheckEntity?> = container.database.messageChecks().observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val feedback: StateFlow<PredictionFeedbackEntity?> = container.database.feedback().observeLatest("msg-$id")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun feedback(f: ModelFeedback) {
        val c = check.value ?: return
        viewModelScope.launch { container.feedback.onMessage(c, f) }
    }
}

/** A flagged message: its text with scam words marked, why it was flagged and the model's verdict. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageDetailScreen(id: Long, onBack: () -> Unit) {
    val vm = containerViewModel(key = "msg-$id") { MessageDetailViewModel(it, id) }
    val check by vm.check.collectAsStateWithLifecycle()
    val fb by vm.feedback.collectAsStateWithLifecycle()

    ScreenFrame(stringResource(R.string.message_detail_title), onBack = onBack) {
        val c = check ?: return@ScreenFrame
        Text(
            c.sender?.let { stringResource(R.string.message_detail_from, it, formatTime(c.ts)) } ?: formatTime(c.ts),
            style = MaterialTheme.typography.bodyMedium,
        )
        c.body?.let { body ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.check_highlighted), style = MaterialTheme.typography.titleMedium)
                    Text(highlightScamPhrases(body), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        val reasons = c.reasons.split(",").filter { it.isNotBlank() }
        if (reasons.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { reasons.forEach { TagChip(it) } }
        }
        MessageVerdictCard(
            Labels.parseMessageVerdict(c.verdict), c.scamProbability, MessageCategory.parse(c.category),
            c.indicators.split(",").filter { it.isNotBlank() }, simulated = c.simulated,
        )
        if (c.linkCount > 0) {
            Text(
                stringResource(R.string.message_detail_links, c.linkCount, ((c.maxUrlProbability ?: 0.0) * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (c.senderTrusted) Text(stringResource(R.string.message_detail_trusted), style = MaterialTheme.typography.bodyMedium)
        if (c.body == null) Text(stringResource(R.string.message_detail_no_text), style = MaterialTheme.typography.bodySmall)
        c.modelVersion?.let { Text(stringResource(R.string.detail_model_version, it), style = MaterialTheme.typography.bodySmall) }
        FeedbackButtons(fb?.verdict?.let { v -> ModelFeedback.entries.firstOrNull { it.name == v } }, vm::feedback)
        Text(stringResource(R.string.check_advice), style = MaterialTheme.typography.bodyMedium)
    }
}
