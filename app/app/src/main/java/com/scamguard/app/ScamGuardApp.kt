package com.scamguard.app

import android.app.Application

/** Application entry point; owns the [AppContainer]. */
class ScamGuardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Shortcut to the container from any context. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as ScamGuardApp).container
