package com.boujuan.tvagent.phone

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boujuan.tvagent.phone.data.VoiceState
import com.boujuan.tvagent.phone.ui.ShownSheet
import com.boujuan.tvagent.phone.ui.ShownViewModel
import com.boujuan.tvagent.phone.ui.appViewModel
import com.boujuan.tvagent.phone.ui.theme.TvAgentTheme
import com.boujuan.tvagent.phone.ui.voice.VoiceButton
import com.boujuan.tvagent.phone.ui.voice.VoicePanel

/**
 * A spoken command from outside the app (the Quick Settings tile, the home-screen widget): a small panel over what
 * you were doing that starts listening at once, lets the agent decide in your pause, shows the result and closes.
 */
class VoiceActivity : ComponentActivity() {
    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.voice.start() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by app.repo.prefs.collectAsStateWithLifecycle()
            val state by app.voice.state.collectAsStateWithLifecycle()
            TvAgentTheme(prefs.theme, prefs.dynamicColor) {
                var active by remember { mutableStateOf(false) }
                // a numbered list the command opened on the TV keeps the panel open, to be picked here too
                val shown by appViewModel { ShownViewModel(it.repo) }.list.collectAsStateWithLifecycle()
                LaunchedEffect(state, shown) {
                    if (state != VoiceState.Idle) active = true else if (active && shown == null) finish()
                }
                ShownSheet()
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { app.voice.cancel(); finish() },
                    contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        VoicePanel(onClose = { finish() })
                        VoiceButton()
                    }
                }
            }
        }
        if (savedInstanceState == null) begin()
    }

    private fun begin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            app.voice.start()
        } else {
            ask.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, VoiceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)

        fun pending(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 0, intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** Quick Settings tile: speak a command from anywhere. */
class VoiceTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(VoiceActivity.pending(this))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(VoiceActivity.intent(this))
        }
    }
}

/** Home-screen widget: one microphone button. */
class VoiceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.voice_widget).apply {
            setOnClickPendingIntent(R.id.widget_mic, VoiceActivity.pending(context))
        }
        manager.updateAppWidget(ComponentName(context, VoiceWidget::class.java), views)
    }
}
