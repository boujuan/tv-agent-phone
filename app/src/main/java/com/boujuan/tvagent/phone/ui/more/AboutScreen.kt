package com.boujuan.tvagent.phone.ui.more

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boujuan.tvagent.phone.BuildConfig
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.app
import com.boujuan.tvagent.phone.data.About
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.Me
import com.boujuan.tvagent.phone.data.UpdatesInfo
import com.boujuan.tvagent.phone.data.sha256
import com.boujuan.tvagent.phone.ui.AgentViewModel
import com.boujuan.tvagent.phone.ui.LabeledValue
import com.boujuan.tvagent.phone.ui.Messages
import com.boujuan.tvagent.phone.ui.SectionCard
import com.boujuan.tvagent.phone.ui.Toggle
import com.boujuan.tvagent.phone.ui.WhileVisible
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.describe
import com.boujuan.tvagent.phone.ui.theme.Amber
import com.boujuan.tvagent.phone.ui.theme.Green
import com.boujuan.tvagent.phone.ui.theme.Red
import com.boujuan.tvagent.phone.update.RELEASE_CERT_SHA256
import com.boujuan.tvagent.phone.update.UpdateState
import com.boujuan.tvagent.phone.update.UpdateWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val REPO = "https://github.com/boujuan/tv-agent-phone"

class AboutViewModel(repo: AgentRepository) : AgentViewModel(repo) {
    val about = MutableStateFlow<About?>(null)
    val me = MutableStateFlow<Me?>(null)
    val tvApp = MutableStateFlow<String?>(null)
    val tvPlayer = MutableStateFlow<String?>(null)
    val updates = MutableStateFlow<UpdatesInfo?>(null)
    /** Why the agent's details couldn't be read: shown in its card, not as a message over the screen, since the rest
     * of About (this app, its updates from GitHub) doesn't need the agent. */
    val agentError = MutableStateFlow<Throwable?>(null)

    fun load() = viewModelScope.launch {
        val api = api ?: return@launch
        try {
            about.value = api.about()
            me.value = api.me()
            val packages = api.tvPackages()                 // {"error"} when the TV can't be reached
            fun version(key: String) = (packages[key] as? JsonObject)?.get("version")?.jsonPrimitive?.contentOrNull
            tvApp.value = version("tvagent")
            tvPlayer.value = version("tvplayer")
            updates.value = runCatching { api.updates() }.getOrNull()      // an extra: its shape mustn't break the rest
            agentError.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            agentError.value = e
        }
    }

    /** Asks GitHub (never the agent); says so when there's nothing newer, as the status alone wouldn't change. */
    fun check(context: Context) = viewModelScope.launch {
        context.app.updater.check()
        if (context.app.updater.state.value is UpdateState.UpToDate) tell(R.string.update_checked_none)
    }

    fun install(context: Context) = viewModelScope.launch {
        val updater = context.app.updater
        val release = when (val s = updater.state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.NeedsPermission -> s.release
            else -> updater.check()
        } ?: return@launch
        updater.install(release, asking = true)
    }
}

