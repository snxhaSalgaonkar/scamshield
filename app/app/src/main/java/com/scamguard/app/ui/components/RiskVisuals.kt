package com.scamguard.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scamguard.app.R
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.ui.Labels
import com.scamguard.app.ui.theme.AlertRed
import com.scamguard.app.ui.theme.SafeGreen
import com.scamguard.app.ui.theme.WarningAmber

/** Colour for a risk level. Red is used only for red flags. */
@Composable
fun levelColor(level: RiskLevel): Color = when (level) {
    RiskLevel.SAFE -> SafeGreen
    RiskLevel.WARNING -> WarningAmber
    RiskLevel.RED_FLAG -> AlertRed
}

/** A 270-degree gauge showing the 0-100 score. */
@Composable
fun ScoreGauge(score: Int, level: RiskLevel, size: Dp = 160.dp) {
    val color = levelColor(level)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val label = stringResource(R.string.cd_score, score, stringResource(Labels.level(level)))
    Box(Modifier.size(size).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            drawArc(color, startAngle = 135f, sweepAngle = 270f * score.coerceIn(0, 100) / 100f, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.displaySmall, color = color)
            Text(stringResource(R.string.score_out_of), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** One labelled bar per evidence layer. */
@Composable
fun LayerBars(l1: Int, l2: Int, l3: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LayerBar(stringResource(R.string.layer_l1), l1)
        LayerBar(stringResource(R.string.layer_l2), l2)
        LayerBar(stringResource(R.string.layer_l3), l3)
    }
}

@Composable
private fun LayerBar(label: String, value: Int) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** A small coloured pill naming the level. */
@Composable
fun LevelChip(level: RiskLevel) {
    Surface(color = levelColor(level), shape = RoundedCornerShape(50)) {
        Text(
            stringResource(Labels.level(level)),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** A neutral pill, e.g. for "Simulated". */
@Composable
fun TagChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Fixed-width spacer used between chips. */
@Composable
fun ChipGap() = androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
