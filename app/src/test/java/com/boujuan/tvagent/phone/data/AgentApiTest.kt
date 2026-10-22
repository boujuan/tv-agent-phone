package com.boujuan.tvagent.phone.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

/** The client against a real TLS server whose self-signed certificate is pinned, as the agent's phone listener is. */
class AgentApiTest {
    private lateinit var server: MockWebServer
    private lateinit var pin: String
    private lateinit var url: String

    @Before
    fun start() {
        val held = HeldCertificate.Builder().commonName("tv-agent on test").build()     // no SAN: host names unchecked
        server = MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(held).build().sslSocketFactory())
        server.start()
        pin = sha256(held.certificate.encoded)
        url = "https://127.0.0.1:${server.port}"
    }

    @After
    fun stop() = server.close()

    private fun json(body: String, code: Int = 200) = MockResponse(code, headersOf("Content-Type", "application/json"), body)

    @Test
    fun `calls go to the pinned agent with this phone's token`() = runBlocking {
        server.enqueue(json("""{"now_playing": {"imdb_id": "tt14452776", "kind": "series", "video_id": "tt14452776:3:2",
            "name": "The Bear", "season": 3, "episode": 2, "extra": "ignored"},
            "player": {"app": "kodi", "position_s": 600, "duration_s": 1800, "playing": true},
            "tracks": {"audio": [{"index": 0, "language": "eng", "label": "English"}], "audio_current": 0,
                       "subtitles": [], "subtitle_current": null, "subtitles_on": false}, "segments": []}"""))
        val np = AgentApi(listOf(url), pin, "tvp_secret").nowPlaying()
        assertEquals("The Bear", np.nowPlaying?.name)
        assertEquals(3, np.nowPlaying?.season)
        assertEquals(600L, np.player?.positionS)
        assertEquals("English", np.tracks?.audio?.first()?.label)
        val request = server.takeRequest()
        assertEquals("/v1/now-playing", request.target)
        assertEquals("Bearer tvp_secret", request.headers["Authorization"])
    }

    @Test
    fun `a different certificate gets nothing, not even the request`() = runBlocking {
        server.enqueue(json("{}"))
        try {
            AgentApi(listOf(url), "0".repeat(64), "tvp_secret").me()
            fail("a different certificate was trusted")
        } catch (e: Unreachable) {
            assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it is PinMismatch })
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the next address is tried when one can't be reached, and remembered`() = runBlocking {
        val closed = ServerSocket(0).use { it.localPort }          // nothing listens there now
        server.enqueue(json("""{"ok": true, "say": "Paused"}"""))
        server.enqueue(json("""{"ok": true, "say": "Playing"}"""))
        val api = AgentApi(listOf("https://127.0.0.1:$closed", url), pin, "tvp_secret")
        assertEquals("Paused", api.action(Actions.transport("pause")).say)
        assertEquals(url, api.activeUrl)
        assertEquals("Playing", api.action(Actions.transport("resume")).say)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the agent's errors arrive with its reason`() = runBlocking {
        server.enqueue(json("""{"detail": "Not paired (or revoked): pair this phone again"}""", 401))
        try {
            AgentApi(listOf(url), pin, "tvp_old").stats()
            fail("no error")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
            assertEquals("Not paired (or revoked): pair this phone again", e.message)
        }
    }

    @Test
    fun `pairing sends the code without any token and gets one`() = runBlocking {
        server.enqueue(json("""{"token": "tvp_new", "device": {"id": "21be1920", "name": "Pixel"},
            "agent": {"name": "boucatchyOS", "version": "0.3.0"}, "stream_port": 8769}"""))
        val reply = AgentApi(listOf(url), pin, null).pair("AN3977VW", "Pixel")
        assertEquals("tvp_new", reply.token)
        assertEquals(8769, reply.streamPort)
        val request = server.takeRequest()
        assertNull(request.headers["Authorization"])
        assertEquals("""{"code":"AN3977VW","name":"Pixel"}""", request.body?.utf8())
    }

    @Test
    fun `bodies carry their length (the agent refuses chunked ones)`() = runBlocking {
        server.enqueue(json("""{"heard": "pause", "ok": true, "say": "Paused"}"""))
        server.enqueue(json("""{"heard": "pause", "ok": true, "say": "Paused"}"""))
        val api = AgentApi(listOf(url), pin, "tvp_secret")
        api.audio(ByteArray(32000))
        api.text("pause")
        val audio = server.takeRequest()
        assertEquals("32000", audio.headers["Content-Length"])
        assertNull(audio.headers["Transfer-Encoding"])
        val text = server.takeRequest()
        assertEquals("""{"text":"pause","source":"phone"}""", text.body?.utf8())
        assertNull(text.headers["Transfer-Encoding"])
    }

    @Test
    fun `a typed address's certificate can be read without trusting it`() {
        server.enqueue(json("{}"))
        assertEquals(pin, Tls.fingerprintOf("127.0.0.1", server.port))
    }
}
