package com.scamguard.app.ui.screens.simulator

import androidx.annotation.StringRes
import com.scamguard.app.R
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.util.Hashing

private const val SEC = 1_000L
private const val MIN = 60 * SEC

/** A scripted event stream with the level the pipeline should reach. */
data class Scenario(
    val id: Int,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val expected: RiskLevel,
    val events: (base: Long) -> List<SignalEvent>,
)

/** The seven demo scenarios. Fake numbers are hashed like real ones; nothing is real data. */
object Scenarios {

    private fun unknownCall(base: Long, n: Int) = SignalEvent.CallRinging(
        ts = base,
        numberHash = Hashing.numberHash("+9190000000$n"),
        prefix = "+91 90",
        isUnknown = true,
        isInternational = false,
        isHidden = false,
        mask = "+91 90•••••0$n",
    )

    val all: List<Scenario> = listOf(
        Scenario(1, R.string.sc1_title, R.string.sc1_desc, RiskLevel.WARNING) { b ->
            listOf(
                unknownCall(b, 1),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.OtpReceived(b + 2 * MIN, senderTrusted = true),
                SignalEvent.CallEnded(b + 6 * MIN, 6 * MIN - 5 * SEC),
            )
        },
        Scenario(2, R.string.sc2_title, R.string.sc2_desc, RiskLevel.RED_FLAG) { b ->
            listOf(
                unknownCall(b, 2),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.OtpReceived(b + MIN, senderTrusted = true),
                SignalEvent.DebitReceived(b + 3 * MIN, 24_999_00, Hashing.payeeHash("demo-payee@upi"), 1_200_00),
                SignalEvent.CallEnded(b + 4 * MIN, 4 * MIN),
            )
        },
        Scenario(3, R.string.sc3_title, R.string.sc3_desc, RiskLevel.RED_FLAG) { b ->
            listOf(
                unknownCall(b, 3),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.ForwardingChanged(b + MIN, enabled = true),
                SignalEvent.OtpReceived(b + 2 * MIN, senderTrusted = true),
                SignalEvent.CallEnded(b + 3 * MIN, 3 * MIN),
            )
        },
        Scenario(4, R.string.sc4_title, R.string.sc4_desc, RiskLevel.RED_FLAG) { b ->
            listOf(
                unknownCall(b, 4),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.SecondCallConnected(b + MIN),
                SignalEvent.OtpReceived(b + 2 * MIN, senderTrusted = true),
                SignalEvent.CallEnded(b + 3 * MIN, 3 * MIN),
            )
        },
        Scenario(5, R.string.sc5_title, R.string.sc5_desc, RiskLevel.RED_FLAG) { b ->
            listOf(
                unknownCall(b, 5),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.RemoteAppOpened(b + MIN, "com.anydesk.anydeskandroid"),
                SignalEvent.DebitReceived(b + 4 * MIN, 49_000_00, Hashing.payeeHash("demo-payee-2@upi"), null),
                SignalEvent.CallEnded(b + 5 * MIN, 5 * MIN - 5 * SEC),
            )
        },
        Scenario(6, R.string.sc6_title, R.string.sc6_desc, RiskLevel.SAFE) { b ->
            listOf(
                unknownCall(b, 6).copy(isUnknown = false),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.OtpReceived(b + MIN, senderTrusted = true),
                SignalEvent.CallEnded(b + 2 * MIN, 2 * MIN),
            )
        },
        Scenario(7, R.string.sc7_title, R.string.sc7_desc, RiskLevel.SAFE) { b ->
            listOf(SignalEvent.OtpReceived(b, senderTrusted = true))
        },
    )
}
