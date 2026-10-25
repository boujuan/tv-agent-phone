package com.boujuan.tvagent.phone.ui.remote

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.data.Actions
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.AppEntry
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.SectionCard
import com.boujuan.tvagent.phone.ui.WhileVisible
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.rememberTap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import com.boujuan.tvagent.phone.data.AppsResponse
import com.boujuan.tvagent.phone.ui.AppIcon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import android.text.format.DateUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RemoteViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val apps = MutableStateFlow<AppsResponse?>(null)
    val volume = MutableStateFlow<Int?>(null)
    // Key presses go one after the other, in the order pressed (each is a command on the TV).
    private val keys = Channel<Pair<String, Boolean>>(capacity = 32)

    init {
        viewModelScope.launch {
            for ((key, long) in keys) {
                val api = api ?: continue
                runCatching { api.key(key, long) }.onFailure { fail(it) }
            }
        }
        viewModelScope.launch {
            repo.current.map { it?.agent?.id }.distinctUntilChanged().collect { apps.value = null; loadApps() }
        }
    }

    fun loadApps() = call { apps.value = it.apps() }
    /** The TV's apps read again now. */
    fun refreshApps() = call { apps.value = it.refreshApps(); tell(R.string.apps_refreshed) }
    fun key(key: String, long: Boolean = false) = keys.trySend(key to long)
    fun action(a: JsonObject) = call(touch = true) { val o = it.action(a); if (!o.ok) tell(o.say) }
    fun type(text: String) = call { it.type(text) }
    fun refreshVolume() = call { volume.value = it.volume().level }
    fun setVolume(level: Int) = call(touch = true) {
        volume.value = level
        it.action(Actions.volume("set", level))
    }
}

