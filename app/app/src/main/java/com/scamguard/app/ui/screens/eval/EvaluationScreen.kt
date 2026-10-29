package com.scamguard.app.ui.screens.eval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scamguard.app.R
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.util.LatencyLog
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

/**
 * Evaluation metrics (PS item 5): accuracy, false positives and false negatives of the voice
 * pipeline on the labelled call transcripts (results.json, written by EvalHarnessTest), and
 * latency measured on this phone during listening.
 */
@Composable
fun EvaluationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val results = remember {
        try {
            JSONObject(context.assets.open("eval/results.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        }
    }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }

    ScreenFrame(stringResource(R.string.eval_title), onBack = onBack) {
        Text(stringResource(R.string.eval_intro), style = MaterialTheme.typography.bodyMedium)

        if (results == null) {
            Text(stringResource(R.string.eval_missing), style = MaterialTheme.typography.bodyMedium)
        } else {
            val d = results.getJSONObject("dataset")
            Text(
                stringResource(R.string.eval_dataset, d.getInt("calls"), d.getInt("scam"), d.getInt("genuine")),
                style = MaterialTheme.typography.titleMedium,
            )
            val m = results.getJSONObject("metrics")
            MetricsCard(stringResource(R.string.eval_red_all), m.getJSONObject("all").getJSONObject("red_flag"))
            MetricsCard(stringResource(R.string.eval_red_test), m.getJSONObject("test").getJSONObject("red_flag"))
            MetricsCard(stringResource(R.string.eval_warn_all), m.getJSONObject("all").getJSONObject("warning"))

            Text(stringResource(R.string.eval_by_type), style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val types = results.getJSONObject("by_scam_type")
                    types.keys().asSequence().sorted().forEach { k ->
                        val t = types.getJSONObject(k)
                        Pair(k.replace('_', ' '), "${pct(t.getDouble("red_flag_recall"))} (n=${t.getInt("n")})").let { (a, b) -> Line(a, b) }
                    }
                }
            }

            val l = results.getJSONObject("latency")
            Text(stringResource(R.string.eval_offline_latency), style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Line(stringResource(R.string.eval_scoring_jvm), "${l.getDouble("scoring_ms_median")} / ${l.getDouble("scoring_ms_p95")} ms")
                    Line(stringResource(R.string.eval_lines_to_red), l.get("lines_to_red_flag_median").toString())
                }
            }
            Text(d.getString("note"), style = MaterialTheme.typography.bodySmall)
        }

        Text(stringResource(R.string.eval_device_latency), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.eval_device_how), style = MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tick.let { } // re-read the log every refresh
                listOf(
                    LatencyLog.Metric.SPOKEN_TO_ALERT to R.string.eval_lat_spoken,
                    LatencyLog.Metric.HEARD_TO_ALERT to R.string.eval_lat_heard,
                    LatencyLog.Metric.STT to R.string.eval_lat_stt,
                    LatencyLog.Metric.SCORING to R.string.eval_lat_scoring,
                ).forEach { (metric, label) ->
                    val s = LatencyLog.stats(metric)
                    Line(
                        stringResource(label),
                        if (s == null) stringResource(R.string.eval_lat_none) else stringResource(R.string.eval_lat_value, s.medianMs, s.p95Ms, s.count),
                    )
                }
            }
        }
        OutlinedButton(onClick = { LatencyLog.clear(); tick++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.eval_lat_clear))
        }
    }
}

@Composable
private fun MetricsCard(title: String, c: JSONObject) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Line(stringResource(R.string.eval_accuracy), pct(c.getDouble("accuracy")))
            Line(stringResource(R.string.eval_precision), pct(c.getDouble("precision")))
            Line(stringResource(R.string.eval_recall), pct(c.getDouble("recall")))
            Line(stringResource(R.string.eval_fpr), pct(c.getDouble("false_positive_rate")))
            Line(stringResource(R.string.eval_fnr), pct(c.getDouble("false_negative_rate")))
            Text(
                stringResource(R.string.eval_confusion, c.getInt("tp"), c.getInt("fp"), c.getInt("tn"), c.getInt("fn")),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun pct(v: Double) = "%.1f%%".format(Locale.ROOT, v * 100)
