package com.scamguard.app.collectors

import com.scamguard.app.bus.ScreenCategory

/**
 * Recognises the official-looking words digital-arrest scammers show or use: agency names,
 * legal threats and "verification" payment demands. Pure Kotlin so it is unit tested.
 * Works on text held in memory only; callers keep the categories and drop the text.
 */
object AuthorityText {

    private val TERMS: Map<ScreenCategory, List<String>> = mapOf(
        ScreenCategory.LAW_ENFORCEMENT to listOf(
            "cbi", "central bureau of investigation", "enforcement directorate", "directorate of enforcement",
            "police", "cyber cell", "cyber crime", "cybercrime", "crime branch", "ncb", "narcotics control bureau",
            "narcotics", "customs", "trai", "telecom regulatory", "reserve bank of india", "income tax department",
            "interpol", "nia", "national investigation agency", "dcp", "ips", "commissioner of police",
            "sub inspector", "inspector", "ministry of home affairs", "pulis", "पुलिस", "सीबीआई",
        ),
        ScreenCategory.LEGAL_THREAT to listOf(
            "arrest warrant", "warrant", "digital arrest", "under arrest", "arrest", "fir", "money laundering",
            "pmla", "supreme court", "high court", "court order", "summons", "non bailable", "non-bailable",
            "accused", "case no", "case number", "seized", "illegal parcel", "drugs", "confidential",
            "do not disclose", "secrecy", "official secrets act", "aadhaar misuse", "गिरफ्तार", "वारंट",
        ),
        ScreenCategory.PAYMENT_DEMAND to listOf(
            "verification account", "verification amount", "safe account", "secure account", "rbi account",
            "refundable", "security deposit", "bail amount", "clearance amount", "fund verification",
            "transfer the amount", "ifsc",
        ),
    )

    /** Words that only matter in a caller's display name ("Officer Sharma", "Govt Dept"). */
    private val NAME_TERMS = listOf("officer", "govt", "government", "court", "department", "dept", "ministry", "bureau")

    private val PATTERNS: Map<ScreenCategory, List<Regex>> =
        TERMS.mapValues { (_, terms) -> terms.map(::wordPattern) }
    private val NAME_PATTERNS = (TERMS.getValue(ScreenCategory.LAW_ENFORCEMENT) + NAME_TERMS).map(::wordPattern)

    /**
     * Categories found in [text]. Nothing is reported unless at least [minTerms] distinct
     * terms match, so a single stray word ("police" in a news ticker) is not evidence.
     */
    fun categories(text: String, minTerms: Int = 2): Set<ScreenCategory> {
        if (text.isBlank()) return emptySet()
        val norm = normalize(text)
        val found = PATTERNS.mapValues { (_, ps) -> ps.count { it.containsMatchIn(norm) } }.filterValues { it > 0 }
        return if (found.values.sum() >= minTerms) found.keys else emptySet()
    }

    /** True when a caller's display name claims an official role. */
    fun isAuthorityName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val norm = normalize(name)
        return NAME_PATTERNS.any { it.containsMatchIn(norm) }
    }

    private fun normalize(text: String) = text.lowercase().replace(Regex("[\\s_]+"), " ")

    // \b does not work for Devanagari, so use explicit "not a letter or digit" lookarounds.
    private fun wordPattern(term: String) = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(term) + "(?![\\p{L}\\p{N}])")
}
