package com.scamguard.app.remote

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.engine.CallWindow
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.ReasonCode
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.engine.ScamType
import com.scamguard.app.engine.WindowState
import com.scamguard.app.util.PhoneNumbers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPayloadTest {
    private val window = CallWindow(
        id = "w1", state = WindowState.ACTIVE, numberHash = "h", prefix = "+91 98", mask = "+91 98•••••21",
        isUnknown = true, isInternational = false, isHidden = false, startedAt = 1_700_000_000_000,
        events = listOf(
            SignalEvent.VoiceKeyword(1, VoiceCategory.AUTHORITY_CLAIM, "cbi"),
            SignalEvent.VoiceKeyword(2, VoiceCategory.ARREST_THREAT, "digital arrest"),
            SignalEvent.VoiceKeyword(3, VoiceCategory.ARREST_THREAT, "digital arrest"),
            SignalEvent.VoiceKeyword(4, VoiceCategory.ISOLATION, null),
        ),
    )

    private fun decision(level: RiskLevel, score: Int, codes: List<ReasonCode>, type: ScamType = ScamType.DIGITAL_ARREST) =
        Decision("w1", 0, score, level, 60, 10, 30, type, codes, comboFired = true, gated = false)

    @After fun tearDown() {
        CallerNumbers.enabled = false
        CallerNumbers.clear()
    }

    @Test fun `report carries number, scores, tactics and reasons only`() {
        val d = decision(RiskLevel.RED_FLAG, 92, listOf(ReasonCode.COMBO_VOICE_DIGITAL_ARREST, ReasonCode.OTP_DURING_CALL, ReasonCode.REPORTED_NUMBER, ReasonCode.CALL_MERGED))
        val json = ReportPayload.build("+919876543210", ReportPayload.call(window, d, userReported = false)) { it.name.lowercase() }

        assertEquals("+919876543210", json.getString("caller_number"))
        assertEquals(92, json.getInt("risk_score"))
        assertEquals(60, json.getInt("l1_score"))
        assertEquals("digital_arrest", json.getString("scam_type"))
        assertEquals("""["cbi","digital arrest"]""", json.getJSONArray("matched_phrases").toString())
        assertEquals("""["voice","sms","reputation","correlation"]""", json.getJSONArray("detected_via").toString())
        assertEquals(4, json.getJSONArray("reasons").length())
        assertEquals("warn", json.getString("action_taken"))
        assertEquals("2023-11-14T22:13:20Z", json.getString("call_started_at"))
        // The server adds device_id; status and feedback are the server's defaults.
        listOf("device_id", "status", "user_feedback", "id").forEach { assertFalse(it, json.has(it)) }
    }

    private fun score(level: RiskLevel, score: Int, userReported: Boolean = false) =
        ReportPayload.riskScore(ReportPayload.call(window, decision(level, score, emptyList()), userReported))

    @Test fun `score stays inside the band the phone decided`() {
        assertEquals(75, score(RiskLevel.RED_FLAG, 73))
        assertEquals(74, score(RiskLevel.WARNING, 80))
        assertEquals(40, score(RiskLevel.WARNING, 20))
        assertEquals(55, score(RiskLevel.WARNING, 55))
        assertEquals("user-reported scam is at least a red flag", 75, score(RiskLevel.SAFE, 12, userReported = true))
    }

    @Test fun `a call reported after it ended is built from the saved decision`() {
        val e = DecisionEntity(
            windowId = "w9", ts = 0, startedAt = 1_700_000_000_000, score = 30, level = "SAFE", peakScore = 52,
            peakLevel = "WARNING", l1 = 0, l2 = 20, l3 = 40, scamType = "OTP_THEFT", reasonCodes = "OTP_DURING_CALL,NOT_A_CODE",
            comboFired = false, numberHash = "h", prefix = "+91 98", mask = "", callEnded = true, simulated = false, feedback = "CONFIRMED_SCAM",
        )
        val json = ReportPayload.build("+919876543210", ReportPayload.call(e)) { it.name }
        assertEquals(75, json.getInt("risk_score"))
        assertEquals("otp_theft", json.getString("scam_type"))
        assertEquals("""["sms","manual_report"]""", json.getJSONArray("detected_via").toString())
        assertEquals("""["Reported as a scam by the user","OTP_DURING_CALL"]""", json.getJSONArray("reasons").toString())
    }

    @Test fun `every scam type maps to a dashboard type`() {
        val allowed = setOf("digital_arrest", "otp_theft", "remote_access", "other")
        ScamType.entries.forEach { assertTrue(it.name, ReportPayload.scamType(it) in allowed) }
    }

    @Test fun `caller numbers are kept only when enabled`() {
        val parsed = PhoneNumbers.parse("98765 43210")!!
        CallerNumbers.remember(parsed)
        assertNull(CallerNumbers.lookup(parsed.hash))

        CallerNumbers.enabled = true
        CallerNumbers.remember(parsed, now = 0)
        assertEquals("+919876543210", CallerNumbers.lookup(parsed.hash, now = 1_000))
        assertNull("expires", CallerNumbers.lookup(parsed.hash, now = 3 * 60 * 60_000L))
    }
}
