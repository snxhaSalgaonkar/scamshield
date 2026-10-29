package com.scamguard.app.voice

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.scamguard.app.BuildConfig
import com.scamguard.app.MainActivity
import com.scamguard.app.R
import com.scamguard.app.alerts.NotificationChannels
import com.scamguard.app.appContainer
import com.scamguard.app.bus.CallChannel
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory
import com.scamguard.app.collectors.SpeechText
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.VoiceRules
import com.scamguard.app.service.LiveWindow
import com.scamguard.app.ui.Labels
import com.scamguard.app.util.Hashing
import com.scamguard.app.util.LatencyLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** One line of the listening log shown on the Live call check screen. */
data class ListenLogLine(val ts: Long, val kind: Kind, val text: String, val matches: List<SpeechText.Match> = emptyList()) {
    enum class Kind { INFO, HEARD, TACTIC, PROBLEM }
}

/** What call listening is doing, for the notification and the Live call check screen. Memory only. */
data class ListenState(
    val active: Boolean = false,
    val test: Boolean = false,
    val startedAt: Long = 0,
    val lines: List<String> = emptyList(),
    val partial: String = "",
    val found: Map<VoiceCategory, String> = emptyMap(),
    /** Current microphone level as the speech service reports it, roughly -2 (silence) to 10 dB. */
    val level: Float = -2f,
    @StringRes val problem: Int? = null,
    val log: List<ListenLogLine> = emptyList(),
    /** The call window being listened to, so the screen can open its details afterwards. */
    val windowId: String? = null,
    /** Whether the call-audio helper (accessibility) is on; without it real calls give silence. */
    val helperOn: Boolean = false,
    /** Whether the call itself is being recorded through Shizuku (no speaker needed). */
    val viaShizuku: Boolean = false,
)

/**
 * Call listening, started only by the user's Allow tap (or the "test listening" button).
 * The phone's speech service ([SpeechRecognizer]) hears the call through the microphone (the
 * call should be on speaker), each partial and final result goes through [SpeechText], and
 * every newly heard scam tactic becomes a [SignalEvent.VoiceKeyword] (category plus the few
 * matched words) for the call window, the same path the simulator's scripted speech takes.
 *
 * Test mode opens a simulated call window, so what is said there is scored, alerts and
 * appears in History tagged as simulated.
 *
 * No audio is recorded by this app. The transcript and the log live in memory for the
 * Live call check screen and are dropped when the next listening session starts or the app
 * process ends; only the matched words are saved with the call. Stops when the call ends,
 * when the user taps Stop, or after [MAX_MS] ([TEST_MS] in test mode).
 */
