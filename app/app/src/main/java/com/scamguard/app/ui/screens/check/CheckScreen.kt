package com.scamguard.app.ui.screens.check

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamguard.app.AppContainer
import com.scamguard.app.R
import com.scamguard.app.bus.ScreenCategory
import com.scamguard.app.collectors.AuthorityText
import com.scamguard.app.data.ModelFeedback
import com.scamguard.app.ml.MessagePrediction
import com.scamguard.app.ml.MessageVerdict
import com.scamguard.app.ml.UrlPrediction
import com.scamguard.app.ml.UrlVerdict
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.components.FeedbackButtons
import com.scamguard.app.ui.components.MessageVerdictCard
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.highlightScamPhrases
import com.scamguard.app.screencheck.ImageText
import com.scamguard.app.ui.navigation.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CheckState(
    val input: String = "",
    val checking: Boolean = false,
    val result: MessagePrediction? = null,
    val feedback: ModelFeedback? = null,
    /** Set when the result came from an image: the text read from it. */
    val imageText: String? = null,
    val imageUnreadable: Boolean = false,
    val official: Set<ScreenCategory> = emptySet(),
)

/** Runs the message and link models on text the user pastes. Nothing is stored except feedback. */
class CheckViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CheckState())
    val state: StateFlow<CheckState> = _state.asStateFlow()

    fun onInput(text: String) = _state.update { it.copy(input = text.take(MAX_CHARS)) }
    fun clear() = _state.update { CheckState() }

    fun check() {
        val text = _state.value.input
        if (text.isBlank() || _state.value.checking) return
        _state.update { it.copy(checking = true, result = null, feedback = null) }
        viewModelScope.launch {
            val result = container.predictions.analyzeMessage(text)
            _state.update { it.copy(checking = false, result = result) }
        }
    }

    /** Reads the text in an image on the phone and runs the same checks as pasted text. */
    fun checkImage(context: Context, uri: Uri) {
        if (_state.value.checking) return
        _state.value = CheckState(checking = true)
        viewModelScope.launch {
            val text = ImageText.read(context.applicationContext, uri)?.take(MAX_CHARS)
            if (text.isNullOrBlank()) {
                _state.value = CheckState(imageText = "", imageUnreadable = text == null)
                return@launch
            }
            val result = container.predictions.analyzeMessage(text)
            _state.value = CheckState(result = result, imageText = text, official = AuthorityText.categories(text))
        }
    }

    fun feedback(f: ModelFeedback) {
        val r = _state.value.result ?: return
        _state.update { it.copy(feedback = f) }
        viewModelScope.launch {
            container.feedback.onManualCheck(f, r.verdict.name, r.scamProbability, r.topCategory?.name, r.modelVersion)
        }
    }

    private companion object {
        const val MAX_CHARS = 2_000
    }
}

/** Check a message or link: on-device verdict, evidence, links (never opened) and feedback. */
@Composable
fun CheckScreen(sharedImage: Uri? = null, onSharedImageChecked: () -> Unit = {}) {
    val vm = containerViewModel { CheckViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.checkImage(context, uri)
    }
    LaunchedEffect(sharedImage) {
        if (sharedImage != null) {
            vm.checkImage(context, sharedImage)
            onSharedImageChecked()
        }
    }

    ScreenFrame(stringResource(R.string.check_title)) {
        Text(stringResource(R.string.check_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = s.input,
            onValueChange = vm::onInput,
            label = { Text(stringResource(R.string.check_input)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            minLines = 4,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { focus.clearFocus(); vm.check() }, enabled = s.input.isNotBlank() && !s.checking, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(if (s.checking) R.string.check_checking else R.string.check_button))
            }
            OutlinedButton(onClick = vm::clear, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.check_clear)) }
        }
        OutlinedButton(
            onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !s.checking,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.check_image_button)) }

        s.imageText?.let { ImageResult(it, s.imageUnreadable, s.official, s.result) }

        s.result?.let { r ->
            MessageVerdictCard(r.verdict, r.scamProbability, r.topCategory, r.indicators)
            if (s.imageText == null && s.input.isNotBlank()) HighlightedText(s.input)
            if (r.urls.isNotEmpty()) LinkList(r.urls)
            FeedbackButtons(s.feedback, vm::feedback)
            Text(stringResource(R.string.check_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.check_advice), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImageResult(text: String, unreadable: Boolean, official: Set<ScreenCategory>, result: MessagePrediction?) {
    if (text.isBlank()) {
        Text(
            stringResource(if (unreadable) R.string.check_image_unreadable else R.string.check_image_no_text),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val flagged = official.isNotEmpty() ||
        result?.verdict == MessageVerdict.LIKELY_SCAM || result?.verdict == MessageVerdict.SUSPICIOUS
    if (official.isNotEmpty()) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.check_image_official_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.check_image_official_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else if (!flagged) {
        Text(stringResource(R.string.check_image_not_proof), style = MaterialTheme.typography.bodySmall)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.check_image_text), style = MaterialTheme.typography.titleMedium)
            Text(highlightScamPhrases(text.take(PREVIEW_CHARS)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val PREVIEW_CHARS = 600

/** The checked message with scam words and sensitive numbers marked. */
@Composable
private fun HighlightedText(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.check_highlighted), style = MaterialTheme.typography.titleMedium)
            Text(highlightScamPhrases(text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LinkList(urls: List<UrlPrediction>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.check_links), style = MaterialTheme.typography.titleMedium)
            urls.forEach { u ->
                val verdict = stringResource(Labels.urlVerdict(u.verdict))
                val name = u.domain ?: "—"
                val p = u.probability
                Text(
                    if (p != null && u.verdict != UrlVerdict.INVALID) stringResource(R.string.check_link_row, name, verdict, (p * 100).roundToInt())
                    else stringResource(R.string.check_link_row_plain, name, verdict),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
