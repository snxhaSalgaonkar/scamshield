package com.scamguard.app.ui

import androidx.annotation.StringRes
import com.scamguard.app.R
import com.scamguard.app.bus.VoiceCategory
import com.scamguard.app.engine.ReasonCode
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.engine.ScamType
import com.scamguard.app.engine.ScoreSource
import com.scamguard.app.data.ModelFeedback
import com.scamguard.app.ml.MessageCategory
import com.scamguard.app.ml.MessageVerdict
import com.scamguard.app.ml.UrlVerdict

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
        ReasonCode.HIDDEN_NUMBER -> R.string.reason_hidden_number
        ReasonCode.LINK_RECEIVED -> R.string.reason_link_received
        ReasonCode.SCAM_MESSAGE -> R.string.reason_scam_message
        ReasonCode.SMS_IMPERSONATION -> R.string.reason_sms_impersonation
        ReasonCode.SMS_CREDENTIAL_REQUEST -> R.string.reason_sms_credential
        ReasonCode.SMS_PAYMENT_REQUEST -> R.string.reason_sms_payment
        ReasonCode.SMS_REMOTE_ACCESS -> R.string.reason_sms_remote
        ReasonCode.SUSPICIOUS_LINK -> R.string.reason_suspicious_link
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
        ReasonCode.COMBO_OTP_ASKED -> R.string.reason_combo_otp_asked
        ReasonCode.COMBO_FORWARDING_OTP -> R.string.reason_combo_forwarding_otp
        ReasonCode.COMBO_MERGE_OTP -> R.string.reason_combo_merge_otp
        ReasonCode.COMBO_REMOTE_DEBIT -> R.string.reason_combo_remote_debit
        ReasonCode.VOICE_OTP_REQUEST -> R.string.reason_voice_otp
        ReasonCode.VOICE_ARREST_THREAT -> R.string.reason_voice_arrest
        ReasonCode.VOICE_PAYMENT_PRESSURE -> R.string.reason_voice_payment
        ReasonCode.VOICE_REMOTE_APP -> R.string.reason_voice_remote
        ReasonCode.VIDEO_CALL_UNKNOWN -> R.string.reason_video_unknown
        ReasonCode.VIDEO_CALL_LONG -> R.string.reason_video_long
        ReasonCode.AUTHORITY_CALLER_NAME -> R.string.reason_authority_name
        ReasonCode.SCREEN_LAW_ENFORCEMENT -> R.string.reason_screen_law
        ReasonCode.SCREEN_LEGAL_THREAT -> R.string.reason_screen_legal
        ReasonCode.SCREEN_PAYMENT_DEMAND -> R.string.reason_screen_payment
        ReasonCode.COMBO_VIDEO_AUTHORITY -> R.string.reason_combo_video_authority
        ReasonCode.VOICE_AUTHORITY_CLAIM -> R.string.reason_voice_authority
        ReasonCode.VOICE_ACCUSATION -> R.string.reason_voice_accusation
        ReasonCode.VOICE_ISOLATION -> R.string.reason_voice_isolation
        ReasonCode.VOICE_CARD_DETAILS -> R.string.reason_voice_card
        ReasonCode.VOICE_LINK_REQUEST -> R.string.reason_voice_link
        ReasonCode.COMBO_VOICE_ACCUSED -> R.string.reason_combo_voice_accused
        ReasonCode.COMBO_VOICE_THREAT -> R.string.reason_combo_voice_threat
        ReasonCode.COMBO_VOICE_IMPERSONATION -> R.string.reason_combo_voice_impersonation
        ReasonCode.COMBO_VOICE_DIGITAL_ARREST -> R.string.reason_combo_voice_digital_arrest
    }

    @StringRes fun voiceCategory(stored: String?): Int = when (stored?.substringBefore('|')) {
        VoiceCategory.OTP_REQUEST.name -> R.string.voice_cat_otp
        VoiceCategory.ARREST_THREAT.name -> R.string.voice_cat_arrest
        VoiceCategory.PAYMENT_PRESSURE.name -> R.string.voice_cat_payment
        VoiceCategory.AUTHORITY_CLAIM.name -> R.string.voice_cat_authority
        VoiceCategory.ACCUSATION.name -> R.string.voice_cat_accusation
        VoiceCategory.ISOLATION.name -> R.string.voice_cat_isolation
        VoiceCategory.CARD_DETAILS.name -> R.string.voice_cat_card
        VoiceCategory.LINK_REQUEST.name -> R.string.voice_cat_link
        else -> R.string.voice_cat_remote
    }

    @StringRes fun category(c: MessageCategory): Int = when (c) {
        MessageCategory.CREDENTIAL_THEFT -> R.string.cat_credential
        MessageCategory.IMPERSONATION_THREAT -> R.string.cat_impersonation
        MessageCategory.PAYMENT_FRAUD -> R.string.cat_payment
        MessageCategory.REMOTE_ACCESS -> R.string.cat_remote
        MessageCategory.PRIZE_LURE -> R.string.cat_prize
        MessageCategory.PHISHING_LINK -> R.string.cat_phishing
        MessageCategory.OTHER_SPAM -> R.string.cat_other
    }

    @StringRes fun messageVerdict(v: MessageVerdict): Int = when (v) {
        MessageVerdict.LIKELY_SCAM -> R.string.mv_likely_scam
        MessageVerdict.SUSPICIOUS -> R.string.mv_suspicious
        MessageVerdict.NO_STRONG_SIGNAL -> R.string.mv_no_signal
        MessageVerdict.UNKNOWN -> R.string.mv_unknown
        MessageVerdict.EMPTY -> R.string.mv_empty
        MessageVerdict.MODEL_UNAVAILABLE -> R.string.mv_unavailable
    }

    @StringRes fun urlVerdict(v: UrlVerdict): Int = when (v) {
        UrlVerdict.SUSPICIOUS -> R.string.uv_suspicious
        UrlVerdict.UNCERTAIN -> R.string.uv_uncertain
        UrlVerdict.NO_STRONG_SIGNAL -> R.string.uv_no_signal
        UrlVerdict.INVALID -> R.string.uv_invalid
        UrlVerdict.MODEL_UNAVAILABLE -> R.string.uv_unavailable
    }

    @StringRes fun feedback(f: ModelFeedback): Int = when (f) {
        ModelFeedback.CORRECT -> R.string.fb_correct
        ModelFeedback.FALSE_POSITIVE -> R.string.fb_false_positive
        ModelFeedback.MISSED_SCAM -> R.string.fb_missed
        ModelFeedback.UNSURE -> R.string.fb_unsure
    }

    @StringRes fun scoreSource(stored: String): Int = when (stored) {
        ScoreSource.MODEL.name -> R.string.source_model
        ScoreSource.GATED.name -> R.string.source_gated
        else -> R.string.source_rules
    }

    fun parseMessageVerdict(stored: String): MessageVerdict =
        MessageVerdict.entries.firstOrNull { it.name == stored } ?: MessageVerdict.UNKNOWN

    /** Parses the comma-separated reason codes stored in Room, skipping unknown names. */
    fun parseReasons(stored: String): List<ReasonCode> =
        stored.split(",").mapNotNull { name -> ReasonCode.entries.firstOrNull { it.name == name } }

    fun parseScamType(stored: String): ScamType = ScamType.entries.firstOrNull { it.name == stored } ?: ScamType.NONE
    fun parseLevel(stored: String): RiskLevel = RiskLevel.entries.firstOrNull { it.name == stored } ?: RiskLevel.SAFE
}
