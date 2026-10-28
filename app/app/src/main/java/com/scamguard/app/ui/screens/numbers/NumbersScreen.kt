package com.scamguard.app.ui.screens.numbers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scamguard.app.R
import com.scamguard.app.data.NumbersRepository
import com.scamguard.app.ui.components.ScreenFrame
import com.scamguard.app.ui.components.SurfacePanel
import com.scamguard.app.ui.components.maskOrUnknown
import com.scamguard.app.ui.navigation.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Trusted and reported numbers; typed numbers are hashed at once and never kept as text. */
class NumbersViewModel(private val numbers: NumbersRepository) : ViewModel() {
    val trusted = numbers.trusted.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reported = numbers.reported.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _invalid = MutableStateFlow(false)
    val invalid: StateFlow<Boolean> = _invalid.asStateFlow()

    fun add(raw: String, asTrusted: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        val ok = numbers.addTyped(raw, asTrusted)
        _invalid.value = !ok
        if (ok) onDone()
    }

    fun removeTrusted(hash: String) = viewModelScope.launch { numbers.removeTrusted(hash) }
    fun removeReport(hash: String) = viewModelScope.launch { numbers.removeReport(hash) }
}

/** Number-reporting flow: report or trust a number, review and remove entries. */
@Composable
fun NumbersScreen(onBack: () -> Unit) {
    val vm = containerViewModel { NumbersViewModel(it.numbers) }
    val trusted by vm.trusted.collectAsStateWithLifecycle()
    val reported by vm.reported.collectAsStateWithLifecycle()
    val invalid by vm.invalid.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    ScreenFrame(stringResource(R.string.numbers_title), onBack = onBack) {
        Text(stringResource(R.string.numbers_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(24) },
            label = { Text(stringResource(R.string.numbers_input)) },
            isError = invalid,
            supportingText = { if (invalid) Text(stringResource(R.string.numbers_invalid)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.add(input, asTrusted = false) { input = "" } }, enabled = input.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.numbers_report)) }
            OutlinedButton(onClick = { vm.add(input, asTrusted = true) { input = "" } }, enabled = input.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.numbers_trust)) }
        }
        NumberList(stringResource(R.string.numbers_reported), reported.map { it.numberHash to maskOrUnknown(it.mask.orEmpty()) }, vm::removeReport)
        NumberList(stringResource(R.string.numbers_trusted), trusted.map { it.numberHash to maskOrUnknown(it.mask) }, vm::removeTrusted)
    }
}

@Composable
private fun NumberList(title: String, rows: List<Pair<String, String>>, onRemove: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    SurfacePanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Column {
            if (rows.isEmpty()) Text(stringResource(R.string.numbers_none), Modifier.padding(vertical = 8.dp))
            rows.forEach { (hash, mask) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(mask, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(hash) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.numbers_remove, mask))
                    }
                }
            }
        }
    }
}
