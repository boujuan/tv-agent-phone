package com.boujuan.tvagent.phone.ui.playing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.data.Actions
import com.boujuan.tvagent.phone.data.SettingsChoices
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.NowPlayingResponse
import com.boujuan.tvagent.phone.data.Track
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.RemoteImage
import com.boujuan.tvagent.phone.ui.SectionCard
import com.boujuan.tvagent.phone.ui.Waiting
import com.boujuan.tvagent.phone.ui.WhileVisible
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.clock
import com.boujuan.tvagent.phone.ui.rememberTap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.ImeAction

class NowPlayingViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val state = MutableStateFlow<NowPlayingResponse?>(null)
    val loaded = MutableStateFlow(false)
    /** The languages to ask for (the agent's settings choices); null until read, or from an agent without them. */
    val languages = MutableStateFlow<SettingsChoices?>(null)

    init {
        viewModelScope.launch { repo.changed.collect { refresh() } }
        viewModelScope.launch { repo.current.collect { state.value = null; loaded.value = false } }
    }

    fun refresh() = viewModelScope.launch {
        val api = api ?: return@launch
        try {
            state.value = api.nowPlaying()
            loaded.value = true
            unreachable.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (unreachable.value == null) fail(e)        // said once, not every 2 s
            unreachable.value = e
        }
    }

    fun action(a: JsonObject) = call(touch = true) { val o = it.action(a); tell(o.say) }
    /** A sentence, as if said ("go to the part where they kiss"). */
    fun say(text: String) = call(touch = true) { tell(it.text(text).say) }
    fun loadLanguages() = viewModelScope.launch {
        if (languages.value == null) languages.value = runCatching { api?.settings()?.choices }.getOrNull()
    }
    fun audio(t: Track) = call(touch = true) { it.setAudioTrack(t.index) }
    fun subtitle(t: Track?) = call(touch = true) { it.setSubtitleTrack(t?.index) }
}

