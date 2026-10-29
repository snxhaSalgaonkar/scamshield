package com.scamguard.app.collectors

/**
 * Finds what to highlight in a call transcript, a message or text read from an image:
 * scam phrases (digital arrest, CBI, OTP, KYC, "transfer the amount"...) and sensitive data
 * that must never be shared (card numbers, OTP codes, CVV, PIN, Aadhaar, PAN, links). Display
 * only; it decides nothing. Pure Kotlin so it is unit tested.
 */
object ScamHighlights {

    enum class Kind { SCAM_PHRASE, SENSITIVE }

    data class Span(val start: Int, val end: Int, val kind: Kind)

    private const val NOT_WORD_BEFORE = "(?<![\\p{L}\\p{N}])"
    private const val NOT_WORD_AFTER = "(?![\\p{L}\\p{N}])"

    private val PHRASES = listOf(
        // Digital arrest and fake officials.
        "digital arrest", "digitally arrested", "under arrest", "arrest warrant", "arrest", "warrant",
        "cbi", "central bureau of investigation", "police", "cyber ?cell", "cyber ?crime", "crime branch",
        "enforcement directorate", "narcotics", "ncb", "customs", "trai", "interpol", "nia", "rbi",
        "reserve bank of india", "income tax", "court order", "supreme court", "high court", "summons",
        "fir", "money laundering", "pmla", "non[- ]?bailable", "illegal parcel", "drugs", "seized",
        "do not disclose", "don'?t tell anyone", "keep (it|this) (secret|confidential)", "confidential", "secrecy",
        "aadhaar misuse", "video call verification",
        // Credential theft.
        "otp", "one[- ]time password", "verification code", "cvv", "upi pin", "atm pin", "pin",
        "password", "card number", "card details", "expiry date", "net ?banking", "login details",
        "kyc", "re-?kyc", "pan card", "aadhaar", "account (will be )?(blocked|suspended|frozen|deactivated)",
        "blocked", "suspended", "expired", "update (your )?(kyc|pan|details)", "verify (your )?account",
        // Money pressure.
        "transfer the amount", "transfer (the )?money", "safe account", "secure account", "verification (account|amount)",
        "security deposit", "refundable", "bail amount", "processing fee", "pay now", "urgent(ly)?", "immediately",
        "within \\d+ (minutes|hours)", "penalty", "refund", "cashback", "lottery", "you (have )?won", "prize",
        // Remote access.
        "anydesk", "teamviewer", "quick ?support", "rustdesk", "screen ?shar(e|ing)", "share (your )?screen",
        "install (this|the) app", "apk",
        // Hinglish / Hindi.
        "otp (batao|bataiye|bhejo|share karo)", "giraftar", "warrant (jaari|jari)", "पुलिस", "सीबीआई", "गिरफ्तार",
        "वारंट", "ओटीपी", "डिजिटल अरेस्ट",
    ).map { Regex(NOT_WORD_BEFORE + it + NOT_WORD_AFTER, RegexOption.IGNORE_CASE) }

    private val CARD = Regex("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)")
    private val AADHAAR = Regex("(?<!\\d)[2-9]\\d{3}[ -]?\\d{4}[ -]?\\d{4}(?!\\d)")
    private val PAN = Regex("(?<![A-Za-z0-9])[A-Z]{5}\\d{4}[A-Z](?![A-Za-z0-9])")
    private val CODE_AFTER_KEYWORD = Regex(
        "(?:otp|code|cvv|pin|password)\\D{0,25}?(?<!\\d)(\\d{3,8})(?!\\d)", RegexOption.IGNORE_CASE,
    )
    private val CODE_BEFORE_KEYWORD = Regex(
        "(?<!\\d)(\\d{4,8})(?!\\d)\\s*(?:is|are|hai)?\\s*(?:your|the|aapka)?\\s*(?:otp|code|verification code|one[- ]time password)",
        RegexOption.IGNORE_CASE,
    )
    private val LINK = Regex("(?:https?://|www\\.)\\S+|(?<![@\\w])[a-z0-9-]+\\.(?:top|xyz|live|click|link|info|online|site|shop|in|com)(?:/\\S*)?(?!\\w)", RegexOption.IGNORE_CASE)

    fun find(text: String): List<Span> {
        if (text.isBlank()) return emptyList()
        val sensitive = buildList {
            CARD.findAll(text).filter { luhn(it.value) }.forEach { add(Span(it.range.first, it.range.last + 1, Kind.SENSITIVE)) }
            AADHAAR.findAll(text).forEach { add(Span(it.range.first, it.range.last + 1, Kind.SENSITIVE)) }
            PAN.findAll(text).forEach { add(Span(it.range.first, it.range.last + 1, Kind.SENSITIVE)) }
            for (re in listOf(CODE_AFTER_KEYWORD, CODE_BEFORE_KEYWORD)) {
                re.findAll(text).forEach { m -> m.groups[1]?.range?.let { add(Span(it.first, it.last + 1, Kind.SENSITIVE)) } }
            }
            LINK.findAll(text).forEach { add(Span(it.range.first, it.range.last + 1, Kind.SENSITIVE)) }
        }
        val phrases = PHRASES.flatMap { re -> re.findAll(text).map { Span(it.range.first, it.range.last + 1, Kind.SCAM_PHRASE) } }
        // Sensitive data wins over a phrase it overlaps; longer spans win over shorter ones.
        val ordered = (sensitive + phrases).sortedWith(
            compareBy<Span> { it.kind != Kind.SENSITIVE }.thenByDescending { it.end - it.start },
        )
        val taken = mutableListOf<Span>()
        for (s in ordered) if (taken.none { it.start < s.end && s.start < it.end }) taken += s
        return taken.sortedBy { it.start }
    }

    /** Card numbers pass the Luhn check; random long numbers (order IDs, phone pairs) mostly do not. */
    internal fun luhn(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }
}
