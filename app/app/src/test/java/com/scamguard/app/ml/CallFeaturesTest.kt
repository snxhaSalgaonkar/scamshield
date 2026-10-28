package com.scamguard.app.ml

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.engine.CallerContext
import com.scamguard.app.engine.MIN
import com.scamguard.app.engine.RiskInput
import com.scamguard.app.engine.T0
import com.scamguard.app.engine.TransactionHistory
import com.scamguard.app.engine.debit
import com.scamguard.app.engine.otp
import com.scamguard.app.engine.ringing
import com.scamguard.app.engine.window
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class CallFeaturesTest {
    private fun f(x: DoubleArray, name: String) = x[CallFeatures.NAMES.indexOf(name)]

    private fun build(vararg events: SignalEvent, caller: CallerContext = CallerContext(), history: TransactionHistory = TransactionHistory(),
                      ring: SignalEvent.CallRinging = ringing()): DoubleArray {
        val w = window(ring, *events)
        return CallFeatures.build(RiskInput(w, caller, history, false, w.events.maxOf { it.ts }, ZoneOffset.UTC))
    }

    @Test fun `ringing only gives the absent value for every event feature`() {
        val x = build()
        assertEquals(CallFeatures.NAMES.size, x.size)
        assertEquals(1.0, f(x, "calls_last_hour"), 0.0)
        CallFeatures.NAMES.filter { it != "calls_last_hour" }.forEach { assertEquals(it, 0.0, f(x, it), 0.0) }
    }

    @Test fun `call, OTP and debit sequence is encoded`() {
        val x = build(
            SignalEvent.CallActive(T0 + 5_000), otp(T0 + MIN, trusted = false),
            debit(T0 + 3 * MIN, amount = 50_000_00, payee = "new", balance = 100_00),
            history = TransactionHistory.from(listOf(1_000_00, 1_000_00, 1_000_00), listOf("old")),
        )
        assertEquals(1.0, f(x, "answered"), 0.0)
        assertEquals(3.0 - 5.0 / 60, f(x, "talk_minutes"), 1e-9)
        assertEquals(1.0, f(x, "otp_count"), 0.0)
        assertEquals(1.0, f(x, "otp_untrusted_sender"), 0.0)
        assertEquals(50.0, f(x, "debit_amount_ratio"), 0.0)
        assertEquals(1.0, f(x, "history_available"), 0.0)
        assertEquals(1.0, f(x, "first_time_payee"), 0.0)
        assertEquals(1.0, f(x, "balance_near_zero"), 0.0)
        assertEquals(1.0, f(x, "otp_then_debit_5min"), 0.0)
    }

    @Test fun `caller context and missing number are encoded`() {
        val x = build(caller = CallerContext(callsLastHour = 4, reported = true), ring = ringing(hidden = true, hash = null))
        assertEquals(1.0, f(x, "number_unavailable"), 0.0)
        assertEquals(1.0, f(x, "is_hidden"), 0.0)
        assertEquals(1.0, f(x, "calls_last_hour"), 0.0) // no number: repeat count unknown
        assertEquals(1.0, f(x, "reported_number"), 0.0)
    }

    @Test fun `message model outputs are maxed over the window and unfamiliar text is ignored`() {
        val x = build(
            SignalEvent.CallActive(T0 + 5_000),
            SignalEvent.SmsAnalyzed(T0 + MIN, 0.4, credential = 0.3, linkCount = 1, maxUrlProbability = 0.2),
            SignalEvent.SmsAnalyzed(T0 + 2 * MIN, 0.9, impersonation = 0.7),
            SignalEvent.SmsAnalyzed(T0 + 3 * MIN, 0.99, impersonation = 0.99, outOfDistribution = true),
            SignalEvent.ApkInstalled(T0 + 4 * MIN, "x", sideloaded = true),
        )
        assertEquals(1.0, f(x, "sms_analyzed"), 0.0)
        assertEquals(0.9, f(x, "max_sms_scam_prob"), 0.0)
        assertEquals(0.7, f(x, "sms_impersonation"), 0.0)
        assertEquals(0.3, f(x, "sms_credential"), 0.0)
        assertEquals(0.2, f(x, "max_url_prob"), 0.0)
        assertEquals(1.0, f(x, "link_received"), 0.0)
        assertEquals(1.0, f(x, "link_then_install"), 0.0)
    }
}
