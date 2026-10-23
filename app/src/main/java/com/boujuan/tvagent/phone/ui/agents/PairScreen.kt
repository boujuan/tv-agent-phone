package com.boujuan.tvagent.phone.ui.agents

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.app
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.Pairing
import com.boujuan.tvagent.phone.data.PairingLink
import com.boujuan.tvagent.phone.data.Tls
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.describe
import com.boujuan.tvagent.phone.ui.theme.Red
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

sealed interface PairState {
    data object Idle : PairState
    data object Working : PairState
    /** A typed code: the certificate found at that address, for the user to compare with the panel's. */
    data class Confirm(val link: PairingLink) : PairState
    data object Done : PairState
    data class Failed(val error: Throwable) : PairState
}

class PairViewModel(private val repo: AgentRepository) : ViewModel() {
    val state = MutableStateFlow<PairState>(PairState.Idle)

    fun pair(link: PairingLink, deviceName: String) {
        if (state.value == PairState.Working) return
        state.value = PairState.Working
        viewModelScope.launch {
            state.value = try {
                repo.pair(link.urls, link.pin, link.code, deviceName, link.name)
                PairState.Done
            } catch (e: Exception) {
                PairState.Failed(e)
            }
        }
    }

    /** Typed address and code: fetch the certificate there first, without trusting it, to show its fingerprint. */
    fun check(address: String, code: String) {
        val url = Pairing.normalizeUrl(address) ?: return
        state.value = PairState.Working
        viewModelScope.launch {
            state.value = try {
                val uri = URI(url)
                val pin = withContext(Dispatchers.IO) { Tls.fingerprintOf(uri.host.trim('[', ']'), uri.port) }
                PairState.Confirm(PairingLink(listOf(url), Pairing.normalizeCode(code), pin, uri.host))
            } catch (e: Exception) {
                PairState.Failed(e)
            }
        }
    }

    fun reset() {
        state.value = PairState.Idle
    }
}

fun deviceName(context: Context): String =
    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
        ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

/**
 * Pairing with an agent: scan the QR code in the panel (Settings › Phones › Pair a phone), or type its address and
 * code. A link opened from elsewhere ([link]) is shown for the user to accept first.
 */
@Composable
fun PairScreen(modifier: Modifier = Modifier, link: PairingLink?, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val vm = appViewModel { PairViewModel(it.repo) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(deviceName(context)) }
    var typed by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var cameraOk by rememberSaveable {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraOk = it }
    LaunchedEffect(state) { if (state == PairState.Done) { vm.reset(); onDone() } }

    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.pair_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.this_phone_name)) })
        if (link != null) {
            val agents by context.app.repo.agents.collectAsStateWithLifecycle()
            val already = agents.orEmpty().firstOrNull { it.pin == link.pin }
            Text(stringResource(R.string.pair_link_found, link.name, link.urls.joinToString(", ")))
            Text(stringResource(R.string.fingerprint_is, Pairing.grouped(link.pin)), fontFamily = FontFamily.Monospace)
            if (already != null) Text(stringResource(R.string.pair_link_already, already.name), color = MaterialTheme.colorScheme.tertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.pair(link, name) }, enabled = state != PairState.Working) {
                    Text(stringResource(if (already != null) R.string.pair_again else R.string.pair))
                }
                onCancel?.let { TextButton(onClick = it) { Text(stringResource(R.string.cancel)) } }
            }
        } else {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !typed, onClick = { typed = false; vm.reset() },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.scan_qr)) }
                SegmentedButton(selected = typed, onClick = { typed = true; vm.reset() },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.type_code)) }
            }
            if (!typed) {
                if (cameraOk) {
                    QrScanner(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) { text ->
                        val found = Pairing.parse(text)
                        if (found != null && state is PairState.Idle) vm.pair(found, name)
                    }
                    Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow_camera)) }
                }
            } else {
                OutlinedTextField(address, { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.agent_address)) }, placeholder = { Text("192.168.1.51") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(code, { code = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.pairing_code)) }, placeholder = { Text("ABCD-EFGH") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                Button(onClick = { vm.check(address, code) },
                    enabled = Pairing.normalizeUrl(address) != null && Pairing.isCode(Pairing.normalizeCode(code)) &&
                        state != PairState.Working) {
                    Text(stringResource(R.string.connect))
                }
            }
            onCancel?.let { TextButton(onClick = it) { Text(stringResource(R.string.cancel)) } }
        }
        when (val s = state) {
            PairState.Working -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is PairState.Failed -> {
                Text(describe(context, s.error), color = Red)
                TextButton(onClick = { vm.reset() }) { Text(stringResource(R.string.try_again)) }
            }
            is PairState.Confirm -> AlertDialog(
                onDismissRequest = { vm.reset() },
                title = { Text(stringResource(R.string.compare_fingerprint)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.compare_fingerprint_hint))
                        Text(Pairing.grouped(s.link.pin), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.pair(s.link, name) }) { Text(stringResource(R.string.it_matches)) } },
                dismissButton = { TextButton(onClick = { vm.reset() }) { Text(stringResource(R.string.it_doesnt)) } },
            )
            else -> {}
        }
    }
}
