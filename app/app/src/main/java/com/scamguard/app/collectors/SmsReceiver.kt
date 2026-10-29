package com.scamguard.app.collectors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.scamguard.app.appContainer
import com.scamguard.app.bus.EventBus
import com.scamguard.app.bus.SignalEvent
import kotlinx.coroutines.launch

/**
 * Turns SMS_RECEIVED into privacy-safe events. Multipart messages are joined per sender,
 * parsed in memory by [SmsParser] (OTP, debit, link) and scored by the on-device message
 * and link models through [com.scamguard.app.service.MessageGuard], then dropped; only
 * event types, amounts, hashes and model probabilities reach the bus. The broadcast is protected by BROADCAST_SMS, so only the system sends it.
 * Whether protection is on is checked by the session manager, the single gate for all
 * collectors.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty().filterNotNull()
        } catch (_: RuntimeException) {
            return // Malformed PDUs: nothing safe to read.
        }
        val container = context.appContainer
        val now = System.currentTimeMillis()
        val bySender = messages.groupBy { it.originatingAddress }.map { (sender, parts) ->
            Triple(sender, container.trustedSenders.isTrusted(sender), parts.joinToString("") { it.messageBody.orEmpty() })
        }
        val classified = bySender.map { (_, trusted, body) -> SmsParser.classify(body).also { emit(container.eventBus, it, trusted, now) } }
        // Model inference runs off the main thread; the receiver stays alive until it is done.
        val pending = goAsync()
        container.appScope.launch {
            try {
                bySender.forEachIndexed { i, (sender, trusted, body) ->
                    container.messageGuard.onSms(body, trusted, now, isOtp = classified[i].isOtp, sender = sender)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Publishes the events an SMS stands for. Pure, so it is unit tested. */
        fun emit(bus: EventBus, c: SmsClassification, senderTrusted: Boolean, ts: Long) {
            if (c.isOtp) bus.emit(SignalEvent.OtpReceived(ts, senderTrusted))
            if (c.isDebit) bus.emit(SignalEvent.DebitReceived(ts, c.amountPaise ?: 0, c.payeeHash, c.balancePaise))
            if (c.hasLink) bus.emit(SignalEvent.LinkReceived(ts))
        }
    }
}
