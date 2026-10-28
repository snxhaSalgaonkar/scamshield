package com.scamguard.app.remote

/**
 * Gateway used by this build: nothing leaves the phone. Every call returns
 * [GatewayResult.NotAvailable] so callers can show an honest message.
 */
class LocalOnlyGateway : BackendGateway {
    private val notAvailable = GatewayResult.NotAvailable(REASON)

    override suspend fun registerDevice(device: DeviceRegistrationDto) = notAvailable
    override suspend fun uploadCallEvent(event: CallEventDto) = notAvailable
    override suspend fun uploadSmsEvent(event: SmsEventDto) = notAvailable
    override suspend fun uploadSignalEvent(event: SignalEventDto) = notAvailable
    override suspend fun reportNumber(report: NumberReportDto) = notAvailable
    override suspend fun fetchHistory(sinceTs: Long) = notAvailable
    override suspend fun syncReputation(sinceTs: Long) = notAvailable
    override suspend fun uploadRecording(upload: RecordingUploadDto) = notAvailable

    companion object {
        const val REASON = "Not available in this build"
    }
}
