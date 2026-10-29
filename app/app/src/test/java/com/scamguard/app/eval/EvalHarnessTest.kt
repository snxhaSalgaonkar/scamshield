package com.scamguard.app.eval

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.collectors.SpeechText
import com.scamguard.app.engine.CallRiskScorer
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.RiskConfig
import com.scamguard.app.engine.RiskInput
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.engine.T0
import com.scamguard.app.engine.WindowTracker
import com.scamguard.app.ml.shippedModels
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneOffset
import java.util.Locale

/**
 * Evaluation of the voice pipeline (PS item 5): every labelled call transcript in
 * `eval/transcripts.jsonl` is played as an unknown incoming call through the same code the
 * app runs on a live call (SpeechText matcher -> VoiceKeyword events -> WindowTracker ->
 * CallRiskScorer with the shipped models). Nothing is tuned here; it only measures.
 *
 * A call counts as flagged at a level if it ever reached that level during the call, because
 * the app alerts on the first escalation. Positive = scam. Writes results.json and RESULTS.md
 * to build/eval/, RESULTS.md to eval/, and results.json to the app's assets for the in-app
 * Evaluation screen.
 */
class EvalHarnessTest {
    private val config = RiskConfig()
    private val scorer = CallRiskScorer(shippedModels())

    private data class Item(val id: String, val split: String, val scam: Boolean, val type: String, val lang: String, val lines: List<String>)

    private data class Outcome(
        val item: Item,
        val peakScore: Int,
        val peakLevel: RiskLevel,
        /** 1-based line after which the call first reached warning / red flag; null if never. */
        val warnLine: Int?,
        val redLine: Int?,
        val scoringNanos: List<Long>,
    ) {
        val warned get() = peakLevel >= RiskLevel.WARNING
        val red get() = peakLevel == RiskLevel.RED_FLAG
    }

    private fun load(): List<Item> {
        val text = requireNotNull(javaClass.getResource("/eval/transcripts.jsonl")) { "missing eval/transcripts.jsonl" }.readText()
        return text.lines().filter { it.isNotBlank() }.map { row ->
            val o = JSONObject(row)
            val lines = o.getJSONArray("lines")
            Item(
                o.getString("id"), o.getString("split"), o.getString("label") == "scam", o.getString("type"),
                o.getString("lang"), List(lines.length()) { lines.getString(it) },
            )
        }
    }

    /** One unknown incoming call: rings, is answered, then each line is heard 30 s apart. */
    private fun play(item: Item, n: Int): Outcome {
        val tracker = WindowTracker { item.id }
        val ring = SignalEvent.CallRinging(
            T0, "eval-hash-$n", "+91 90", isUnknown = true, isInternational = false, isHidden = false, mask = "+91 90•••••00",
        )
        val steps = mutableListOf<Pair<Int, SignalEvent>>(0 to ring, 0 to SignalEvent.CallActive(T0 + 5_000))
        item.lines.forEachIndexed { i, line ->
            val ts = T0 + 20_000 + i * 30_000L
            SpeechText.events(ts, line).forEach { steps += (i + 1) to it }
        }
        var peak: Decision? = null
        var warnLine: Int? = null
        var redLine: Int? = null
        val nanos = mutableListOf<Long>()
        for ((line, e) in steps) {
            val w = tracker.onEvent(e, config) ?: continue
            val start = System.nanoTime()
            val d = scorer.evaluate(RiskInput(w, voiceLayerEnabled = true, now = e.ts, zone = ZoneOffset.UTC), config)
            nanos += System.nanoTime() - start
            if (peak == null || d.level > peak.level || (d.level == peak.level && d.score > peak.score)) peak = d
            if (d.level >= RiskLevel.WARNING && warnLine == null) warnLine = line
            if (d.level == RiskLevel.RED_FLAG && redLine == null) redLine = line
        }
        return Outcome(item, peak?.score ?: 0, peak?.level ?: RiskLevel.SAFE, warnLine, redLine, nanos)
    }

    private class Confusion(val tp: Int, val fp: Int, val tn: Int, val fn: Int) {
        val n get() = tp + fp + tn + fn
        fun ratio(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b
        val accuracy get() = ratio(tp + tn, n)
        val precision get() = ratio(tp, tp + fp)
        val recall get() = ratio(tp, tp + fn)
        val f1 get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        val fpr get() = ratio(fp, fp + tn)
        val fnr get() = ratio(fn, fn + tp)

        fun json() = JSONObject()
            .put("n", n).put("tp", tp).put("fp", fp).put("tn", tn).put("fn", fn)
            .put("accuracy", r(accuracy)).put("precision", r(precision)).put("recall", r(recall)).put("f1", r(f1))
            .put("false_positive_rate", r(fpr)).put("false_negative_rate", r(fnr))

        companion object {
            fun of(outcomes: List<Outcome>, flagged: (Outcome) -> Boolean) = Confusion(
                tp = outcomes.count { it.item.scam && flagged(it) },
                fp = outcomes.count { !it.item.scam && flagged(it) },
                tn = outcomes.count { !it.item.scam && !flagged(it) },
                fn = outcomes.count { it.item.scam && !flagged(it) },
            )
        }
    }