/** This app's signer: the release key, or a development one (debug builds). */
private fun signedWithReleaseKey(context: Context): Boolean = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    info.signingInfo?.apkContentsSigners.orEmpty().all { sha256(it.toByteArray()) == RELEASE_CERT_SHA256 }
}.getOrDefault(false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val vm = appViewModel { AboutViewModel(it.repo) }
    Messages(vm)
    val about by vm.about.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val tvApp by vm.tvApp.collectAsStateWithLifecycle()
    val tvPlayer by vm.tvPlayer.collectAsStateWithLifecycle()
    val updates by vm.updates.collectAsStateWithLifecycle()
    val agentError by vm.agentError.collectAsStateWithLifecycle()
    val conn by context.app.repo.current.collectAsStateWithLifecycle()
    val prefs by context.app.repo.prefs.collectAsStateWithLifecycle()
    val state by context.app.updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val releaseKey = remember { signedWithReleaseKey(context) }
    var canInstall by remember { mutableStateOf(context.app.updater.canInstall()) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    WhileVisible(60_000) {
        canInstall = context.app.updater.canInstall()          // back from the "Install apps" setting
        vm.load()
    }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(stringResource(R.string.about_app)) {
            LabeledValue(stringResource(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            LabeledValue(stringResource(R.string.signed_with), stringResource(if (releaseKey) R.string.release_key else R.string.debug_key))
        }

        SectionCard(stringResource(R.string.updates)) {
            UpdateStatus(state)
            if (!canInstall) {
                Text(stringResource(R.string.update_permission_hint), style = MaterialTheme.typography.bodySmall, color = Amber)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
                }) { Text(stringResource(R.string.update_allow)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing
                OutlinedButton(onClick = { vm.check(context) }, enabled = !busy) { Text(stringResource(R.string.update_check)) }
                if (state is UpdateState.Available || state is UpdateState.NeedsPermission) {
                    Button(onClick = { vm.install(context) }) { Text(stringResource(R.string.update_now)) }
                }
            }
            Toggle(stringResource(R.string.update_auto), prefs.autoUpdate) { on ->
                scope.launch { context.app.repo.setPrefs(prefs.copy(autoUpdate = on)) }
                UpdateWork.schedule(context, on)
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Text(stringResource(R.string.update_auto_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(stringResource(R.string.voice_agent)) {
            agentError?.let { Text(describe(context, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            LabeledValue(stringResource(R.string.computer), conn?.agent?.name.orEmpty())
            LabeledValue(stringResource(R.string.server_version), about?.version ?: "–")
            LabeledValue(stringResource(R.string.tv_app), tvApp ?: "–")
            LabeledValue(stringResource(R.string.tv_player), tvPlayer ?: "–")
            me?.device?.let { d -> LabeledValue(stringResource(R.string.this_phone_there), d.name + (d.appVersion?.let { " · $it" } ?: "")) }
            updates?.let { AgentUpdates(it) }
        }

        SectionCard(stringResource(R.string.licence)) {
            Text(stringResource(R.string.licence_text), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { open(REPO) }) { Text(stringResource(R.string.link_repo)) }
                TextButton(onClick = { open("$REPO/releases") }) { Text(stringResource(R.string.link_releases)) }
                TextButton(onClick = { open("$REPO/blob/main/CHANGELOG.md") }) { Text(stringResource(R.string.link_changelog)) }
                TextButton(onClick = { open("$REPO/blob/main/LICENSE") }) { Text(stringResource(R.string.link_licence)) }
            }
        }
    }
}

@Composable
private fun UpdateStatus(state: UpdateState) {
    when (state) {
        UpdateState.Idle -> Text(stringResource(R.string.update_idle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is UpdateState.UpToDate -> {
            Text(stringResource(R.string.update_none, state.latest.version), color = Green)
            Text(stringResource(R.string.update_checked_at, DateUtils.formatDateTime(LocalContext.current, state.at, DateUtils.FORMAT_SHOW_TIME)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is UpdateState.Available -> {
            Text(stringResource(R.string.update_available, state.release.version, BuildConfig.VERSION_NAME), fontWeight = FontWeight.SemiBold)
            Notes(state.release.body)
        }
        is UpdateState.NeedsPermission -> {
            Text(stringResource(R.string.update_available, state.release.version, BuildConfig.VERSION_NAME), fontWeight = FontWeight.SemiBold)
            Notes(state.release.body)
        }
        is UpdateState.Downloading -> {
            Text(stringResource(R.string.update_downloading, state.release.version))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.Installing -> {
            Text(stringResource(R.string.update_installing, state.release.version))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is UpdateState.Failed -> Text(stringResource(R.string.update_failed, state.message), color = Red)
    }
}

/** The release notes (the CHANGELOG section), folded after a few lines. */
@Composable
private fun Notes(body: String?) {
    if (body.isNullOrBlank()) return
    var all by rememberSaveable { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val text = remember(body) { notes(body) }
    Text(text, style = MaterialTheme.typography.bodySmall, maxLines = if (all) Int.MAX_VALUE else 6,
        overflow = TextOverflow.Ellipsis, onTextLayout = { more = it.hasVisualOverflow })
    if (!all && more) TextButton(onClick = { all = true }) { Text(stringResource(R.string.show_all)) }
}

/** The CHANGELOG's Markdown as text: its wrapped lines joined, "###" headings and **bold** in bold, "-" as bullets. */
internal fun notes(markdown: String): AnnotatedString {
    val lines = mutableListOf<String>()
    for (line in markdown.replace("\r", "").trim().lines()) {
        val continues = line.startsWith(" ") && line.isNotBlank() && lines.isNotEmpty() && lines.last().isNotBlank()
        if (continues) lines[lines.size - 1] = lines.last() + " " + line.trim() else lines += line.trimEnd()
    }
    return buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            if (i > 0) append("\n")
            when {
                line.startsWith("#") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(line.trimStart('#', ' ')) }
                line.startsWith("- ") -> { append("• "); inline(line.removePrefix("- ")) }
                else -> inline(line)
            }
        }
    }
}

private fun AnnotatedString.Builder.inline(text: String) {
    var at = 0
    for (m in Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""").findAll(text)) {
        append(text.substring(at, m.range.first))
        val (bold, italic, code) = m.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            else -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
        }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/** What the agent found of newer releases (its own update check): the server, the panel, the TV app, the player. */
@Composable
private fun AgentUpdates(u: UpdatesInfo) {
    val labels = mapOf("server" to R.string.server_version, "panel" to R.string.panel, "tv" to R.string.tv_app,
        "tvplayer" to R.string.tv_player, "phone" to R.string.about_app)
    for ((key, c) in u.components) {
        if (c.newer == true && c.latest != null) {
            val label = labels[key]?.let { stringResource(it) } ?: key
            Text(stringResource(R.string.agent_update_newer, label, c.latest), color = Amber,
                style = MaterialTheme.typography.bodySmall)
        }
    }
    val checked = u.checked?.let { DateUtils.getRelativeTimeSpanString((it * 1000).toLong()).toString() }
    Text(stringResource(R.string.agent_updates_checked, checked ?: "–") + (u.error?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
