package com.scamguard.app.collectors

import com.scamguard.app.util.Hashing

/**
 * What an SMS tells us, with every value already reduced to something safe to keep:
 * no OTP digits, no URL, no payee name (only its hash).
 */
data class SmsClassification(
    val isOtp: Boolean = false,
    val isDebit: Boolean = false,
    /** Debited amount in paise, or null when the message does not state one. */
    val amountPaise: Long? = null,
    val payeeHash: String? = null,
    val balancePaise: Long? = null,
    val hasLink: Boolean = false,
) {
    val isRelevant: Boolean get() = isOtp || isDebit || hasLink
}

/**
 * Deterministic keyword parser for bank, OTP and link messages. The text is only read in
 * memory; nothing derived from it except the [SmsClassification] leaves this object, and
 * a parsing error returns an empty classification rather than an exception carrying text.
 */
object SmsParser {
    private val otpKeyword = Regex("""\botp\b|one[\s-]?time[\s-]?pass(word|code)|verification code|security code""")
    private val otpCode = Regex("""(?<![\d.,])\d{4,8}(?![\d,]|\.\d)""")

    private val debitVerb = Regex("""\b(debited|withdrawn|spent|deducted|sent|paid)\b""")
    private val futureDebit = Regex("""\b(will be|to be|is being) (debited|deducted)\b""")
    private val moneyMarker = Regex("""\brs\.?|\binr\b|₹|\ba/c\b|\bacct?\b|\bupi\b|avl\.? ?bal""")

    private const val NUM = """(\d{1,3}(?:,\d{2,3})+|\d{1,13})(?:\.(\d{1,2}))?"""
    private val currencyAmount = Regex("""(?:rs\.?|inr|₹)\s*$NUM""")
    private val balance = Regex("""(?:avl\.?\s*bal(?:ance)?|available\s+bal(?:ance)?|\bbal(?:ance)?)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)?\s*$NUM""")

    private val vpa = Regex("""\b[a-z0-9._-]{2,}@[a-z]{2,}\b""")
    private val payeeAfter = Regex("""\b(?:to|at|towards|trf to|info:?)\s+([a-z][a-z0-9 ._&-]{2,30}?)(?=\s+(?:on|ref|via|upi|avl|for)\b|[.,;]|$)""")

    private val link = Regex("""https?://|\bwww\.|\b[a-z0-9-]{2,}\.(?:com|in|ly|xyz|top|link|info|net|org|co|me|app|site|online|click)\b(?:/\S*)?""")

    /** Classifies [body]. The caller must drop [body] right after this returns. */
    fun classify(body: String): SmsClassification = try {
        parse(body.lowercase().replace('\n', ' '))
    } catch (_: RuntimeException) {
        SmsClassification()
    }

    private fun parse(text: String): SmsClassification {
        val isOtp = otpKeyword.containsMatchIn(text) && otpCode.containsMatchIn(text)
        val isDebit = debitVerb.containsMatchIn(text) && !futureDebit.containsMatchIn(text) &&
            moneyMarker.containsMatchIn(text) && !(isOtp && !text.contains("debited"))
        val hasLink = link.containsMatchIn(text.replace(vpa, " "))
        if (!isDebit) return SmsClassification(isOtp = isOtp, hasLink = hasLink)

        val balanceMatch = balance.find(text)
        val amount = currencyAmount.findAll(text)
            .firstOrNull { m -> balanceMatch == null || m.range.first !in balanceMatch.range }
            ?.let { toPaise(it.groupValues[1], it.groupValues[2]) }
        return SmsClassification(
            isOtp = isOtp,
            isDebit = true,
            amountPaise = amount,
            payeeHash = payee(text)?.let(Hashing::payeeHash),
            balancePaise = balanceMatch?.let { toPaise(it.groupValues[1], it.groupValues[2]) },
            hasLink = hasLink,
        )
    }

    private fun payee(text: String): String? =
        vpa.find(text)?.value ?: payeeAfter.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.any(Char::isLetter) }

    /** "1,23,456" + "5" -> 12345650 paise. Null if the number does not fit. */
    internal fun toPaise(whole: String, fraction: String): Long? {
        val rupees = whole.replace(",", "").toLongOrNull() ?: return null
        val paise = fraction.padEnd(2, '0').ifEmpty { "00" }.toLong()
        if (rupees > Long.MAX_VALUE / 100 - 1) return null
        return rupees * 100 + paise
    }
}
