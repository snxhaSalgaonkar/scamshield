package com.scamguard.app.reputation

import android.content.Context
import org.json.JSONObject

/**
 * Bank, wallet and delivery SMS sender headers. Indian headers look like "VM-HDFCBK" or
 * "AD-SBIINB-S"; the 6-letter middle part identifies the sender.
 */
class TrustedSenders(private val codes: Set<String>) {

    fun isTrusted(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val parts = sender.uppercase().split("-")
        return parts.any { it in codes }
    }

    companion object {
        fun fromAssets(context: Context): TrustedSenders {
            val json = context.assets.open("trusted_senders.json").bufferedReader().use { it.readText() }
            val array = JSONObject(json).getJSONArray("senders")
            return TrustedSenders((0 until array.length()).map { array.getString(it).uppercase() }.toSet())
        }
    }
}