@Composable
fun NowPlayingScreen() {
    val vm = appViewModel { NowPlayingViewModel(it.repo) }
    Messages(vm)
    val np by vm.state.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val tap = rememberTap()
    WhileVisible(2_000) { vm.refresh() }
    LaunchedEffect(vm) { vm.loadLanguages() }
    val languages by vm.languages.collectAsStateWithLifecycle()
    fun act(a: JsonObject) { tap(); vm.action(a) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val item = np?.nowPlaying
        val player = np?.player
        if (item == null && player == null) {
            SectionCard {
                if (loaded) Text(stringResource(R.string.nothing_playing), style = MaterialTheme.typography.titleMedium)
                else Waiting(vm) { vm.refresh() }
                if (loaded) {
                    Text(stringResource(R.string.nothing_playing_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { act(Actions.continueWatching()) }) { Text(stringResource(R.string.continue_watching)) }
                }
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RemoteImage(item?.thumbnail ?: item?.poster,
                Modifier.width(120.dp).aspectRatio(if (item?.thumbnail != null) 16f / 9 else 2f / 3).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item?.name ?: stringResource(R.string.something_playing), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                if (item?.season != null && item.episode != null) {
                    Text(stringResource(R.string.season_episode, item.season, item.episode) +
                        (item.episodeName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge)
                }
                player?.let {
                    Text(stringResource(when (it.app) {
                        "tvplayer" -> R.string.player_tvplayer
                        "kodi" -> R.string.player_kodi
                        else -> R.string.player_just
                    }),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (player != null) {
            Position(player.positionS, player.durationS) { act(Actions.transport("seek_to", seconds = it.toInt())) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val series = item?.season != null
                if (series) FilledTonalIconButton(onClick = { act(Actions.next(-1)) }) { Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous_episode)) }
                FilledTonalIconButton(onClick = { act(Actions.transport("back", seconds = 10)) }) { Icon(Icons.Filled.Replay10, stringResource(R.string.key_back10)) }
                FilledIconButton(onClick = { act(Actions.transport(if (player.playing) "pause" else "resume")) }, modifier = Modifier.size(64.dp)) {
                    Icon(if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (player.playing) R.string.pause else R.string.resume), Modifier.size(36.dp))
                }
                FilledTonalIconButton(onClick = { act(Actions.transport("forward", seconds = 30)) }) { Icon(Icons.Filled.Forward30, stringResource(R.string.key_forward30)) }
                if (series) FilledTonalIconButton(onClick = { act(Actions.next(1)) }) { Icon(Icons.Filled.SkipNext, stringResource(R.string.next_episode)) }
            }
        }
        SectionCard(stringResource(R.string.skip)) {
            val pos = player?.positionS ?: 0
            val inside = np?.segments.orEmpty().firstOrNull { pos in it.startS until it.endS }
            if (inside != null) {
                Button(onClick = { act(Actions.skip(inside.what)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.skip_this, segmentName(inside.what)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (what in listOf("intro", "recap", "credits")) {
                    OutlinedButton(onClick = { act(Actions.skip(what)) }, modifier = Modifier.weight(1f)) {
                        Text(segmentName(what), maxLines = 1)
                    }
                }
            }
            if (player != null) {
                OutlinedButton(onClick = { act(Actions.replay()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.ClosedCaption, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.replay))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { act(Actions.transport("stop")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.stop))
                }
                OutlinedButton(onClick = { act(Actions.cancel()) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                SleepTimer { act(it) }
            }
            Text(stringResource(R.string.cancel_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (player != null) SceneSearch { tap(); vm.say(it) }
        TracksCard(np, languages, onAudio = { tap(); vm.audio(it) }, onSubtitle = { tap(); vm.subtitle(it) }, onAction = { act(it) })
    }
}

@Composable
private fun segmentName(what: String) = stringResource(
    when (what) {
        "intro" -> R.string.segment_intro
        "recap" -> R.string.segment_recap
        else -> R.string.segment_credits
    }
)

/** The position: follows the TV while untouched; dragging and letting go jumps there. */
@Composable
private fun Position(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(position, dragging) { if (!dragging) value = position.toFloat() }
    Column {
        Slider(
            value = value.coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = duration > 0,
            onValueChange = { dragging = true; value = it },
            onValueChangeFinished = { dragging = false; onSeek(value.toLong()) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(value.toLong()), style = MaterialTheme.typography.bodySmall)
            if (duration > 0) Text("-" + clock(duration - value.toLong()), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TracksCard(np: NowPlayingResponse?, languages: SettingsChoices?, onAudio: (Track) -> Unit,
                       onSubtitle: (Track?) -> Unit, onAction: (JsonObject) -> Unit) {
    val tracks = np?.tracks
    SectionCard(stringResource(R.string.tracks)) {
        if (np?.nowPlaying != null && languages != null) Languages(languages, audio = np.player?.app != "just", onAction)
        if (tracks == null) {
            Text(stringResource(R.string.tracks_player_only), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onAction(Actions.subtitles("on")) }) { Text(stringResource(R.string.subtitles_on)) }
                FilledTonalButton(onClick = { onAction(Actions.subtitles("off")) }) { Text(stringResource(R.string.subtitles_off)) }
            }
            return@SectionCard
        }
        TrackPicker(stringResource(R.string.audio), tracks.audio, tracks.audio.firstOrNull { it.index == tracks.audioCurrent },
            allowOff = false) { it?.let(onAudio) }
        TrackPicker(stringResource(R.string.subtitles), tracks.subtitles,
            tracks.subtitles.firstOrNull { it.index == tracks.subtitleCurrent }, allowOff = true, onPick = onSubtitle)
    }
}

/** A sleep timer: after this one (episode or film), in 30 min or an hour; or the TV off after this one. */
@Composable
private fun SleepTimer(onAction: (JsonObject) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedIconButton(onClick = { open = true }) { Icon(Icons.Filled.Bedtime, stringResource(R.string.timer)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((label, action) in listOf(
                R.string.timer_after to Actions.timer(),
                R.string.timer_30 to Actions.timer(minutes = 30),
                R.string.timer_60 to Actions.timer(minutes = 60),
                R.string.timer_off to Actions.timer(then = "off"),
                R.string.timer_cancel to Actions.timer(cancel = true),
            )) {
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; onAction(action) })
            }
        }
    }
}

/** "Go to the part where…": looked for in the subtitles, in the language it's written in (the prefix follows it). */
@Composable
private fun SceneSearch(onSay: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val prefix = stringResource(R.string.scene_prefix)
    fun go() {
        if (text.isNotBlank()) onSay(prefix.trim() + " " + text.trim())     // resources lose a trailing space
        text = ""
    }
    SectionCard(stringResource(R.string.scene_title)) {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.scene_label)) },
            trailingIcon = { IconButton(onClick = { go() }, enabled = text.isNotBlank()) { Icon(Icons.Filled.Search, stringResource(R.string.go)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { go() }))
        Text(stringResource(R.string.scene_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Audio or subtitles in a language, as saying "ponlo en español" does: the track, or another copy that has it. */
@Composable
private fun Languages(choices: SettingsChoices, audio: Boolean, onAction: (JsonObject) -> Unit) {
    val original = stringResource(R.string.original_language)
    val audios = choices.audioLanguage.ifEmpty { choices.audio }.map { it.code to if (it.code == "original") original else it.label }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Just Player: only subtitles can be asked for (the agent changes its audio in TV Agent Player and Kodi)
        if (audio) LanguageMenu(stringResource(R.string.audio_in), audios) { onAction(Actions.language(audio = it)) }
        LanguageMenu(stringResource(R.string.subtitles_in), choices.subtitles.map { it.code to it.label } +
            ("off" to stringResource(R.string.off))) { onAction(Actions.language(subtitles = it)) }
    }
    Text(stringResource(R.string.language_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun LanguageMenu(label: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }, enabled = options.isNotEmpty()) { Text(label, maxLines = 1) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(code) })
            }
        }
    }
}

@Composable
private fun TrackPicker(title: String, options: List<Track>, current: Track?, allowOff: Boolean, onPick: (Track?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val off = stringResource(R.string.off)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Column {
            OutlinedButton(onClick = { open = true }, enabled = options.isNotEmpty()) {
                Text(current?.let { trackLabel(it) } ?: off, maxLines = 1)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allowOff) DropdownMenuItem(text = { Text(off) }, onClick = { open = false; onPick(null) })
                options.forEach { t ->
                    DropdownMenuItem(text = { Text(trackLabel(t)) }, onClick = { open = false; onPick(t) })
                }
            }
        }
    }
}

private fun trackLabel(t: Track): String {
    val parts = listOfNotNull(
        t.label.ifEmpty { t.language.ifEmpty { "#${t.index + 1}" } },
        t.name.takeIf { it.isNotBlank() && it != t.label },
        t.codec.takeIf { it.isNotBlank() }?.uppercase(),
        t.channels?.let { "${it}ch" },
        "forced".takeIf { t.forced },
    )
    return parts.joinToString(" · ")
}
