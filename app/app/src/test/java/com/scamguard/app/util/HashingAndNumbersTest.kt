package com.scamguard.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HashingAndNumbersTest {

    @Test fun `indian numbers normalise to the same hash in every format`() {
        val forms = listOf("+91 98765 43210", "09876543210", "9876543210", "919876543210", "0091 98765-43210")
        assertEquals(1, forms.map { PhoneNumbers.parse(it)!!.hash }.toSet().size)
    }

    @Test fun `prefix and mask never contain the full number`() {
        val p = PhoneNumbers.parse("+91 98765 43210")!!
        assertEquals("+91 98", p.prefix)
        assertEquals("+91 98•••••10", p.mask)
        assertFalse(p.isInternational)
        assertFalse(p.mask.contains("98765"))
    }

    @Test fun `foreign numbers are international`() {
        val p = PhoneNumbers.parse("+44 7700 900123")!!
        assertTrue(p.isInternational)
        assertEquals("+447", p.prefix)
    }

    @Test fun `empty or hidden numbers parse to null`() {
        assertNull(PhoneNumbers.parse(null))
        assertNull(PhoneNumbers.parse(""))
        assertNull(PhoneNumbers.parse("Private"))
    }

    @Test fun `hash is hex sha256 and stable`() {
        val h = Hashing.numberHash("+919876543210")
        assertEquals(64, h.length)
        assertEquals(h, Hashing.numberHash("+919876543210"))
    }

    @Test fun `seed numbers are stored in the normalised form the app hashes`() {
        // org.json is stubbed in local unit tests, so read the raw file and search it.
        val seed = File("src/main/assets/reputation_seed.json").readText()
        assertTrue(seed.contains("\"" + PhoneNumbers.parse("1800 1234")!!.normalized + "\""))
        assertTrue(seed.contains("\"" + PhoneNumbers.parse("1800-202-6161")!!.normalized + "\""))
        assertTrue(seed.contains("\"" + PhoneNumbers.parse("+44 7700 900123")!!.normalized + "\""))
        assertTrue(!seed.contains("\"hash\""))
    }

    @Test fun `a different pepper gives a different hash`() {
        val before = Hashing.numberHash("+919876543210")
        Hashing.install(Hashing.randomPepper())
        assertNotEquals(before, Hashing.numberHash("+919876543210"))
    }
}
