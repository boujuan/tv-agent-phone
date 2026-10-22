package com.boujuan.tvagent.phone.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AgentStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Stands in for the Keystore (not on a JVM): reversible, and never the token as is. */
    private object Rot : Sealer {
        override fun seal(plain: String) = "sealed:" + plain.reversed()
        override fun open(sealed: String) = sealed.removePrefix("sealed:").reversed().takeIf { sealed.startsWith("sealed:") }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var store: AgentStore

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(tmp.root, "tv-agent.preferences_pb")
        store = AgentStore(PreferenceDataStoreFactory.create(scope = scope) { file }, Rot)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun agent(name: String, pin: String) = SavedAgent(
        id = "$name-$pin", name = name, urls = listOf("https://192.168.1.51:8768"), pin = pin, streamPort = 8769,
        deviceId = "d1", deviceName = "Pixel", sealedToken = "",
    )

    @Test
    fun `paired agents are kept with their token sealed, the newest selected`() = runBlocking {
        val a = store.add(agent("laptop", "a".repeat(64)), "tvp_one")
        val b = store.add(agent("desktop", "b".repeat(64)), "tvp_two")
        assertEquals(listOf("laptop", "desktop"), store.agents.first().map { it.name })
        assertEquals(b.id, store.selected.first()?.id)
        assertEquals("tvp_one", store.token(a))
        assertFalse(file.readBytes().decodeToString().contains("tvp_one"))
        store.select(a.id)
        assertEquals(a.id, store.selected.first()?.id)
    }

    @Test
    fun `pairing the same computer again replaces it, forgetting selects another`() = runBlocking {
        store.add(agent("laptop", "a".repeat(64)), "tvp_one")
        val again = store.add(agent("laptop", "a".repeat(64)).copy(id = "new"), "tvp_new")
        assertEquals(listOf("new"), store.agents.first().map { it.id })
        assertEquals("tvp_new", store.token(again))
        val other = store.add(agent("desktop", "b".repeat(64)), "tvp_two")
        store.remove(other.id)
        assertEquals("new", store.selected.first()?.id)
        store.remove("new")
        assertNull(store.selected.first())
        assertTrue(store.agents.first().isEmpty())
    }

    @Test
    fun `app preferences default sensibly and persist`() = runBlocking {
        assertEquals(AppPrefs(), store.prefs.first())
        store.setPrefs(AppPrefs(theme = "dark", haptics = false))
        assertEquals("dark", store.prefs.first().theme)
        assertFalse(store.prefs.first().haptics)
    }
}
