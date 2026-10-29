package com.scamguard.app.collectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamHighlightsTest {
    private fun marked(text: String, kind: ScamHighlights.Kind) =
        ScamHighlights.find(text).filter { it.kind == kind }.map { text.substring(it.start, it.end) }

    @Test
    fun marksDigitalArrestAndOfficials() {
        val p = marked("This is CBI. You are under digital arrest, do not disclose.", ScamHighlights.Kind.SCAM_PHRASE)
        assertTrue("CBI" in p)
        assertTrue("digital arrest" in p)
        assertTrue("do not disclose" in p)
    }

    @Test
    fun marksOtpCodeAndWord() {
        val text = "482913 is your OTP for HDFC Bank. Do not share."
        assertEquals(listOf("482913"), marked(text, ScamHighlights.Kind.SENSITIVE))
        assertTrue("OTP" in marked(text, ScamHighlights.Kind.SCAM_PHRASE))
        assertEquals(listOf("7731"), marked("Sir please tell me the OTP 7731 quickly", ScamHighlights.Kind.SENSITIVE))
    }

    @Test
    fun marksCardNumberOnlyWhenLuhnValid() {
        assertEquals(listOf("4111 1111 1111 1111"), marked("card 4111 1111 1111 1111 cvv", ScamHighlights.Kind.SENSITIVE))
        assertFalse(ScamHighlights.luhn("4111 1111 1111 1112"))
    }

    @Test
    fun marksLinksPanAndCvv() {
        val s = marked("Update PAN ABCDE1234F at http://sbi-kyc-update.top/login, CVV 123", ScamHighlights.Kind.SENSITIVE)
        assertTrue("ABCDE1234F" in s)
        assertTrue("http://sbi-kyc-update.top/login," in s || "http://sbi-kyc-update.top/login" in s)
        assertTrue("123" in s)
    }

    @Test
    fun ignoresPlainText() {
        assertTrue(ScamHighlights.find("See you at dinner tonight").isEmpty())
    }
}
