package com.scamguard.app.util

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SHA-256 hashing used for every identifier the app keeps (phone numbers, payees).
 * The pepper is random per install ([PepperStore]) and installed at app start, so stored
 * hashes cannot be matched against a precomputed table of numbers from another phone.
 * Phone numbers are a small space, so a hash is a one-way code, not strong anonymity:
 * the pepper is what keeps a copied database from being trivially reversed.
 */
object Hashing {
    /** Random until [install] runs; unit tests and the simulator never need a stable value. */
    @Volatile private var pepper: String = randomPepper()

    /** Sets the per-install pepper. Called once from the Application before any hashing. */
    fun install(value: String) {
        require(value.length >= 32) { "pepper too short" }
        pepper = value
    }

    /** Hex SHA-256 of [input]. */
    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Hash of a normalised (E.164-like) phone number. */
    fun numberHash(normalized: String): String = sha256Hex(pepper + normalized)

    /** Hash of a payee name or UPI handle as written in a debit SMS. */
    fun payeeHash(payee: String): String = sha256Hex(pepper + "payee:" + payee.trim().lowercase())

    /** 32 random bytes as hex. */
    fun randomPepper(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
