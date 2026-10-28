package com.scamguard.app

import android.content.Context
import com.scamguard.app.alerts.AlertDispatcher
import com.scamguard.app.bus.EventBus
import com.scamguard.app.data.NumbersRepository
import com.scamguard.app.data.db.AppDatabase
import com.scamguard.app.data.settings.SettingsRepository
import com.scamguard.app.remote.BackendGateway
import com.scamguard.app.remote.LocalOnlyGateway
import com.scamguard.app.reputation.LocalReputation
import com.scamguard.app.reputation.TrustedSenders
import com.scamguard.app.service.CallSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container. One instance lives in [ScamGuardApp]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = AppDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    val gateway: BackendGateway = LocalOnlyGateway()
    val eventBus = EventBus()

    val reputation = LocalReputation(appContext, database.reputation())
    val trustedSenders: TrustedSenders by lazy { TrustedSenders.fromAssets(appContext) }
    val numbers = NumbersRepository(database.trustedNumbers(), reputation, database.reputation().observeReported())

    val alertDispatcher = AlertDispatcher(appContext)
    val sessionManager = CallSessionManager(eventBus, database, settings, reputation, numbers, alertDispatcher)
}
