package com.scamguard.app

import android.app.Application
import com.scamguard.app.alerts.NotificationChannels
import com.scamguard.app.util.Hashing
import com.scamguard.app.util.LatencyLog
import com.scamguard.app.util.PepperStore
import kotlinx.coroutines.launch

/** Application entry point; owns the [AppContainer] and starts the event pipeline. */
class ScamGuardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Hashing.install(PepperStore.loadOrCreate(this))
        LatencyLog.init(this)
        container = AppContainer(this)
        NotificationChannels.create(this)
        // Subscribe before any receiver can emit, so an SMS that woke the process is not lost.
        container.sessionManager.start(container.appScope)
        container.voiceGuard.start(container.appScope)
        container.centralReporter.start(container.appScope)
        container.appScope.launch { container.reputation.importSeed() }
        container.appScope.launch { container.predictions.ensureLoaded() }
    }
}

/** Shortcut to the container from any context. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as ScamGuardApp).container
