package com.scamguard.app.remote

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** The server refused a report for good (bad data, rate limit); retrying will not help. */
class ReportRejected(message: String) : Exception(message)

/**
 * Talks to the authority dashboard's Supabase project over its REST API (no SDK). The phone signs
 * in anonymously, registers a random device id once, then inserts rows into `reports`; row-level
 * security on the server only lets it insert its own reports. Holds the publishable key only.
 * Blocking calls: use from Dispatchers.IO.
 */
class AuthorityServer(context: Context, private val baseUrl: String, private val apiKey: String) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Inserts one row into `reports`. [report] must not carry `device_id`; it is added here. */
    fun insertReport(report: JSONObject) {
        repeat(2) { attempt ->
            val token = session()
            ensureProfile(token)
            val body = JSONObject(report.toString()).put("device_id", deviceId())
            val r = request("POST", "/rest/v1/reports", body.toString(), token, prefer = "return=minimal")
            when {
                r.code in 200..299 -> return
                r.code == 401 && attempt == 0 -> clearSession()
                r.code in 400..499 -> throw ReportRejected("reports: HTTP ${r.code} ${r.body.take(300)}")
                else -> throw IOException("reports: HTTP ${r.code}")
            }
        }
        throw IOException("reports: not authorised")
    }

    private fun deviceId(): String = prefs.getString(DEVICE_ID, null) ?: error("no device id before sign-in")

    /** A valid access token: the saved one, a refreshed one, or a new anonymous user. */
    private fun session(): String {
        val token = prefs.getString(ACCESS, null)
        if (token != null && prefs.getLong(EXPIRES_AT, 0) - EXPIRY_MARGIN_MS > System.currentTimeMillis()) return token

        prefs.getString(REFRESH, null)?.let { refresh ->
            val r = request("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh).toString())
            if (r.code in 200..299) return saveSession(JSONObject(r.body), newUser = false)
        }
        val r = request("POST", "/auth/v1/signup", JSONObject().put("data", JSONObject()).toString())
        if (r.code !in 200..299) throw IOException("anonymous sign-in: HTTP ${r.code} ${r.body.take(300)}")
        return saveSession(JSONObject(r.body), newUser = true)
    }

    /** A new anonymous user gets a new device id, so it can never collide with an earlier profile. */
    private fun saveSession(json: JSONObject, newUser: Boolean): String {
        val access = json.getString("access_token")
        val edit = prefs.edit()
            .putString(ACCESS, access)
            .putString(REFRESH, json.optString("refresh_token"))
            .putLong(EXPIRES_AT, System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
        if (newUser) {
            edit.putString(USER_ID, json.getJSONObject("user").getString("id"))
                .putString(DEVICE_ID, UUID.randomUUID().toString())
                .putBoolean(PROFILE_DONE, false)
        }
        edit.apply()
        return access
    }

    private fun ensureProfile(token: String) {
        if (prefs.getBoolean(PROFILE_DONE, false)) return
        val body = JSONObject().put("id", prefs.getString(USER_ID, null)).put("device_id", deviceId())
        val r = request("POST", "/rest/v1/profiles", body.toString(), token, prefer = "return=minimal")
        // 409: this user's profile already exists (an earlier attempt got through).
        if (r.code !in 200..299 && r.code != 409) throw IOException("profile: HTTP ${r.code} ${r.body.take(300)}")
        prefs.edit().putBoolean(PROFILE_DONE, true).apply()
    }

    private fun clearSession() = prefs.edit().remove(ACCESS).remove(EXPIRES_AT).apply()

    private class Response(val code: Int, val body: String)

    private fun request(method: String, path: String, body: String?, bearer: String? = null, prefer: String? = null): Response {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("apikey", apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            bearer?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { conn.setRequestProperty("Prefer", it) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            return Response(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val PREFS = "authority_server"
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val EXPIRES_AT = "expires_at"
        const val USER_ID = "user_id"
        const val DEVICE_ID = "device_id"
        const val PROFILE_DONE = "profile_done"
        const val EXPIRY_MARGIN_MS = 60_000L
        const val TIMEOUT_MS = 10_000
    }
}
