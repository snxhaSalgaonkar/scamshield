package com.scamguard.app.ml

/**
 * Kotlin port of ml/scamml/urlfeat.py. Only the registrable domain is used, so the model
 * never sees (and the app never fetches) a page, path or query. [twoLevelSuffixes] comes
 * from the URL model artefact so both sides use the same list.
 */
class UrlFeaturizer(private val twoLevelSuffixes: Set<String>) {
    private val scheme = Regex("^[a-z][a-z0-9+.-]*://")
    private val www = Regex("^www[0-9]*\\.")
    private val ipv4 = Regex("^[0-9]{1,3}(?:\\.[0-9]{1,3}){3}$")
    private val hostChars = Regex("^[a-z0-9.-]+$")

    /** URLs found in a message, as written (lower-cased). Nothing is opened. */
    fun extractUrls(text: String): List<String> =
        TextFeaturizer.urlRegex.findAll(TextFeaturizer.clean(text))
            .map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"') }
            .toList()

    fun hostOf(url: String): String? {
        var s = TextFeaturizer.clean(url).trim()
        s = scheme.replaceFirst(s, "")
        for (sep in charArrayOf('/', '?', '#')) s = s.substringBefore(sep)
        s = s.substringAfterLast('@')
        s = s.substringBefore(':').trim('.')
        s = www.replaceFirst(s, "")
        if (s.isEmpty() || !hostChars.matches(s) || '.' !in s) return null
        return s
    }

    fun registrableDomain(host: String): String {
        if (ipv4.matches(host)) return host
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size >= 3 && labels.takeLast(2).joinToString(".") in twoLevelSuffixes) {
            return labels.takeLast(3).joinToString(".")
        }
        return labels.takeLast(2).joinToString(".")
    }

    fun analyze(url: String): List<String> {
        val host = hostOf(url) ?: return emptyList()
        if (ipv4.matches(host)) return listOf("ip")
        val labels = registrableDomain(host).split('.')
        val name = labels[0]
        val feats = ArrayList<String>()
        feats.add("t:" + labels.last())
        if (labels.size == 3) feats.add("s2:" + labels.drop(1).joinToString("."))
        val padded = "^$name$"
        for (n in 3..5) for (i in 0..padded.length - n) feats.add("c:" + padded.substring(i, i + n))
        feats.add("len:" + bucket(name.length, intArrayOf(4, 7, 10, 14, 20)))
        feats.add("hy:" + minOf(name.count { it == '-' }, 3))
        feats.add("dg:" + bucket(name.count { it in '0'..'9' }, intArrayOf(0, 2, 5)))
        if (name.startsWith("xn--")) feats.add("puny")
        return feats
    }

    private fun bucket(n: Int, edges: IntArray): Int {
        edges.forEachIndexed { i, e -> if (n <= e) return i }
        return edges.size
    }
}
