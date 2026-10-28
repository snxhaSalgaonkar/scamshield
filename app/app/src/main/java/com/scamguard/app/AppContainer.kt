package com.scamguard.app

import android.content.Context
import com.scamguard.app.alerts.AlertDispatcher
import com.scamguard.app.bus.EventBus
import com.scamguard.app.data.FeedbackRepository
import com.scamguard.app.data.NumbersRepository
import com.scamguard.app.data.db.AppDatabase
import com.scamguard.app.data.settings.SettingsRepository
import com.scamguard.app.remote.BackendGateway
import com.scamguard.app.remote.LocalOnlyGateway
import com.scamguard.app.reputation.LocalReputation
import com.scamguard.app.reputation.TrustedSenders
import com.scamguard.app.engine.CallRiskScorer
import com.scamguard.app.ml.ModelSource
import com.scamguard.app.ml.OnDeviceScamPredictionService
import com.scamguard.app.ml.ScamPredictionService
import com.scamguard.app.service.CallSessionManager
import com.scamguard.app.service.MessageGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

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

    /** All on-device ML (message, link and call models). Loaded in the background at start-up. */
    val predictions: ScamPredictionService = OnDeviceScamPredictionService(ModelSource.assets(appContext))
    val sessionManager = CallSessionManager(
        eventBus, database, settings, reputation, numbers, alertDispatcher, CallRiskScorer(predictions),
    )
    val messageGuard = MessageGuard(
        predictions, eventBus, database.messageChecks(), settings, alertDispatcher, { sessionManager.liveWindowId },
    )
    val feedback = FeedbackRepository(database)

    /** "Delete all my data": every table, in-memory windows and notifications. Settings are kept. */
    suspend fun deleteAllData() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        sessionManager.resetAll()
        reputation.importSeed()
    }
}
