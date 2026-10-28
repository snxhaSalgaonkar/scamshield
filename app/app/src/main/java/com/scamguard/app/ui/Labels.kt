package com.scamguard.app.ui

import androidx.annotation.StringRes
import com.scamguard.app.R
import com.scamguard.app.bus.VoiceCategory
import com.scamguard.app.engine.ReasonCode
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.engine.ScamType

/** Plain-language labels for engine enums, usable from Compose and notifications. */
object Labels {

    @StringRes fun level(level: RiskLevel): Int = when (level) {
        RiskLevel.SAFE -> R.string.level_safe
        RiskLevel.WARNING -> R.string.level_warning
        RiskLevel.RED_FLAG -> R.string.level_red_flag
    }

    @StringRes fun scamType(type: ScamType): Int = when (type) {
        ScamType.OTP_THEFT -> R.string.scam_otp_theft
        ScamType.MONEY_TRANSFER_PRESSURE -> R.string.scam_money_transfer
        ScamType.CALL_FORWARDING_HIJACK -> R.string.scam_forwarding
        ScamType.CALL_MERGE_FRAUD -> R.string.scam_merge
        ScamType.REMOTE_ACCESS_FRAUD -> R.string.scam_remote_access
        ScamType.DIGITAL_ARREST -> R.string.scam_digital_arrest
        ScamType.SUSPICIOUS_CALL -> R.string.scam_suspicious
        ScamType.NONE -> R.string.scam_none
    }

    @StringRes fun reason(code: ReasonCode): Int = when (code) {
        ReasonCode.UNKNOWN_CALLER -> R.string.reason_unknown_caller
        ReasonCode.INTERNATIONAL_OR_VOIP -> R.string.reason_international
        ReasonCode.REPEATED_CALLS -> R.string.reason_repeated_calls
        ReasonCode.REPORTED_NUMBER -> R.string.reason_reported
        ReasonCode.LONG_CALL -> R.string.reason_long_call
        ReasonCode.ODD_HOURS -> R.string.reason_odd_hours
        ReasonCode.KNOWN_BANK_NUMBER -> R.string.reason_known_bank
        ReasonCode.USER_MARKED_SAFE -> R.string.reason_user_safe
        ReasonCode.OTP_DURING_CALL -> R.string.reason_otp
        ReasonCode.DEBIT_DURING_CALL -> R.string.reason_debit
        ReasonCode.FORWARDING_ENABLED -> R.string.reason_forwarding
        ReasonCode.CALL_MERGED -> R.string.reason_merge
        ReasonCode.AMOUNT_FAR_ABOVE_NORMAL -> R.string.reason_amount_high
        ReasonCode.FIRST_TIME_PAYEE -> R.string.reason_new_payee
        ReasonCode.MULTIPLE_DEBITS -> R.string.reason_multiple_debits
        ReasonCode.BALANCE_NEAR_ZERO -> R.string.reason_balance_low
        ReasonCode.REMOTE_ACCESS_APP -> R.string.reason_remote_app
        ReasonCode.SIDELOADED_APK -> R.string.reason_sideloaded
        ReasonCode.LINK_THEN_INSTALL -> R.string.reason_link_install
        ReasonCode.NETWORK_LOST -> R.string.reason_network_lost
        ReasonCode.COMBO_OTP_DEBIT -> R.string.reason_combo_otp_debit
        ReasonCode.COMBO_FORWARDING_OTP -> R.string.reason_combo_forwarding_otp
        ReasonCode.COMBO_MERGE_OTP -> R.string.reason_combo_merge_otp
        ReasonCode.COMBO_REMOTE_DEBIT -> R.string.reason_combo_remote_debit
        ReasonCode.VOICE_OTP_REQUEST -> R.string.reason_voice_otp
        ReasonCode.VOICE_ARREST_THREAT -> R.string.reason_voice_arrest
        ReasonCode.VOICE_PAYMENT_PRESSURE -> R.string.reason_voice_payment
        ReasonCode.VOICE_REMOTE_APP -> R.string.reason_voice_remote
    }

    @StringRes fun voiceCategory(stored: String?): Int = when (stored) {
        VoiceCategory.OTP_REQUEST.name -> R.string.voice_cat_otp
        VoiceCategory.ARREST_THREAT.name -> R.string.voice_cat_arrest
        VoiceCategory.PAYMENT_PRESSURE.name -> R.string.voice_cat_payment
        else -> R.string.voice_cat_remote
    }

    /** Parses the comma-separated reason codes stored in Room, skipping unknown names. */
    fun parseReasons(stored: String): List<ReasonCode> =
        stored.split(",").mapNotNull { name -> ReasonCode.entries.firstOrNull { it.name == name } }

    fun parseScamType(stored: String): ScamType = ScamType.entries.firstOrNull { it.name == stored } ?: ScamType.NONE
    fun parseLevel(stored: String): RiskLevel = RiskLevel.entries.firstOrNull { it.name == stored } ?: RiskLevel.SAFE
}
