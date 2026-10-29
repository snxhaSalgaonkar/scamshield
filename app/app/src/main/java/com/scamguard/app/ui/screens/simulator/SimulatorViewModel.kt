package com.scamguard.app.ui.screens.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamguard.app.AppContainer
import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.collectors.SpeechText
import com.scamguard.app.engine.RiskLevel
import com.scamguard.app.service.MessageGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Outcome of one scenario run. */
data class ScenarioResult(val level: RiskLevel, val score: Int) {
    fun passed(expected: RiskLevel) = level == expected
}

/**
 * Plays scripted scenarios through the real pipeline (EventBus -> CallSessionManager ->
 * on-device call model -> Room -> AlertDispatcher) in the simulator lane. Demo SMS texts
 * go through the real on-device message model, and scripted speech through the same
 * transcript matcher the voice layer uses. Every result is tagged as simulated.
 */
class SimulatorViewModel(private val container: AppContainer) : ViewModel() {
    private val _results = MutableStateFlow<Map<Int, ScenarioResult>>(emptyMap())
    val results: StateFlow<Map<Int, ScenarioResult>> = _results.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Plays one scenario with real alerts, so the demo shows the banner or full-screen alert. */
    fun play(scenario: Scenario) = launchRun { run(scenario, quiet = false) }

    /** Plays all scenarios silently and records pass/fail. */
    fun runAll() = launchRun { Scenarios.all.forEach { run(it, quiet = true) } }

    private fun launchRun(block: suspend () -> Unit) {
        if (_running.value) return
        _running.value = true
        viewModelScope.launch {
            try { block() } finally { _running.value = false }
        }
    }

    private suspend fun run(scenario: Scenario, quiet: Boolean) {
        val session = container.sessionManager
        session.resetSimulation()
        val base = baseTime()
        val steps = scenario.events(base).map { it.ts to it } +
            scenario.messages(base).map { (ts, text) -> ts to text } +
            scenario.speech(base).flatMap { (ts, line) -> SpeechText.events(ts, line).map { ts to it } }
        var last = 0L
        for ((ts, step) in steps.sortedBy { it.first }) {
            val event = when (step) {
                is SignalEvent -> step
                is String -> MessageGuard.toEvent(container.predictions.analyzeMessage(step), ts)
                else -> null
            } ?: continue
            last = container.eventBus.emit(event, simulated = true, quiet = quiet)
        }
        session.awaitProcessed(last)
        val decision = session.simulated.value?.decision
        val result = ScenarioResult(decision?.level ?: RiskLevel.SAFE, decision?.score ?: 0)
        _results.update { it + (scenario.id to result) }
    }

    /** Today at 12:00 local time, so the odd-hours rule never changes the expected result. */
    private fun baseTime(): Long =
        LocalDate.now().atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
