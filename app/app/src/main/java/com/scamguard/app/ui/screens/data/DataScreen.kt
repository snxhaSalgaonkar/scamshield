package com.scamguard.app.ui.screens.data

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamguard.app.AppContainer
import com.scamguard.app.R
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.navigation.containerViewModel
import com.scamguard.app.ui.theme.AlertRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Export (to a file the user picks) and deletion of everything the app stores. */
class DataViewModel(private val container: AppContainer) : ViewModel() {
    val feedbackCount = container.database.feedback().observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun export(uri: Uri, done: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = try {
            val json = container.feedback.exportJson()
            withContext(Dispatchers.IO) {
                container.appContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null
            }
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
        done(ok)
    }

    fun deleteAll(done: () -> Unit) = viewModelScope.launch {
        container.deleteAllData()
        done()
    }
}

@Composable
fun DataScreen(onBack: () -> Unit) {
    val vm = containerViewModel { DataViewModel(it) }
    val count by vm.feedbackCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmExport by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val exported = stringResource(R.string.data_export_done)
    val failed = stringResource(R.string.data_export_failed)
    val deleted = stringResource(R.string.data_deleted)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri) { ok -> Toast.makeText(context, if (ok) exported else failed, Toast.LENGTH_SHORT).show() }
    }

    ScreenFrame(stringResource(R.string.data_title), onBack = onBack) {
        Text(stringResource(R.string.data_intro), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.data_feedback_count, count), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { confirmExport = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.data_export))
        }
        Button(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.data_delete)) }
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text(stringResource(R.string.data_export_confirm_title)) },
            text = { Text(stringResource(R.string.data_export_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmExport = false; picker.launch("scamguard-feedback.json") }) { Text(stringResource(R.string.action_export)) }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.data_delete_confirm_title)) },
            text = { Text(stringResource(R.string.data_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAll { Toast.makeText(context, deleted, Toast.LENGTH_SHORT).show() }
                }) { Text(stringResource(R.string.action_delete), color = AlertRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
