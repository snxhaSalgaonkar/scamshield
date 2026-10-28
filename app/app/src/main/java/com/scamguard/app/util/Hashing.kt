package com.scamguard.app.util

import java.security.MessageDigest

/**
 * SHA-256 hashing used for every identifier the app keeps (phone numbers, payees).
 * A fixed app-wide pepper makes hashes comparable with the bundled seed list and a
 * future backend, while raw values are never stored.
 */
object Hashing {
    private const val PEPPER = "scg-v1-7f3a9c2e"

    /** Hex SHA-256 of [input]. */
    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Hash of a normalised (E.164-like) phone number. */
    fun numberHash(normalized: String): String = sha256Hex(PEPPER + normalized)

    /** Hash of a payee name or UPI handle as written in a debit SMS. */
    fun payeeHash(payee: String): String = sha256Hex(PEPPER + "payee:" + payee.trim().lowercase())
}
