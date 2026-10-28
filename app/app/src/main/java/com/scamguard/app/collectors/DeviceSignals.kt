package com.scamguard.app.collectors

import com.scamguard.app.bus.SignalEvent

/**
 * Reads a carrier's answer to the call-forwarding status code (*#21#). Answers are free
 * text and differ per carrier and language, so only clear wording is trusted; anything
 * else is null (unknown). The answer can contain the forward-to number, so it is never
 * kept or logged.
 */
object ForwardingResponseParser {
    private val off = Regex("""\b(not active|not activated|not enabled|deactivated|disabled|inactive|not forwarded|is off|erased)\b""")
    private val on = Regex("""\b(active|activated|enabled|forwarded to|is on)\b""")

    /** True if forwarding is on, false if off, null if the text cannot be read reliably. */
    fun parse(response: CharSequence?): Boolean? {
        val text = response?.toString()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            off.containsMatchIn(text) -> false
            on.containsMatchIn(text) -> true
            else -> null
        }
    }
}

/**
 * Compares a forwarding result with the stored baseline. Only a reliable "off -> on"
 * change is an event; the first reliable reading only sets the baseline.
 */
object ForwardingBaseline {
    data class Outcome(val emitEnabled: Boolean, val newBaseline: Boolean?)

    fun compare(baseline: Boolean?, result: Boolean?): Outcome = when (result) {
        null -> Outcome(emitEnabled = false, newBaseline = baseline)
        true -> Outcome(emitEnabled = baseline == false, newBaseline = true)
        false -> Outcome(emitEnabled = false, newBaseline = false)
    }
}

/** Remote-access tools scammers ask victims to install, and app-store installers. */
object KnownApps {
    val remoteAccess: Set<String> = setOf(
        "com.anydesk.anydeskandroid",
        "com.teamviewer.quicksupport.market",
        "com.teamviewer.teamviewer.market.mobile",
        "com.rustdesk.rustdesk",
    )

    private val stores = setOf(
        "com.android.vending",
        "com.amazon.venezia",
        "com.sec.android.app.samsungapps",
        "com.xiaomi.market",
        "com.xiaomi.mipicks",
        "com.heytap.market",
        "com.oppo.market",
        "com.bbk.appstore",
        "com.huawei.appmarket",
    )

    /** An install from no installer or a file manager / package installer, not an app store. */
    fun isSideloaded(installer: String?): Boolean = installer == null || installer !in stores
}

/**
 * Turns connectivity callbacks into [SignalEvent.NetworkLost] once a loss has lasted
 * [minLossMs]. Reported once per loss, either when it reaches the minimum ([tick]) or
 * when the network returns. Weak evidence only: lifts, tunnels and airplane mode look
 * the same as a SIM swap.
 */
class NetworkLossTracker(private val minLossMs: Long = 60_000) {
    private var lostAt: Long? = null
    private var reported = false

    fun onLost(ts: Long) {
        if (lostAt == null) { lostAt = ts; reported = false }
    }

    fun onAvailable(ts: Long): SignalEvent.NetworkLost? {
        val since = lostAt ?: return null
        lostAt = null
        return if (!reported && ts - since >= minLossMs) SignalEvent.NetworkLost(ts, ts - since) else null
    }

    fun tick(ts: Long): SignalEvent.NetworkLost? {
        val since = lostAt ?: return null
        if (reported || ts - since < minLossMs) return null
        reported = true
        return SignalEvent.NetworkLost(ts, ts - since)
    }
}