    @Test fun `voice pipeline evaluation on labelled call transcripts`() {
        val items = load()
        assertTrue("dataset should have at least 100 calls", items.size >= 100)
        assertTrue(items.any { it.scam } && items.any { !it.scam })

        repeat(3) { play(items[it], it) } // warm-up so JIT start-up does not count as scoring time
        val outcomes = items.mapIndexed { i, it -> play(it, i) }

        val red: (Outcome) -> Boolean = { it.red }
        val warn: (Outcome) -> Boolean = { it.warned }
        val splits = linkedMapOf("all" to outcomes, "validation" to outcomes.filter { it.item.split == "validation" }, "test" to outcomes.filter { it.item.split == "test" })

        val ms = outcomes.flatMap { it.scoringNanos }.map { it / 1_000_000.0 }.sorted()
        val redLines = outcomes.filter { it.item.scam }.mapNotNull { it.redLine }

        val json = JSONObject()
            .put("generated_by", "EvalHarnessTest (app/src/test/java/com/scamguard/app/eval)")
            .put("dataset", JSONObject()
                .put("calls", items.size)
                .put("scam", items.count { it.scam }).put("genuine", items.count { !it.scam })
                .put("by_language", JSONObject(items.groupingBy { it.lang }.eachCount()))
                .put("note", "Team-written synthetic transcripts (English, Hinglish, Hindi), text only: speech-to-text errors are not included."))
            .put("thresholds", JSONObject().put("red_flag", "level RED_FLAG (score >= ${config.redFlagThreshold}, never on one signal)").put("warning", "level WARNING or above (score >= ${config.warningThreshold})"))
        val metrics = JSONObject()
        splits.forEach { (name, list) ->
            metrics.put(name, JSONObject().put("red_flag", Confusion.of(list, red).json()).put("warning", Confusion.of(list, warn).json()))
        }
        json.put("metrics", metrics)
        json.put("by_scam_type", JSONObject().also { o ->
            outcomes.filter { it.item.scam }.groupBy { it.item.type }.toSortedMap().forEach { (type, list) ->
                o.put(type, JSONObject().put("n", list.size).put("red_flag_recall", r(list.count(red).toDouble() / list.size)).put("warning_recall", r(list.count(warn).toDouble() / list.size)))
            }
        })
        json.put("by_genuine_type", JSONObject().also { o ->
            outcomes.filter { !it.item.scam }.groupBy { it.item.type }.toSortedMap().forEach { (type, list) ->
                o.put(type, JSONObject().put("n", list.size).put("red_flag_false_positives", list.count(red)).put("warning_false_positives", list.count(warn)))
            }
        })
        json.put("by_language", JSONObject().also { o ->
            outcomes.groupBy { it.item.lang }.toSortedMap().forEach { (lang, list) ->
                o.put(lang, JSONObject().put("red_flag", Confusion.of(list, red).json()).put("warning", Confusion.of(list, warn).json()))
            }
        })
        json.put("latency", JSONObject()
            .put("scoring_ms_median", r(pct(ms, 0.5))).put("scoring_ms_p95", r(pct(ms, 0.95))).put("scoring_ms_max", r(ms.last()))
            .put("scoring_runs", ms.size)
            .put("scoring_note", "Text -> risk decision on the JVM (laptop). On-device speech-to-text and phrase-to-alert times are measured by the app (Settings -> Evaluation and metrics).")
            .put("lines_to_red_flag_median", pct(redLines.map { it.toDouble() }.sorted(), 0.5))
            .put("lines_note", "How many spoken lines (about 30 s apart) a scam call needed before the red flag."))
        json.put("errors", JSONObject()
            .put("missed_scams_at_red_flag", JSONArray(outcomes.filter { it.item.scam && !it.red }.map { "${it.item.id} (${it.item.type}, peak ${it.peakScore})" }))
            .put("false_alarms_at_warning", JSONArray(outcomes.filter { !it.item.scam && it.warned }.map { "${it.item.id} (${it.item.type}, peak ${it.peakScore})" }))
            .put("false_alarms_at_red_flag", JSONArray(outcomes.filter { !it.item.scam && it.red }.map { "${it.item.id} (${it.item.type}, peak ${it.peakScore})" })))

        val markdown = markdown(json)
        File("build/eval").apply { mkdirs() }.let { dir ->
            File(dir, "results.json").writeText(json.toString(2))
            File(dir, "RESULTS.md").writeText(markdown)
        }
        File("eval").apply { mkdirs() }.resolve("RESULTS.md").writeText(markdown)
        File("src/main/assets/eval").apply { mkdirs() }.resolve("results.json").writeText(json.toString(2))
        println(markdown)
    }

