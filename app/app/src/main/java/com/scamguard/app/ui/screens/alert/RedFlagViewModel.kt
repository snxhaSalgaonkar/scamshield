package com.scamguard.app.ui.screens.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamguard.app.AppContainer
import com.scamguard.app.alerts.FullScreenAlertActivity
import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.engine.ReasonCode
import com.scamguard.app.engine.ScamType
import com.scamguard.app.ui.Labels
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the red-flag screen shows. */
data class RedFlagState(
    val score: Int,
    val scamType: ScamType,
    val reasons: List<ReasonCode>,
    val callEnded: Boolean,
    val mask: String,
    val isTest: Boolean,
)

/** Loads a window's decision for the red-flag screen and records the user's verdict. */
class RedFlagViewModel(private val container: AppContainer, private val windowId: String) : ViewModel() {
    val isTest = windowId == FullScreenAlertActivity.TEST_WINDOW

    val state: StateFlow<RedFlagState?> = (
        if (isTest) {
            flowOf(
                RedFlagState(
                    82, ScamType.OTP_THEFT,
                    listOf(ReasonCode.COMBO_OTP_DEBIT, ReasonCode.DEBIT_DURING_CALL, ReasonCode.OTP_DURING_CALL),
                    callEnded = false, mask = "+91 98•••••00", isTest = true,
                )
            )
        } else {
            container.database.decisions().observe(windowId).map { e ->
                e?.let {
                    RedFlagState(
                        it.score, Labels.parseScamType(it.scamType), Labels.parseReasons(it.reasonCodes),
                        it.callEnded, it.mask, isTest = false,
                    )
                }
            }
        }
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun thisWasMe(done: () -> Unit) = feedback(FeedbackKind.THIS_WAS_ME, done)
    fun reportScam(done: () -> Unit) = feedback(FeedbackKind.CONFIRMED_SCAM, done)

    private fun feedback(kind: FeedbackKind, done: () -> Unit) = viewModelScope.launch {
        if (!isTest) container.sessionManager.submitFeedback(windowId, kind)
        done()
    }
}
