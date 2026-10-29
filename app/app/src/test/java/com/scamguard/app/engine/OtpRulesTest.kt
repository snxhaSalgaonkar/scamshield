package com.scamguard.app.engine

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.collectors.SpeechText
import com.scamguard.app.ml.shippedModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** OTP theft on an unknown call, played through the real scorer and shipped models. */
class OtpRulesTest {
    private val scorer = CallRiskScorer(shippedModels())
    private val config = RiskConfig()
    private val ring = SignalEvent.CallRinging(T0, "h", "+91 90", isUnknown = true, isInternational = false, isHidden = false)
    private val active = SignalEvent.CallActive(T0 + 5_000)
    private fun say(sec: Long, text: String) = SpeechText.events(T0 + sec * 1000, text)
    private fun otp(sec: Long) = SignalEvent.OtpReceived(T0 + sec * 1000, senderTrusted = true)

    private fun peak(steps: List<SignalEvent>, voice: Boolean = true): Decision {
        val t = WindowTracker { "w" }
        var best: Decision? = null
        for (e in steps) {
            val w = t.onEvent(e, config) ?: continue
            val d = scorer.evaluate(RiskInput(w, voiceLayerEnabled = voice, now = e.ts, zone = ZoneOffset.UTC), config)
            if (best == null || d.level > best.level || (d.level == best.level && d.score > best.score)) best = d
        }
        return best!!
    }

    @Test fun `caller asks for the OTP that just arrived - red flag`() {
        val d = peak(listOf(ring, active) + say(20, "please tell me the OTP you just received") + otp(60))
        assertEquals(RiskLevel.RED_FLAG, d.level)
        assertTrue(ReasonCode.COMBO_OTP_ASKED in d.reasonCodes)
        assertEquals(ScamType.OTP_THEFT, d.scamType)
    }

    @Test fun `bank impersonation plus OTP request plus OTP - scam band, English and Hinglish`() {
        listOf(
            "I am calling from SBI bank head office, your account will be blocked" to "please tell me the OTP you just received",
            "Main SBI bank se bol raha hoon, aapka account block ho jayega" to "jo OTP aaya hai woh bata dijiye",
        ).forEach { (claim, ask) ->
            val d = peak(listOf(ring, active) + say(20, claim) + otp(60) + say(70, ask))
            assertEquals(claim, RiskLevel.RED_FLAG, d.level)
            assertTrue(claim, d.score >= config.scamFloor)
        }
    }

    @Test fun `OTP then debit within 5 minutes - red flag`() {
        val d = peak(listOf(ring, active, otp(60), SignalEvent.DebitReceived(T0 + 120_000, 2_500_000)))
        assertEquals(RiskLevel.RED_FLAG, d.level)
        assertTrue(ReasonCode.COMBO_OTP_DEBIT in d.reasonCodes)
    }

    @Test fun `no false alarm - OTP alone, genuine bank call, delivery OTP, voice layer off`() {
        assertEquals(RiskLevel.SAFE, peak(listOf(ring, active, otp(60))).level)
        assertEquals(RiskLevel.SAFE, peak(listOf(ring, active) + say(20, "Hello, this is HDFC bank calling about your credit card bill due on Friday") + otp(60)).level)
        assertEquals(RiskLevel.SAFE, peak(listOf(ring, active) + say(20, "I am the delivery boy, please tell me the delivery OTP for your order") + otp(60)).level)
        assertEquals(RiskLevel.SAFE, peak(listOf(ring, active) + say(20, "please tell me the OTP") + otp(60), voice = false).level)
    }

    @Test fun `known caller stays silent`() {
        val known = ring.copy(isUnknown = false)
        val d = peak(listOf(known, active) + say(20, "please tell me the OTP you just received") + otp(60))
        assertEquals(RiskLevel.SAFE, d.level)
    }
}
