package com.scamguard.app.service

import com.scamguard.app.alerts.AlertDispatcher
import com.scamguard.app.bus.Envelope
import com.scamguard.app.bus.EventBus
import com.scamguard.app.bus.FeedbackKind
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.data.EventMapping
import com.scamguard.app.data.NumbersRepository
import com.scamguard.app.data.TrustSource
import com.scamguard.app.data.db.AppDatabase
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.data.settings.AppSettings
import com.scamguard.app.data.settings.SettingsRepository
import com.scamguard.app.engine.CallWindow
import com.scamguard.app.engine.CallerContext
import com.scamguard.app.engine.Decision
import com.scamguard.app.engine.RiskConfig
import com.scamguard.app.engine.RiskEngine
import com.scamguard.app.engine.RiskInput
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.engine.TransactionHistory
import com.scamguard.app.engine.WindowState
import com.scamguard.app.engine.WindowTracker
import com.scamguard.app.reputation.LocalReputation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** The live state of a call window as the UI sees it. */
data class LiveWindow(val window: CallWindow, val decision: Decision)

/**
 * The pipeline's heart: collects bus events, keeps the [CallWindow] through a
 * [WindowTracker], scores every change with [RiskEngine], persists the result and hands it
 * to [AlertDispatcher]. Live and simulated events run in separate lanes.
 * Windows from known callers are scored (silently) but never persisted.
 */
