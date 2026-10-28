package com.scamguard.app.util

/** A phone number reduced to what the app is allowed to keep or show. */
data class ParsedNumber(
    /** Normalised number, kept in memory only long enough to hash it. */
    val normalized: String,
    /** Country code plus the first national digits, e.g. "+91 98". */
    val prefix: String,
    /** Masked form for display, e.g. "+91 98•••••21". */
    val mask: String,
    val isInternational: Boolean,
) {
    val hash: String get() = Hashing.numberHash(normalized)
}

/**
 * Normalises raw caller numbers. India (+91) is the home country; anything else with a
 * country code is treated as international.
 */
object PhoneNumbers {
    const val HOME_CC = "91"
    const val HIDDEN_PREFIX = "hidden"

    /** Returns null for empty, hidden or non-numeric input. */
    fun parse(raw: String?): ParsedNumber? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 3) return null

        val international: Boolean
        val normalized: String
        when {
            hasPlus || digits.startsWith("00") -> {
                val d = if (hasPlus) digits else digits.removePrefix("00")
                normalized = "+$d"
                international = !d.startsWith(HOME_CC)
            }
            digits.length == 12 && digits.startsWith(HOME_CC) -> {
                normalized = "+$digits"; international = false
            }
            digits.length == 11 && digits.startsWith("0") -> {
                normalized = "+$HOME_CC${digits.drop(1)}"; international = false
            }
            digits.length == 10 || digits.startsWith("1800") || digits.startsWith("1860") -> {
                normalized = "+$HOME_CC$digits"; international = false
            }
            else -> {
                // Short codes and service numbers are kept as plain digits.
                normalized = digits; international = false
            }
        }
        val prefix = prefixOf(normalized, international)
        val mask = prefix + "•••••" + normalized.takeLast(2)
        return ParsedNumber(normalized, prefix, mask, international)
    }

    private fun prefixOf(normalized: String, international: Boolean): String = when {
        !normalized.startsWith("+") -> normalized.take(2)
        international -> normalized.take(4)
        else -> "+$HOME_CC " + normalized.removePrefix("+$HOME_CC").take(2)
    }
}
