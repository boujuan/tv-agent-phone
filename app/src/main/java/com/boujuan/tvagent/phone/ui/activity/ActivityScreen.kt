package com.boujuan.tvagent.phone.ui.activity

import android.content.Context
import android.media.MediaPlayer
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.data.ActivityEntry
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.CommandResult
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.LabeledValue
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.SectionCard
import com.boujuan.tvagent.phone.ui.WhileVisible
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.theme.Amber
import com.boujuan.tvagent.phone.ui.theme.Green
import com.boujuan.tvagent.phone.ui.theme.Red
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ActivityViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val entries = MutableStateFlow<List<ActivityEntry>>(emptyList())
    val more = MutableStateFlow(false)
    val enabled = MutableStateFlow(true)
    val preview = MutableStateFlow<CommandResult?>(null)
    private var player: MediaPlayer? = null

    init {
        viewModelScope.launch { repo.changed.collect { refresh() } }
        viewModelScope.launch { repo.current.collect { entries.value = emptyList(); refresh() } }
    }

    /** The newest page, keeping older pages already loaded. */
    fun refresh() = call {
        val page = it.activity(PAGE)
        enabled.value = page.enabled
        val newest = page.entries.map { e -> e.t }.toSet()
        val older = entries.value.filter { e -> e.t !in newest && e.t < (page.entries.lastOrNull()?.t ?: Double.MAX_VALUE) }
        entries.value = page.entries + older
        if (older.isEmpty()) more.value = page.more
    }

    fun loadMore() = call {
        val page = it.activity(PAGE, before = entries.value.lastOrNull()?.t)
        entries.value = entries.value + page.entries
        more.value = page.more
    }

    fun mark(e: ActivityEntry, wrong: Boolean) = call {
        val r = it.mark(e, wrong)
        r.entry?.let { changed -> entries.value = entries.value.map { x -> if (x.t == changed.t) changed else x } }
    }

    fun again(e: ActivityEntry) = call(touch = true) { tell(it.text(e.heard).say) }

    /** What a sentence would do (teaching: the right action for what was heard). */
    fun check(sentence: String) = call { preview.value = it.decide(sentence) }

    fun teach(e: ActivityEntry) = call {
        val action = preview.value?.action ?: return@call
        it.learn(e.heard, action, e)
        preview.value = null
        tell(R.string.taught)
        refresh()
    }

    fun play(context: Context, e: ActivityEntry) = call {
        val name = e.recording ?: return@call
        val bytes = it.recording(name)
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "recording.wav").apply { writeBytes(bytes) }
        }
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.path)
            setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
            prepare()
            start()
        }
    }

    override fun onCleared() {
        player?.release()
    }

    companion object {
        const val PAGE = 60
    }
}

@Composable
fun ActivityScreen() {
    val vm = appViewModel { ActivityViewModel(it.repo) }
    Messages(vm)
    val entries by vm.entries.collectAsStateWithLifecycle()
    val more by vm.more.collectAsStateWithLifecycle()
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("all") }
    var open by remember { mutableStateOf<ActivityEntry?>(null) }
    WhileVisible(10_000) { vm.refresh() }
    val shown = entries.filter {
        when (filter) {
            "wrong" -> it.wrong == true || it.ok == false
            "voice" -> it.source in setOf("tv", "laptop", "phone")
            else -> true
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Summary(entries) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((key, label) in listOf("all" to R.string.filter_all, "voice" to R.string.filter_voice,
                    "wrong" to R.string.filter_wrong)) {
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(stringResource(label)) })
                }
            }
        }
        if (!enabled) item {
            Text(stringResource(R.string.history_off), color = Amber)
        }
        items(shown) { e -> EntryRow(e) { open = e } }
        if (more && filter == "all") item {
            OutlinedButton(onClick = { vm.loadMore() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) }
        }
    }
    open?.let { e -> EntrySheet(vm, entries.firstOrNull { it.t == e.t } ?: e) { open = null } }
}

private fun sourceIcon(source: String) = when (source) {
    "tv" -> "📺"
    "phone" -> "📱"
    "laptop" -> "💻"
    "mcp" -> "🤖"
    "text" -> "⌨"
    else -> "·"
}

/** How long a voice command took to be decided: after your last word when streamed, else transcribing + deciding. */
private fun responseMs(e: ActivityEntry): Double? =
    e.afterSpeechMs ?: e.sttMs?.let { it + (e.decideMs ?: 0.0) }

