package com.scamguard.app.util

import com.scamguard.app.data.settings.ForwardingStatus

/** How usable a capability is right now. */
enum class CapabilityState { AVAILABLE, DENIED, RESTRICTED, UNSUPPORTED, NOT_CONFIGURED }

/** Every permission, role and special access the Health screen lists. */
enum class CapabilityId {
    SMS, PHONE_STATE, CONTACTS, NOTIFICATIONS, FULL_SCREEN, CALL_SCREENING,
    END_CALL, FORWARDING, USAGE_ACCESS, BATTERY, MICROPHONE, DEVICE_ADMIN,
}

data class Capability(val id: CapabilityId, val state: CapabilityState, val optional: Boolean)

/**
 * Maps a [HealthSnapshot] to the Health screen's rows. Pure, so the per-API-level
 * degradation rules are unit tested.
 */
object Capabilities {

    fun list(
        h: HealthSnapshot,
        sdk: Int,
        hasTelephony: Boolean,
        forwarding: ForwardingStatus,
        deviceAdmin: Boolean,
    ): List<Capability> {
        fun perm(granted: Boolean) = if (granted) CapabilityState.AVAILABLE else CapabilityState.DENIED
        val noPhone = CapabilityState.UNSUPPORTED.takeUnless { hasTelephony }
        return listOf(
            Capability(CapabilityId.SMS, noPhone ?: perm(h.sms), optional = false),
            Capability(CapabilityId.PHONE_STATE, noPhone ?: perm(h.phoneState), optional = false),
            Capability(CapabilityId.NOTIFICATIONS, perm(h.notifications), optional = false),
            Capability(
                CapabilityId.CALL_SCREENING,
                when {
                    noPhone != null -> noPhone
                    sdk < 29 -> CapabilityState.UNSUPPORTED
                    h.screeningRole -> CapabilityState.AVAILABLE
                    else -> CapabilityState.NOT_CONFIGURED
                },
                optional = false,
            ),
            Capability(CapabilityId.CONTACTS, perm(h.contacts), optional = true),
            Capability(
                CapabilityId.FULL_SCREEN,
                if (h.fullScreenIntent) CapabilityState.AVAILABLE else CapabilityState.RESTRICTED,
                optional = true,
            ),
            Capability(
                CapabilityId.END_CALL,
                when {
                    noPhone != null -> noPhone
                    sdk < 28 -> CapabilityState.UNSUPPORTED
                    else -> perm(h.answerCalls)
                },
                optional = true,
            ),
            Capability(
                CapabilityId.FORWARDING,
                when {
                    noPhone != null -> noPhone
                    !h.callPhone || !h.phoneState -> CapabilityState.DENIED
                    forwarding == ForwardingStatus.AVAILABLE -> CapabilityState.AVAILABLE
                    forwarding == ForwardingStatus.UNAVAILABLE -> CapabilityState.UNSUPPORTED
                    else -> CapabilityState.NOT_CONFIGURED
                },
                optional = true,
            ),
            Capability(CapabilityId.USAGE_ACCESS, if (h.usageAccess) CapabilityState.AVAILABLE else CapabilityState.NOT_CONFIGURED, optional = true),
            Capability(CapabilityId.BATTERY, if (h.batteryUnrestricted) CapabilityState.AVAILABLE else CapabilityState.RESTRICTED, optional = true),
            Capability(CapabilityId.MICROPHONE, perm(h.microphone), optional = true),
            Capability(CapabilityId.DEVICE_ADMIN, if (deviceAdmin) CapabilityState.AVAILABLE else CapabilityState.NOT_CONFIGURED, optional = true),
        )
    }
}
