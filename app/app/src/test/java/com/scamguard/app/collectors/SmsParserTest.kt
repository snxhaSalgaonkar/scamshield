package com.scamguard.app.collectors

import com.scamguard.app.util.Hashing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** All messages here are synthetic; none is a real bank message or real OTP. */
class SmsParserTest {

    @Test fun `OTP needs a keyword and a 4 to 8 digit code`() {
        assertTrue(SmsParser.classify("123456 is your OTP for login. Do not share it.").isOtp)
        assertTrue(SmsParser.classify("Your one-time password is 9081. Valid 5 min.").isOtp)
        assertTrue(SmsParser.classify("Verification code: 44120873").isOtp)
        assertFalse(SmsParser.classify("Never share your OTP with anyone.").isOtp)
        assertFalse(SmsParser.classify("Your code is 123456").isOtp)
        assertFalse(SmsParser.classify("OTP 123").isOtp)
        assertFalse(SmsParser.classify("OTP 123456789").isOtp)
    }

    @Test fun `classification never carries the OTP value`() {
        val c = SmsParser.classify("OTP 739104 for txn")
        assertFalse(c.toString().contains("739104"))
    }

    @Test fun `debit with rupee amount, payee and balance`() {
        val c = SmsParser.classify("Rs.2,500.50 debited from A/c XX1234 to demo.shop@okaxis on 01-01. Avl Bal Rs 10,000.00")
        assertTrue(c.isDebit)
        assertEquals(2_500_50L, c.amountPaise)
        assertEquals(10_000_00L, c.balancePaise)
        assertEquals(Hashing.payeeHash("demo.shop@okaxis"), c.payeeHash)
        assertFalse(c.hasLink)
    }

    @Test fun `INR and rupee sign, Indian comma grouping, single decimal`() {
        assertEquals(1_23_456_50L, SmsParser.classify("INR 1,23,456.5 debited from a/c 99").amountPaise)
        assertEquals(499_00L, SmsParser.classify("₹499 spent on card ending 1111 at DEMO STORE.").amountPaise)
        assertEquals(75_00L, SmsParser.classify("Sent Rs75 via UPI to friend").amountPaise)
    }

    @Test fun `balance first in the text is not taken as the amount`() {
        val c = SmsParser.classify("Avl Bal: INR 900.00. Rs 100 debited from A/c 12")
        assertEquals(100_00L, c.amountPaise)
        assertEquals(900_00L, c.balancePaise)
    }

    @Test fun `debit without an amount is still a debit`() {
        val c = SmsParser.classify("Your A/c has been debited. Call us if not you.")
        assertTrue(c.isDebit)
        assertNull(c.amountPaise)
    }

    @Test fun `payee from a name after to`() {
        val c = SmsParser.classify("Rs 300 debited from A/c 1 to Demo Traders on 02-02")
        assertNotNull(c.payeeHash)
        assertEquals(Hashing.payeeHash("demo traders"), c.payeeHash)
    }

    @Test fun `credits, future debits and OTP for a payment are not debits`() {
        assertFalse(SmsParser.classify("Rs 500 credited to your A/c 12").isDebit)
        assertFalse(SmsParser.classify("Rs 500 will be debited from A/c 12 tomorrow").isDebit)
        val otp = SmsParser.classify("OTP 482913 to pay Rs 999 at DEMO. Never share it.")
        assertTrue(otp.isOtp)
        assertFalse(otp.isDebit)
    }

    @Test fun `links are detected but never kept`() {
        val c = SmsParser.classify("Your KYC expires. Update at http://kyc-update.example.xyz/a now")
        assertTrue(c.hasLink)
        assertFalse(c.toString().contains("example"))
        assertTrue(SmsParser.classify("Track parcel: bit.ly/demo1").hasLink)
        assertTrue(SmsParser.classify("visit www.demo-shop.in").hasLink)
    }

    @Test fun `UPI handles and amounts are not links`() {
        assertFalse(SmsParser.classify("Rs.10.50 sent to demo@okicici via UPI").hasLink)
    }

    @Test fun `ordinary and empty messages are irrelevant`() {
        assertFalse(SmsParser.classify("See you at 5 near the station?").isRelevant)
        assertFalse(SmsParser.classify("").isRelevant)
        assertFalse(SmsParser.classify("Happy birthday! 🎂").isRelevant)
    }

    @Test fun `oversized amounts do not overflow`() {
        val c = SmsParser.classify("Rs 9999999999999 debited from A/c 1")
        assertTrue(c.isDebit)
        assertEquals(9_999_999_999_999_00L, c.amountPaise)
        assertNull(SmsParser.toPaise("99999999999999999999", ""))
    }
}
