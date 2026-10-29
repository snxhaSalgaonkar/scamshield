package com.scamguard.app.remote

import com.scamguard.app.util.ParsedNumber

/**
 * The normalised numbers of recent callers, in memory only, so a flagged call can be reported
 * to the authority server with the caller's number (the server needs the real number to flag it
 * across devices). Nothing is stored on disk; entries are dropped after [TTL_MS] or when more than
 * [MAX] callers are held. Does nothing unless [enabled] (set only when a server is configured).
 */
object CallerNumbers {
    @Volatile var enabled: Boolean = false

    private const val MAX = 20
    private const val TTL_MS = 2 * 60 * 60_000L

    private val byHash = LinkedHashMap<String, Pair<String, Long>>()

    /** Remembers the caller's number under its hash. */
    @Synchronized
    fun remember(parsed: ParsedNumber?, now: Long = System.currentTimeMillis()) {
        if (!enabled || parsed == null) return
        val hash = parsed.hash
        byHash.remove(hash)
        byHash[hash] = parsed.normalized to now
        prune(now)
    }

    /** The normalised number for [hash], if it rang recently. */
    @Synchronized
    fun lookup(hash: String?, now: Long = System.currentTimeMillis()): String? {
        prune(now)
        return hash?.let { byHash[it]?.first }
    }

    @Synchronized
    fun clear() = byHash.clear()

    private fun prune(now: Long) {
        byHash.entries.removeAll { now - it.value.second > TTL_MS }
        while (byHash.size > MAX) byHash.remove(byHash.keys.first())
    }
}
