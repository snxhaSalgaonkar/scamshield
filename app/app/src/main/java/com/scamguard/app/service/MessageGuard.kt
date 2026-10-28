package com.scamguard.app.service

import com.scamguard.app.alerts.AlertDispatcher
import com.scamguard.app.bus.EventBus
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.data.db.MessageCheckDao
import com.scamguard.app.data.db.MessageCheckEntity
import com.scamguard.app.data.settings.SettingsRepository
import com.scamguard.app.ml.MessageCategory
import com.scamguard.app.ml.MessagePrediction
import com.scamguard.app.ml.MessageVerdict
import com.scamguard.app.ml.ScamPredictionService

/**
 * Runs the message and link models on an incoming SMS, publishes the probabilities to
 * the call pipeline, stores suspicious verdicts (never the text) and raises a message
 * alert. The text is held only in memory for the duration of [onSms].
 */
class MessageGuard(
    private val predictions: ScamPredictionService,
    private val bus: EventBus,
    private val checks: MessageCheckDao,
    private val settings: SettingsRepository,
    private val dispatcher: AlertDispatcher,
    private val liveWindowId: () -> String?,
) {
    /** Returns the stored check id when the message was suspicious, else null. */
    suspend fun onSms(body: String, senderTrusted: Boolean, ts: Long, simulated: Boolean = false, quiet: Boolean = false): Long? {
        if (!simulated && !settings.current().protectionEnabled) return null
        val prediction = predictions.analyzeMessage(body)
        toEvent(prediction, ts)?.let { bus.emit(it, simulated = simulated, quiet = true) }
        if (prediction.verdict != MessageVerdict.LIKELY_SCAM && prediction.verdict != MessageVerdict.SUSPICIOUS) return null
        val top = prediction.topCategory
        val id = checks.insert(
            MessageCheckEntity(
                ts = ts,
                source = if (simulated) "SIMULATED" else "SMS",
                verdict = prediction.verdict.name,
                scamProbability = prediction.scamProbability,
                category = top?.name,
                categoryProbability = top?.let { prediction.categories[it] },
                indicators = prediction.indicators.joinToString(","),
                linkCount = prediction.urls.size,
                maxUrlProbability = prediction.urls.mapNotNull { it.probability }.maxOrNull(),
                senderTrusted = senderTrusted,
                windowId = if (simulated) null else liveWindowId(),
                modelVersion = prediction.modelVersion,
                simulated = simulated,
            )
        )
        // A trusted bank sender ID is still recorded but does not raise its own alert.
        if (!quiet && !senderTrusted) dispatcher.notifyMessage(id, prediction, simulated)
        return id
    }

    companion object {
        /** The probabilities the call model needs; null when the message model did not run. */
        fun toEvent(p: MessagePrediction, ts: Long): SignalEvent.SmsAnalyzed? {
            val prob = p.scamProbability ?: return null
            return SignalEvent.SmsAnalyzed(
                ts = ts,
                scamProbability = prob,
                impersonation = p.categoryScore(MessageCategory.IMPERSONATION_THREAT),
                credential = p.categoryScore(MessageCategory.CREDENTIAL_THEFT),
                payment = p.categoryScore(MessageCategory.PAYMENT_FRAUD),
                remoteAccess = p.categoryScore(MessageCategory.REMOTE_ACCESS),
                topCategory = p.topCategory?.name,
                linkCount = p.urls.size,
                maxUrlProbability = p.urls.mapNotNull { it.probability }.maxOrNull(),
                outOfDistribution = p.verdict == MessageVerdict.UNKNOWN,
                flagged = p.verdict == MessageVerdict.LIKELY_SCAM || p.verdict == MessageVerdict.SUSPICIOUS,
            )
        }
    }
}
