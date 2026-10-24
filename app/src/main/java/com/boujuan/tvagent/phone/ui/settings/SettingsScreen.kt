package com.boujuan.tvagent.phone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.LangChoice
import com.boujuan.tvagent.phone.data.SettingsResponse
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.Choice
import com.boujuan.tvagent.phone.ui.Editable
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.SectionCard
import com.boujuan.tvagent.phone.ui.Toggle
import com.boujuan.tvagent.phone.ui.Waiting
import com.boujuan.tvagent.phone.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class SettingsViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val settings = MutableStateFlow<SettingsResponse?>(null)

    init {           // another agent: its settings (each visit re-reads them too: voice can change them)
        viewModelScope.launch {
            repo.current.map { it?.agent?.id }.distinctUntilChanged().drop(1).collect { settings.value = null; load() }
        }
    }

    fun load() = call { settings.value = it.settings() }

    /** One setting: the agent checks it, saves it in config.toml and uses it from the next command. */
    fun set(key: String, value: JsonElement) = call {
        settings.value = it.setSettings(buildJsonObject { put(key, value) })
        tell(R.string.saved)
    }

    fun setList(key: String, values: List<String>) = set(key, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
}

@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it.repo) }
    Messages(vm)
    val state by vm.settings.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) {              // on opening and on coming back: "from now on, audio in Spanish" said meanwhile
        vm.load()
        onPauseOrDispose { }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val r = state ?: run { Waiting(vm) { vm.load() }; return@Column }
        val s = r.settings
        Text(stringResource(R.string.settings_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard(stringResource(R.string.playback)) {
            Choice(stringResource(R.string.player), r.choices.player.map {
                it to stringResource(when (it) {
                    "tvplayer" -> R.string.player_tvplayer_long
                    "kodi" -> R.string.player_kodi_long
                    else -> R.string.player_just_long
                })
            }, s.player) { vm.set("player", JsonPrimitive(it)) }
            Choice(stringResource(R.string.max_resolution), r.choices.maxHeight.map { it to "${it}p" }, s.maxHeight) {
                vm.set("max_height", JsonPrimitive(it))
            }
            Bitrate(s.maxMbps) { vm.set("max_mbps", JsonPrimitive(it)) }
            Toggle(stringResource(R.string.probe_streams), s.probeStreams) { vm.set("probe_streams", JsonPrimitive(it)) }
            Toggle(stringResource(R.string.binge), s.binge) { vm.set("binge", JsonPrimitive(it)) }
            Toggle(stringResource(R.string.auto_skip), s.autoSkip) { vm.set("auto_skip", JsonPrimitive(it)) }
            Toggle(stringResource(R.string.check_episodes), s.checkEpisodes) { vm.set("check_episodes", JsonPrimitive(it)) }
            Text(stringResource(R.string.check_episodes_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(R.string.languages)) {
            Text(stringResource(R.string.languages_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val original = stringResource(R.string.original_language)
            Choice(stringResource(R.string.audio), r.choices.audioLanguage.ifEmpty { r.choices.audio }.map {
                it.code to if (it.code == "original") original else it.label
            }, s.audioLanguage) { vm.set("audio_language", JsonPrimitive(it)) }
            Choice(stringResource(R.string.native_language), r.choices.audio.map { it.code to it.label }, s.nativeLanguage) {
                vm.set("native_language", JsonPrimitive(it))
            }
            if (r.choices.subtitlesWhen.isNotEmpty()) {
                val native = r.choices.audio.firstOrNull { it.code == s.nativeLanguage }?.label ?: s.nativeLanguage
                Choice(stringResource(R.string.subtitles_when), r.choices.subtitlesWhen.map {
                    it.code to when (it.code) {
                        "foreign" -> stringResource(R.string.subtitles_when_foreign, native)
                        "always" -> stringResource(R.string.subtitles_when_always)
                        "off" -> stringResource(R.string.subtitles_when_off)
                        else -> it.label
                    }
                }, s.subtitles) { vm.set("subtitles", JsonPrimitive(it)) }
            }
            Text(stringResource(R.string.subtitles_order), style = MaterialTheme.typography.titleSmall)
            OrderedLanguages(r.choices.subtitles, s.subtitleLanguages) { vm.setList("subtitle_languages", it) }
        }
        SectionCard(stringResource(R.string.other)) {
            Editable(stringResource(R.string.twitch_favourites), s.twitchFavorites.joinToString(", ")) { text ->
                vm.setList("twitch_favorites", text.split(',').map { it.trim() }.filter { it.isNotEmpty() })
            }
            Toggle(stringResource(R.string.keep_history), s.logTranscripts) { vm.set("log_transcripts", JsonPrimitive(it)) }
        }
        SectionCard(stringResource(R.string.updates)) {
            Choice(stringResource(R.string.updates_mode), listOf(
                "auto" to stringResource(R.string.updates_mode_auto),
                "notify" to stringResource(R.string.updates_mode_notify),
                "off" to stringResource(R.string.updates_mode_off),
            ), s.updates) { vm.set("updates", JsonPrimitive(it)) }
            Text(stringResource(R.string.updates_mode_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Bitrate(mbps: Double, onSet: (Double) -> Unit) {
    var value by remember(mbps) { mutableFloatStateOf(if (mbps <= 0) 0f else mbps.toFloat()) }
    Column {
        Text(if (value <= 0f) stringResource(R.string.bitrate_unlimited) else stringResource(R.string.bitrate_limit, value.toInt()))
        Slider(value = value, onValueChange = { value = (it / 5).toInt() * 5f }, valueRange = 0f..200f,
            onValueChangeFinished = { onSet(value.toDouble()) })
        Text(stringResource(R.string.bitrate_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ticked languages are used, the first available first; arrows set the order. */
@Composable
private fun OrderedLanguages(options: List<LangChoice>, chosen: List<String>, onChange: (List<String>) -> Unit) {
    val ordered = chosen.mapNotNull { c -> options.firstOrNull { it.code == c } } + options.filter { it.code !in chosen }
    ordered.forEach { lang ->
        val on = lang.code in chosen
        val i = chosen.indexOf(lang.code)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = on, onCheckedChange = { tick ->
                val next = if (tick) chosen + lang.code else chosen - lang.code
                if (next.isNotEmpty()) onChange(next)
            })
            Text(lang.label, Modifier.weight(1f))
            if (on) {
                IconButton(onClick = { onChange(chosen.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up))
                }
                IconButton(onClick = { onChange(chosen.toMutableList().apply { add(i + 1, removeAt(i)) }) },
                    enabled = i < chosen.size - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down))
                }
            }
        }
    }
}
