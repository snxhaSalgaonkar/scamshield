package com.scamguard.app

import android.content.Context
import com.scamguard.app.data.db.AppDatabase
import com.scamguard.app.data.settings.SettingsRepository
import com.scamguard.app.remote.BackendGateway
import com.scamguard.app.remote.LocalOnlyGateway
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
}
