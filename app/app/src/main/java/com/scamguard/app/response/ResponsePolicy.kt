package com.scamguard.app.response

import com.scamguard.app.data.settings.ResponseMode
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.RiskLevel

/**
 * Risk bands of the shared contract: 0-39 SAFE, 40-74 WARN, 75-89 HIGH, 90-100 SCAM. The
 * engine's level is the authority (a red flag that rests on one signal is capped at warning),
 * the score only splits a red flag into HIGH and SCAM.
 */
enum class Band { SAFE, WARN, HIGH, SCAM;

    companion object {
        const val SCAM_SCORE = 90

        fun of(d: Decision): Band = when {
            d.gated || d.level == RiskLevel.SAFE -> SAFE
            d.level == RiskLevel.WARNING -> WARN
            d.score >= SCAM_SCORE -> SCAM
            else -> HIGH
        }
    }
}

/** One thing the response engine does. Executed in list order by [ResponseController]. */
enum class Action {
    /** Interstitial when a payment app is opened during a flagged call ("Don't send money"). */
    SOFT_PAYMENT_GUARD,
    /** Payment and banking apps cannot be opened until the call is over (plus cooldown). */
    FREEZE_PAYMENTS,
    /** End the call through the disconnect ladder. */
    DISCONNECT,
    /** Send the report (and freeze request) to the authority server. */
    REPORT,
    LOCK,
    /** Switch the phone off, only after the report was acknowledged. */
    SWITCH_OFF,
    /** Full-screen alert with reasons, "End call", 1930 and the release button. */
    SHOW_ALERT,
}

/** What this phone allows right now (probed, never assumed). */
data class ResponseCapabilities(
    val shizuku: Boolean = false,
    /** TelecomManager.endCall with ANSWER_PHONE_CALLS. */
    val telecomEndCall: Boolean = false,
    val deviceAdmin: Boolean = false,
    val deviceOwner: Boolean = false,
    /** The accessibility helper is on, so a payment app can be covered by the block screen. */
    val accessibility: Boolean = false,
    /** The authority server is configured in this build. */
    val serverConfigured: Boolean = false,
) {
    val canDisconnect get() = shizuku || telecomEndCall
    val canLock get() = deviceAdmin || shizuku
    val canFreeze get() = shizuku || deviceOwner || accessibility
}

/** The consents from Settings that the actions need. */
data class ResponseConsents(
    val mode: ResponseMode = ResponseMode.STANDARD,
    val autoProtect: Boolean = false,
    val paymentGuard: Boolean = false,
    val switchOff: Boolean = false,
    val sendReports: Boolean = false,
)

/** The ordered response for one band. [countdownSec] > 0 means [afterCountdown] waits for it (cancellable). */
data class Response(
    val now: List<Action>,
    val countdownSec: Int = 0,
    val afterCountdown: List<Action> = emptyList(),
) {
    val all: List<Action> get() = now + afterCountdown

    companion object {
        val NONE = Response(emptyList())
    }
}

/**
 * Decides what happens for a band. Pure Kotlin so every band x mode x capability combination
 * is unit tested.
 *
 * | Band | Protected (auto-protect on)                       | Standard                              |
 * |------|---------------------------------------------------|---------------------------------------|
 * | WARN | soft payment guard                                | soft payment guard                    |
 * | HIGH | freeze; 5-s countdown, then end call, report, lock| freeze, alert with End call, report   |
 * | SCAM | freeze, end call, report, lock, (switch off), alert| freeze, alert with End call, report  |
 *
 * Freeze goes first because it protects money even if ending the call fails. Every action needs
 * its consent and a capability that works on this phone; missing ones are dropped, and the alert
 * then tells the user to hang up. A simulated call is never disconnected or switched off.
 */
object ResponsePolicy {
    const val COUNTDOWN_SEC = 5

    fun decide(band: Band, consents: ResponseConsents, caps: ResponseCapabilities, simulated: Boolean): Response {
        val freeze = consents.paymentGuard && caps.canFreeze
        val report = consents.sendReports && caps.serverConfigured
        val auto = consents.mode == ResponseMode.PROTECTED && consents.autoProtect
        val disconnect = auto && caps.canDisconnect && !simulated
        val lock = auto && caps.canLock
        val switchOff = auto && consents.switchOff && caps.shizuku && !simulated

        return when (band) {
            Band.SAFE -> Response.NONE
            Band.WARN -> Response(listOfNotNull(Action.SOFT_PAYMENT_GUARD.takeIf { consents.paymentGuard && caps.accessibility }))
            Band.HIGH -> if (auto) {
                Response(
                    now = listOfNotNull(Action.FREEZE_PAYMENTS.takeIf { freeze }, Action.SHOW_ALERT),
                    countdownSec = COUNTDOWN_SEC,
                    afterCountdown = listOfNotNull(
                        Action.DISCONNECT.takeIf { disconnect },
                        Action.REPORT.takeIf { report },
                        Action.LOCK.takeIf { lock },
                    ),
                )
            } else {
                standard(freeze, report)
            }
            Band.SCAM -> if (auto) {
                Response(
                    listOfNotNull(
                        Action.FREEZE_PAYMENTS.takeIf { freeze },
                        Action.DISCONNECT.takeIf { disconnect },
                        Action.REPORT.takeIf { report },
                        Action.LOCK.takeIf { lock },
                        // Runs after REPORT, which it waits for (up to 3 s) so the report is not lost.
                        Action.SWITCH_OFF.takeIf { switchOff },
                        Action.SHOW_ALERT,
                    )
                )
            } else {
                standard(freeze, report)
            }
        }
    }

    private fun standard(freeze: Boolean, report: Boolean) = Response(
        listOfNotNull(Action.FREEZE_PAYMENTS.takeIf { freeze }, Action.SHOW_ALERT, Action.REPORT.takeIf { report })
    )
}
