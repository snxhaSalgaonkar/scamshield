package com.scamguard.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scamguard.app.AppContainer

/** The app container, provided by the hosting activity. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Creates a ViewModel from the [AppContainer] without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