    private fun markdown(j: JSONObject): String = buildString {
        val d = j.getJSONObject("dataset")
        appendLine("# Voice pipeline evaluation (PS item 5)")
        appendLine()
        appendLine("Generated by `EvalHarnessTest`. Re-run: `./gradlew :app:testDebugUnitTest --tests '*EvalHarnessTest*'`.")
        appendLine()
        appendLine("- Dataset: ${d.getInt("calls")} labelled call transcripts (${d.getInt("scam")} scam, ${d.getInt("genuine")} genuine), languages ${d.getJSONObject("by_language")}.")
        appendLine("- Each transcript is played as an unknown incoming call through the app's real detection code (phrase matcher, script-stage rules, on-device call model). Nothing was tuned on this data.")
        appendLine("- ${d.getString("note")}")
        appendLine()
        appendLine("## Accuracy, false positives, false negatives")
        appendLine()
        appendLine("| Split | Threshold | n | TP | FP | TN | FN | Accuracy | Precision | Recall | FPR | FNR |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|")
        val m = j.getJSONObject("metrics")
        for (split in listOf("all", "validation", "test")) for (t in listOf("red_flag", "warning")) {
            val c = m.getJSONObject(split).getJSONObject(t)
            appendLine("| $split | ${t.replace('_', ' ')} | ${c.getInt("n")} | ${c.getInt("tp")} | ${c.getInt("fp")} | ${c.getInt("tn")} | ${c.getInt("fn")} | ${p(c, "accuracy")} | ${p(c, "precision")} | ${p(c, "recall")} | ${p(c, "false_positive_rate")} | ${p(c, "false_negative_rate")} |")
        }
        appendLine()
        appendLine("## Recall by scam type")
        appendLine()
        appendLine("| Scam type | n | Red-flag recall | Warning recall |")
        appendLine("|---|---|---|---|")
        j.getJSONObject("by_scam_type").let { o -> o.keys().asSequence().sorted().forEach { k -> val v = o.getJSONObject(k); appendLine("| $k | ${v.getInt("n")} | ${p(v, "red_flag_recall")} | ${p(v, "warning_recall")} |") } }
        appendLine()
        appendLine("## False alarms by genuine-call type")
        appendLine()
        appendLine("| Genuine type | n | Red-flag false positives | Warning false positives |")
        appendLine("|---|---|---|---|")
        j.getJSONObject("by_genuine_type").let { o -> o.keys().asSequence().sorted().forEach { k -> val v = o.getJSONObject(k); appendLine("| $k | ${v.getInt("n")} | ${v.getInt("red_flag_false_positives")} | ${v.getInt("warning_false_positives")} |") } }
        appendLine()
        appendLine("## By language (red flag)")
        appendLine()
        appendLine("| Language | n | Accuracy | Recall | FPR | FNR |")
        appendLine("|---|---|---|---|---|---|")
        j.getJSONObject("by_language").let { o -> o.keys().asSequence().sorted().forEach { k -> val c = o.getJSONObject(k).getJSONObject("red_flag"); appendLine("| $k | ${c.getInt("n")} | ${p(c, "accuracy")} | ${p(c, "recall")} | ${p(c, "false_positive_rate")} | ${p(c, "false_negative_rate")} |") } }
        appendLine()
        val l = j.getJSONObject("latency")
        appendLine("## Latency")
        appendLine()
        appendLine("- Scoring (text -> risk decision): median ${l.getDouble("scoring_ms_median")} ms, p95 ${l.getDouble("scoring_ms_p95")} ms over ${l.getInt("scoring_runs")} runs. ${l.getString("scoring_note")}")
        appendLine("- Lines spoken before a scam reached the red flag (median): ${l.get("lines_to_red_flag_median")}. ${l.getString("lines_note")}")
        appendLine()
        appendLine("## Errors")
        appendLine()
        val e = j.getJSONObject("errors")
        appendLine("- Scams missed at the red flag: ${e.getJSONArray("missed_scams_at_red_flag").join(", ").replace("\"", "").ifEmpty { "none" }}")
        appendLine("- Genuine calls warned: ${e.getJSONArray("false_alarms_at_warning").join(", ").replace("\"", "").ifEmpty { "none" }}")
        appendLine("- Genuine calls red-flagged: ${e.getJSONArray("false_alarms_at_red_flag").join(", ").replace("\"", "").ifEmpty { "none" }}")
        appendLine()
        appendLine("Limits: small team-written dataset; text only (no speech-to-text errors); single-call context with no SMS, reputation or app signals, so scams that rely on those (for example wrong-number OTP theft) are under-detected here. Real-call accuracy will differ.")
    }

    private fun p(o: JSONObject, key: String) = "%.1f%%".format(Locale.ROOT, o.getDouble(key) * 100)

    private companion object {
        fun r(v: Double) = Math.round(v * 1000) / 1000.0
        fun pct(sorted: List<Double>, q: Double): Double =
            if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * q).toInt().coerceIn(0, sorted.size - 1)]
    }
}
