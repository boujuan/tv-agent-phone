package com.boujuan.tvagent.phone.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.boujuan.tvagent.phone.R

// Settings controls shared by the Decisions and Settings screens: each change goes to the agent at once.

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

/** A text value saved with its own button (the agent checks it and says why not). */
@Composable
fun Editable(label: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        trailingIcon = {
            if (text != value) TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.save)) }
        })
}

@Composable
fun <T> Choice(label: String, options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(value) { open = false }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Column {
            OutlinedButton(onClick = { open = true }) { Text(options.firstOrNull { it.first == value }?.second ?: value.toString()) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(key) }) }
            }
        }
    }
}

/** A model the agent can use: its id, a readable name and, for some, why to pick it. */
data class ModelOption(val id: String, val label: String, @StringRes val note: Int? = null)

/**
 * A model chosen from a list (the panel's, benchmarked for the agent), in a full-screen picker that also takes any
 * other id typed in. The current one stays shown even when it isn't in the list.
 */
@Composable
fun ModelPicker(label: String, value: String, options: List<ModelOption>, onPick: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { it.id == value }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(current?.label ?: value.ifEmpty { "–" }, style = MaterialTheme.typography.bodyLarge)
                if (current != null) Text(value, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { open = true }) { Text(stringResource(R.string.change)) }
        }
    }
    if (open) ModelPickerDialog(label, value, options, onDismiss = { open = false }) { open = false; onPick(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerDialog(title: String, value: String, options: List<ModelOption>, onDismiss: () -> Unit,
                              onPick: (String) -> Unit) {
    var other by rememberSaveable { mutableStateOf(if (options.any { it.id == value }) "" else value) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(title) }, navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                })
            },
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad).imePadding()) {
                items(options, key = { it.id }) { m ->
                    ListItem(
                        headlineContent = { Text(m.label) },
                        supportingContent = {
                            Text(listOfNotNull(m.id, m.note?.let { stringResource(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = { RadioButton(selected = m.id == value, onClick = { onPick(m.id) }) },
                        modifier = Modifier.clickable { onPick(m.id) },
                    )
                    HorizontalDivider()
                }
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.model_other), style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(other, { other = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("provider/model") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (other.isNotBlank()) onPick(other.trim()) }))
                        OutlinedButton(onClick = { onPick(other.trim()) }, enabled = other.isNotBlank()) {
                            Text(stringResource(R.string.use))
                        }
                        Text(stringResource(R.string.model_other_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
