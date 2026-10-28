package com.scamguard.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.scamguard.app.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Short local date and time. */
fun formatTime(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

/** Clock time only. */
fun formatClock(ts: Long): String = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(ts))

/** Rupees from paise, e.g. "₹24,999.00". */
fun formatRupees(paise: Long): String =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).format(paise / 100.0)

/** The masked number, or "Number not available" for hidden callers and calls seen without the screening role. */
@Composable
fun maskOrUnknown(mask: String): String =
    mask.ifEmpty { stringResource(R.string.number_unavailable) }
