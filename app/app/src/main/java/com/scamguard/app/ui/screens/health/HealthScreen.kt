package com.scamguard.app.ui.screens.health

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scamguard.app.R
import com.scamguard.app.actions.GuardDeviceAdmin
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.rememberHealth
import com.scamguard.app.ui.navigation.LocalAppContainer
import com.scamguard.app.ui.screens.settings.SectionTitle
import com.scamguard.app.ui.theme.SafeGreen
import com.scamguard.app.ui.theme.WarningAmber
import com.scamguard.app.util.Capabilities
import com.scamguard.app.util.Capability
import com.scamguard.app.util.CapabilityId
import com.scamguard.app.util.CapabilityState
import com.scamguard.app.util.Permissions

/** Health & permissions: every capability, its state, and a way to fix it. */
@Composable
fun HealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    var refresh by remember { mutableIntStateOf(0) }
    val health = rememberHealth()
    val items = remember(health, s.forwardingStatus, refresh) {
        Capabilities.list(
            health, Build.VERSION.SDK_INT,
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
            s.forwardingStatus, GuardDeviceAdmin.isActive(context),
        )
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Permanently denied permissions return at once without a dialog: send the user to app settings.
        if (result.isNotEmpty() && result.values.none { it }) settingsLauncher.launchSafely(Permissions.appDetails(context))
        refresh++
    }

    ScreenFrame(stringResource(R.string.health_title), onBack = onBack) {
        Text(stringResource(R.string.health_intro), style = MaterialTheme.typography.bodyMedium)
        SectionTitle(stringResource(R.string.health_required))
        items.filter { !it.optional }.forEach { CapabilityCard(it, context, permissionLauncher::launch, settingsLauncher::launchSafely) }
        SectionTitle(stringResource(R.string.health_optional))
        items.filter { it.optional }.forEach { CapabilityCard(it, context, permissionLauncher::launch, settingsLauncher::launchSafely) }
    }
}

private fun androidx.activity.result.ActivityResultLauncher<Intent>.launchSafely(intent: Intent) {
    try { launch(intent) } catch (_: ActivityNotFoundException) { }
}

@Composable
private fun CapabilityCard(
    c: Capability,
    context: Context,
    requestPermissions: (Array<String>) -> Unit,
    openSettings: (Intent) -> Unit,
) {
    val (title, desc) = texts(c.id)
    val stateLabel = stringResource(stateLabel(c.state))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    stateLabel,
                    color = stateColor(c.state),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { stateDescription = stateLabel },
                )
            }
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            detail(c)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = WarningAmber, modifier = Modifier.padding(top = 4.dp)) }
            val action = action(c, context)
            if (action != null) {
                OutlinedButton(
                    onClick = {
                        when (action) {
                            is Fix.Request -> requestPermissions(action.permissions)
                            is Fix.Open -> openSettings(action.intent)
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(if (action is Fix.Request) R.string.health_grant else R.string.health_open_settings)) }
            }
        }
    }
}

private sealed interface Fix {
    class Request(val permissions: Array<String>) : Fix
    class Open(val intent: Intent) : Fix
}

private fun action(c: Capability, context: Context): Fix? {
    if (c.state == CapabilityState.AVAILABLE || c.state == CapabilityState.UNSUPPORTED) return null
    return when (c.id) {
        CapabilityId.SMS -> Fix.Request(arrayOf(Manifest.permission.RECEIVE_SMS))
        CapabilityId.PHONE_STATE -> Fix.Request(arrayOf(Manifest.permission.READ_PHONE_STATE))
        CapabilityId.CONTACTS -> Fix.Request(arrayOf(Manifest.permission.READ_CONTACTS))
        CapabilityId.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Fix.Request(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            else Fix.Open(Permissions.appDetails(context))
        CapabilityId.FULL_SCREEN -> Fix.Open(Permissions.fullScreenIntentSettings(context))
        CapabilityId.CALL_SCREENING -> Permissions.screeningRoleIntent(context)?.let { Fix.Open(it) }
        CapabilityId.END_CALL ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Fix.Request(arrayOf(Manifest.permission.ANSWER_PHONE_CALLS)) else null
        CapabilityId.FORWARDING ->
            if (c.state == CapabilityState.DENIED) Fix.Request(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)) else null
        CapabilityId.USAGE_ACCESS -> Fix.Open(Permissions.usageAccessIntent())
        CapabilityId.VIDEO_CALLS -> Fix.Open(Permissions.notificationAccessIntent(context))
        CapabilityId.BATTERY -> Fix.Open(Permissions.batteryOptimizationIntent())
        CapabilityId.MICROPHONE -> Fix.Request(arrayOf(Manifest.permission.RECORD_AUDIO))
        CapabilityId.DEVICE_ADMIN -> Fix.Open(GuardDeviceAdmin.activationIntent(context))
    }
}

/** Extra explanation for degraded states. */
private fun detail(c: Capability): Int? = when {
    c.id == CapabilityId.FORWARDING && c.state == CapabilityState.UNSUPPORTED -> R.string.health_forwarding_unavailable
    c.id == CapabilityId.CALL_SCREENING && c.state == CapabilityState.UNSUPPORTED -> R.string.health_screening_old_android
    c.id == CapabilityId.END_CALL && c.state == CapabilityState.UNSUPPORTED -> R.string.health_end_call_old_android
    c.id == CapabilityId.CONTACTS && c.state == CapabilityState.DENIED -> R.string.health_contacts_denied
    else -> null
}

private fun texts(id: CapabilityId): Pair<Int, Int> = when (id) {
    CapabilityId.SMS -> R.string.cap_sms to R.string.cap_sms_desc
    CapabilityId.PHONE_STATE -> R.string.cap_phone to R.string.cap_phone_desc
    CapabilityId.CONTACTS -> R.string.cap_contacts to R.string.cap_contacts_desc
    CapabilityId.NOTIFICATIONS -> R.string.cap_notifications to R.string.cap_notifications_desc
    CapabilityId.FULL_SCREEN -> R.string.cap_full_screen to R.string.cap_full_screen_desc
    CapabilityId.CALL_SCREENING -> R.string.cap_screening to R.string.cap_screening_desc
    CapabilityId.END_CALL -> R.string.cap_end_call to R.string.cap_end_call_desc
    CapabilityId.FORWARDING -> R.string.cap_forwarding to R.string.cap_forwarding_desc
    CapabilityId.USAGE_ACCESS -> R.string.cap_usage to R.string.cap_usage_desc
    CapabilityId.VIDEO_CALLS -> R.string.cap_video_calls to R.string.cap_video_calls_desc
    CapabilityId.BATTERY -> R.string.cap_battery to R.string.cap_battery_desc
    CapabilityId.MICROPHONE -> R.string.cap_microphone to R.string.cap_microphone_desc
    CapabilityId.DEVICE_ADMIN -> R.string.cap_device_admin to R.string.cap_device_admin_desc
}

private fun stateLabel(s: CapabilityState): Int = when (s) {
    CapabilityState.AVAILABLE -> R.string.cap_state_available
    CapabilityState.DENIED -> R.string.cap_state_denied
    CapabilityState.RESTRICTED -> R.string.cap_state_restricted
    CapabilityState.UNSUPPORTED -> R.string.cap_state_unsupported
    CapabilityState.NOT_CONFIGURED -> R.string.cap_state_not_configured
}

@Composable
private fun stateColor(s: CapabilityState): Color = when (s) {
    CapabilityState.AVAILABLE -> SafeGreen
    CapabilityState.UNSUPPORTED -> MaterialTheme.colorScheme.outline
    else -> WarningAmber
}