@Composable
private fun Summary(entries: List<ActivityEntry>) {
    val voice = entries.filter { it.source in setOf("tv", "laptop", "phone") }.take(40)
    val times = voice.mapNotNull { responseMs(it) }.reversed()
    fun median(xs: List<Double>) = xs.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
    SectionCard(stringResource(R.string.response_times)) {
        LabeledValue(stringResource(R.string.median_decided), median(times)?.let { "%.0f ms".format(it) } ?: "–")
        LabeledValue(stringResource(R.string.median_stt), median(voice.mapNotNull { it.sttMs })?.let { "%.0f ms".format(it) } ?: "–")
        LabeledValue(stringResource(R.string.median_doing), median(entries.take(40).mapNotNull { it.ms })?.let { "%.0f ms".format(it) } ?: "–")
        if (times.size >= 2) {
            val bar = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                val top = (times.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                val w = size.width / times.size
                times.forEachIndexed { i, t ->
                    val h = (t / top * size.height).toFloat()
                    drawRect(bar, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h))
                }
            }
            Text(stringResource(R.string.chart_caption, times.size), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntryRow(e: ActivityEntry, onOpen: () -> Unit) {
    val colors = if (e.wrong == true) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), colors = colors) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text(sourceIcon(e.source) + "  ")
                Text(if (e.heard.isNotEmpty()) "“${e.heard}”" else stringResource(R.string.heard_nothing),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(DateUtils.getRelativeTimeSpanString((e.t * 1000).toLong()).toString(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (e.say.isNotEmpty()) Text(e.say, color = when (e.ok) { true -> Green; false -> Red; null -> MaterialTheme.colorScheme.onSurface },
                style = MaterialTheme.typography.bodyMedium)
            Text(timesLine(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun timesLine(e: ActivityEntry): String {
    val parts = mutableListOf<String>()
    e.afterSpeechMs?.let { parts += stringResource(R.string.t_after_speech, it.toInt()) }
    e.sttMs?.let { parts += stringResource(R.string.t_stt, it.toInt()) }
    e.decideMs?.let { parts += stringResource(R.string.t_decide, e.via, if (it < 1) "%.2f".format(it) else "%.0f".format(it)) }
    e.ms?.let { parts += stringResource(R.string.t_done, it.toInt()) }
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(vm: ActivityViewModel, e: ActivityEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    var teaching by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (e.heard.isNotEmpty()) "“${e.heard}”" else stringResource(R.string.heard_nothing),
                style = MaterialTheme.typography.titleLarge)
            Text(e.say, color = if (e.ok == false) Red else Green, fontWeight = FontWeight.Medium)
            LabeledValue(stringResource(R.string.action), e.type)
            LabeledValue(stringResource(R.string.decided_via), listOf(e.via, e.rule).filter { it.isNotEmpty() }.joinToString(" · "))
            e.confidence?.let { LabeledValue(stringResource(R.string.confidence), "%.2f".format(it)) }
            e.stt?.let { LabeledValue(stringResource(R.string.speech_to_text), it) }
            e.audioS?.let { LabeledValue(stringResource(R.string.audio_length), "%.1f s".format(it)) }
            val cost = (e.cost ?: 0.0) + (e.sttCost ?: 0.0)
            if (cost > 0) LabeledValue(stringResource(R.string.cost), "$%.5f".format(cost))
            Text(timesLine(e), style = MaterialTheme.typography.bodySmall)
            e.action?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.mark(e, e.wrong != true) }) {
                    Text(stringResource(if (e.wrong == true) R.string.not_wrong else R.string.mark_wrong))
                }
                if (e.heard.isNotEmpty()) OutlinedButton(onClick = { teaching = true }) { Text(stringResource(R.string.teach)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.heard.isNotEmpty()) OutlinedButton(onClick = { vm.again(e); onClose() }) { Text(stringResource(R.string.do_again)) }
                if (e.recording != null) OutlinedButton(onClick = { vm.play(context, e) }) { Text(stringResource(R.string.play_recording)) }
            }
        }
    }
    if (teaching) TeachDialog(vm, e) { teaching = false }
}

/** "It should have done…": say it as a command, see what that does, and teach it for what was heard. */
@Composable
private fun TeachDialog(vm: ActivityViewModel, e: ActivityEntry, onClose: () -> Unit) {
    var sentence by rememberSaveable { mutableStateOf("") }
    val preview by vm.preview.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.preview.value = null }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.teach_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.teach_hint, e.heard))
                OutlinedTextField(sentence, { sentence = it }, singleLine = true,
                    label = { Text(stringResource(R.string.teach_as)) }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.check(sentence) }, enabled = sentence.isNotBlank()) { Text(stringResource(R.string.check)) }
                preview?.let { p ->
                    Text(if (p.action?.get("type")?.toString()?.contains("none") == true) stringResource(R.string.teach_not_understood)
                    else stringResource(R.string.teach_would, p.describe), fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.teach(e); onClose() },
                enabled = preview?.action != null && preview?.action?.get("type")?.toString()?.contains("none") != true) {
                Text(stringResource(R.string.teach))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
