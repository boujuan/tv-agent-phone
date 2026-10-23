package com.boujuan.tvagent.phone.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import java.util.UUID

/** The selected agent and its API client. */
data class Connection(val agent: SavedAgent, val api: AgentApi, val token: String)

/**
 * The paired agents and the one in use. Screens watch [current] and, after anything that changes the TV, [changed]
 * (so Now playing and Activity refresh at once instead of at their next poll).
 */
class AgentRepository(val store: AgentStore, scope: CoroutineScope, private val http: OkHttpClient) {
    /** null until read from storage. */
    val agents: StateFlow<List<SavedAgent>?> = store.agents.stateIn(scope, SharingStarted.Eagerly, null)
    val prefs: StateFlow<AppPrefs> = store.prefs.stateIn(scope, SharingStarted.Eagerly, AppPrefs())
    val current: StateFlow<Connection?> = store.selected.distinctUntilChanged().map { agent ->
        agent?.let { a -> store.token(a)?.let { Connection(a, AgentApi(a.urls, a.pin, it, http), it) } }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val changed: SharedFlow<Unit> = _changed
    fun touched() {
        _changed.tryEmit(Unit)
    }

    /** Pair with the code; the agent's certificate is [pin] (from the QR code, or confirmed by the user). */
    suspend fun pair(urls: List<String>, pin: String, code: String, deviceName: String, fallbackName: String): SavedAgent {
        val api = AgentApi(urls, pin, null, http)
        val reply = api.pair(code, deviceName)
        val ordered = listOf(api.activeUrl) + urls.filter { it != api.activeUrl }
        val agent = SavedAgent(
            id = UUID.randomUUID().toString(), name = reply.agent.name.ifEmpty { fallbackName }, urls = ordered,
            pin = pin, streamPort = reply.streamPort, deviceId = reply.device.id, deviceName = reply.device.name,
            version = reply.agent.version, sealedToken = "",
        )
        return store.add(agent, reply.token)
    }

    /** Forget an agent here, and (when it can be reached) make the agent forget this phone too. */
    suspend fun forget(agent: SavedAgent): Boolean {
        val told = store.token(agent)?.let { token ->
            runCatching { AgentApi(agent.urls, agent.pin, token, http).forget().ok }.getOrDefault(false)
        } ?: false
        store.remove(agent.id)
        return told
    }

    /** Addresses edited by hand: kept in the order given, and the ones added there kept from then on. */
    suspend fun setUrls(agent: SavedAgent, urls: List<String>) = store.edit(agent.id) { Addresses.edited(it, urls) }

    /**
     * After the agent answered [me] over [conn]: its addresses as it listens on them now (a new LAN address, Tailscale)
     * replace the saved ones, with the one that answered first and those typed by hand kept; its version and stream
     * port too. An older agent that sends no addresses changes nothing.
     */
    suspend fun refresh(conn: Connection, me: Me) {
        val offered = Addresses.offered(me.addresses)
        store.edit(conn.agent.id) { a ->
            a.copy(
                urls = offered?.let { Addresses.merge(a, it, conn.api.activeUrl) } ?: a.urls,
                version = me.agent.version.ifEmpty { a.version },
                streamPort = me.streamPort.takeIf { it in 1..65535 } ?: a.streamPort,
            )
        }
    }

    suspend fun select(agent: SavedAgent) = store.select(agent.id)
    suspend fun setPrefs(prefs: AppPrefs) = store.setPrefs(prefs)
}
