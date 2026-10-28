package com.scamguard.app.remote

/** Result of a gateway call. */
sealed interface GatewayResult<out T> {
    data class Success<T>(val value: T) : GatewayResult<T>
    data class NotAvailable(val reason: String) : GatewayResult<Nothing>
}

/**
 * The only seam through which data could ever leave the phone. Every DTO carries events
 * and scores only, never message text, URLs, contacts or audio transcripts.
 * The shipped build uses [LocalOnlyGateway].
 */
interface BackendGateway {
    suspend fun registerDevice(device: DeviceRegistrationDto): GatewayResult<Unit>
    suspend fun uploadCallEvent(event: CallEventDto): GatewayResult<Unit>
    suspend fun uploadSmsEvent(event: SmsEventDto): GatewayResult<Unit>
    suspend fun uploadSignalEvent(event: SignalEventDto): GatewayResult<Unit>
    suspend fun reportNumber(report: NumberReportDto): GatewayResult<Unit>
    suspend fun fetchHistory(sinceTs: Long): GatewayResult<List<DecisionDto>>
    suspend fun syncReputation(sinceTs: Long): GatewayResult<List<ReputationDto>>
    suspend fun uploadRecording(upload: RecordingUploadDto): GatewayResult<Unit>
}
