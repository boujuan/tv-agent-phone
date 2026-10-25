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
import com.boujuan.tvagent.phone.ui.actionLabel
import com.boujuan.tvagent.phone.ui.viaLabel
import com.boujuan.tvagent.phone.ui.methodLabel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.boujuan.tvagent.phone.data.ApiException
import com.boujuan.tvagent.phone.ui.Toggle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow

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

    /** A command's big parts, read when asked for (t -> its parts; empty: none kept). */
    val details = MutableStateFlow<Map<Double, JsonObject>>(emptyMap())

    fun loadDetails(e: ActivityEntry) = call { details.value += e.t to it.activityDetails(e.t) }

    /** "This was right": kept as a test by the agent (an agent before it: just not wrong). */
    fun right(e: ActivityEntry) = call {
        try {
            tell(kept(it.right(e, e.heard).kept))
        } catch (x: ApiException) {
            if (x.code != 404) throw x
            it.mark(e, false)
        }
        refresh()
    }

    /** "This was wrong, it should be…": marked and kept as a test; taught too when asked (and understood). */
    fun wrong(e: ActivityEntry, words: String, should: JsonObject?, episode: String, moment: String, teach: Boolean) = call {
        try {
            tell(kept(it.wrong(e, words, should, episode, moment).kept))
        } catch (x: ApiException) {
            if (x.code != 404) throw x
            it.mark(e, true)                    // an agent before it: marked wrong only
        }
        if (teach && should != null && e.heard.isNotBlank()) {
            it.learn(e.heard, should, e)
            tell(R.string.taught)
        }
        preview.value = null
        refresh()
    }

    private fun kept(what: List<String>): Int = when {
        what.any { it.contains("voice test") } -> R.string.kept_voice_test
        what.any { it.contains("sentences") } -> R.string.kept_sentence
        what.any { it.contains("case") } -> R.string.kept_case
        else -> R.string.kept_nothing
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
            "voice" -> it.spoken
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
    val voice = entries.filter { it.spoken }.take(40)
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
            if (e.taught != null) Text(stringResource(R.string.taught_chip), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
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
    e.decideMs?.let { parts += stringResource(R.string.t_decide, viaLabel(e.via), if (it < 1) "%.2f".format(it) else "%.0f".format(it)) }
    e.ms?.let { parts += stringResource(R.string.t_done, it.toInt()) }
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(vm: ActivityViewModel, e: ActivityEntry, onClose: () -> Unit) {
    val context = LocalContext.current
    var wrong by remember { mutableStateOf(false) }
    val allDetails by vm.details.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (e.heard.isNotEmpty()) "“${e.heard}”" else stringResource(R.string.heard_nothing),
                style = MaterialTheme.typography.titleLarge)
            Text(e.say, color = when (e.ok) { true -> Green; false -> Red; null -> MaterialTheme.colorScheme.onSurface },
                fontWeight = FontWeight.Medium)
            LabeledValue(stringResource(R.string.action), actionLabel(e.action, e.type))
            LabeledValue(stringResource(R.string.decided_via), listOf(viaLabel(e.via), e.rule).filter { it.isNotEmpty() }.joinToString(" · ") +
                (e.confidence?.let { " · %.2f".format(it) } ?: ""))
            (e.sttModel ?: e.stt)?.let { model ->
                val where = when (e.sttWhere) { "hosted" -> stringResource(R.string.where_hosted); "local" -> stringResource(R.string.where_local); else -> null }
                val hint = listOfNotNull(e.sttLanguage?.let { stringResource(R.string.stt_hint_used, it) },
                    e.sttVocabulary?.let { stringResource(R.string.stt_names_used, it) })
                LabeledValue(stringResource(R.string.speech_to_text), (listOfNotNull(model, where) + hint).joinToString(" · "))
            }
            e.audioS?.let { LabeledValue(stringResource(R.string.audio_length), "%.1f s".format(it)) }
            val cost = (e.cost ?: 0.0) + (e.sttCost ?: 0.0)
            if (cost > 0) LabeledValue(stringResource(R.string.cost), "$%.5f".format(cost))
            Text(timesLine(e), style = MaterialTheme.typography.bodySmall)
            e.trace?.takeIf { it.isNotEmpty() }?.let { Trace(it) }
            // What its searches read and the models' prompts: big, so asked for here, not with the list
            val details = allDetails[e.t]
            when {
                details != null && details.isEmpty() -> Text(stringResource(R.string.details_none), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                details != null -> details.forEach { (key, value) -> DetailPart(key, value.toString()) }
                e.trace != null -> TextButton(onClick = { vm.loadDetails(e) }) { Text(stringResource(R.string.details_read)) }
            }
            var raw by remember { mutableStateOf(false) }
            if (e.action != null || e.settings != null) {
                TextButton(onClick = { raw = !raw }) { Text(stringResource(if (raw) R.string.details_hide else R.string.details)) }
            }
            if (raw) {
                e.action?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                e.settings?.let {
                    Text(stringResource(R.string.settings_then), style = MaterialTheme.typography.titleSmall)
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.right(e) }) { Text(stringResource(R.string.this_was_right)) }
                OutlinedButton(onClick = { wrong = true }) { Text(stringResource(R.string.this_was_wrong)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.heard.isNotEmpty()) OutlinedButton(onClick = { vm.again(e); onClose() }) { Text(stringResource(R.string.do_again)) }
                if (e.recording != null) OutlinedButton(onClick = { vm.play(context, e) }) { Text(stringResource(R.string.play_recording)) }
            }
        }
    }
    if (wrong) WrongDialog(vm, e) { wrong = false }
}

