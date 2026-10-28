package com.scamguard.app.engine

import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {

    private fun assertScore(expected: Int, d: Decision) = assertEquals(d.reasonCodes.toString(), expected, d.score)

    // --- Gate ---

    @Test fun `known caller keeps the engine silent even with OTP and debit`() {
        val d = evaluate(window(ringing(unknown = false), SignalEvent.CallActive(T0), otp(), debit()))
        assertScore(0, d)
        assertEquals(RiskLevel.SAFE, d.level)
        assertTrue(d.gated)
        assertTrue(d.reasonCodes.isEmpty())
        assertFalse(d.comboFired)
    }

    @Test fun `saved contact call with an OTP is silent`() {
        val d = evaluate(window(ringing(unknown = false), SignalEvent.CallActive(T0), otp()))
        assertEquals(RiskLevel.SAFE, d.level)
        assertScore(0, d)
    }

    @Test fun `unknown caller alone scores only the gate points`() {
        val d = evaluate(window())
        assertScore(10, d)
        assertEquals(listOf(ReasonCode.UNKNOWN_CALLER), d.reasonCodes)
        assertEquals(RiskLevel.SAFE, d.level)
        assertEquals(ScamType.NONE, d.scamType)
    }

    @Test fun `hidden number is treated as unknown`() {
        val d = evaluate(window(ringing(unknown = false, hidden = true, hash = null)))
        assertFalse(d.gated)
        assertScore(10, d)
    }

    // --- Correlation rules ---

    @Test fun `otp during window adds 25`() = assertScore(35, evaluate(window(ringing(), otp())))

    @Test fun `debit during window adds 30`() {
        val d = evaluate(window(ringing(), debit()))
        assertScore(40, d)
        assertTrue(ReasonCode.DEBIT_DURING_CALL in d.reasonCodes)
    }

    @Test fun `forwarding turned on adds 35, turned off adds nothing`() {
        assertScore(45, evaluate(window(ringing(), SignalEvent.ForwardingChanged(T0 + MIN, true))))
        assertScore(10, evaluate(window(ringing(), SignalEvent.ForwardingChanged(T0 + MIN, false))))
    }

    @Test fun `call merge adds 25`() = assertScore(35, evaluate(window(ringing(), SignalEvent.SecondCallConnected(T0 + MIN))))

    @Test fun `remote access app opened adds 20`() =
        assertScore(30, evaluate(window(ringing(), SignalEvent.RemoteAppOpened(T0 + MIN, "com.anydesk.anydeskandroid"))))

    @Test fun `sideloaded apk adds 10, store install adds nothing`() {
        assertScore(20, evaluate(window(ringing(), SignalEvent.ApkInstalled(T0 + MIN, "x.y", sideloaded = true))))
        assertScore(10, evaluate(window(ringing(), SignalEvent.ApkInstalled(T0 + MIN, "x.y", sideloaded = false))))
    }

    @Test fun `link then install adds 15 on top of the install`() {
        val d = evaluate(window(ringing(), SignalEvent.LinkReceived(T0 + MIN), SignalEvent.ApkInstalled(T0 + 2 * MIN, "x.y", true)))
        assertScore(35, d)
        assertTrue(ReasonCode.LINK_THEN_INSTALL in d.reasonCodes)
    }

    @Test fun `install before link is not link-then-install`() {
        val d = evaluate(window(ringing(), SignalEvent.ApkInstalled(T0 + MIN, "x.y", false), SignalEvent.LinkReceived(T0 + 2 * MIN)))
        assertFalse(ReasonCode.LINK_THEN_INSTALL in d.reasonCodes)
    }

    @Test fun `network loss adds 5`() = assertScore(15, evaluate(window(ringing(), SignalEvent.NetworkLost(T0 + MIN, 10_000))))

    // --- Transaction analysis ---

    @Test fun `amount far above normal adds 10`() {
        val history = TransactionHistory(medianDebitPaise = 1_000_00)
        assertScore(50, evaluate(window(ringing(), debit(amount = 10_000_00)), history = history))
    }

    @Test fun `big multiple of a tiny median under Rs 5000 is not far above normal`() {
        val history = TransactionHistory(medianDebitPaise = 100_00)
        assertScore(40, evaluate(window(ringing(), debit(amount = 4_000_00)), history = history))
    }

    @Test fun `no median means no far-above-normal rule`() =
        assertScore(40, evaluate(window(ringing(), debit(amount = 90_000_00))))

    @Test fun `first time payee adds 5, known payee adds nothing`() {
        assertScore(45, evaluate(window(ringing(), debit(payee = "p-new"))))
        assertScore(40, evaluate(window(ringing(), debit(payee = "p-old")), history = TransactionHistory(knownPayees = setOf("p-old"))))
    }

    @Test fun `two debits within 10 minutes add 5, further apart do not`() {
        assertScore(45, evaluate(window(ringing(), debit(T0 + MIN), debit(T0 + 9 * MIN))))
        assertScore(40, evaluate(window(ringing(), debit(T0 + MIN), debit(T0 + 12 * MIN))))
    }

    @Test fun `balance near zero adds 5`() {
        assertScore(45, evaluate(window(ringing(), debit(balance = 120_00))))
        assertScore(40, evaluate(window(ringing(), debit(balance = 50_000_00))))
    }

    @Test fun `transaction analysis is capped at 20`() {
        val history = TransactionHistory(medianDebitPaise = 500_00)
        val w = window(ringing(), debit(T0 + MIN, 20_000_00, "p1", 10_00), debit(T0 + 2 * MIN, 20_000_00, "p2", 5_00))
        // 10 + 30 + min(10 + 5 + 5 + 5, 20) = 60
        assertScore(60, evaluate(w, history = history))
    }

    // --- Caller extras ---

    @Test fun `international number adds 10`() = assertScore(20, evaluate(window(ringing(international = true))))

    @Test fun `three calls in an hour add 10, two do not`() {
        assertScore(20, evaluate(window(), caller = CallerContext(callsLastHour = 3)))
        assertScore(10, evaluate(window(), caller = CallerContext(callsLastHour = 2)))
    }

    @Test fun `reported number adds 15`() = assertScore(25, evaluate(window(), caller = CallerContext(reported = true)))

    @Test fun `user confirming a scam counts as reported, once`() {
        val w = window(ringing(), SignalEvent.UserFeedback(T0 + MIN, FeedbackKind.CONFIRMED_SCAM))
        assertScore(25, evaluate(w, caller = CallerContext(reported = true)))
    }

    @Test fun `call over 5 minutes adds 5, exactly 5 does not`() {
        assertScore(15, evaluate(window(ringing(), SignalEvent.CallActive(T0), SignalEvent.CallEnded(T0 + 6 * MIN, 6 * MIN))))
        assertScore(10, evaluate(window(ringing(), SignalEvent.CallActive(T0), SignalEvent.CallEnded(T0 + 5 * MIN, 5 * MIN))))
    }

    @Test fun `ongoing long call counts using now`() {
        val w = window(ringing(), SignalEvent.CallActive(T0))
        assertScore(15, evaluate(w, now = T0 + 7 * MIN))
    }

    @Test fun `odd hours are 23 to 5`() {
        val day = T0 - 12 * 60 * MIN // 00:00 UTC
        assertScore(15, evaluate(window(ringing(ts = day + 23 * 60 * MIN + 30 * MIN))))
        assertScore(15, evaluate(window(ringing(ts = day + 4 * 60 * MIN + 59 * MIN))))
        assertScore(10, evaluate(window(ringing(ts = day + 5 * 60 * MIN))))
        assertScore(10, evaluate(window(ringing(ts = day + 22 * 60 * MIN + 59 * MIN))))
    }

    // --- Reducers ---

    @Test fun `known bank number reduces by 30`() {
        val d = evaluate(window(ringing(), otp()), caller = CallerContext(knownBankNumber = true))
        assertScore(5, d)
        assertEquals(ReasonCode.KNOWN_BANK_NUMBER, d.reasonCodes.last())
    }

    @Test fun `this was me reduces by 30 and cancels combos`() {
        val w = window(ringing(), otp(), debit(), SignalEvent.UserFeedback(T0 + 3 * MIN, FeedbackKind.THIS_WAS_ME))
        val d = evaluate(w)
        assertScore(35, d)
        assertFalse(d.comboFired)
        assertEquals(RiskLevel.SAFE, d.level)
    }

    // --- Clamping ---

    @Test fun `score never goes below 0`() {
        val w = window(ringing(), SignalEvent.UserFeedback(T0 + MIN, FeedbackKind.THIS_WAS_ME))
        assertScore(0, evaluate(w, caller = CallerContext(knownBankNumber = true)))
    }

    @Test fun `score never exceeds 100`() {
        val w = window(
            ringing(international = true), SignalEvent.CallActive(T0), otp(), debit(),
            SignalEvent.ForwardingChanged(T0 + 3 * MIN, true), SignalEvent.SecondCallConnected(T0 + 3 * MIN),
            SignalEvent.RemoteAppOpened(T0 + 4 * MIN, "rustdesk"),
        )
        val d = evaluate(w, caller = CallerContext(callsLastHour = 5, reported = true))
        assertScore(100, d)
        assertEquals(RiskLevel.RED_FLAG, d.level)
    }

    // --- Combos ---

    @Test fun `otp plus debit within 5 minutes is a red flag`() {
        val d = evaluate(window(ringing(), otp(T0 + MIN), debit(T0 + 4 * MIN)))
        assertTrue(d.comboFired)
        assertEquals(ReasonCode.COMBO_OTP_DEBIT, d.reasonCodes.first())
        assertScore(75, d)
        assertEquals(RiskLevel.RED_FLAG, d.level)
    }

    @Test fun `otp and debit 6 minutes apart is not a combo`() {
        val d = evaluate(window(ringing(), otp(T0 + MIN), debit(T0 + 7 * MIN)))
        assertFalse(d.comboFired)
        assertScore(65, d)
        assertEquals(RiskLevel.WARNING, d.level)
    }

    @Test fun `forwarding plus otp is a red flag`() {
        val d = evaluate(window(ringing(), SignalEvent.ForwardingChanged(T0 + MIN, true), otp(T0 + 2 * MIN)))
        assertTrue(ReasonCode.COMBO_FORWARDING_OTP in d.reasonCodes)
        assertScore(75, d)
    }

    @Test fun `merge plus otp is a red flag`() {
        val d = evaluate(window(ringing(), SignalEvent.SecondCallConnected(T0 + MIN), otp(T0 + 2 * MIN)))
        assertTrue(ReasonCode.COMBO_MERGE_OTP in d.reasonCodes)
        assertScore(75, d)
    }

    @Test fun `remote access app plus debit is a red flag`() {
        val d = evaluate(window(ringing(), SignalEvent.RemoteAppOpened(T0 + MIN, "anydesk"), debit()))
        assertTrue(ReasonCode.COMBO_REMOTE_DEBIT in d.reasonCodes)
        assertScore(75, d)
    }

    @Test fun `combo floor does not lower a higher score`() {
        val w = window(ringing(international = true), otp(), debit(), SignalEvent.ForwardingChanged(T0 + MIN, true))
        assertScore(100, evaluate(w))
    }

    // --- Scam types ---

    @Test fun `scam type follows the most specific signal`() {
        assertEquals(ScamType.OTP_THEFT, evaluate(window(ringing(), otp(), debit())).scamType)
        assertEquals(ScamType.MONEY_TRANSFER_PRESSURE, evaluate(window(ringing(), debit())).scamType)
        assertEquals(ScamType.CALL_FORWARDING_HIJACK, evaluate(window(ringing(), SignalEvent.ForwardingChanged(T0, true), otp())).scamType)
        assertEquals(ScamType.CALL_MERGE_FRAUD, evaluate(window(ringing(), SignalEvent.SecondCallConnected(T0), otp())).scamType)
        assertEquals(ScamType.REMOTE_ACCESS_FRAUD, evaluate(window(ringing(), SignalEvent.RemoteAppOpened(T0, "a"), debit())).scamType)
        assertEquals(
            ScamType.SUSPICIOUS_CALL,
            evaluate(window(ringing(international = true), SignalEvent.NetworkLost(T0 + MIN, 1)), caller = CallerContext(callsLastHour = 4, reported = true)).scamType,
        )
    }

    @Test fun `digital arrest needs the voice layer`() {
        val w = window(ringing(), SignalEvent.VoiceKeyword(T0 + MIN, VoiceCategory.ARREST_THREAT))
        val on = evaluate(w, voice = true)
        assertEquals(ScamType.DIGITAL_ARREST, on.scamType)
        assertEquals(25, on.l1)
        assertScore(35, on)
        val off = evaluate(w, voice = false)
        assertEquals(0, off.l1)
        assertScore(10, off)
    }

    @Test fun `voice layer is capped`() {
        val w = window(
            ringing(),
            SignalEvent.VoiceKeyword(T0, VoiceCategory.ARREST_THREAT),
            SignalEvent.VoiceKeyword(T0, VoiceCategory.OTP_REQUEST),
            SignalEvent.VoiceKeyword(T0, VoiceCategory.PAYMENT_PRESSURE),
            SignalEvent.VoiceKeyword(T0, VoiceCategory.REMOTE_APP_REQUEST),
        )
        assertEquals(40, evaluate(w, voice = true).l1)
    }

    @Test fun `layers are reported separately`() {
        val d = evaluate(window(ringing(international = true), otp()))
        assertEquals(0, d.l1)
        assertEquals(20, d.l2)
        assertEquals(25, d.l3)
    }

    // --- Thresholds ---

    @Test fun `threshold boundaries`() {
        assertEquals(RiskLevel.SAFE, RiskEngine.levelFor(0))
        assertEquals(RiskLevel.SAFE, RiskEngine.levelFor(39))
        assertEquals(RiskLevel.WARNING, RiskEngine.levelFor(40))
        assertEquals(RiskLevel.WARNING, RiskEngine.levelFor(74))
        assertEquals(RiskLevel.RED_FLAG, RiskEngine.levelFor(75))
        assertEquals(RiskLevel.RED_FLAG, RiskEngine.levelFor(100))
    }

    @Test fun `real windows on each side of the warning threshold`() {
        // 10 + 25 = 35: silent. 10 + 25 + 5 (long call) = 40: warning.
        assertEquals(RiskLevel.SAFE, evaluate(window(ringing(), otp())).level)
        val long = window(ringing(), SignalEvent.CallActive(T0), otp(), SignalEvent.CallEnded(T0 + 6 * MIN, 6 * MIN))
        val d = evaluate(long)
        assertScore(40, d)
        assertEquals(RiskLevel.WARNING, d.level)
    }

    @Test fun `real windows on each side of the red flag threshold`() {
        // 10 + 30 + 25 + 5 + 5 = 75 without any combo; dropping the long call gives 70.
        val base = arrayOf<SignalEvent>(SignalEvent.CallActive(T0), debit(T0 + MIN, balance = 10_00), SignalEvent.SecondCallConnected(T0 + 2 * MIN))
        val short = evaluate(window(ringing(), *base, SignalEvent.CallEnded(T0 + 3 * MIN, 3 * MIN)))
        assertScore(70, short)
        assertEquals(RiskLevel.WARNING, short.level)
        val long = evaluate(window(ringing(), *base, SignalEvent.CallEnded(T0 + 6 * MIN, 6 * MIN)))
        assertFalse(long.comboFired)
        assertScore(75, long)
        assertEquals(RiskLevel.RED_FLAG, long.level)
    }

    // --- Config ---

    @Test fun `overrides change weights and ignore unknown keys`() {
        val config = RiskConfig().withOverrides(mapOf("otpDuringWindow" to 50, "nope" to 1))
        assertEquals(50, config.otpDuringWindow)
        assertScore(60, evaluate(window(ringing(), otp()), config = config))
        assertEquals(RiskConfig.WEIGHT_KEYS.toSet(), config.weights().keys)
    }

    @Test fun `engine is deterministic`() {
        val w = window(ringing(), otp(), debit(payee = "p"))
        assertEquals(evaluate(w), evaluate(w))
    }

    @Test fun `median of debit history`() {
        assertEquals(300L, TransactionHistory.median(listOf(500, 100, 300)))
        assertEquals(250L, TransactionHistory.median(listOf(100, 200, 300, 400)))
        assertEquals(null, TransactionHistory.from(listOf(1, 2), emptyList()).medianDebitPaise)
    }
}
