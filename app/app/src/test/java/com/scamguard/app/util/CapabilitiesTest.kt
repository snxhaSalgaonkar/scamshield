package com.scamguard.app.util

import com.scamguard.app.data.settings.ForwardingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilitiesTest {
    private val none = HealthSnapshot(
        sms = false, phoneState = false, contacts = false, callPhone = false, answerCalls = false,
        notifications = false, fullScreenIntent = false, screeningRole = false, usageAccess = false,
        batteryUnrestricted = false, microphone = false,
    )
    private val all = HealthSnapshot(
        sms = true, phoneState = true, contacts = true, callPhone = true, answerCalls = true,
        notifications = true, fullScreenIntent = true, screeningRole = true, usageAccess = true,
        batteryUnrestricted = true, microphone = true, notificationAccess = true,
    )

    private fun state(h: HealthSnapshot, id: CapabilityId, sdk: Int = 35, phone: Boolean = true, fwd: ForwardingStatus = ForwardingStatus.AVAILABLE) =
        Capabilities.list(h, sdk, phone, fwd, deviceAdmin = false).first { it.id == id }.state

    @Test fun `everything granted is available`() {
        Capabilities.list(all, 35, true, ForwardingStatus.AVAILABLE, deviceAdmin = true)
            .forEach { assertEquals(it.id.name, CapabilityState.AVAILABLE, it.state) }
    }

    @Test fun `denials are reported, never thrown`() {
        assertEquals(CapabilityState.DENIED, state(none, CapabilityId.SMS))
        assertEquals(CapabilityState.NOT_CONFIGURED, state(none, CapabilityId.CALL_SCREENING))
        assertEquals(CapabilityState.RESTRICTED, state(none, CapabilityId.FULL_SCREEN))
    }

    @Test fun `old Android versions mark screening and end call unsupported`() {
        assertEquals(CapabilityState.UNSUPPORTED, state(all, CapabilityId.CALL_SCREENING, sdk = 28))
        assertEquals(CapabilityState.UNSUPPORTED, state(all, CapabilityId.END_CALL, sdk = 27))
        assertEquals(CapabilityState.AVAILABLE, state(all, CapabilityId.END_CALL, sdk = 28))
    }

    @Test fun `forwarding check follows the carrier result`() {
        assertEquals(CapabilityState.UNSUPPORTED, state(all, CapabilityId.FORWARDING, fwd = ForwardingStatus.UNAVAILABLE))
        assertEquals(CapabilityState.NOT_CONFIGURED, state(all, CapabilityId.FORWARDING, fwd = ForwardingStatus.NOT_CHECKED))
        assertEquals(CapabilityState.DENIED, state(none, CapabilityId.FORWARDING))
    }

    @Test fun `tablet without telephony`() {
        assertEquals(CapabilityState.UNSUPPORTED, state(all, CapabilityId.SMS, phone = false))
        assertEquals(CapabilityState.UNSUPPORTED, state(all, CapabilityId.FORWARDING, phone = false))
    }
}
