package com.boujuan.tvagent.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boujuan.tvagent.phone.data.Pairing
import com.boujuan.tvagent.phone.data.PairingLink
import com.boujuan.tvagent.phone.ui.AppRoot
import com.boujuan.tvagent.phone.ui.theme.TvAgentTheme

/** The app's one activity. A tvagent://pair link (the panel's QR code read by another scanner) opens pairing. */
class MainActivity : ComponentActivity() {
    private var link by mutableStateOf<PairingLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        link = linkOf(intent)
        setContent {
            val prefs by app.repo.prefs.collectAsStateWithLifecycle()
            TvAgentTheme(prefs.theme, prefs.dynamicColor) {
                AppRoot(pairingLink = link, onLinkUsed = { link = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkOf(intent)?.let { link = it }
    }

    private fun linkOf(intent: Intent?): PairingLink? = intent?.dataString?.let { Pairing.parse(it) }
}
