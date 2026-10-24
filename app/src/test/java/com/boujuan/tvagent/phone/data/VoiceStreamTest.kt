package com.boujuan.tvagent.phone.data

import com.boujuan.tvagent.phone.BuildConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class VoiceStreamTest {
    @Test
    fun `the agent's lines become events`() {
        assertEquals(StreamEvent.Heard("play the"), StreamProtocol.parse("""{"heard": "play the"}"""))
        assertEquals(StreamEvent.Stop, StreamProtocol.parse("""{"stop": true}"""))
        assertEquals(StreamEvent.Result("pause", "Pause", true),
            StreamProtocol.parse("""{"transcript": "pause", "say": "Pause", "ok": true}"""))
        assertEquals(StreamEvent.Result("", "Didn't hear anything", false),
            StreamProtocol.parse("""{"transcript": "", "say": "Didn't hear anything", "ok": false}"""))
        assertEquals(StreamEvent.Error("busy"), StreamProtocol.parse("""{"error": "busy"}"""))
        assertNull(StreamProtocol.parse("not json"))
        assertNull(StreamProtocol.parse("""{"something": "else"}"""))
    }

    @Test
    fun `cancelling sends the agent's cancel frame and reads its answer`() {
        ServerSocket(0).use { listener ->
            var frame = -1
            val agent = thread {
                listener.accept().use { s ->
                    val input = DataInputStream(s.getInputStream())
                    while (input.read() != '\n'.code) { /* the hello */ }
                    frame = input.readUnsignedShort()
                    s.getOutputStream().apply { write("{\"cancelled\": true}\n".toByteArray()); flush() }
                }
            }
            VoiceStream(Socket("127.0.0.1", listener.localPort), "tvp_secret").use { stream ->
                stream.cancel()
                assertEquals(StreamEvent.Cancelled, stream.next())
            }
            agent.join(5000)
            assertEquals(0xFFFF, frame)
        }
    }

    @Test
    fun `audio goes out in length-prefixed frames, then the end marker`() {
        ServerSocket(0).use { listener ->
            val got = mutableListOf<ByteArray>()
            var hello = ""
            val agent = thread {
                listener.accept().use { s ->
                    val input = DataInputStream(s.getInputStream())
                    hello = generateSequence { input.read().takeIf { it != '\n'.code && it >= 0 } }
                        .map { it.toChar() }.joinToString("")
                    while (true) {
                        val n = input.readUnsignedShort()
                        if (n == 0) break
                        got += ByteArray(n).also { input.readFully(it) }
                    }
                    val out = s.getOutputStream()
                    out.write("{\"heard\": \"pause\"}\n{\"stop\": true}\n{\"transcript\": \"pause\", \"say\": \"Pause\", \"ok\": true}\n".toByteArray())
                    out.flush()
                }
            }
            val audio = ByteArray(10_000) { (it % 251).toByte() }
            VoiceStream(Socket("127.0.0.1", listener.localPort), "tvp_secret").use { stream ->
                stream.send(audio, audio.size)
                stream.end()
                assertEquals(StreamEvent.Heard("pause"), stream.next())
                assertEquals(StreamEvent.Stop, stream.next())
                assertEquals(StreamEvent.Result("pause", "Pause", true), stream.next())
                assertNull(stream.next())
            }
            agent.join(5000)
            assertEquals("""{"token":"tvp_secret","rate":16000,"app_version":"${BuildConfig.VERSION_NAME}"}""", hello)
            assertEquals(listOf(8192, 1808), got.map { it.size })          // no frame over the agent's 8192
            assertArrayEquals(audio, got.reduce { a, b -> a + b })
        }
    }

    @Test
    fun `loudness is measured from 16-bit little-endian samples`() {
        val quiet = ByteArray(640)
        val loud = ByteArray(640) { if (it % 2 == 0) 0x70 else 0x17 }   // 0x1770 = 6000 in every sample
        assertEquals(0f, Recorder.level(quiet))
        assertEquals(1f, Recorder.level(loud))
    }
}
