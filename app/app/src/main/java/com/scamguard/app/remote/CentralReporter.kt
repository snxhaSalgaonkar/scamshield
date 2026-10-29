package com.scamguard.app.remote

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.scamguard.app.BuildConfig
import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.data.db.DecisionDao
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.engine.CallWindow
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.service.LiveWindow
import com.scamguard.app.ui.Labels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Automated reporting of flagged calls to the authority dashboard (PS item 3). It only watches
 * the live call state and the saved decisions; it never changes detection, alerts or the call.
 *
 * One report per call, sent when the call
 * - reaches a red flag (at once),
 * - is reported as a scam by the user ("Report scam", during or after the call), or
 * - reached a warning and the call has ended.
 * Calls the user marked "This was me", known callers and simulated calls are not reported.
 *
 * The caller's number comes from the call screener ([CallerNumbers]); without the call-screening
 * role it is read from the call log once the call has ended ([CallLogNumbers]). Reports wait in a
 * small outbox file while the phone is offline. Off (does nothing) when no server is configured.
 */
class CentralReporter(
    context: Context,
    private val live: StateFlow<LiveWindow?>,
    private val decisions: DecisionDao,
) {
    private val appContext = context.applicationContext
    private val server: AuthorityServer? =
        if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            AuthorityServer(appContext, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        } else {
            null
        }
    private val outbox = File(appContext.noBackupFilesDir, OUTBOX)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val outboxLock = Mutex()
    private val sendLock = Mutex()

    private data class Tracked(val window: CallWindow, val peak: Decision)
    private var open: Tracked? = null
    private var scope: CoroutineScope? = null

    val configured: Boolean get() = server != null

    fun start(scope: CoroutineScope) {
        if (server == null) return
        CallerNumbers.enabled = true
        this.scope = scope
        // Calls reported before this version are not sent retroactively.
        if (!prefs.contains(SINCE)) prefs.edit().putLong(SINCE, System.currentTimeMillis()).apply()
        scope.launch { flush() }
        scope.launch { live.collect { onLive(it) } }
        scope.launch { decisions.observeAll().collect { rows -> rows.forEach { onSaved(it) } } }
    }

    private suspend fun onLive(lw: LiveWindow?) {
        val prev = open
        if (prev != null && (lw == null || lw.window.id != prev.window.id)) {
            open = null
            if (prev.peak.level >= RiskLevel.WARNING) attempt(prev, retry = true)
        }
        if (lw == null || !lw.window.isUnknown || lw.decision.gated) return

        val same = open?.takeIf { it.window.id == lw.window.id }
        val peak = if (same == null || higher(lw.decision, same.peak)) lw.decision else same.peak
        val t = Tracked(lw.window, peak)
        open = t
        val due = peak.level == RiskLevel.RED_FLAG || userReported(lw.window) ||
            (peak.level == RiskLevel.WARNING && lw.window.callEnded)
        if (due) attempt(t, retry = lw.window.callEnded)
    }

    /** "Report scam" tapped after the call (the window is gone, only the saved decision is left). */
    private suspend fun onSaved(e: DecisionEntity) {
        if (e.simulated || e.feedback != FeedbackKind.CONFIRMED_SCAM.name) return
        if (e.startedAt < prefs.getLong(SINCE, Long.MAX_VALUE) || isSent(e.windowId)) return
        if (open?.window?.id == e.windowId) return // the live path reports it
        val number = numberFor(e.numberHash, e.startedAt)
        if (number == null) {
            Log.w(TAG, "Report scam: no caller number (grant Call logs or set ScamShield as Caller ID & spam app)")
            return
        }
        enqueue(e.windowId, number, ReportPayload.call(e))
    }

    /** Sends the call's report once its number is known; after the call, waits for the call log. */
    private suspend fun attempt(t: Tracked, retry: Boolean) {
        if (isSent(t.window.id) || userSaidSafe(t.window)) return
        numberFor(t.window.numberHash, t.window.startedAt)?.let {
            enqueue(t.window.id, it, ReportPayload.call(t.window, t.peak, userReported(t.window)))
            return
        }
        if (!retry) return
        scope?.launch {
            for (wait in RETRY_DELAYS_MS) {
                delay(wait)
                if (isSent(t.window.id)) return@launch
                numberFor(t.window.numberHash, t.window.startedAt)?.let {
                    enqueue(t.window.id, it, ReportPayload.call(t.window, t.peak, userReported(t.window)))
                    return@launch
                }
            }
            Log.w(TAG, "Not reported: no caller number (hidden, or grant Call logs / set ScamShield as Caller ID & spam app)")
        }
    }

    private suspend fun numberFor(hash: String?, startedAt: Long): String? =
        CallerNumbers.lookup(hash) ?: withContext(Dispatchers.IO) { CallLogNumbers.find(appContext, startedAt) }

    private fun higher(a: Decision, b: Decision) = a.level > b.level || (a.level == b.level && a.score > b.score)

    private fun userSaidSafe(w: CallWindow) =
        w.events.any { it is SignalEvent.UserFeedback && it.kind == FeedbackKind.THIS_WAS_ME }

    private fun userReported(w: CallWindow) =
        w.events.any { it is SignalEvent.UserFeedback && it.kind == FeedbackKind.CONFIRMED_SCAM }

    private fun isSent(windowId: String) = windowId in (prefs.getStringSet(SENT, emptySet()) ?: emptySet())

    /** Queues one report per call (checked and marked under a lock), then sends in the background. */
    private suspend fun enqueue(windowId: String, number: String, call: ReportPayload.Call) {
        val queued = sendLock.withLock {
            if (isSent(windowId)) return@withLock false
            val english = englishContext()
            val payload = ReportPayload.build(number, call) { english.getString(Labels.reason(it)) }
            outboxLock.withLock { withContext(Dispatchers.IO) { save(load().put(payload.toString())) } }
            val sent = (prefs.getStringSet(SENT, emptySet()) ?: emptySet()).toMutableList().apply { add(windowId) }
            prefs.edit().putStringSet(SENT, sent.takeLast(MAX_SENT).toSet()).apply()
            true
        }
        if (queued) {
            Log.i(TAG, "Report queued (${call.level}, user reported: ${call.userReported})")
            scope?.launch { flush() }
        }
    }

    /** Sends every waiting report, oldest first; stops at the first network failure. */
    private suspend fun flush() {
        val s = server ?: return
        outboxLock.withLock {
            withContext(Dispatchers.IO) {
                val pending = load()
                val left = JSONArray()
                var offline = false
                for (i in 0 until pending.length()) {
                    val raw = pending.getString(i)
                    if (offline) { left.put(raw); continue }
                    try {
                        s.insertReport(JSONObject(raw))
                        Log.i(TAG, "Report sent to the authority server")
                    } catch (e: ReportRejected) {
                        Log.w(TAG, "Report dropped: ${e.message}")
                    } catch (e: IOException) {
                        Log.w(TAG, "Report kept for retry: ${e.message}")
                        offline = true
                        left.put(raw)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Report dropped: $e")
                    }
                }
                save(left)
            }
        }
    }

    private fun load(): JSONArray = try {
        if (outbox.exists()) JSONArray(outbox.readText()) else JSONArray()
    } catch (_: Exception) {
        JSONArray()
    }

    private fun save(items: JSONArray) {
        val kept = JSONArray()
        val from = (items.length() - MAX_OUTBOX).coerceAtLeast(0)
        for (i in from until items.length()) kept.put(items.get(i))
        if (kept.length() == 0) outbox.delete() else outbox.writeText(kept.toString())
    }

    /** Reasons go to the dashboard in English whatever the phone's language. */
    private fun englishContext(): Context =
        appContext.createConfigurationContext(Configuration(appContext.resources.configuration).apply { setLocale(Locale.ENGLISH) })

    private companion object {
        const val TAG = "CentralReporter"
        const val OUTBOX = "report_outbox.json"
        const val PREFS = "central_reporter"
        const val SENT = "sent_windows"
        const val SINCE = "reporting_since"
        const val MAX_OUTBOX = 50
        const val MAX_SENT = 200
        /** The call log entry is written a moment after the call ends. */
        val RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 15_000L, 40_000L)
    }
}
