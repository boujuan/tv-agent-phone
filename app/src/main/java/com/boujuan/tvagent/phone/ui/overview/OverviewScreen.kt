package com.boujuan.tvagent.phone.ui.overview

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.app
import com.boujuan.tvagent.phone.data.About
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.DiagnoseStep
import com.boujuan.tvagent.phone.data.StageStats
import com.boujuan.tvagent.phone.data.TvStatus
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.LabeledValue
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.SectionCard
import com.boujuan.tvagent.phone.ui.Waiting
import com.boujuan.tvagent.phone.ui.WhileVisible
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.theme.Amber
import com.boujuan.tvagent.phone.ui.theme.Green
import com.boujuan.tvagent.phone.ui.theme.Red
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.boujuan.tvagent.phone.ui.playerName
import com.boujuan.tvagent.phone.ui.stageLabel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class OverviewViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val about = MutableStateFlow<About?>(null)
    val stats = MutableStateFlow<Map<String, StageStats>>(emptyMap())
    val tv = MutableStateFlow<TvStatus?>(null)
    val tvPlayer = MutableStateFlow<String?>(null)          // TV Agent Player's version (read once: the TV's packages)
    val steps = MutableStateFlow<List<DiagnoseStep>>(emptyList())
    val screenshot = MutableStateFlow<ImageBitmap?>(null)
    val working = MutableStateFlow(false)

    init {
        viewModelScope.launch { repo.current.collect { about.value = null; tv.value = null; steps.value = emptyList() } }
    }

    fun refresh() = call {
        about.value = it.about()
        stats.value = it.stats()
        tv.value = it.tvStatus()
        if (tvPlayer.value == null) {
            tvPlayer.value = runCatching { (it.tvPackages()["tvplayer"] as? JsonObject)?.get("version")?.jsonPrimitive?.contentOrNull }
                .getOrNull()
        }
    }

    private fun busy(block: suspend (com.boujuan.tvagent.phone.data.AgentApi) -> Unit) = call {
        working.value = true
        try { block(it) } finally { working.value = false }
    }

    fun diagnose() = busy { steps.value = it.diagnose().steps }
    fun reconnect() = busy {
        val r = it.reconnect()
        tell(if (r.ok) "✔ ${r.host.orEmpty()}" else "✗ ${r.error.orEmpty()}")
        tv.value = it.tvStatus()
    }
    fun speedtest() = busy { val s = it.speedtest(); tell(s.mbps?.let { m -> "%.0f Mbit/s".format(m) } ?: s.error.orEmpty()) }
    fun key(key: String) = call(touch = true) { it.key(key) }
    fun screenshot() = busy {
        val png = it.screenshot()
        screenshot.value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap() }
    }
}

