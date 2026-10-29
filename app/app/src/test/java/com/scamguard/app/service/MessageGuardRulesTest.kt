package com.scamguard.app.service

import com.scamguard.app.ml.MessageCategory
import com.scamguard.app.ml.MessagePrediction
import com.scamguard.app.ml.MessageVerdict
import com.scamguard.app.ml.UrlPrediction
import com.scamguard.app.ml.UrlVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageGuardRulesTest {
    private val clean = MessagePrediction(MessageVerdict.NO_STRONG_SIGNAL, 0.05)
    private val cannotJudge = MessagePrediction(MessageVerdict.UNKNOWN, 0.2)

    @Test fun otpDuringCallIsFlaggedEvenFromBank() {
        val p = MessageGuard.otpRisk(clean, isOtp = true, senderTrusted = true, duringCall = true)
        assertEquals(MessageVerdict.SUSPICIOUS, p.verdict)
        assertEquals(MessageCategory.CREDENTIAL_THEFT, p.topCategory)
        assertTrue(MessageGuard.OTP_DURING_CALL in p.indicators)
    }

    @Test fun bankOtpOutsideCallStaysClean() {
        assertEquals(MessageVerdict.NO_STRONG_SIGNAL, MessageGuard.otpRisk(clean, isOtp = true, senderTrusted = true, duringCall = false).verdict)
    }

    @Test fun otpFromUnknownSenderIsFlagged() {
        assertEquals(MessageVerdict.SUSPICIOUS, MessageGuard.otpRisk(clean, isOtp = true, senderTrusted = false, duringCall = false).verdict)
    }

    @Test fun hinglishArrestThreatTheModelCannotJudgeIsFlagged() {
        val p = MessageGuard.ruleRisk(cannotJudge, "CBI se bol raha hoon, aapke naam pe arrest warrant hai", senderTrusted = false)
        assertEquals(MessageVerdict.SUSPICIOUS, p.verdict)
        assertEquals(MessageCategory.IMPERSONATION_THREAT, p.topCategory)
    }

    @Test fun phishingLinkIsFlagged() {
        val withLink = clean.copy(urls = listOf(UrlPrediction("sbi-kyc.top", 0.95, UrlVerdict.SUSPICIOUS, "u")))
        assertEquals(MessageVerdict.SUSPICIOUS, MessageGuard.ruleRisk(withLink, "update now sbi-kyc.top", senderTrusted = false).verdict)
    }

    @Test fun ordinaryMessageIsNotFlagged() {
        assertEquals(MessageVerdict.NO_STRONG_SIGNAL, MessageGuard.ruleRisk(clean, "See you at 7 for dinner", senderTrusted = false).verdict)
    }

    private val modelSaysScam = MessagePrediction(MessageVerdict.LIKELY_SCAM, 0.97)

    @Test fun registeredBusinessHeaderIsNotFlaggedOnModelAlone() {
        listOf("AX-CANBNK-S", "JZ-JioPay-S", "VM-MMTRIP-P", "JD-LOCKER-S", "VA-EKARTL").forEach { sender ->
            val p = MessageGuard.classify(modelSaysScam, "Dear Customer, Acct XXX513 Dr. INR 85.00", sender, false, isOtp = false, duringCall = false)
            assertEquals(sender, MessageVerdict.NO_STRONG_SIGNAL, p.verdict)
        }
        val otp = MessageGuard.classify(clean, "447969 is your OTP to access DigiLocker", "JD-LOCKER-S", false, isOtp = true, duringCall = false)
        assertEquals(MessageVerdict.NO_STRONG_SIGNAL, otp.verdict)
    }

    @Test fun mobileNumberSenderIsStillFlagged() {
        val p = MessageGuard.classify(modelSaysScam, "URGENT! Your bank account will be blocked", "+917385670291", false, isOtp = false, duringCall = false)
        assertEquals(MessageVerdict.LIKELY_SCAM, p.verdict)
    }

    @Test fun registeredHeaderOtpDuringCallIsStillFlagged() {
        val p = MessageGuard.classify(clean, "778995 is the OTP", "JM-MMTRIP-S", false, isOtp = true, duringCall = true)
        assertEquals(MessageVerdict.SUSPICIOUS, p.verdict)
    }
}
