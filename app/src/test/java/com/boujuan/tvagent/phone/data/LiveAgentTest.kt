package com.boujuan.tvagent.phone.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Against a running tv-agent (opt-in): pairs with the link from its local API, makes the calls every screen makes
 * (nothing that plays or changes the TV), flips the private-providers switch and back, then forgets the phone again.
 * With -PtvAgentVoice=1 it also streams a second of silence over TLS (that wakes the TV and lowers its volume a moment).
 *
 *   ./gradlew testDebugUnitTest --tests '*LiveAgentTest' -PtvAgentPairLink='tvagent://pair?…'
 *
 * (the link: POST /v1/clients/pairing on the agent's local API, or the panel's QR code read with any scanner).
 */
class LiveAgentTest {
    private val link = System.getProperty("tvAgentPairLink").orEmpty()
    private val voice = System.getProperty("tvAgentVoice").orEmpty() == "1"

    @Test
    fun `pair, use and forget a live agent`() = runBlocking {
        assumeTrue("no -PtvAgentPairLink given", link.isNotEmpty())
        val pairing = Pairing.parse(link) ?: error("not a pairing link: $link")
        val reply = AgentApi(pairing.urls, pairing.pin, null).pair(pairing.code, "LiveAgentTest")
        val api = AgentApi(pairing.urls, pairing.pin, reply.token)
        try {
            assertEquals("LiveAgentTest", api.me().device.name)
            // Overview
            assertTrue(api.about().version.isNotEmpty())
            api.stats()
            val tv = api.tvStatus()
            assertTrue(tv.toString(), tv.error != null || tv.model.isNotEmpty())
            // Remote
            assertTrue(api.apps().isNotEmpty())
            api.volume()
            // Now playing, the lists, Activity
            api.nowPlaying()
            api.list("recent")
            api.activity(5)
            // Decisions: the switch phones may change saves (and is put back); the laptop's files are the panel's
            val before = api.pipeline()
            assertEquals(!before.llmPrivate, api.setPipeline(buildJsonObject { put("llm_private", !before.llmPrivate) }).llmPrivate)
            assertEquals(before.llmPrivate, api.setPipeline(buildJsonObject { put("llm_private", before.llmPrivate) }).llmPrivate)
            try {
                api.setPipeline(buildJsonObject { put("stt_model", before.sttModel) })
                error("a phone changed stt_model")
            } catch (e: ApiException) {
                assertEquals(403, e.code)
            }
            assertEquals("transport", api.decide("pause").action?.get("type")?.toString()?.trim('"'))
            api.learned()
            // Settings
            assertTrue(api.settings().choices.audio.isNotEmpty())
            if (voice) {
                val agent = SavedAgent("live", reply.agent.name, pairing.urls, pairing.pin, reply.streamPort, reply.device.id,
                    reply.device.name, sealedToken = "")
                VoiceStream.open(api, agent, reply.token).use { stream ->
                    val silence = ByteArray(640)
                    repeat(50) { stream.send(silence, silence.size) }
                    stream.end()
                    val events = generateSequence { stream.next() }.toList()
                    assertTrue(events.toString(), events.last() is StreamEvent.Result)
                }
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
