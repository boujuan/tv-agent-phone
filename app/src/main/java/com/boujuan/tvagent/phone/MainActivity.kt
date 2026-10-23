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
    private var open by mutableStateOf<String?>(null)      // a screen to show (an update notification opens About)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A link only when it's just been opened: Android re-creates the activity from the same intent later (from
        // recents, after an update), and that code is used up by then.
        val fresh = savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        link = if (fresh) linkOf(intent) else null
        intent.data = null
        if (fresh) open = intent.getStringExtra(OPEN)
        setContent {
            val prefs by app.repo.prefs.collectAsStateWithLifecycle()
            TvAgentTheme(prefs.theme, prefs.dynamicColor) {
                AppRoot(pairingLink = link, onLinkUsed = { link = null }, open = open, onOpened = { open = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkOf(intent)?.let { link = it }
        intent.data = null
        intent.getStringExtra(OPEN)?.let { open = it }
    }

    override fun onResume() {
        super.onResume()
        app.inFront = true
    }

    override fun onPause() {
        app.inFront = false
        super.onPause()
    }

    private fun linkOf(intent: Intent?): PairingLink? = intent?.dataString?.let { Pairing.parse(it) }

    companion object {
        const val OPEN = "open"
    }
}