/** One of a command's big parts (a plot it read, a prompt and its answer): folded to a few lines. */
@Composable
private fun DetailPart(key: String, value: String) {
    var all by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { all = !all }) {
        Text(key, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (all) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * "This was wrong, it should be…" (as the panel's): what was said, what it should have done (said the way the agent
 * understands it, checked first), the right episode or moment when it searched for one; taught too, unless unticked.
 */
@Composable
private fun WrongDialog(vm: ActivityViewModel, e: ActivityEntry, onClose: () -> Unit) {
    var words by rememberSaveable { mutableStateOf(e.heard) }
    var sentence by rememberSaveable { mutableStateOf("") }
    var episode by rememberSaveable { mutableStateOf("") }
    var moment by rememberSaveable { mutableStateOf("") }
    var teach by rememberSaveable { mutableStateOf(true) }
    val preview by vm.preview.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.preview.value = null }
    val searched = remember(e) {
        e.trace.orEmpty().mapNotNull { it as? JsonObject }.filter { runCatching { it["step"]?.jsonPrimitive?.contentOrNull }.getOrNull() == "asked" }
            .mapNotNull { runCatching { it["stage"]?.jsonPrimitive?.contentOrNull }.getOrNull() }.toSet()
    }
    val understood = preview?.action?.takeIf { runCatching { it["type"]?.jsonPrimitive?.contentOrNull }.getOrNull() != "none" }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.this_was_wrong)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(words, { words = it }, label = { Text(stringResource(R.string.wrong_words)) },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sentence, { sentence = it }, singleLine = true, label = { Text(stringResource(R.string.teach_as)) },
                    modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.check(sentence) }, enabled = sentence.isNotBlank()) { Text(stringResource(R.string.check)) }
                preview?.let { p ->
                    Text(if (understood == null) stringResource(R.string.teach_not_understood)
                    else stringResource(R.string.teach_would, p.describe.ifEmpty { actionLabel(p.action) }), fontWeight = FontWeight.Medium)
                }
                if ("episode" in searched) OutlinedTextField(episode, { episode = it }, singleLine = true,
                    label = { Text(stringResource(R.string.wrong_episode)) }, modifier = Modifier.fillMaxWidth())
                if ("scene" in searched) OutlinedTextField(moment, { moment = it }, singleLine = true,
                    label = { Text(stringResource(R.string.wrong_moment)) }, modifier = Modifier.fillMaxWidth())
                if (e.heard.isNotEmpty()) Toggle(stringResource(R.string.wrong_teach), teach) { teach = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.wrong(e, words, understood, episode, moment, teach); onClose() },
                enabled = understood != null || episode.isNotBlank() || moment.isNotBlank()) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What a command did, step by step (the agent's trace), by stage as the panel's Decisions tab shows it: when each
 * step ended, how long it took, how it was done (rule, model, add-on…), how sure against its bar, what came of it. */
@Composable
fun Trace(steps: JsonArray) {
    Text(stringResource(R.string.trace_title), style = MaterialTheme.typography.titleSmall)
    var stage: String? = null
    steps.forEach { el ->
        val s = el as? JsonObject ?: return@forEach
        fun str(k: String) = runCatching { s[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
        fun num(k: String) = runCatching { s[k]?.jsonPrimitive?.doubleOrNull }.getOrNull()
        val now = str("stage")
        if (now != null && now != stage) {
            stage = now
            Text(stageName(now), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        val label = str("label") ?: str("step").orEmpty()
        val method = (s["method"] as? JsonObject)?.let { m ->
            val kind = runCatching { m["kind"]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()
            val id = runCatching { m["id"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            listOfNotNull(methodLabel(kind).takeIf { kind.isNotEmpty() }, id).joinToString(" ")
        }
        val sure = num("confidence")?.let { c -> num("threshold")?.let { t -> c to t } }
        val time = listOfNotNull(num("at")?.let { secs(it) }, num("ms")?.let { "(${secs(it)})" }).joinToString(" ")
        Column(Modifier.padding(vertical = 2.dp)) {
            Row {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val line = listOfNotNull(method, str("verdict"),
                sure?.let { (c, t) -> stringResource(R.string.trace_sure, c, t) }, str("why")).filter { it.isNotEmpty() }
            if (line.isNotEmpty()) {
                Text(line.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = if (sure != null && sure.first < sure.second) Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun secs(ms: Double) = if (ms >= 1000) "%.1f s".format(ms / 1000) else "%.0f ms".format(ms)

/** A trace stage's name: deciding, finding the title, the episode, the scene, starting it, the chain… */
@Composable
private fun stageName(stage: String) = when (stage) {
    "decide", "llm", "d1" -> stringResource(R.string.tstage_decide)
    "title" -> stringResource(R.string.tstage_title)
    "episode" -> stringResource(R.string.tstage_episode)
    "scene" -> stringResource(R.string.tstage_scene)
    "start" -> stringResource(R.string.tstage_start)
    "chain" -> stringResource(R.string.tstage_chain)
    "skip" -> stringResource(R.string.tstage_skip)
    "stt" -> stringResource(R.string.speech_to_text)
    else -> stage
}