class CallSessionManager(
    private val bus: EventBus,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val reputation: LocalReputation,
    private val numbers: NumbersRepository,
    private val dispatcher: AlertDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val liveTracker = WindowTracker { UUID.randomUUID().toString() }
    private val simTracker = WindowTracker { "sim-" + UUID.randomUUID().toString() }

    private val _live = MutableStateFlow<LiveWindow?>(null)
    /** The open live window and its latest decision, or null when idle. */
    val live: StateFlow<LiveWindow?> = _live.asStateFlow()

    private val _simulated = MutableStateFlow<LiveWindow?>(null)
    val simulated: StateFlow<LiveWindow?> = _simulated.asStateFlow()

    private val _processedSeq = MutableStateFlow(0L)

    /** Subscribes to the bus immediately (so no early event is lost) and starts the expiry ticker. */
    fun start(scope: CoroutineScope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { env -> mutex.withLock { handle(env) } }
        }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                mutex.withLock { tick() }
            }
        }
    }

    /** Suspends until the event with sequence number [seq] has been fully processed. */
    suspend fun awaitProcessed(seq: Long) {
        _processedSeq.first { it >= seq }
    }

    /** Clears the simulator lane before a scripted scenario. */
    suspend fun resetSimulation() = mutex.withLock {
        simTracker.reset()
        _simulated.value = null
    }

    /** Forgets in-memory windows (after "delete all my data"). */
    suspend fun resetAll() = mutex.withLock {
        liveTracker.reset(); simTracker.reset()
        _live.value = null; _simulated.value = null
        dispatcher.reset()
    }

    /**
     * Records the user's verdict on a window: trusts or reports the number and, if the
     * window is still open, feeds the verdict back into the engine.
     */
    suspend fun submitFeedback(windowId: String, kind: FeedbackKind) {
        val entity = db.decisions().get(windowId) ?: return
        db.decisions().setFeedback(windowId, kind.name)
        val hash = entity.numberHash
        if (!entity.simulated && hash != null) {
            when (kind) {
                FeedbackKind.THIS_WAS_ME -> numbers.trust(hash, entity.prefix, entity.mask, TrustSource.THIS_WAS_ME)
                FeedbackKind.CONFIRMED_SCAM -> numbers.report(hash, entity.prefix, entity.mask)
            }
        }
        val tracker = if (entity.simulated) simTracker else liveTracker
        val stillOpen = mutex.withLock { tracker.current?.id == windowId }
        if (stillOpen) {
            awaitProcessed(bus.emit(SignalEvent.UserFeedback(clock(), kind), simulated = entity.simulated, quiet = true))
        } else if (kind == FeedbackKind.THIS_WAS_ME) {
            dispatcher.cancel(windowId)
        }
    }

    private suspend fun handle(env: Envelope) {
        try {
            val s = settings.current()
            if (env.simulated || s.protectionEnabled) process(env, s)
        } finally {
            _processedSeq.value = env.seq
        }
    }

    private suspend fun process(env: Envelope, s: AppSettings) {
        val config = RiskConfig().withOverrides(s.riskOverrides)
        val tracker = if (env.simulated) simTracker else liveTracker
        val window = tracker.onEvent(env.event, config)
        val persisted = window?.takeIf { it.isUnknown }

        persistEvent(env, persisted)
        if (window == null) {
            if (tracker.current == null) publish(env.simulated, null)
            return
        }
        val decision = RiskEngine.evaluate(buildInput(window, env.simulated, s, env.event.ts), config)
        publish(env.simulated, LiveWindow(window, decision))
        if (persisted != null) persistDecision(persisted, decision, env.simulated)
        if (!env.quiet) dispatcher.onDecision(window, decision, env.event)
    }

    /** Re-scores an ongoing call (talk time grows) and closes expired windows. */
    private suspend fun tick() {
        val current = _live.value ?: return
        val now = clock()
        val s = settings.current()
        val config = RiskConfig().withOverrides(s.riskOverrides)
        if (!current.window.isOpenAt(now, config)) {
            liveTracker.reset()
            _live.value = null
            return
        }
        if (current.window.state != WindowState.ACTIVE) return
        val decision = RiskEngine.evaluate(buildInput(current.window, false, s, now), config)
        if (decision == current.decision.copy(evaluatedAt = now)) return
        _live.value = LiveWindow(current.window, decision)
        if (current.window.isUnknown) persistDecision(current.window, decision, simulated = false)
        dispatcher.onDecision(current.window, decision, null)
    }

    private suspend fun buildInput(window: CallWindow, simulated: Boolean, s: AppSettings, now: Long): RiskInput {
        if (simulated) return RiskInput(window, voiceLayerEnabled = s.voiceLayer, now = now)
        val hash = window.numberHash
        val caller = CallerContext(
            callsLastHour = hash?.let { db.callEvents().ringsSince(it, now - HOUR_MS) }?.coerceAtLeast(1) ?: 1,
            reported = reputation.isReported(hash),
            knownBankNumber = reputation.isKnownBank(hash),
        )
        val history = TransactionHistory.from(
            db.signalEvents().recentDebitAmounts(before = window.startedAt, limit = 20),
            db.signalEvents().knownPayees(before = window.startedAt),
        )
        return RiskInput(window, caller, history, s.voiceLayer, now)
    }

    private fun publish(simulated: Boolean, value: LiveWindow?) {
        if (simulated) _simulated.value = value else _live.value = value
    }

    private suspend fun persistEvent(env: Envelope, window: CallWindow?) {
        val e = env.event
        if (window != null) EventMapping.callEvent(e, window, env.simulated)?.let { db.callEvents().insert(it) }
        EventMapping.signalEvent(e, window?.id, env.simulated)?.let { db.signalEvents().insert(it) }
    }

    private suspend fun persistDecision(window: CallWindow, d: Decision, simulated: Boolean) {
        val existing = db.decisions().get(window.id)
        val peakScore = maxOf(existing?.peakScore ?: 0, d.score)
        val peakLevel = maxOf(existing?.peakLevel?.let { RiskLevel.valueOf(it) } ?: d.level, d.level)
        db.decisions().upsert(
            DecisionEntity(
                windowId = window.id,
                ts = d.evaluatedAt,
                startedAt = window.startedAt,
                score = d.score,
                level = d.level.name,
                peakScore = peakScore,
                peakLevel = peakLevel.name,
                l1 = d.l1, l2 = d.l2, l3 = d.l3,
                scamType = d.scamType.name,
                reasonCodes = d.reasonCodes.joinToString(",") { it.name },
                comboFired = d.comboFired,
                numberHash = window.numberHash,
                prefix = window.prefix,
                mask = window.mask,
                callEnded = window.callEnded,
                simulated = simulated,
                feedback = existing?.feedback,
            )
        )
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val HOUR_MS = 3_600_000L
    }
}
