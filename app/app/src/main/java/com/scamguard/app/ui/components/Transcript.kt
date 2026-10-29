package com.scamguard.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.scamguard.app.collectors.ScamHighlights
import com.scamguard.app.collectors.SpeechText
import com.scamguard.app.engine.RiskLevel

/**
 * The text with scam phrases (digital arrest, CBI, OTP, KYC...) in bold red and sensitive data
 * (OTP codes, card numbers, CVV, Aadhaar, PAN, links) in bold white on red. Used for call
 * transcripts, messages and text read from images.
 */
@Composable
fun highlightScamPhrases(text: String): AnnotatedString {
    val red = levelColor(RiskLevel.RED_FLAG)
    val phrase = SpanStyle(color = red, fontWeight = FontWeight.Bold)
    val sensitive = SpanStyle(color = Color.White, background = red, fontWeight = FontWeight.Bold)

    val spans = ScamHighlights.find(text).toMutableList()
    // Every occurrence of the phrases the call rules matched, for the ones the list above lacks.
    SpeechText.matches(text).forEach { m ->
        var i = text.indexOf(m.phrase, ignoreCase = true)
        while (i >= 0) {
            val end = i + m.phrase.length
            if (spans.none { it.start < end && i < it.end }) spans += ScamHighlights.Span(i, end, ScamHighlights.Kind.SCAM_PHRASE)
            i = text.indexOf(m.phrase, end, ignoreCase = true)
        }
    }
    return buildAnnotatedString {
        var at = 0
        for (s in spans.sortedBy { it.start }) {
            if (s.start < at) continue
            append(text.substring(at, s.start))
            withStyle(if (s.kind == ScamHighlights.Kind.SENSITIVE) sensitive else phrase) { append(text.substring(s.start, s.end)) }
            at = s.end
        }
        append(text.substring(at))
    }
}
