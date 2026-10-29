package com.scamguard.app.engine

import com.scamguard.app.bus.CallChannel
import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.bus.ScreenCategory
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.ml.shippedModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DigitalArrestRulesTest {
    private val scorer = CallRiskScorer(shippedModels())

    private fun video(unknown: Boolean = true, authorityName: Boolean = false) =
        ringing(unknown = unknown).copy(channel = CallChannel.VIDEO_APP, authorityName = authorityName)

    private fun score(w: CallWindow, now: Long = w.events.maxOf { it.ts }) =
        scorer.evaluate(RiskInput(w, CallerContext(), TransactionHistory(), false, now, ZoneOffset.UTC))

    private val active = SignalEvent.CallActive(T0 + 5_000)
    private fun screen(ts: Long, vararg c: ScreenCategory) = SignalEvent.ScreenEvidence(ts, c.toSet())

    @Test fun `SIM calls are unchanged`() {
        val w = window(ringing(), active, otp(T0 + 2 * MIN))
        val d = score(w)
        assertTrue(d.reasonCodes.none { it == ReasonCode.VIDEO_CALL_UNKNOWN })
    }

    @Test fun `known video callers stay silent`() {
        val w = window(video(unknown = false), active, screen(T0 + MIN, ScreenCategory.LAW_ENFORCEMENT, ScreenCategory.LEGAL_THREAT))
        val d = score(w)
        assertTrue(d.gated)
        assertEquals(RiskLevel.SAFE, d.level)
    }

    @Test fun `a short unknown video call is noted but quiet`() {
        val d = score(window(video(), active), now = T0 + 2 * MIN)
        assertTrue(ReasonCode.VIDEO_CALL_UNKNOWN in d.reasonCodes)
        assertEquals(RiskLevel.SAFE, d.level)
    }

    @Test fun `a very long unknown video call alone is at most a warning`() {
        val d = score(window(video(), active), now = T0 + 90 * MIN)
        assertTrue(ReasonCode.VIDEO_CALL_LONG in d.reasonCodes)
        assertTrue(d.level != RiskLevel.RED_FLAG)
        assertFalse(d.comboFired)
    }

    @Test fun `fake officer documents on a long video call are a red flag for digital arrest`() {
        val w = window(video(), active, screen(T0 + 5 * MIN, ScreenCategory.LAW_ENFORCEMENT, ScreenCategory.LEGAL_THREAT))
        val d = score(w, now = T0 + 30 * MIN)
        assertEquals(RiskLevel.RED_FLAG, d.level)
        assertEquals(ScamType.DIGITAL_ARREST, d.scamType)
        assertEquals(ReasonCode.COMBO_VIDEO_AUTHORITY, d.reasonCodes.first())
        assertTrue(d.l1 > 0)
    }

    @Test fun `an official-sounding caller name plus a debit is a red flag`() {
        val w = window(video(authorityName = true), active, debit(T0 + 10 * MIN, amount = 50_000_00))
        val d = score(w)
        assertEquals(RiskLevel.RED_FLAG, d.level)
        assertEquals(ScamType.DIGITAL_ARREST, d.scamType)
        assertTrue(ReasonCode.AUTHORITY_CALLER_NAME in d.reasonCodes)
    }

    @Test fun `screen evidence alone on a short call does not fire the combo`() {
        val w = window(video(), active, screen(T0 + MIN, ScreenCategory.LEGAL_THREAT))
        val d = score(w, now = T0 + 2 * MIN)
        assertFalse(d.comboFired)
        assertTrue(d.level != RiskLevel.RED_FLAG)
    }

    @Test fun `this was me silences a video window`() {
        val w = window(
            video(), active,
            screen(T0 + MIN, ScreenCategory.LAW_ENFORCEMENT, ScreenCategory.LEGAL_THREAT),
            SignalEvent.UserFeedback(T0 + 2 * MIN, FeedbackKind.THIS_WAS_ME),
        )
        assertEquals(RiskLevel.SAFE, score(w, now = T0 + 40 * MIN).level)
    }

    @Test fun `screen points are capped`() {
        val w = window(
            video(), active,
            screen(T0 + MIN, ScreenCategory.LAW_ENFORCEMENT, ScreenCategory.LEGAL_THREAT, ScreenCategory.PAYMENT_DEMAND),
        )
        val c = RiskConfig()
        val hits = DigitalArrestRules.hits(RiskInput(w, now = T0 + 2 * MIN, zone = ZoneOffset.UTC), c)
        val screenPoints = hits.filter { it.code.name.startsWith("SCREEN_") }.sumOf { it.points }
        assertEquals(c.screenCap, screenPoints)
    }
}
