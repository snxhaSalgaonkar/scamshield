package com.scamguard.app.engine

import com.scamguard.app.bus.CallChannel
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory
import com.scamguard.app.collectors.SpeechText
import com.scamguard.app.ml.shippedModels
import com.scamguard.app.ui.screens.simulator.Scenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class VoiceRulesTest {
    private val scorer = CallRiskScorer(shippedModels())
    private val active = SignalEvent.CallActive(T0 + 5_000)

    private fun said(vararg lines: String, ring: SignalEvent.CallRinging = ringing()): CallWindow {
        val speech = lines.mapIndexed { i, l -> SpeechText.events(T0 + (i + 1) * MIN, l) }.flatten()
        return window(ring, active, *speech.toTypedArray())
    }

    private fun score(w: CallWindow, voice: Boolean = true) =
        scorer.evaluate(RiskInput(w, CallerContext(), TransactionHistory(), voice, w.events.maxOf { it.ts }, ZoneOffset.UTC))

    private fun kw(vararg c: VoiceCategory) = c.map { SignalEvent.VoiceKeyword(T0 + MIN, it) }.toTypedArray()

    @Test fun `the demo script climbs warning then red flag then scam line by line`() {
        val lines = Scenarios.DIGITAL_ARREST_SPEECH_EN
        val first = score(said(lines[0]))
        assertEquals(RiskLevel.WARNING, first.level)
        assertEquals(ReasonCode.COMBO_VOICE_ACCUSED, first.reasonCodes.first())

        val second = score(said(lines[0], lines[1]))
        assertEquals(RiskLevel.RED_FLAG, second.level)
        assertTrue(second.score in 75..89)

        val third = score(said(*lines.toTypedArray()))
        assertEquals(RiskLevel.RED_FLAG, third.level)
        assertTrue(third.score >= 90)
        assertEquals(ReasonCode.COMBO_VOICE_DIGITAL_ARREST, third.reasonCodes.first())
        assertEquals(ScamType.DIGITAL_ARREST, third.scamType)
        assertTrue(third.l1 > 0)
    }

    @Test fun `the Hinglish script reaches the same bands`() {
        val lines = Scenarios.DIGITAL_ARREST_SPEECH_HINGLISH
        assertEquals(RiskLevel.WARNING, score(said(lines[0])).level)
        assertEquals(RiskLevel.RED_FLAG, score(said(lines[0], lines[1])).level)
        assertTrue(score(said(*lines.toTypedArray())).score >= 90)
    }

    @Test fun `the audio of a video call is scored the same way`() {
        val ring = ringing().copy(channel = CallChannel.VIDEO_APP)
        val d = score(said(*Scenarios.DIGITAL_ARREST_SPEECH_EN.toTypedArray(), ring = ring))
        assertTrue(d.score >= 90)
        assertEquals(ScamType.DIGITAL_ARREST, d.scamType)
    }

    @Test fun `nothing changes without the voice layer`() {
        val w = said(*Scenarios.DIGITAL_ARREST_SPEECH_EN.toTypedArray())
        val d = score(w, voice = false)
        assertTrue(d.reasonCodes.none { it.layer == Layer.L1_VOICE })
        assertFalse(d.level == RiskLevel.RED_FLAG)
    }

    @Test fun `known callers stay silent whatever is said`() {
        val d = score(said(*Scenarios.DIGITAL_ARREST_SPEECH_EN.toTypedArray(), ring = ringing(unknown = false)))
        assertTrue(d.gated)
        assertEquals(RiskLevel.SAFE, d.level)
    }

    @Test fun `this was me silences spoken evidence`() {
        val w = said(*Scenarios.DIGITAL_ARREST_SPEECH_EN.toTypedArray())
            .accept(SignalEvent.UserFeedback(T0 + 5 * MIN, com.scamguard.app.bus.FeedbackKind.THIS_WAS_ME), RiskConfig())
        assertEquals(RiskLevel.SAFE, score(w).level)
    }

    @Test fun `genuine calls stay below warning`() {
        listOf(Scenarios.BANK_REMINDER_SPEECH, Scenarios.DELIVERY_SPEECH, Scenarios.TRAFFIC_POLICE_SPEECH).forEach {
            val d = score(said(it))
            assertEquals(it, RiskLevel.SAFE, d.level)
        }
    }

    @Test fun `a red flag never rests on one spoken category`() {
        VoiceCategory.entries.forEach { c ->
            assertTrue(c.name, VoiceRules.combos(setOf(c), RiskConfig()).none { it.second >= 75 })
        }
    }

    @Test fun `hearing digital arrest alone goes straight to a red flag`() {
        listOf(
            "you are under digital arrest",
            "Aap digital arrest mein ho",
            "aapko digitally arrested kiya gaya hai",
            "आप डिजिटल अरेस्ट में हैं",
            "you will be arrested. This is a digital arrest",
        ).forEach { line ->
            val d = score(said(line))
            assertEquals(line, RiskLevel.RED_FLAG, d.level)
            assertTrue(line, d.score >= 75)
            assertEquals(line, ScamType.DIGITAL_ARREST, d.scamType)
        }
    }

    @Test fun `a plain arrest word alone is not a red flag`() {
        assertTrue(score(said("the thief was arrested yesterday")).level != RiskLevel.RED_FLAG)
    }

    @Test fun `officer asking for an OTP is impersonation`() {
        val d = score(window(ringing(), active, *kw(VoiceCategory.AUTHORITY_CLAIM, VoiceCategory.OTP_REQUEST)))
        assertEquals(RiskLevel.RED_FLAG, d.level)
        assertEquals(ScamType.OTP_THEFT, d.scamType)
        assertTrue(ReasonCode.COMBO_VOICE_IMPERSONATION in d.reasonCodes)
    }

    @Test fun `arrest threat plus a payment demand is a red flag without naming an agency`() {
        val d = score(window(ringing(), active, *kw(VoiceCategory.ARREST_THREAT, VoiceCategory.PAYMENT_PRESSURE)))
        assertEquals(RiskLevel.RED_FLAG, d.level)
    }

    @Test fun `spoken points are capped`() {
        val d = score(window(ringing(), active, *kw(*VoiceCategory.entries.toTypedArray())))
        assertTrue(d.l1 <= RiskConfig().voiceTotalCap)
    }

    @Test fun `rule fallback does not count the original categories twice`() {
        val w = window(ringing(), active, *kw(VoiceCategory.ARREST_THREAT))
        val input = RiskInput(w, voiceLayerEnabled = true, now = T0 + MIN, zone = ZoneOffset.UTC)
        val base = RiskEngine.evaluate(input)
        val d = VoiceRules.apply(input, base)
        assertEquals(base.l1, d.l1)
        assertEquals(base.score, d.score)
    }

    @Test fun `stages follow the script`() {
        fun stage(vararg lines: String) = VoiceRules.stage(lines.flatMap { SpeechText.categories(it) }.toSet())
        val s = Scenarios.DIGITAL_ARREST_SPEECH_EN
        assertEquals(VoiceRules.Stage.NONE, stage("Hello, how are you?"))
        assertEquals(VoiceRules.Stage.ACCUSATION, stage(s[0]))
        assertEquals(VoiceRules.Stage.THREAT, stage(s[0], s[1]))
        assertEquals(VoiceRules.Stage.DEMAND, stage(*s.toTypedArray()))
    }
}
