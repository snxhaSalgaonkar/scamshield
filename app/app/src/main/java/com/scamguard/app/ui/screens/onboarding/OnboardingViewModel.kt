package com.scamguard.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scamguard.app.data.settings.AppSettings
import com.scamguard.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds onboarding consent choices and marks onboarding as finished. */
class OnboardingViewModel(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setVoiceLayer(on: Boolean) = viewModelScope.launch { settings.setVoiceLayer(on) }
    fun setTrainingUpload(on: Boolean) = viewModelScope.launch { settings.setTrainingUpload(on) }
    fun setCaseSharing(on: Boolean) = viewModelScope.launch { settings.setCaseSharing(on) }
    fun finish() = viewModelScope.launch { settings.setOnboardingDone(true) }
}
