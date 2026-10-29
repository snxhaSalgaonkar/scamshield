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
import com.scamguard.app.ml.UrlVerdict
import com.scamguard.app.collectors.AuthorityText

/**
 * Runs the message and link models (plus OTP and rule checks) on an incoming SMS, publishes
 * the probabilities to the call pipeline, stores suspicious messages with their text on this
 * phone for the message history, and raises a message alert. Unflagged text is never stored.
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
    suspend fun onSms(
        body: String,
        senderTrusted: Boolean,
        ts: Long,
        simulated: Boolean = false,
        quiet: Boolean = false,
        isOtp: Boolean = false,
        sender: String? = null,
        fromInbox: Boolean = false,
    ): Long? {
        if (!simulated && !fromInbox && !settings.current().protectionEnabled) return null
        val modelPrediction = predictions.analyzeMessage(body)
        // Old inbox messages must not feed the live call pipeline.
        if (!fromInbox) toEvent(modelPrediction, ts)?.let { bus.emit(it, simulated = simulated, quiet = true) }
        val duringCall = !simulated && !fromInbox && liveWindowId() != null
        val prediction = classify(modelPrediction, body, sender, senderTrusted, isOtp, duringCall)
        if (prediction.verdict != MessageVerdict.LIKELY_SCAM && prediction.verdict != MessageVerdict.SUSPICIOUS) return null
        val reasons = prediction.indicators.filter { it in RULE_REASONS }
        val top = prediction.topCategory
        val id = checks.insert(
            MessageCheckEntity(
                ts = ts,
                source = when {
                    simulated -> "SIMULATED"
                    fromInbox -> "INBOX"
                    else -> "SMS"
                },
                verdict = prediction.verdict.name,
                scamProbability = prediction.scamProbability,
                category = top?.name,
                categoryProbability = top?.let { prediction.categories[it] },
                indicators = (prediction.indicators - RULE_REASONS).joinToString(","),
                linkCount = prediction.urls.size,
                maxUrlProbability = prediction.urls.mapNotNull { it.probability }.maxOrNull(),
                senderTrusted = senderTrusted,
                windowId = if (simulated || fromInbox) null else liveWindowId(),
                modelVersion = prediction.modelVersion,
                simulated = simulated,
                body = body.take(MAX_BODY_CHARS),
                sender = sender,
                reasons = reasons.joinToString(","),
            )
        )
        // A trusted bank sender ID is still recorded but does not raise its own alert, except
        // when its OTP arrives during a call: that is the moment OTP fraud happens.
        if (!quiet && (!isKnownSender(sender, senderTrusted) || (isOtp && duringCall))) dispatcher.notifyMessage(id, prediction, simulated)
        return id
    }

    companion object {
        const val OTP_DURING_CALL = "OTP during a call"
        const val OTP_UNKNOWN_SENDER = "OTP from an unknown sender"
        const val SCAM_LINK = "Suspicious link"
        const val OFFICIAL_THREAT = "Fake police / court / arrest words"
        private val RULE_REASONS = setOf(OTP_DURING_CALL, OTP_UNKNOWN_SENDER, SCAM_LINK, OFFICIAL_THREAT)
        private const val MAX_BODY_CHARS = 2_000

        /**
         * Indian DLT-registered business headers ("AX-CANBNK-S", "JZ-JioPay-S", "VM-MMTRIP-P"):
         * only registered entities can send from them, while SMS fraud comes from ordinary
         * mobile numbers. The text model (trained partly on synthetic Indian scams) scores many
         * genuine bank and telecom alerts as scams, so registered headers are not flagged on the
         * model or text rules alone.
         */
        private val REGISTERED_HEADER = Regex("^[A-Z]{2}-[A-Z0-9]{3,9}(-[STGP])?$", RegexOption.IGNORE_CASE)

        fun isRegisteredSender(sender: String?): Boolean = sender != null && REGISTERED_HEADER.matches(sender.trim())

        fun isKnownSender(sender: String?, senderTrusted: Boolean) = senderTrusted || isRegisteredSender(sender)

        /** Model verdict, then the sender filter, then the OTP and text rules. */
        fun classify(
            model: MessagePrediction, body: String, sender: String?, senderTrusted: Boolean, isOtp: Boolean, duringCall: Boolean,
        ): MessagePrediction {
            val known = isKnownSender(sender, senderTrusted)
            val flagged = model.verdict == MessageVerdict.LIKELY_SCAM || model.verdict == MessageVerdict.SUSPICIOUS
            val base = if (known && flagged) model.copy(verdict = MessageVerdict.NO_STRONG_SIGNAL) else model
            return ruleRisk(otpRisk(base, isOtp, known, duringCall), body, known)
        }

        /**
         * Safety net for what the text model misses: it learned from English SMS, so Hindi or
         * Hinglish scams come back "cannot judge". A link the link model calls phishing, or
         * police/court/arrest wording (two or more terms), flags the message from a sender that
         * is not a known bank ID.
         */
        fun ruleRisk(p: MessagePrediction, body: String, senderTrusted: Boolean): MessagePrediction {
            if (senderTrusted || p.verdict == MessageVerdict.LIKELY_SCAM || p.verdict == MessageVerdict.SUSPICIOUS) return p
            val (reason, category) = when {
                p.urls.any { it.verdict == UrlVerdict.SUSPICIOUS } -> SCAM_LINK to MessageCategory.PHISHING_LINK
                AuthorityText.categories(body).isNotEmpty() -> OFFICIAL_THREAT to MessageCategory.IMPERSONATION_THREAT
                else -> return p
            }
            return p.copy(verdict = MessageVerdict.SUSPICIOUS, categories = mapOf(category to 1.0), indicators = listOf(reason) + p.indicators)
        }

        /**
         * An OTP SMS reads like a normal bank message, so the text model rarely flags it. It is
         * still OTP-fraud risk when it arrives during a call (the caller is waiting for it) or
         * from a sender that is not a known bank ID; such OTPs are raised to SUSPICIOUS so they
         * reach the message history.
         */
        fun otpRisk(p: MessagePrediction, isOtp: Boolean, senderTrusted: Boolean, duringCall: Boolean): MessagePrediction {
            if (!isOtp || p.verdict == MessageVerdict.LIKELY_SCAM) return p
            val reason = when {
                duringCall -> OTP_DURING_CALL
                !senderTrusted -> OTP_UNKNOWN_SENDER
                else -> return p
            }
            return p.copy(
                verdict = MessageVerdict.SUSPICIOUS,
                // The model's category only means something when it already found the text suspicious.
                categories = if (p.verdict == MessageVerdict.SUSPICIOUS && p.categories.isNotEmpty()) p.categories
                else mapOf(MessageCategory.CREDENTIAL_THEFT to 1.0),
                indicators = listOf(reason) + p.indicators,
            )
        }

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
