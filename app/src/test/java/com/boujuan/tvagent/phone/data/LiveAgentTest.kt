package com.boujuan.tvagent.phone.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Against a running tv-agent (opt-in): pairs with the link from its local API, uses the API the screens use, streams
 * a second of silence over TLS, then forgets the phone again. Read-only on the TV, apart from nothing.
 *
 *   ./gradlew testDebugUnitTest --tests '*LiveAgentTest' -PtvAgentPairLink='tvagent://pair?…'
 *
 * (the link: POST /v1/clients/pairing on the agent's local API, or the panel's QR code read with any scanner).
 */
class LiveAgentTest {
    private val link = System.getProperty("tvAgentPairLink").orEmpty()

    @Test
    fun `pair, use and forget a live agent`() = runBlocking {
        assumeTrue("no -PtvAgentPairLink given", link.isNotEmpty())
        val pairing = Pairing.parse(link) ?: error("not a pairing link: $link")
        val reply = AgentApi(pairing.urls, pairing.pin, null).pair(pairing.code, "LiveAgentTest")
        val api = AgentApi(pairing.urls, pairing.pin, reply.token)
        try {
            assertEquals("LiveAgentTest", api.me().device.name)
            assertTrue(api.about().version.isNotEmpty())
            assertTrue(api.settings().choices.audio.isNotEmpty())
            assertTrue(api.pipeline().steps.isNotEmpty())
            api.activity(5)
            api.stats()
            assertTrue(api.apps().isNotEmpty())
            assertEquals("transport", api.decide("pause").action?.get("type")?.toString()?.trim('"'))
            api.nowPlaying()
            val agent = SavedAgent("live", reply.agent.name, pairing.urls, pairing.pin, reply.streamPort, reply.device.id,
                reply.device.name, sealedToken = "")
            VoiceStream.open(api, agent, reply.token).use { stream ->
                val silence = ByteArray(640)
                repeat(50) { stream.send(silence, silence.size) }
                stream.end()
                val events = generateSequence { stream.next() }.toList()
                assertTrue(events.toString(), events.last() is StreamEvent.Result)
            }
        } finally {
            api.forget()
        }
        try {
            api.me()
            error("still paired after forgetting")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
        }
    }
}
