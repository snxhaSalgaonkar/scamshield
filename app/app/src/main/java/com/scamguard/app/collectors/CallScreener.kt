package com.scamguard.app.collectors

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.scamguard.app.appContainer
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.remote.CallerNumbers
import com.scamguard.app.util.Permissions
import com.scamguard.app.util.PhoneNumbers
import kotlinx.coroutines.launch

/**
 * Call-screening service (role on Android 10+). It is the only place the caller's number
 * is seen: it is hashed at once, checked against contacts and the trusted list in memory,
 * and dropped (when an authority server is configured, [CallerNumbers] keeps it in memory
 * for a while so a flagged call can be reported). Every call is allowed straight away; this app never blocks, silences or
 * rejects a call.
 */
class CallScreener : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // Allow first, so nothing we do can delay or affect the call.
        respondToCall(details, CallResponse.Builder().build())
        if (!isIncoming(details)) return

        val ts = System.currentTimeMillis()
        val hidden = details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val raw = if (hidden) null else details.handle?.schemeSpecificPart
        val parsed = PhoneNumbers.parse(raw)
        CallerNumbers.remember(parsed)
        val inContacts = raw != null && isInContacts(this, raw)
        val container = applicationContext.appContainer

        container.appScope.launch {
            val trusted = container.numbers.isTrusted(parsed?.hash)
            container.eventBus.emit(
                SignalEvent.CallRinging(
                    ts = ts,
                    numberHash = parsed?.hash,
                    prefix = parsed?.prefix ?: PhoneNumbers.HIDDEN_PREFIX,
                    isUnknown = !inContacts && !trusted,
                    isInternational = parsed?.isInternational ?: false,
                    isHidden = parsed == null,
                    mask = parsed?.mask.orEmpty(),
                )
            )
        }
    }

    private fun isIncoming(details: Call.Details): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || details.callDirection == Call.Details.DIRECTION_INCOMING

    companion object {
        /** PhoneLookup on the in-memory number; only "found or not" is kept. */
        fun isInContacts(context: Context, raw: String): Boolean {
            if (!Permissions.isGranted(context, Manifest.permission.READ_CONTACTS)) return false
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(raw))
            return try {
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                    ?.use { it.moveToFirst() } ?: false
            } catch (_: RuntimeException) {
                false
            }
        }
    }
}
