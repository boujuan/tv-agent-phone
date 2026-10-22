package com.boujuan.tvagent.phone

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.boujuan.tvagent.phone.data.AgentRepository
import com.boujuan.tvagent.phone.data.AgentStore
import com.boujuan.tvagent.phone.data.KeystoreSealer
import com.boujuan.tvagent.phone.data.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** The app's few long-lived objects: the paired agents and their API, the voice command in progress, HTTP. */
class TvAgentApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repo: AgentRepository
        private set
    lateinit var voice: VoiceController
        private set
    /** For pictures from the internet (posters); the agent's own calls use a pinned client (AgentApi). */
    val web: OkHttpClient by lazy { OkHttpClient() }

    override fun onCreate() {
        super.onCreate()
        val data = PreferenceDataStoreFactory.create { preferencesDataStoreFile("tv-agent") }
        repo = AgentRepository(AgentStore(data, KeystoreSealer()), scope, OkHttpClient())
        voice = VoiceController(repo, scope)
    }
}

val Context.app: TvAgentApp get() = applicationContext as TvAgentApp
