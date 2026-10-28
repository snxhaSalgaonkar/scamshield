package com.scamguard.app.ml

import java.text.Normalizer

/**
 * Kotlin port of ml/scamml/textfeat.py; both must produce identical features, which the
 * golden-vector tests check. Regexes use explicit ASCII classes only, so java.util.regex
 * (unit tests) and Android's ICU engine behave the same.
 */
object TextFeaturizer {
    const val URL_TOKEN = "zurlz"
    const val EMAIL_TOKEN = "zemailz"
    const val PHONE_TOKEN = "zphonez"
    const val MONEY_TOKEN = "zmoneyz"
    const val NUM_TOKEN = "znumz"

    private const val WHITESPACE =
        "\t\n\r\u000b\u000c\u001c\u001d\u001e\u001f\u0085        " +
            "         　"
    private val whitespaceSet = WHITESPACE.toSet()

    private const val TLDS = "com|in|net|org|info|xyz|top|ly|co|me|io|app|site|online|click|link|live|tk|ml|ga|cf|gq|" +
        "biz|us|uk|cc|ru|cn|shop|store|vip|icu|buzz|club|pw|win|bid|loan|work|gov|edu|bank|money"

    val urlRegex = Regex(
        "(?:[a-z][a-z0-9+.-]*://|www\\.)[^ ]+" +
            "|(?<![a-z0-9@._-])[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.(?:$TLDS)(?![a-z0-9-])(?:/[^ ]*)?"
    )
    private val emailRegex = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
    private val moneyRegex = Regex("(?:rs\\.?|inr|usd|eur|gbp|₹|£|\\$|€) ?[0-9][0-9,]*(?:\\.[0-9]+)?")
    private val phoneRegex = Regex("\\+?[0-9][0-9 -]{7,}[0-9]")
    private val numRegex = Regex("[0-9]+")

    private const val CHAR_MIN = 3
    private const val CHAR_MAX = 5
    private const val MAX_TOKEN_CHARS = 30

    /** NFKC, lower-case and the fixed whitespace set mapped to spaces. */
    fun clean(text: String): String {
        val lower = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        val sb = StringBuilder(lower.length)
        for (ch in lower) sb.append(if (ch in whitespaceSet) ' ' else ch)
        return sb.toString()
    }

    fun normalize(text: String): String {
        var t = clean(text)
        t = urlRegex.replace(t, " $URL_TOKEN ")
        t = emailRegex.replace(t, " $EMAIL_TOKEN ")
        t = moneyRegex.replace(t, " $MONEY_TOKEN ")
        t = phoneRegex.replace(t, " $PHONE_TOKEN ")
        t = numRegex.replace(t, " $NUM_TOKEN ")
        return t
    }

    /** Runs of code points in Unicode categories L*, N* or M*. */
    fun tokens(normalized: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < normalized.length) {
            val cp = normalized.codePointAt(i)
            if (isTokenChar(cp)) {
                cur.appendCodePoint(cp)
            } else if (cur.isNotEmpty()) {
                out.add(cur.toString()); cur.setLength(0)
            }
            i += Character.charCount(cp)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    fun isTokenChar(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
        else -> false
    }

    /** All features of [text], with repeats. */
    fun analyze(text: String): List<String> {
        val toks = tokens(normalize(text))
        val feats = ArrayList<String>(toks.size * 16)
        toks.forEach { feats.add("w:$it") }
        for (i in 0 until toks.size - 1) feats.add("b:${toks[i]} ${toks[i + 1]}")
        toks.forEach { charNgrams(it, feats) }
        return feats
    }

    private fun charNgrams(token: String, into: MutableList<String>) {
        val cps = token.codePoints().limit(MAX_TOKEN_CHARS.toLong()).toArray()
        val padded = IntArray(cps.size + 2)
        padded[0] = ' '.code
        cps.copyInto(padded, 1)
        padded[padded.size - 1] = ' '.code
        for (n in CHAR_MIN..CHAR_MAX) {
            for (i in 0..padded.size - n) {
                val sb = StringBuilder("c:")
                for (j in i until i + n) sb.appendCodePoint(padded[j])
                into.add(sb.toString())
            }
        }
    }
}
