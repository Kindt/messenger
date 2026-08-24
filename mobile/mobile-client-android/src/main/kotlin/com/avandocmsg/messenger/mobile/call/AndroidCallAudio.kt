package com.avandocmsg.messenger.mobile.call

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.avandocmsg.messenger.media.PcmuCodec
import com.avandocmsg.messenger.mobile.sdk.call.InProcessCallClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Android capture/playback bridge for in-process PCMU calls (8 kHz / 16-bit / mono). */
class AndroidCallAudio(
    private val scope: CoroutineScope,
    private val client: InProcessCallClient
) : AutoCloseable {
    private val sampleRate = 8_000
    private val frameBytes = 160 * 2
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var captureJob: Job? = null
    private var running = true

    val captureEnabled: Boolean
        get() = recorder != null

    val playbackEnabled: Boolean
        get() = player != null

    fun start() {
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        player = openPlayer(format)?.also {
            client.onPcmu { pcmu ->
                if (running) {
                    play(it, pcm16le(PcmuCodec.decode(pcmu)))
                }
            }
        }
        recorder = openRecorder()?.also { mic ->
            captureJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(frameBytes)
                while (isActive && running && client.mediaReady()) {
                    val read = mic.read(buffer, 0, buffer.size)
                    if (read >= buffer.size) {
                        client.sendPcmu(encodeFrame(buffer))
                    }
                }
            }
        }
    }

    override fun close() {
        running = false
        captureJob?.cancel()
        recorder?.run {
            stop()
            release()
        }
        recorder = null
        player?.run {
            stop()
            flush()
            release()
        }
        player = null
    }

    private fun openRecorder(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (min <= 0) {
            return null
        }
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                min.coerceAtLeast(frameBytes * 4)
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    return null
                }
                record.startRecording()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openPlayer(format: AudioFormat): AudioTrack? {
        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (min <= 0) {
            return null
        }
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(format)
                .setBufferSizeInBytes(min.coerceAtLeast(frameBytes * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { track ->
                    if (track.state != AudioTrack.STATE_INITIALIZED) {
                        track.release()
                        return null
                    }
                    track.play()
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun play(track: AudioTrack, pcm16le: ByteArray) {
        if (pcm16le.isNotEmpty()) {
            track.write(pcm16le, 0, pcm16le.size)
        }
    }

    private fun encodeFrame(pcm16le: ByteArray): ByteArray =
        PcmuCodec.encode(pcm16leToShorts(pcm16le))

    private fun pcm16le(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            bytes[i * 2] = (samples[i].toInt() and 0xff).toByte()
            bytes[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
        }
        return bytes
    }

    private fun pcm16leToShorts(bytes: ByteArray): ShortArray {
        val samples = ShortArray(bytes.size / 2)
        for (i in samples.indices) {
            samples[i] = ((bytes[i * 2].toInt() and 0xff) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
        }
        return samples
    }
}