class CallListenService : LifecycleService() {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var stopped = false
    private var attemptPeak = Float.NEGATIVE_INFINITY
    private var loudest = Float.NEGATIVE_INFINITY
    private var heardText = false
    private var silentAttempts = 0
    /** Our own microphone capture streamed into the recogniser (Android 13+); null means the recogniser's own mic. */
    private var capture: CallAudioCapture? = null
    private var captureOkLogged = false
    private var onDevice = false
    private var useNetwork = false
    private var recogniserLogged: Boolean? = null
    private val emitted = HashMap<String, MutableSet<VoiceCategory>>()
    /** When the speech service last reported the end of an utterance (for the latency metrics). */
    private var speechEndAt = 0L
    /** The recogniser's language for this session, from Settings ("en-IN" or "hi-IN"). */
    private var language = LANGUAGE
    /** "Auto": Hindi with switching to English mid-call (on-device recogniser, Android 14+). */
    private var autoSwitch = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            log(ListenLogLine.Kind.INFO, getString(R.string.listen_log_stopped_user))
            stopSelf()
            return START_NOT_STICKY
        }
        if (_state.value.active) return START_NOT_STICKY
        val test = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
        _state.value = ListenState(active = true, test = test, startedAt = System.currentTimeMillis())
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: RuntimeException) {
            // Android refused a microphone service (not started from a visible tap).
            Log.w(TAG, "could not start listening: ${e.javaClass.simpleName}")
            _state.value = _state.value.copy(active = false)
            log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_log_refused))
            stopSelf()
            return START_NOT_STICKY
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            update { it.copy(problem = R.string.listen_unavailable) }
            log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_unavailable))
            lifecycleScope.launch { delay(8_000); stopSelf() }
            return START_NOT_STICKY
        }
        val helper = CallAudioHelper.isEnabled(this)
        update { it.copy(helperOn = helper) }
        log(if (helper) ListenLogLine.Kind.INFO else ListenLogLine.Kind.PROBLEM, getString(if (helper) R.string.listen_helper_on else R.string.listen_helper_off))
        lifecycleScope.launch {
            applyLanguage(appContainer.settings.current().listenLanguage)
            log(ListenLogLine.Kind.INFO, getString(if (test) R.string.listen_log_started_test else R.string.listen_log_started, languageLabel()))
            checkLanguageSupport()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startCapture(test)
            } else if (ShizukuAudio.status() == ShizukuAudio.Status.READY) {
                log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_log_shizuku_old_android))
            }
            if (test) startTestCall()
            listen()
            watchCall(test)
        }
        lifecycleScope.launch { watchSilence() }
        return START_NOT_STICKY
    }

    /** Test mode: a simulated unknown call, so the words are scored and saved like a real call's. */
    private suspend fun startTestCall() {
        val c = appContainer
        c.sessionManager.resetSimulation()
        val now = System.currentTimeMillis()
        c.eventBus.emit(
            SignalEvent.CallRinging(
                ts = now, numberHash = Hashing.numberHash(TEST_NUMBER), prefix = "+91 90", isUnknown = true,
                isInternational = false, isHidden = false, mask = "+91 90•••••00",
            ),
            simulated = true,
        )
        val seq = c.eventBus.emit(SignalEvent.CallActive(now + 1), simulated = true)
        c.sessionManager.awaitProcessed(seq)
    }

    private fun currentWindow(): LiveWindow? = with(appContainer.sessionManager) {
        if (_state.value.test) simulated.value else live.value
    }

    /** Stops with the call; in test mode after [TEST_MS]. Also refreshes the notification. */
    private suspend fun watchCall(test: Boolean) {
        val started = _state.value.startedAt
        var sawCall = false
        while (!stopped) {
            val now = System.currentTimeMillis()
            val w = currentWindow()
            // Outgoing and other calls the app opened no window for are followed through the audio mode.
            val phoneCall = isInCall(this)
            if ((w != null && !w.window.callEnded) || phoneCall) sawCall = true
            val over = when {
                test -> now - started > TEST_MS
                sawCall -> !phoneCall && (w == null || w.window.callEnded)
                else -> now - started > NO_CALL_MS
            }
            if (over) {
                log(ListenLogLine.Kind.INFO, getString(if (sawCall && !test) R.string.listen_log_call_ended else R.string.listen_log_time_up))
                break
            }
            if (now - started > MAX_MS) break
            refresh()
            delay(1_000)
        }
        stopSelf()
    }

    /** No sound at all after a while usually means the phone gives other apps silence during calls. */
    private suspend fun watchSilence() {
        delay(SILENCE_CHECK_MS)
        if (!heardText && loudest < RMS_SOUND) {
            val msg = if (_state.value.viaShizuku) R.string.listen_silent_shizuku else R.string.listen_silent
            update { it.copy(problem = msg) }
            log(ListenLogLine.Kind.PROBLEM, getString(msg))
        }
    }

    /**
     * Reads the audio ourselves: the call itself through Shizuku when it is running and allowed,
     * otherwise the microphone (which keeps working during calls only with the helper on).
     */
    private suspend fun startCapture(test: Boolean) {
        val shizuku = ShizukuAudio.status()
        // A test has no call, so the call-audio sources would only give silence.
        val shell = if (!test && shizuku == ShizukuAudio.Status.READY) ShizukuAudio.bind(applicationContext) else null
        log(
            if (shell != null || test) ListenLogLine.Kind.INFO else ListenLogLine.Kind.PROBLEM,
            getString(
                when {
                    shell != null -> R.string.listen_log_shizuku_ready
                    test -> R.string.listen_log_shizuku_test
                    shizuku == ShizukuAudio.Status.READY -> R.string.listen_log_shizuku_failed
                    shizuku == ShizukuAudio.Status.NO_PERMISSION -> R.string.listen_log_shizuku_permission
                    else -> R.string.listen_log_shizuku_off
                },
            ),
        )
        if (stopped) return
        capture = CallAudioCapture(
            onLevel = { level ->
                main.post {
                    if (level > attemptPeak) attemptPeak = level
                    if (level > loudest) loudest = level
                    _state.value = _state.value.copy(level = level)
                    if (!captureOkLogged && capture?.gotAudio == true && level >= RMS_SOUND) {
                        captureOkLogged = true
                        log(ListenLogLine.Kind.INFO, getString(R.string.listen_log_capture_ok))
                    }
                }
            },
            onSourceChanged = { name, viaShizuku ->
                main.post {
                    update { it.copy(viaShizuku = viaShizuku) }
                    log(ListenLogLine.Kind.INFO, getString(if (viaShizuku) R.string.listen_log_source_shizuku else R.string.listen_log_source, name))
                }
            },
            onBlocked = { name -> main.post { log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_log_blocked, name)) } },
            shell = shell,
            voip = isVoipCall(),
            onShellLost = {
                main.post {
                    update { it.copy(viaShizuku = false) }
                    log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_log_shizuku_lost))
                }
            },
        ).also { it.start() }
    }

    /** A WhatsApp / Telegram / Skype call: its audio is VoIP, not on the phone's call path. */
    private fun isVoipCall(): Boolean {
        val channel = currentWindow()?.window?.channel
        if (channel == CallChannel.VOICE_APP || channel == CallChannel.VIDEO_APP) return true
        if (channel == CallChannel.CELLULAR && currentWindow()?.window?.callEnded == false) return false
        return getSystemService(AudioManager::class.java)?.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    /** Picks the recogniser language from the setting; "auto" needs Android 14+ to switch, else Hindi. */
    private fun applyLanguage(tag: String) {
        language = if (tag == LANGUAGE_HINDI || tag == LANGUAGE_AUTO) LANGUAGE_HINDI else LANGUAGE
        autoSwitch = tag == LANGUAGE_AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (tag == LANGUAGE_AUTO && !autoSwitch) log(ListenLogLine.Kind.INFO, getString(R.string.listen_log_auto_unsupported))
    }

    private fun languageLabel(): String = if (autoSwitch) "$LANGUAGE_HINDI + $LANGUAGE" else language

    /** Logs whether the speech service has the language offline (Android 13+). */
    private fun checkLanguageSupport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val probe = SpeechRecognizer.createSpeechRecognizer(this)
        try {
            var answered = false
            probe.checkRecognitionSupport(recognizeIntent(), Executors.newSingleThreadExecutor(), object : RecognitionSupportCallback {
                override fun onSupportResult(s: RecognitionSupport) {
                    if (answered) return
                    answered = true
                    val offline = language in s.installedOnDeviceLanguages
                    val online = s.onlineLanguages.isEmpty() || language in s.onlineLanguages
                    main.post {
                        log(ListenLogLine.Kind.INFO, getString(R.string.listen_log_support, yesNo(online), yesNo(offline)))
                        // Without the pack the on-device recogniser cannot do Hindi; say how to get it.
                        if (!offline && language == LANGUAGE_HINDI) log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_log_hindi_pack))
                        probe.destroy()
                    }
                }

                override fun onError(error: Int) {
                    main.post { probe.destroy() }
                }
            })
        } catch (_: RuntimeException) {
            probe.destroy()
        }
    }

    private fun yesNo(b: Boolean) = getString(if (b) R.string.yes else R.string.no)

    private fun recognizeIntent(pipe: ParcelFileDescriptor? = null) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        .also {
            if (autoSwitch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val both = arrayListOf(LANGUAGE_HINDI, LANGUAGE)
                it.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                    .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, both)
                    .putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, both)
            }
            if (pipe != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, CallAudioCapture.RATE)
            }
        }

    private fun listen() {
        if (stopped) return
        val r = recognizer ?: createRecognizer().also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        attemptPeak = Float.NEGATIVE_INFINITY
        try {
            r.startListening(recognizeIntent(capture?.newPipe()))
        } catch (e: RuntimeException) {
            Log.w(TAG, "startListening failed: ${e.javaClass.simpleName}")
            restart(RETRY_MS, fresh = true)
        }
    }

    /**
     * With our own audio, the on-device recogniser is preferred (it accepts streamed audio and
     * needs no network); if it fails, the network recogniser is used instead.
     */
    private fun createRecognizer(): SpeechRecognizer {
        onDevice = capture != null && !useNetwork && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if (recogniserLogged != onDevice) {
            recogniserLogged = onDevice
            val what = getString(if (onDevice) R.string.listen_recogniser_device else R.string.listen_recogniser_network)
            log(ListenLogLine.Kind.INFO, getString(R.string.listen_log_recogniser, what))
        }
        return if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
    }

    /** The recogniser stops after each utterance; start it again so listening is continuous. */
    private fun restart(afterMs: Long, fresh: Boolean = false) {
        if (stopped) return
        if (fresh) {
            recognizer?.destroy()
            recognizer = null
        }
        main.postDelayed(::listen, afterMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            speechEndAt = System.currentTimeMillis()
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (capture != null) return // levels come from our own capture
            if (rmsdB > attemptPeak) attemptPeak = rmsdB
            if (rmsdB > loudest) loudest = rmsdB
            _state.value = _state.value.copy(level = rmsdB)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.let { heard(it, final = false) }
        }

        override fun onResults(results: Bundle?) {
            if (speechEndAt > 0) LatencyLog.record(LatencyLog.Metric.STT, System.currentTimeMillis() - speechEndAt)
            val text = best(results)
            if (text != null) heard(text, final = true) else noSpeech()
            restart(0)
        }

        override fun onError(error: Int) {
            if (BuildConfig.DEBUG) Log.d(TAG, "recogniser error $error")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    noSpeech()
                    restart(0)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    update { it.copy(problem = R.string.listen_no_permission) }
                    log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_no_permission))
                    stopped = true
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    update { it.copy(problem = R.string.listen_offline) }
                    log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_log_error, errorName(error)))
                    restart(RETRY_MS)
                }
                else -> {
                    log(ListenLogLine.Kind.PROBLEM, getString(R.string.listen_log_error, errorName(error)))
                    if (onDevice) useNetwork = true
                    restart(RETRY_MS, fresh = true)
                }
            }
        }
    }

    /** One listening attempt ended with no words. Logged with its loudest sound, so silence and noise can be told apart. */
    private fun noSpeech() {
        val peak = attemptPeak
        val silent = peak < RMS_SOUND
        if (silent) silentAttempts++ else silentAttempts = 0
        // Log every attempt with sound, but only every fifth silent one, so the log stays readable.
        if (!silent || silentAttempts % 5 == 1) {
            val db = if (peak.isFinite()) "%.1f".format(peak) else "–"
            log(ListenLogLine.Kind.INFO, getString(if (silent) R.string.listen_log_silence else R.string.listen_log_not_understood, db))
        }
    }

    private fun errorName(e: Int): String = when (e) {
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_AUDIO -> "audio (microphone busy?)"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recogniser busy"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language unavailable"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "server disconnected"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "too many requests"
        else -> "code $e"
    }

    private fun best(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    /** Matches the text and emits each tactic once per call window; keeps a few lines for display. */
    private fun heard(text: String, final: Boolean) {
        heardText = true
        silentAttempts = 0
        val now = System.currentTimeMillis()
        val matches = SpeechText.matches(text)
        val w = currentWindow()?.window
        if (w != null && _state.value.windowId != w.id) _state.value = _state.value.copy(windowId = w.id)
        val fresh = if (w != null && !w.callEnded) {
            val sent = emitted.getOrPut(w.id) { mutableSetOf() }
            matches.filter { sent.add(it.category) }.also { new ->
                // Before emitting, so the alert this phrase causes can be timed against it.
                if (new.isNotEmpty()) {
                    val spoken = if (final && speechEndAt > 0 && now - speechEndAt < SPEECH_END_VALID_MS) speechEndAt else now
                    LatencyLog.voiceHeard(w.id, now, spoken)
                }
            }.onEach {
                appContainer.eventBus.emit(SignalEvent.VoiceKeyword(now, it.category, it.phrase), simulated = _state.value.test)
            }
        } else {
            matches.filter { it.category !in _state.value.found }
        }
        if (final) {
            log(ListenLogLine.Kind.HEARD, text, matches)
            if (w != null) lifecycleScope.launch(Dispatchers.IO) { TranscriptStore.append(applicationContext, w.id, now, text) }
        }
        fresh.forEach {
            log(ListenLogLine.Kind.TACTIC, getString(R.string.listen_log_tactic, it.phrase, getString(Labels.voiceCategory(it.category.name))))
        }
        update { s ->
            s.copy(
                lines = if (final) (s.lines + text).takeLast(MAX_LINES) else s.lines,
                partial = if (final) "" else text,
                found = s.found + matches.filter { it.category !in s.found }.associate { it.category to it.phrase },
                problem = if (s.problem in CLEARED_BY_SPEECH) null else s.problem,
            )
        }
    }

    private fun log(kind: ListenLogLine.Kind, text: String, matches: List<SpeechText.Match> = emptyList()) {
        val line = ListenLogLine(System.currentTimeMillis(), kind, text, matches)
        _state.value = _state.value.copy(log = (_state.value.log + line).takeLast(MAX_LOG))
        if (BuildConfig.DEBUG) Log.d(TAG, "${kind.name}: ${if (kind == ListenLogLine.Kind.HEARD) "${text.length} chars" else text}")
    }

    private fun update(change: (ListenState) -> ListenState) {
        _state.value = change(_state.value)
        refresh()
    }

    private fun refresh() {
        if (stopped) return
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification())
        } catch (_: SecurityException) {
            // Notifications turned off; listening continues.
        }
    }

    private fun notification(): android.app.Notification {
        val s = _state.value
        val stop = PendingIntent.getService(
            this, 0, Intent(this, CallListenService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(this, 1, openScreenIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val summary = buildList {
            s.problem?.let { add(getString(it)) }
            add(riskLine(this@CallListenService, currentWindow()?.decision, s))
            if (s.found.isNotEmpty()) add(getString(R.string.listen_found, s.found.values.joinToString { "“$it”" }))
            val heard = (s.lines + s.partial).filter { it.isNotBlank() }.takeLast(2)
            if (heard.isEmpty()) add(getString(if (s.viaShizuku) R.string.listen_waiting_shizuku else R.string.listen_waiting)) else addAll(heard.map { "“$it”" })
        }
        return NotificationCompat.Builder(this, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (s.test) R.string.listen_title_test else R.string.listen_title))
            .setContentText(summary.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.joinToString("\n")))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.listen_stop), stop)
            .build()
    }

    override fun onDestroy() {
        stopped = true
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        capture?.stop()
        capture = null
        // The shell service stays bound: the response engine shares it (end call, freeze, lock).
        emitted.clear()
        if (_state.value.test) {
            // Close the simulated test call so it is scored once more and saved in History.
            appContainer.eventBus.emit(
                SignalEvent.CallEnded(System.currentTimeMillis(), System.currentTimeMillis() - _state.value.startedAt),
                simulated = true,
            )
        }
        log(ListenLogLine.Kind.INFO, getString(R.string.listen_log_stopped))
        // Keep the log and what was heard for the screen; drop the live transcript lines.
        _state.value = _state.value.copy(active = false, partial = "", level = -2f)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallListen"
        private const val ACTION_STOP = "com.scamguard.app.voice.STOP"
        private const val EXTRA_TEST = "test"
        private const val NOTIFICATION_ID = 0x4002
        /** Indian English; Google's recogniser also writes common Hindi words in Roman script with it. */
        const val LANGUAGE = "en-IN"
        const val LANGUAGE_HINDI = "hi-IN"
        const val LANGUAGE_AUTO = "auto"
        private const val TEST_NUMBER = "+919000000000"
        private const val MAX_LINES = 30
        private const val MAX_LOG = 200
        private const val RETRY_MS = 1_000L
        private const val SPEECH_END_VALID_MS = 10_000L
        private const val SILENCE_CHECK_MS = 12_000L
        private const val RMS_SOUND = 2f
        private const val NO_CALL_MS = 60_000L
        private const val TEST_MS = 3 * 60_000L
        private const val MAX_MS = 90 * 60_000L

        /** Problems that hearing words proves wrong. */
        private val CLEARED_BY_SPEECH = setOf(R.string.listen_silent, R.string.listen_silent_shizuku, R.string.listen_offline)

        private val _state = MutableStateFlow(ListenState())
        val state: StateFlow<ListenState> = _state.asStateFlow()

        fun start(context: Context, test: Boolean) {
            val intent = Intent(context, CallListenService::class.java).putExtra(EXTRA_TEST, test)
            ContextCompat.startForegroundService(context, intent)
        }

        /** A phone or messaging-app call is in progress (any direction); needs no permission. */
        fun isInCall(context: Context): Boolean {
            val mode = context.getSystemService(AudioManager::class.java)?.mode
            return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CallListenService::class.java).setAction(ACTION_STOP))
        }

        fun openScreenIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_LISTEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** "Risk 82 · threats / isolation", or just the stage when no call is being scored. */
        fun riskLine(context: Context, decision: Decision?, s: ListenState): String {
            val stage = context.getString(stageLabel(VoiceRules.stage(s.found.keys)))
            return if (decision != null) context.getString(R.string.listen_risk, decision.score, stage) else stage
        }

        @StringRes fun stageLabel(stage: VoiceRules.Stage): Int = when (stage) {
            VoiceRules.Stage.NONE -> R.string.stage_none
            VoiceRules.Stage.AUTHORITY -> R.string.stage_authority
            VoiceRules.Stage.ACCUSATION -> R.string.stage_accusation
            VoiceRules.Stage.THREAT -> R.string.stage_threat
            VoiceRules.Stage.DEMAND -> R.string.stage_demand
        }
    }
}
