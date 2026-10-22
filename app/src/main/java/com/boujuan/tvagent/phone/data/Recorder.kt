package com.boujuan.tvagent.phone.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * The phone's microphone as the agent wants it: 16 kHz mono PCM16, in 20 ms chunks. The caller holds
 * RECORD_AUDIO (the screens ask first).
 */
class Recorder : AutoCloseable {
    private val chunk = StreamProtocol.RATE / 50 * 2               // 20 ms of 16-bit samples, in bytes
    private val record: AudioRecord

    init {
        val min = AudioRecord.getMinBufferSize(StreamProtocol.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        @SuppressLint("MissingPermission")
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, StreamProtocol.RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, chunk * 10))
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("microphone unavailable")
        }
        record = r
        record.startRecording()
    }

    /** The next 20 ms (blocking), or null if the microphone stopped. */
    fun read(): ByteArray? {
        val buf = ByteArray(chunk)
        var at = 0
        while (at < chunk) {
            val n = record.read(buf, at, chunk - at)
            if (n <= 0) return null
            at += n
        }
        return buf
    }

    override fun close() {
        runCatching { record.stop() }
        record.release()
    }

    companion object {
        /** 0..1, roughly how loud a chunk is (for the level meter and the fallback's end of speech). */
        fun level(pcm: ByteArray): Float {
            var sum = 0.0
            val n = pcm.size / 2
            for (i in 0 until n) {
                val s = (pcm[2 * i].toInt() and 0xff) or (pcm[2 * i + 1].toInt() shl 8)
                sum += s.toDouble() * s
            }
            val rms = if (n > 0) sqrt(sum / n) else 0.0
            return (rms / 6000.0).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