/** The speech model that runs, by where: this laptop's, the hosted one, or one then the other. */
@Composable
private fun sttLine(a: About): String {
    val local = stringResource(R.string.stt_on_laptop, a.sttModel)
    val hosted = stringResource(R.string.stt_hosted, a.sttHostedModel.substringAfterLast('/'))
    return when (a.sttWhere) {
        "hosted" -> hosted
        "local-first" -> "$local → $hosted"
        "hosted-first" -> "$hosted → $local"
        else -> local
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen() {
    val vm = appViewModel { OverviewViewModel(it.repo) }
    Messages(vm)
    val about by vm.about.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val tv by vm.tv.collectAsStateWithLifecycle()
    val tvPlayer by vm.tvPlayer.collectAsStateWithLifecycle()
    val steps by vm.steps.collectAsStateWithLifecycle()
    val shot by vm.screenshot.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    val conn by LocalContext.current.app.repo.current.collectAsStateWithLifecycle()
    WhileVisible(20_000) { vm.refresh() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(stringResource(R.string.voice_agent)) {
            LabeledValue(stringResource(R.string.computer), conn?.agent?.name.orEmpty())
            LabeledValue(stringResource(R.string.address), conn?.api?.activeUrl.orEmpty())
            about?.let { a ->
                LabeledValue(stringResource(R.string.version), a.version)
                LabeledValue(stringResource(R.string.running_since),
                    android.text.format.DateUtils.getRelativeTimeSpanString((a.started * 1000).toLong()).toString())
                LabeledValue(stringResource(R.string.speech_to_text), sttLine(a))
                LabeledValue(stringResource(R.string.llm), a.llmModel)
                if (a.d1Where.isNotEmpty()) LabeledValue(stringResource(R.string.step_d1),
                    listOf(a.hostedD1Model.ifEmpty { "d1" }, whereName(a.d1Where)).joinToString(" · "))
                LabeledValue(stringResource(R.string.player), playerName(a.player))
            } ?: Waiting(vm) { vm.refresh() }
        }
        SectionCard(stringResource(R.string.tv)) {
            val t = tv
            when {
                t == null -> Waiting(vm) { vm.refresh() }
                t.error != null -> Text(stringResource(R.string.tv_unreachable, t.error), color = Red)
                else -> {
                    LabeledValue(stringResource(R.string.model), "${t.model} · Android ${t.android}")
                    LabeledValue(stringResource(R.string.screen), t.screen.orEmpty())
                    LabeledValue(stringResource(R.string.in_front), t.foregroundLabel ?: t.foreground.orEmpty())
                    LabeledValue(stringResource(R.string.network), listOfNotNull(t.link, t.wifi?.ssid,
                        t.wifi?.rssi?.let { "$it dBm" }, t.wifi?.linkMbps?.let { "$it Mbit/s" }).joinToString(" · "))
                    t.pingMs?.let { LabeledValue(stringResource(R.string.ping), "%.0f ms".format(it)) }
                    t.cpu?.let { LabeledValue(stringResource(R.string.cpu), "%.0f %%".format(it)) }
                    t.memory?.let { LabeledValue(stringResource(R.string.memory), "${it.usedMb} / ${it.totalMb} MB") }
                    t.uptime?.let { LabeledValue(stringResource(R.string.uptime), it) }
                    LabeledValue(stringResource(R.string.tv_app), t.app.version ?: stringResource(R.string.not_installed))
                    tvPlayer?.let { LabeledValue(stringResource(R.string.tv_player), it) }
                    t.apps.forEach { a ->
                        LabeledValue(stringResource(R.string.running, a.name), "%.1f %% · %d MB".format(a.cpu, a.rssKb / 1024))
                    }
                }
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = { vm.key("wake") }) { Text(stringResource(R.string.key_wake)) }
                FilledTonalButton(onClick = { vm.key("sleep") }) { Text(stringResource(R.string.key_sleep)) }
                FilledTonalButton(onClick = { vm.reconnect() }, enabled = !working) { Text(stringResource(R.string.reconnect)) }
                FilledTonalButton(onClick = { vm.diagnose() }, enabled = !working) { Text(stringResource(R.string.diagnose)) }
                FilledTonalButton(onClick = { vm.speedtest() }, enabled = !working) { Text(stringResource(R.string.speedtest)) }
                FilledTonalButton(onClick = { vm.screenshot() }, enabled = !working) { Text(stringResource(R.string.screenshot)) }
            }
            steps.forEach { s ->
                Text("${if (s.ok == true) "✔" else if (s.ok == false) "✗" else "·"} ${s.step}: ${s.detail}",
                    color = when (s.ok) { true -> Green; false -> Red; null -> Amber }, style = MaterialTheme.typography.bodySmall)
                if (s.hint.isNotEmpty()) Text("   ${s.hint}", style = MaterialTheme.typography.bodySmall)
            }
            shot?.let { Image(it, contentDescription = stringResource(R.string.screenshot), modifier = Modifier.fillMaxWidth()) }
        }
        if (stats.isNotEmpty()) SectionCard(stringResource(R.string.latency)) {
            Text(stringResource(R.string.latency_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            stats.entries.sortedBy { it.key }.forEach { (stage, s) ->
                LabeledValue(stageLabel(stage), "%.1f / %.1f ms · %d".format(s.p50, s.p95, s.n))
            }
        }
    }
}

/** Where a model runs: on the computer, hosted, or one of them first. */
@Composable
private fun whereName(where: String) = when (where) {
    "local" -> stringResource(R.string.where_local)
    "hosted" -> stringResource(R.string.where_hosted)
    "local-first" -> stringResource(R.string.where_local_first)
    "hosted-first" -> stringResource(R.string.where_hosted_first)
    else -> where
}
