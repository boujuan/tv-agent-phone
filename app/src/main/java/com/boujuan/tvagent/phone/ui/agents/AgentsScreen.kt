package com.boujuan.tvagent.phone.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.app
import com.boujuan.tvagent.phone.data.Pairing
import com.boujuan.tvagent.phone.data.SavedAgent
import com.boujuan.tvagent.phone.ui.LabeledValue
import com.boujuan.tvagent.phone.ui.LocalSnackbar
import com.boujuan.tvagent.phone.ui.SectionCard
import com.boujuan.tvagent.phone.ui.Toggle
import kotlinx.coroutines.launch

/** The agents this phone is paired with: use one, change its addresses, or forget it (here and there). */
@Composable
fun AgentsScreen(onAdd: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repo
    val agents by repo.agents.collectAsStateWithLifecycle()
    val conn by repo.current.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var forgetting by remember { mutableStateOf<SavedAgent?>(null) }
    var editing by remember { mutableStateOf<SavedAgent?>(null) }
    val forgottenBoth = stringResource(R.string.forgotten_both)
    val forgottenHere = stringResource(R.string.forgotten_here)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        agents.orEmpty().forEach { a ->
            SectionCard(a.name + if (a.id == conn?.agent?.id) " ✓" else "") {
                LabeledValue(stringResource(R.string.addresses), a.urls.joinToString("\n"))
                LabeledValue(stringResource(R.string.fingerprint), Pairing.grouped(a.pin))
                LabeledValue(stringResource(R.string.this_phone_there), a.deviceName)
                if (a.version.isNotEmpty()) LabeledValue(stringResource(R.string.version), a.version)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (a.id != conn?.agent?.id) Button(onClick = { scope.launch { repo.select(a) } }) { Text(stringResource(R.string.use)) }
                    OutlinedButton(onClick = { editing = a }) { Text(stringResource(R.string.edit_addresses)) }
                    OutlinedButton(onClick = { forgetting = a }) { Text(stringResource(R.string.forget)) }
                }
            }
        }
        Button(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(" " + stringResource(R.string.add_agent))
        }
    }
    forgetting?.let { a ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text(stringResource(R.string.forget_title, a.name)) },
            text = { Text(stringResource(R.string.forget_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    forgetting = null
                    scope.launch {
                        val told = repo.forget(a)
                        snackbar.showSnackbar(if (told) forgottenBoth else forgottenHere)
                    }
                }) { Text(stringResource(R.string.forget)) }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    editing?.let { a ->
        var text by remember(a) { mutableStateOf(a.urls.joinToString("\n")) }
        val urls = text.lines().mapNotNull { Pairing.normalizeUrl(it) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.edit_addresses)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.addresses_hint))
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                }
            },
            confirmButton = {
                TextButton(onClick = { editing = null; scope.launch { repo.setUrls(a, urls) } }, enabled = urls.isNotEmpty()) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** This app's own preferences: theme, colours, haptics. */
@Composable
fun AppPrefsScreen() {
    val repo = LocalContext.current.app.repo
    val prefs by repo.prefs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(stringResource(R.string.theme)) {
            val options = listOf("system" to R.string.theme_system, "light" to R.string.theme_light, "dark" to R.string.theme_dark)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (key, label) ->
                    SegmentedButton(selected = prefs.theme == key, onClick = { scope.launch { repo.setPrefs(prefs.copy(theme = key)) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(stringResource(label)) }
                }
            }
            Toggle(stringResource(R.string.dynamic_color), prefs.dynamicColor) { scope.launch { repo.setPrefs(prefs.copy(dynamicColor = it)) } }
        }
        SectionCard(stringResource(R.string.feel)) {
            Toggle(stringResource(R.string.haptics), prefs.haptics) { scope.launch { repo.setPrefs(prefs.copy(haptics = it)) } }
        }
    }
}
