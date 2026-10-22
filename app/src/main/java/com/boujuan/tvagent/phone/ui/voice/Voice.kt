package com.boujuan.tvagent.phone.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boujuan.tvagent.phone.R
import com.boujuan.tvagent.phone.app
import com.boujuan.tvagent.phone.data.VoiceState
import com.boujuan.tvagent.phone.ui.theme.Green
import com.boujuan.tvagent.phone.ui.theme.Red
import kotlinx.coroutines.delay

/**
 * The microphone button. Hold it while you speak (released: sent), or tap it and speak (the agent decides in your
 * pause; tap again to stop). Asks for the microphone the first time.
 */
@Composable
fun VoiceButton(modifier: Modifier = Modifier, size: Int = 72) {
    val context = LocalContext.current
    val voice = context.app.voice
    val state by voice.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val prefs by context.app.repo.prefs.collectAsStateWithLifecycle()
    val haptics by rememberUpdatedState(prefs.haptics)       // read when pressed (the press handler stays the same)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val listening = state is VoiceState.Listening || state is VoiceState.Connecting
    val level = (state as? VoiceState.Listening)?.level ?: 0f
    val label = stringResource(R.string.voice_button)
    Surface(
        shape = CircleShape,
        color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(size.dp)
            .scale(1f + level * 0.25f)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        ask.launch(Manifest.permission.RECORD_AUDIO)
                        return@detectTapGestures
                    }
                    if (voice.busy) {                       // listening after a tap: this tap ends it
                        voice.finish()
                        return@detectTapGestures
                    }
                    if (haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    voice.start()
                    val pressed = System.currentTimeMillis()
                    tryAwaitRelease()
                    if (System.currentTimeMillis() - pressed > HOLD_MS) {   // held: push to talk, so released = done
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        voice.finish()
                    }
                })
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size((size / 2.4).dp))
        }
    }
}

private const val HOLD_MS = 450

/** What the agent heard and did, over the top of the screen while a command is on (closes by itself after). */
@Composable
fun VoicePanel(modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val voice = LocalContext.current.app.voice
    val state by voice.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state is VoiceState.Done || state is VoiceState.Failed) {
            delay(if (state is VoiceState.Done) 5000 else 7000)
            voice.dismiss()
            onClose?.invoke()
        }
    }
    AnimatedVisibility(
        visible = state != VoiceState.Idle,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoiceContent(state)
                }
                IconButton(onClick = { voice.cancel(); onClose?.invoke() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun VoiceContent(state: VoiceState) {
    when (state) {
        VoiceState.Idle -> {}
        VoiceState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.voice_connecting))
        }
        is VoiceState.Listening -> {
            Text(stringResource(if (state.streaming) R.string.voice_listening else R.string.voice_listening_whole),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { state.level }, modifier = Modifier.fillMaxWidth().height(4.dp))
            if (state.heard.isNotEmpty()) Text("“${state.heard}”", style = MaterialTheme.typography.titleMedium)
        }
        is VoiceState.Deciding -> {
            Text(stringResource(R.string.voice_deciding), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
            if (state.heard.isNotEmpty()) Text("“${state.heard}”", style = MaterialTheme.typography.titleMedium)
        }
        is VoiceState.Done -> {
            if (state.heard.isNotEmpty()) Text("“${state.heard}”", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(if (state.ok) Green else Red, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(state.say, fontWeight = FontWeight.Medium)
            }
        }
        is VoiceState.Failed -> Text(
            when (state.reason) {
                VoiceState.Reason.NO_AGENT -> stringResource(R.string.voice_no_agent)
                VoiceState.Reason.MICROPHONE -> stringResource(R.string.voice_no_microphone)
                VoiceState.Reason.BUSY -> stringResource(R.string.voice_busy)
                VoiceState.Reason.REVOKED -> stringResource(R.string.error_revoked)
                VoiceState.Reason.UNREACHABLE -> stringResource(R.string.error_unreachable)
                VoiceState.Reason.OTHER -> state.detail
            },
            color = Red,
        )
    }
}
