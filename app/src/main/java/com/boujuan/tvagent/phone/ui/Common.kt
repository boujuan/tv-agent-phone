package com.boujuan.tvagent.phone.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.TvAgentApp
import com.boujuan.tvagent.phone.app
import com.boujuan.tvagent.phone.data.AgentApi
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.ApiException
import com.boujuan.tvagent.phone.data.PinMismatch
import com.boujuan.tvagent.phone.data.Unreachable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/** What a screen tells the user in the snackbar. */
sealed interface UiMessage {
    data class Text(val text: String) : UiMessage
    data class Res(val id: Int) : UiMessage
    data class Error(val error: Throwable) : UiMessage
}

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** A screen's ViewModel: calls the selected agent, reports failures, tells others when the TV changed. */
abstract class AgentViewModel(protected val repo: AgentRepository) : ViewModel() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    protected val api: AgentApi? get() = repo.current.value?.api

    protected fun tell(text: String) = _messages.tryEmit(UiMessage.Text(text))
    protected fun tell(id: Int) = _messages.tryEmit(UiMessage.Res(id))
    protected fun fail(e: Throwable) = _messages.tryEmit(UiMessage.Error(e))

    /** Runs a call to the agent; touch: it changes the TV, so screens showing it refresh. */
    protected fun call(touch: Boolean = false, block: suspend (AgentApi) -> Unit) = viewModelScope.launch {
        val api = api ?: return@launch
        try {
            block(api)
            if (touch) repo.touched()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline make: (TvAgentApp) -> VM): VM =
    viewModel { make(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TvAgentApp) }

/** Shows a ViewModel's messages in the screen's snackbar. */
@Composable
fun Messages(vm: AgentViewModel) {
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(vm) {
        val scope = this
        vm.messages.collect { m ->
            val text = when (m) {
                is UiMessage.Text -> m.text
                is UiMessage.Res -> resources.getString(m.id)
                is UiMessage.Error -> describe(context, m.error)
            }
            snackbar.currentSnackbarData?.dismiss()         // the newest replaces it at once
            scope.launch { snackbar.showSnackbar(text) }
        }
    }
}

fun describe(context: Context, e: Throwable): String {
    val chain = generateSequence(e) { it.cause.takeIf { c -> c !== it } }
    return when {
        chain.any { it is PinMismatch } -> context.getString(R.string.error_other_computer)
        e is Unreachable -> context.getString(R.string.error_unreachable)
        e is ApiException && e.code == 401 -> context.getString(R.string.error_revoked)
        e is ApiException -> e.message ?: "HTTP ${e.code}"
        else -> e.message ?: e.javaClass.simpleName
    }
}

/** Runs [block] every [ms] while the screen is in front (polling what's playing, the TV's status). */
@Composable
fun WhileVisible(ms: Long, block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                current()
                delay(ms)
            }
        }
    }
}

/** A light tap on the phone for a remote button (when haptics are on). */
@Composable
fun rememberTap(): () -> Unit {
    val view = LocalView.current
    val prefs by LocalContext.current.app.repo.prefs.collectAsStateWithLifecycle()
    return { if (prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ---- pictures from the internet (posters), small and cached ----

private val images = LruCache<String, ImageBitmap>(48)

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val image by produceState(url?.let { images.get(it) }, url) {
        if (url.isNullOrEmpty() || value != null || !url.startsWith("https://")) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.app.web.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val bytes = r.body.bytes()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 360) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?.asImageBitmap()
                }
            }.getOrNull()
        }?.also { images.put(url, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = Modifier.matchParentSize()) }
    }
}
