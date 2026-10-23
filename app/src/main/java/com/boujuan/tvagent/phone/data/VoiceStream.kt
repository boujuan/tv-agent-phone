package com.boujuan.tvagent.phone.data

import com.boujuan.tvagent.phone.BuildConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.Closeable
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.net.URI

/** What the agent says while a command streams in. */
sealed interface StreamEvent {
    /** What it has understood so far. */
    data class Heard(val text: String) : StreamEvent
    /** It has decided: stop recording. */
    data object Stop : StreamEvent
    data class Result(val transcript: String, val say: String, val ok: Boolean) : StreamEvent
    /** "unauthorized" (revoked), "busy" (another command is being decided). */
    data class Error(val reason: String) : StreamEvent
}

/**
 * The agent's audio stream (tv-agent's server.py, the TV app's protocol), over TLS to the phone stream port:
 *  phone → agent  a JSON line {"token", "rate": 16000}, then frames: a 2-byte big-endian length and PCM16 LE audio;
 *                 a frame of length 0 ends the audio
 *  agent → phone  JSON lines: {"heard"} as it understands, {"stop": true} when it has decided, then
 *                 {"transcript", "say", "ok"}; or {"error"}; then it closes
 */
object StreamProtocol {
    const val RATE = 16000
    const val MAX_FRAME = 8192

    fun hello(token: String, appVersion: String = BuildConfig.VERSION_NAME): ByteArray =
        (buildJsonObject { put("token", token); put("rate", RATE); put("app_version", appVersion) }.toString() + "\n").toByteArray()

    fun parse(line: String): StreamEvent? {
        val o: JsonObject = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
        fun str(key: String) = o[key]?.jsonPrimitive?.content.orEmpty()
        return when {
            "error" in o -> StreamEvent.Error(str("error"))
            "transcript" in o -> StreamEvent.Result(str("transcript"), str("say"), o["ok"]?.jsonPrimitive?.booleanOrNull == true)
            "stop" in o -> StreamEvent.Stop
            "heard" in o -> StreamEvent.Heard(str("heard"))
            else -> null
        }
    }
}

class VoiceStream(private val socket: Socket, token: String) : Closeable {
    private val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    private val reader: BufferedReader = socket.getInputStream().bufferedReader()

    init {
        out.write(StreamProtocol.hello(token))
        out.flush()
    }

    @Synchronized
    fun send(pcm: ByteArray, length: Int) {
        var at = 0
        while (at < length) {
            val n = minOf(StreamProtocol.MAX_FRAME, length - at)
            out.writeShort(n)
            out.write(pcm, at, n)
            at += n
        }
        out.flush()
    }

    @Synchronized
    fun end() {
        out.writeShort(0)
        out.flush()
    }

    /** The next event, or null when the agent has closed the stream. */
    fun next(): StreamEvent? {
        while (true) {
            val line = reader.readLine() ?: return null
            StreamProtocol.parse(line)?.let { return it }
        }
    }

    override fun close() = socket.close()

    companion object {
        /** To the agent's addresses in turn, the one its API last answered on first. */
        fun open(api: AgentApi, agent: SavedAgent, token: String): VoiceStream {
            var last: Exception? = null
            for (url in api.addresses) {
                try {
                    val socket = Tls.socket(URI(url).host.trim('[', ']'), agent.streamPort, agent.pin)
                    socket.soTimeout = 20_000
                    return VoiceStream(socket, token)
                } catch (e: Exception) {
                    last = e
                }
            }
            throw last ?: IOException("no address")
        }
    }
}
