package com.boujuan.tvagent.phone.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Connecting : VoiceState
    /** streaming: the agent hears it as you speak (and decides in your pause); else it's sent when you stop. */
    data class Listening(val heard: String, val level: Float, val streaming: Boolean) : VoiceState
    data class Deciding(val heard: String) : VoiceState
    data class Done(val heard: String, val say: String, val ok: Boolean) : VoiceState
    data class Failed(val reason: Reason, val detail: String = "") : VoiceState

    enum class Reason { NO_AGENT, MICROPHONE, BUSY, REVOKED, UNREACHABLE, OTHER }
}

/**
 * One spoken command at a time, from any screen, the Quick Settings tile or the widget.
 *
 * Streamed (the agent's stream port, like the TV remote's mic): the agent hears it as you speak and decides in your
 * pause, then says stop. Holding the button ends it when released; a tap leaves the end to the agent (or 12 s).
 * When the stream can't be had, the recording goes to /v1/audio in one piece: on release, or after a pause.
 */
class VoiceController(private val repo: AgentRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state
    private var job: Job? = null
    private val ending = AtomicBoolean(false)

    val busy: Boolean get() = job?.isActive == true

    fun start() {
        if (busy) return
        val conn = repo.current.value ?: run { _state.value = VoiceState.Failed(VoiceState.Reason.NO_AGENT); return }
        ending.set(false)
        _state.value = VoiceState.Connecting
        job = scope.launch { run(conn) }
    }

    /** Stop listening now (the button released, or tapped again); the agent decides on what it has. */
    fun finish() = ending.set(true)

    fun cancel() {
        job?.cancel()
        _state.value = VoiceState.Idle
    }

    fun dismiss() {
        if (!busy) _state.value = VoiceState.Idle
    }

    /**
     * The microphone starts at once and keeps what you say while the stream connects (a quick hold-to-talk would
     * otherwise lose its first words); that audio goes first once it's open, or into the whole recording if it can't.
     */
    private suspend fun run(conn: Connection) = withContext(Dispatchers.IO) {
        val mic = try {
            Recorder()
        } catch (e: Exception) {
            _state.value = VoiceState.Failed(VoiceState.Reason.MICROPHONE, e.message.orEmpty())
            return@withContext
        }
        val until = System.currentTimeMillis() + MAX_MS
        var stream: VoiceStream? = null
        try {
            val opening = async { runCatching { VoiceStream.open(conn.api, conn.agent, conn.token) }.getOrNull() }
            val early = ArrayList<ByteArray>()
            _state.value = VoiceState.Listening("", 0f, true)
            while (opening.isActive && !ending.get() && System.currentTimeMillis() < until) {
                val pcm = mic.read() ?: break
                early += pcm
                _state.value = VoiceState.Listening("", Recorder.level(pcm), true)
            }
            stream = opening.await()
            if (stream != null) streamed(conn, stream, mic, early, until) else whole(conn, mic, early, until)
        } finally {
            mic.close()
            stream?.close()
            repo.touched()
        }
    }

    private suspend fun streamed(conn: Connection, stream: VoiceStream, mic: Recorder, early: List<ByteArray>, until: Long) =
        withContext(Dispatchers.IO) {
            val heard = AtomicReference("")
            val stop = AtomicBoolean(false)
            val result = AtomicReference<StreamEvent?>(null)
            val reader = launch {
                while (isActive) {
                    val e = try { stream.next() } catch (_: IOException) { null } ?: break
                    when (e) {
                        is StreamEvent.Heard -> heard.set(e.text)
                        is StreamEvent.Stop -> stop.set(true)
                        is StreamEvent.Result, is StreamEvent.Error -> { result.set(e); stop.set(true); break }
                    }
                }
                stop.set(true)
            }
            try {
                early.forEach { stream.send(it, it.size) }
                while (isActive && !stop.get() && !ending.get() && System.currentTimeMillis() < until) {
                    val pcm = mic.read() ?: break
                    stream.send(pcm, pcm.size)
                    _state.value = VoiceState.Listening(heard.get(), Recorder.level(pcm), true)
                }
                _state.value = VoiceState.Deciding(heard.get())
                stream.end()
            } catch (_: IOException) {
                // the agent closed first (it decided, or refused): its answer is read below
            }
            withTimeoutOrNull(25_000) { reader.join() } ?: reader.cancel()
            _state.value = when (val r = result.get()) {
                is StreamEvent.Result -> VoiceState.Done(r.transcript, r.say, r.ok)
                is StreamEvent.Error -> VoiceState.Failed(
                    if (r.reason == "busy") VoiceState.Reason.BUSY else VoiceState.Reason.REVOKED, r.reason)
                else -> VoiceState.Failed(VoiceState.Reason.UNREACHABLE, conn.agent.name)
            }
        }

    private suspend fun whole(conn: Connection, mic: Recorder, early: List<ByteArray>, until: Long) =
        withContext(Dispatchers.IO) {
            val audio = ByteArrayOutputStream()
            var spoke = false
            var quietMs = 0
            fun take(pcm: ByteArray): Boolean {         // false: a pause after speaking, so it's done
                audio.write(pcm)
                val level = Recorder.level(pcm)
                _state.value = VoiceState.Listening("", level, false)
                if (level > SPEECH) {
                    spoke = true
                    quietMs = 0
                } else if (spoke) {
                    quietMs += 20
                }
                return quietMs < QUIET_MS
            }
            var going = early.all { take(it) }
            while (going && isActive && !ending.get() && System.currentTimeMillis() < until) {
                going = take(mic.read() ?: break)
            }
            _state.value = VoiceState.Deciding("")
            _state.value = try {
                val r = conn.api.audio(audio.toByteArray())
                VoiceState.Done(r.heard, r.say, r.ok)
            } catch (e: ApiException) {
                when (e.code) {
                    401 -> VoiceState.Failed(VoiceState.Reason.REVOKED)
                    429 -> VoiceState.Failed(VoiceState.Reason.BUSY)
                    else -> VoiceState.Failed(VoiceState.Reason.OTHER, e.message.orEmpty())
                }
            } catch (e: IOException) {
                VoiceState.Failed(VoiceState.Reason.UNREACHABLE, conn.agent.name)
            }
        }

    companion object {
        const val MAX_MS = 12_000L          // the agent takes 12 s at most
        const val SPEECH = 0.06f
        const val QUIET_MS = 800
    }
}
