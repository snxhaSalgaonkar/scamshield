package com.scamguard.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamguard.app.AppContainer
import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.data.db.CallEventEntity
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.data.db.PredictionFeedbackEntity
import com.scamguard.app.data.db.SignalEventEntity
import com.scamguard.app.data.ModelFeedback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A decision plus the call and signal events of its window. */
data class AlertDetail(
    val decision: DecisionEntity,
    val callEvents: List<CallEventEntity>,
    val signalEvents: List<SignalEventEntity>,
)

/** Loads one window for the detail screen and records the user's verdict. */
class AlertDetailViewModel(private val container: AppContainer, private val windowId: String) : ViewModel() {
    private val db = container.database

    @OptIn(ExperimentalCoroutinesApi::class)
    val detail: StateFlow<AlertDetail?> = db.decisions().observe(windowId)
        .mapLatest { d -> d?.let { AlertDetail(it, db.callEvents().byWindow(windowId), db.signalEvents().byWindow(windowId)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val modelFeedback: StateFlow<PredictionFeedbackEntity?> = db.feedback().observeLatest(windowId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun feedback(kind: FeedbackKind) = viewModelScope.launch { container.sessionManager.submitFeedback(windowId, kind) }

    /** Feedback on the model's prediction; stored separately and never used to retrain automatically. */
    fun modelFeedback(f: ModelFeedback) = viewModelScope.launch {
        detail.value?.decision?.let { container.feedback.onCallWindow(it, f) }
    }
}
