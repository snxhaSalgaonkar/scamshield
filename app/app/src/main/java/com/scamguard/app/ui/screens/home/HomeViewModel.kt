package com.scamguard.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamguard.app.AppContainer
import com.scamguard.app.data.db.DecisionEntity
import com.scamguard.app.data.settings.AppSettings
import com.scamguard.app.service.GuardService
import com.scamguard.app.service.LiveWindow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Home screen shows. */
data class HomeState(
    val settings: AppSettings = AppSettings(),
    val callsChecked: Int = 0,
    val alertsThisWeek: Int = 0,
    val lastAlert: DecisionEntity? = null,
    val live: LiveWindow? = null,
)

/** Home: protection toggle, counters, last alert and the live warning banner. */
class HomeViewModel(private val container: AppContainer, now: Long = System.currentTimeMillis()) : ViewModel() {
    private val decisions = container.database.decisions()

    val state: StateFlow<HomeState> = combine(
        container.settings.settings,
        container.database.callEvents().observeCallsChecked(),
        decisions.observeAlertsSince(now - WEEK_MS),
        decisions.observeLatestAlert(),
        container.sessionManager.live,
    ) { settings, calls, alerts, last, live -> HomeState(settings, calls, alerts, last, live) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setProtection(enabled: Boolean) = viewModelScope.launch {
        container.settings.setProtectionEnabled(enabled)
        if (enabled) GuardService.start(container.appContext) else GuardService.stop(container.appContext)
    }

    private companion object {
        const val WEEK_MS = 7 * 24 * 3_600_000L
    }
}
