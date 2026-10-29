package com.scamguard.app.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device latency measurements for the evaluation metrics (PS item 5). Measurement only:
 * nothing here changes detection or alerts.
 *
 * - STT: the speech service's end of an utterance until its transcript arrives.
 * - SCORING: one risk evaluation of the call window.
 * - HEARD_TO_ALERT: a transcript with a new scam phrase arrives until the alert is raised.
 * - SPOKEN_TO_ALERT: the caller finishes the phrase (speech end) until the alert is raised.
 *
 * Only numbers are kept (last [MAX] per metric), in a small file in app-private storage.
 */
object LatencyLog {
    enum class Metric { STT, SCORING, HEARD_TO_ALERT, SPOKEN_TO_ALERT }

    data class Stats(val count: Int, val medianMs: Long, val p95Ms: Long, val lastMs: Long)

    private const val MAX = 200
    private const val FILE = "latency.json"
    /** A voice trigger older than this is not the cause of an alert. */
    private const val TRIGGER_WINDOW_MS = 60_000L

    private val samples = HashMap<Metric, ArrayDeque<Long>>()
    private val pending = HashMap<String, Pair<Long, Long>>()
    private var file: File? = null

    /** Loads earlier measurements; without it the log works in memory only (unit tests). */
    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, FILE)
        file = f
        try {
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            Metric.entries.forEach { m ->
                val arr = o.optJSONArray(m.name) ?: return@forEach
                samples[m] = ArrayDeque(List(arr.length()) { arr.getLong(it) })
            }
        } catch (_: Exception) {
            // Corrupt file: start again.
        }
    }

    @Synchronized
    fun record(metric: Metric, ms: Long) {
        if (ms < 0) return
        val q = samples.getOrPut(metric) { ArrayDeque() }
        q.addLast(ms)
        while (q.size > MAX) q.removeFirst()
        save()
    }

    /** A new scam phrase was heard on [windowId]; [spokenAt] is when the caller finished saying it. */
    @Synchronized
    fun voiceHeard(windowId: String, heardAt: Long, spokenAt: Long) {
        pending[windowId] = heardAt to spokenAt
    }

    /** An alert was raised for [windowId]; times it against the voice phrase that led to it, if any. */
    @Synchronized
    fun alertShown(windowId: String, at: Long) {
        val (heard, spoken) = pending.remove(windowId) ?: return
        if (at - heard > TRIGGER_WINDOW_MS) return
        record(Metric.HEARD_TO_ALERT, at - heard)
        record(Metric.SPOKEN_TO_ALERT, at - spoken)
    }

    @Synchronized
    fun stats(metric: Metric): Stats? {
        val q = samples[metric]?.takeIf { it.isNotEmpty() } ?: return null
        val sorted = q.sorted()
        fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
        return Stats(sorted.size, pct(0.5), pct(0.95), q.last())
    }

    @Synchronized
    fun clear() {
        samples.clear()
        pending.clear()
        save()
    }

    private fun save() {
        val f = file ?: return
        try {
            val o = JSONObject()
            samples.forEach { (m, q) -> o.put(m.name, JSONArray(q.toList())) }
            f.writeText(o.toString())
        } catch (_: Exception) {
            // Storage full or unavailable: keep measuring in memory.
        }
    }
}
