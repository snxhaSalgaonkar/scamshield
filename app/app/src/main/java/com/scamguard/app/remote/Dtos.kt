package com.scamguard.app.remote

/** Device registration: a random install id and the consent levels the user chose. */
data class DeviceRegistrationDto(
    val installIdHash: String,
    val appVersion: String,
    val consents: Set<String>,
)

/** A call state change. The number is only ever a hash plus a region prefix. */
data class CallEventDto(
    val windowId: String,
    val ts: Long,
    val type: String,
    val numberHash: String?,
    val prefix: String,
    val durationMs: Long?,
)

/** An SMS-derived event. Holds the event type and amounts, never the message text. */
data class SmsEventDto(
    val windowId: String?,
    val ts: Long,
    val type: String,
    val amountPaise: Long?,
    val payeeHash: String?,
    val balancePaise: Long?,
    val senderTrusted: Boolean?,
)

/** A non-SMS phone signal (forwarding, app, network). */
data class SignalEventDto(
    val windowId: String?,
    val ts: Long,
    val type: String,
    val packageName: String?,
    val flag: Boolean?,
)

/** A user report that a number is a scam. */
data class NumberReportDto(
    val numberHash: String,
    val prefix: String,
    val scamType: String,
    val ts: Long,
)

/** A scored call window. */
data class DecisionDto(
    val windowId: String,
    val ts: Long,
    val score: Int,
    val level: String,
    val scamType: String,
    val reasonCodes: List<String>,
)

/** A reputation entry from a shared list. */
data class ReputationDto(
    val numberHash: String,
    val prefix: String,
    val status: String,
    val reports: Int,
    val expiresAt: Long?,
)

/** A user-chosen recording, uploaded only with explicit consent. */
data class RecordingUploadDto(
    val contentUri: String,
    val consentTs: Long,
    val windowId: String?,
)
