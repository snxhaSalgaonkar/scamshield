package com.scamguard.app.ui.screens.simulator

import androidx.annotation.StringRes
import com.scamguard.app.R
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.util.Hashing

private const val SEC = 1_000L
private const val MIN = 60 * SEC

/**
 * A scripted event stream with the level the pipeline should reach. [messages] are demo
 * SMS texts (timestamp to text) that the simulator runs through the real on-device message
 * model, exactly as a received SMS would be; they are invented for the demo.
 */
data class Scenario(
    val id: Int,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val expected: RiskLevel,
    val messages: (base: Long) -> List<Pair<Long, String>> = { emptyList() },
    val events: (base: Long) -> List<SignalEvent>,
)

/** The demo scenarios. Fake numbers are hashed like real ones; nothing is real data. */
object Scenarios {

    private fun unknownCall(base: Long, n: Int) = SignalEvent.CallRinging(
        ts = base,
        numberHash = Hashing.numberHash("+919000000${n.toString().padStart(2, '0')}"),
        prefix = "+91 90",
        isUnknown = true,
        isInternational = false,
        isHidden = false,
        mask = "+91 90•••••${n.toString().padStart(2, '0')}",
    )

    const val DIGITAL_ARREST_SMS = "Mumbai Police Cyber Cell: an FIR is registered on your Aadhaar for money laundering. " +
        "Stay on the video call and do not inform family. Move your savings to the verification account today or face arrest."
    const val KYC_SMS = "Dear customer your SBI YONO KYC has expired and your account will be blocked today. " +
        "Update now: sbi-yono-kyc.top/update.apk"
    const val DELIVERY_SMS = "Your Amazon order is out for delivery today and will arrive by 6 pm. Track it in the Amazon app."

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
        Scenario(8, R.string.sc8_title, R.string.sc8_desc, RiskLevel.RED_FLAG, messages = { b -> listOf(b + 2 * MIN to DIGITAL_ARREST_SMS) }) { b ->
            listOf(
                unknownCall(b, 8).copy(isInternational = true, prefix = "+44", mask = "+44 77•••••08"),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.DebitReceived(b + 48 * MIN, 1_50_000_00, Hashing.payeeHash("demo-verification@upi"), 3_000_00),
                SignalEvent.CallEnded(b + 50 * MIN, 50 * MIN - 5 * SEC),
            )
        },
        Scenario(9, R.string.sc9_title, R.string.sc9_desc, RiskLevel.RED_FLAG, messages = { b -> listOf(b + MIN to KYC_SMS) }) { b ->
            listOf(
                unknownCall(b, 9),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.LinkReceived(b + MIN),
                SignalEvent.ApkInstalled(b + 4 * MIN, "com.example.kycupdate", sideloaded = true),
                SignalEvent.CallEnded(b + 6 * MIN, 6 * MIN - 5 * SEC),
            )
        },
        Scenario(10, R.string.sc10_title, R.string.sc10_desc, RiskLevel.SAFE, messages = { b -> listOf(b + 30 * SEC to DELIVERY_SMS) }) { b ->
            listOf(
                unknownCall(b, 10),
                SignalEvent.CallActive(b + 5 * SEC),
                SignalEvent.CallEnded(b + 90 * SEC, 85 * SEC),
            )
        },
    )
}
