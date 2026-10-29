package com.scamguard.app.data

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.data.db.MessageCheckEntity
import com.scamguard.app.data.db.PredictionFeedbackEntity
import com.scamguard.app.ml.MessageCategory
import com.scamguard.app.ml.MessagePrediction
import com.scamguard.app.ml.MessageVerdict
import com.scamguard.app.ml.UrlPrediction
import com.scamguard.app.ml.UrlVerdict
import com.scamguard.app.service.MessageGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyAndExportTest {

    @Test fun `model events carry no message text`() {
        val smsFields = SignalEvent.SmsAnalyzed::class.java.declaredFields.filter { it.type == String::class.java }.map { it.name }
        assertEquals(listOf("topCategory"), smsFields)
    }

    @Test fun `flagged message text and sender stay on the phone and never reach the export`() {
        val msg = MessageCheckEntity(
            ts = 1L, source = "SMS", verdict = "SUSPICIOUS", scamProbability = 0.9, category = null,
            categoryProbability = null, indicators = "", linkCount = 0, maxUrlProbability = null,
            senderTrusted = false, windowId = null, modelVersion = "t1", simulated = false,
            body = "SECRET-BODY your OTP 482913", sender = "SECRET-SENDER", reasons = "OTP during a call",
        )
        val out = FeedbackRepository.buildExport(emptyList(), emptyList(), listOf(msg)).toString()
        listOf("SECRET-BODY", "482913", "SECRET-SENDER").forEach { assertFalse(it, out.contains(it)) }
    }

    @Test fun `message prediction becomes probabilities only`() {
        val p = MessagePrediction(
            MessageVerdict.LIKELY_SCAM, 0.9,
            mapOf(MessageCategory.IMPERSONATION_THREAT to 0.5, MessageCategory.CREDENTIAL_THEFT to 0.5),
            urls = listOf(UrlPrediction("a.top", 0.7, UrlVerdict.UNCERTAIN, "u1")),
        )
        val e = MessageGuard.toEvent(p, 5L)!!
        assertEquals(0.45, e.impersonation, 1e-12)
        assertEquals(0.45, e.credential, 1e-12)
        assertEquals(0.0, e.payment, 0.0)
        assertEquals(0.7, e.maxUrlProbability!!, 0.0)
        assertTrue(e.flagged)
        assertNull(MessageGuard.toEvent(MessagePrediction(MessageVerdict.MODEL_UNAVAILABLE, null), 5L))
        val ood = MessageGuard.toEvent(MessagePrediction(MessageVerdict.UNKNOWN, 0.3), 5L)!!
        assertTrue(ood.outOfDistribution)
        assertFalse(ood.flagged)
    }

    @Test fun `sms model detail round-trips through storage`() {
        val e = SignalEvent.SmsAnalyzed(1, 0.912, topCategory = "PAYMENT_FRAUD", maxUrlProbability = 0.5, flagged = true)
        val stored = EventMapping.signalEvent(e, "w", simulated = false)!!
        assertEquals("SMS_ML", stored.type)
        val back = EventMapping.parseSmsDetail(stored.detail)
        assertEquals("0.912", back["p"]); assertEquals("PAYMENT_FRAUD", back["cat"]); assertEquals("1", back["f"])
    }

    @Test fun `feedback export leaves out numbers, hashes and masks and is marked unreviewed`() {
        val decision = DecisionEntity(
            windowId = "w1", ts = 2, startedAt = 1, score = 80, level = "RED_FLAG", peakScore = 80, peakLevel = "RED_FLAG",
            l1 = 0, l2 = 10, l3 = 60, scamType = "OTP_THEFT", reasonCodes = "OTP_DURING_CALL", comboFired = false,
            numberHash = "SECRET_HASH", prefix = "+91 98", mask = "+91 98•••••21", callEnded = true, simulated = false,
            feedback = null, scoreSource = "MODEL", probability = 0.8, modelVersion = "call-x", features = "0,1,2",
        )
        val fb = PredictionFeedbackEntity(
            ts = 3, targetType = "CALL_WINDOW", targetId = "w1", verdict = "FALSE_POSITIVE", predictedLevel = "RED_FLAG",
            predictedType = "OTP_THEFT", probability = 0.8, modelVersion = "call-x", features = "0,1,2", simulated = false,
        )
        val msg = MessageCheckEntity(
            ts = 4, source = "SMS", verdict = "LIKELY_SCAM", scamProbability = 0.9, category = "PAYMENT_FRAUD",
            categoryProbability = 0.6, indicators = "upi", linkCount = 1, maxUrlProbability = 0.3, senderTrusted = false,
            windowId = "w1", modelVersion = "text-x", simulated = false,
        )
        val out = FeedbackRepository.buildExport(listOf(fb), listOf(decision), listOf(msg)).toString()
        listOf("SECRET_HASH", "+91 98", "•••", "numberHash", "mask", "prefix", "w1").forEach { assertFalse(it, it in out) }
        assertTrue("\"reviewed\":false" in out)
        assertTrue("FALSE_POSITIVE" in out)
    }
}