@Composable
fun RemoteScreen() {
    val vm = appViewModel { RemoteViewModel(it.repo) }
    Messages(vm)
    val apps by vm.apps.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val tap = rememberTap()
    var numbers by rememberSaveable { mutableStateOf(false) }
    WhileVisible(30_000) {
        vm.refreshVolume()
        if (vm.apps.value == null) vm.loadApps()
    }
    fun press(key: String, long: Boolean = false) { tap(); vm.key(key, long) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Key(Icons.Filled.Bedtime, R.string.key_sleep) { press("sleep") }
            Key(Icons.Filled.PowerSettingsNew, R.string.key_wake) { press("wake") }
            // The remote's gear and input buttons (KEYCODE_NOTIFICATION, KEYCODE_TV_INPUT): the launcher answers
            // these, and ignores INFO, MENU and SETTINGS.
            Key(Icons.Filled.Settings, R.string.key_gear) { press("gear") }
            Key(Icons.AutoMirrored.Filled.Input, R.string.key_input) { press("input") }
            Key(Icons.Filled.Dialpad, R.string.key_numbers) { tap(); numbers = !numbers }
        }
        DPad(onKey = { k, long -> press(k, long) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Key(Icons.AutoMirrored.Filled.ArrowBack, R.string.key_back) { press("back") }
            Key(Icons.Filled.Home, R.string.key_home) { press("home") }
            Key(Icons.Filled.Replay10, R.string.key_back10) { tap(); vm.action(Actions.transport("back", seconds = 10)) }
            Key(Icons.Filled.PlayArrow, R.string.key_play_pause) { press("play_pause") }
            Key(Icons.Filled.Forward30, R.string.key_forward30) { tap(); vm.action(Actions.transport("forward", seconds = 30)) }
        }
        if (numbers) {
            NumberPad { press(it) }
            Text(stringResource(R.string.numbers_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(R.string.volume)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Key(Icons.AutoMirrored.Filled.VolumeDown, R.string.key_volume_down) { press("volume_down"); vm.refreshVolume() }
                VolumeSlider(volume, Modifier.weight(1f).padding(horizontal = 8.dp)) { vm.setVolume(it) }
                Key(Icons.AutoMirrored.Filled.VolumeUp, R.string.key_volume_up) { press("volume_up"); vm.refreshVolume() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(volume?.let { stringResource(R.string.volume_level, it) } ?: "–", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { tap(); vm.action(Actions.volume("mute")) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mute))
                }
                TextButton(onClick = { tap(); vm.action(Actions.volume("unmute")) }) { Text(stringResource(R.string.unmute)) }
            }
        }
        apps?.let { a -> AppsCard(a, onRefresh = { vm.refreshApps() }) { tap(); vm.action(Actions.openApp(it)) } }
        SectionCard(stringResource(R.string.keyboard)) { Keyboard(onType = { vm.type(it) }, onKey = { press(it) }) }
    }
}

@Composable
private fun Key(icon: ImageVector, label: Int, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

/** Up, down, left, right around OK; holding OK long-presses it (the TV's context menus). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DPad(onKey: (String, Boolean) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(250.dp)) {
        Box(contentAlignment = Alignment.Center) {
            for ((key, angle, align) in listOf(Triple("up", 0f, Alignment.TopCenter), Triple("right", 90f, Alignment.CenterEnd),
                Triple("down", 180f, Alignment.BottomCenter), Triple("left", 270f, Alignment.CenterStart))) {
                Box(
                    Modifier.align(align).size(80.dp).clip(CircleShape)
                        .combinedClickable(onClick = { onKey(key, false) }, onLongClick = { onKey(key, true) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(dirLabel(key)),
                        modifier = Modifier.size(44.dp).rotate(angle))
                }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp)
                .clip(CircleShape).combinedClickable(onClick = { onKey("ok", false) }, onLongClick = { onKey("ok", true) })) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.key_ok), color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun dirLabel(key: String) = when (key) {
    "up" -> R.string.key_up
    "down" -> R.string.key_down
    "left" -> R.string.key_left
    else -> R.string.key_right
}

@Composable
private fun NumberPad(onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("0"))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                row.forEach { n ->
                    FilledTonalIconButton(onClick = { onKey(n) }, modifier = Modifier.padding(horizontal = 8.dp).size(64.dp)) {
                        Text(n, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeSlider(level: Int?, modifier: Modifier, onSet: (Int) -> Unit) {
    var dragging by rememberSaveable { mutableStateOf(false) }
    var value by rememberSaveable { mutableFloatStateOf(0f) }
    if (!dragging) value = (level ?: 0).toFloat()
    Slider(
        value = value, valueRange = 0f..100f, enabled = level != null, modifier = modifier,
        onValueChange = { dragging = true; value = it },
        onValueChangeFinished = { dragging = false; onSet(value.toInt()) },
    )
}

/** The apps on the TV (as its home screen lists them, read by the agent), with their icons: a tap opens one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsCard(r: AppsResponse, onRefresh: () -> Unit, onOpen: (String) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.apps), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.apps_refresh)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.apps.forEach { a ->
                Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(a.key) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (a.icon) AppIcon(a.`package`, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    else Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center) { Text(a.label.take(1), style = MaterialTheme.typography.titleMedium) }
                    Text(a.label, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val read = r.checked?.let { DateUtils.getRelativeTimeSpanString((it * 1000).toLong()).toString() }
        Text(when {
            r.checked == null -> stringResource(R.string.apps_not_read)
            r.source == "adb" -> stringResource(R.string.apps_read_adb, read.orEmpty())
            else -> stringResource(R.string.apps_read, read.orEmpty())
        } + (r.error?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Typing into the TV's focused field (a search box): sent as you submit; the TV's input types no accents. */
@Composable
private fun Keyboard(onType: (String) -> Unit, onKey: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = { if (text.isNotBlank()) { onType(text); text = "" } }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.type_on_tv)) },
        supportingText = { Text(stringResource(R.string.type_on_tv_hint)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            FilledTonalIconButton(onClick = send) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
            }
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { onKey("delete") }) {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.key_delete))
        }
        TextButton(onClick = { onKey("enter") }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.key_enter))
        }
        TextButton(onClick = { onKey("search") }) { Text(stringResource(R.string.key_search)) }
    }
    Spacer(Modifier.height(4.dp))
}
