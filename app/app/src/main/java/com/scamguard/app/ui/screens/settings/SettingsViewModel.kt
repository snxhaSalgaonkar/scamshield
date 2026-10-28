package com.scamguard.app.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamguard.app.AppContainer
import com.scamguard.app.data.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings: consent levels, language and Demo Mode. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settings = container.settings

    val state: StateFlow<AppSettings?> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setVoiceLayer(on: Boolean) = viewModelScope.launch { settings.setVoiceLayer(on) }
    fun setTrainingUpload(on: Boolean) = viewModelScope.launch { settings.setTrainingUpload(on) }
    fun setCaseSharing(on: Boolean) = viewModelScope.launch { settings.setCaseSharing(on) }
    fun setDemoMode(on: Boolean) = viewModelScope.launch { settings.setDemoMode(on) }

    /** "system", "en" or "hi". Applied through AppCompat so it also works below Android 13. */
    fun setLanguage(tag: String) {
        viewModelScope.launch { settings.setLanguage(tag) }
        val locales = if (tag == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
