package com.scamguard.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Reads and writes [AppSettings] in DataStore Preferences. */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = edit { it[ONBOARDING] = done }
    suspend fun setProtectionEnabled(enabled: Boolean) = edit { it[PROTECTION] = enabled }
    suspend fun setVoiceLayer(enabled: Boolean) = edit { it[VOICE] = enabled }
    suspend fun setTrainingUpload(enabled: Boolean) = edit { it[TRAINING] = enabled }
    suspend fun setCaseSharing(enabled: Boolean) = edit { it[CASE_SHARING] = enabled }
    suspend fun setLanguage(tag: String) = edit { it[LANGUAGE] = tag }
    suspend fun setDemoMode(enabled: Boolean) = edit { it[DEMO] = enabled }

    suspend fun setRiskOverrides(overrides: Map<String, Int>) = edit {
        it[RISK_OVERRIDES] = overrides.entries.joinToString(",") { (k, v) -> "$k=$v" }
    }

    /** Stores the latest check; [baseline] is the last reliable reading, kept when null. */
    suspend fun setForwardingResult(status: ForwardingStatus, baseline: Boolean?, checkedAt: Long) = edit {
        it[FWD_STATUS] = status.name
        it[FWD_CHECKED_AT] = checkedAt
        if (baseline != null) it[FWD_BASELINE] = baseline
    }

    /** Wipes every stored preference. */
    suspend fun clear() = edit { it.clear() }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings() = AppSettings(
        onboardingDone = this[ONBOARDING] ?: false,
        protectionEnabled = this[PROTECTION] ?: true,
        voiceLayer = this[VOICE] ?: false,
        trainingUpload = this[TRAINING] ?: false,
        caseSharing = this[CASE_SHARING] ?: false,
        language = this[LANGUAGE] ?: "system",
        demoMode = this[DEMO] ?: false,
        riskOverrides = parseOverrides(this[RISK_OVERRIDES]),
        forwardingBaseline = this[FWD_BASELINE],
        forwardingStatus = this[FWD_STATUS]
            ?.let { runCatching { ForwardingStatus.valueOf(it) }.getOrNull() }
            ?: ForwardingStatus.NOT_CHECKED,
        forwardingCheckedAt = this[FWD_CHECKED_AT] ?: 0,
    )

    private fun parseOverrides(raw: String?): Map<String, Int> =
        raw.orEmpty().split(",").mapNotNull { pair ->
            val (k, v) = pair.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toIntOrNull()?.let { k to it }
        }.toMap()

    private companion object {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val PROTECTION = booleanPreferencesKey("protection_enabled")
        val VOICE = booleanPreferencesKey("consent_voice_layer")
        val TRAINING = booleanPreferencesKey("consent_training_upload")
        val CASE_SHARING = booleanPreferencesKey("consent_case_sharing")
        val LANGUAGE = stringPreferencesKey("language")
        val DEMO = booleanPreferencesKey("demo_mode")
        val RISK_OVERRIDES = stringPreferencesKey("risk_overrides")
        val FWD_BASELINE = booleanPreferencesKey("forwarding_baseline")
        val FWD_STATUS = stringPreferencesKey("forwarding_status")
        val FWD_CHECKED_AT = longPreferencesKey("forwarding_checked_at")
    }
}
