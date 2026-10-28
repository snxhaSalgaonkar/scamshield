package com.scamguard.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scamguard.app.R
import com.scamguard.app.data.ModelFeedback
import com.scamguard.app.ml.MessageCategory
import com.scamguard.app.ml.MessageVerdict
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.theme.AlertRedContainer
import com.scamguard.app.ui.theme.AppSurfaceRaised
import com.scamguard.app.ui.theme.AppText
import com.scamguard.app.ui.theme.AlertRed
import com.scamguard.app.ui.theme.WarningAmber
import com.scamguard.app.ui.theme.WarningAmberLight
import kotlin.math.roundToInt

/** Colour for a message verdict; neutral when the model makes no claim. */
@Composable
fun verdictColor(v: MessageVerdict): Color = when (v) {
    MessageVerdict.LIKELY_SCAM -> AlertRedContainer
    MessageVerdict.SUSPICIOUS -> WarningAmberLight
    else -> AppSurfaceRaised
}

/** Verdict, model estimate, experimental category and indicator words for one message. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageVerdictCard(
    verdict: MessageVerdict,
    probability: Double?,
    category: MessageCategory?,
    indicators: List<String>,
    simulated: Boolean = false,
) {
    val strong = verdict == MessageVerdict.LIKELY_SCAM || verdict == MessageVerdict.SUSPICIOUS
    Card(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = verdictColor(verdict),
            contentColor = if (strong) AppText else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Labels.messageVerdict(verdict)),
                    style = MaterialTheme.typography.titleLarge,
                    color = when (verdict) {
                        MessageVerdict.LIKELY_SCAM -> AlertRed
                        MessageVerdict.SUSPICIOUS -> WarningAmber
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                if (simulated) TagChip(stringResource(R.string.tag_simulated))
            }
            if (probability != null && verdict != MessageVerdict.UNKNOWN) {
                Text(stringResource(R.string.model_estimate, (probability * 100).roundToInt()), style = MaterialTheme.typography.bodyLarge)
            }
            when (verdict) {
                MessageVerdict.UNKNOWN -> Text(stringResource(R.string.mv_unknown_body), style = MaterialTheme.typography.bodyMedium)
                MessageVerdict.NO_STRONG_SIGNAL -> Text(stringResource(R.string.mv_no_signal_body), style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }
            if (strong && category != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.check_category, stringResource(Labels.category(category))),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TagChip(stringResource(R.string.tag_experimental))
                }
            }
            if (strong && indicators.isNotEmpty()) {
                Text(stringResource(R.string.check_indicators), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    indicators.forEach { TagChip(it) }
                }
            }
        }
    }
}

/** "Was this prediction right?" with the four feedback options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackButtons(selected: ModelFeedback?, onSelect: (ModelFeedback) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.fb_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModelFeedback.entries.forEach { f ->
                OutlinedButton(onClick = { onSelect(f) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text((if (f == selected) "✓ " else "") + stringResource(Labels.feedback(f)))
                }
            }
        }
        if (selected != null) {
            Text(
                stringResource(R.string.fb_saved, stringResource(Labels.feedback(selected))),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
