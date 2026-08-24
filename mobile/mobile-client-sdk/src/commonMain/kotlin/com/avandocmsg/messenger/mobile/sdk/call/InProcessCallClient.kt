package com.avandocmsg.messenger.mobile.sdk.call

import com.avandocmsg.messenger.mobile.sdk.api.KorusApiClient
import com.avandocmsg.messenger.mobile.sdk.model.CallJoinDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class InProcessCallClient(
    private val api: KorusApiClient,
    private val scope: CoroutineScope,
    private val mediaFactory: () -> CallAudioMedia
) : AutoCloseable {
    private val leaving = AtomicBoolean(false)
    private var token: String? = null
    private var join: CallJoinDto? = null
    private var media: CallAudioMedia? = null
    private var pollJob: Job? = null
    private var hangupListener: () -> Unit = {}

    val activeJoin: CallJoinDto?
        get() = join

    suspend fun start(
        token: String,
        chatId: String,
        kind: String = "group",
        mediaIntent: String = "audio"
    ): CallJoinDto {
        this.token = required(token, "token")
        join = api.createCall(this.token!!, chatId, kind, mediaIntent)
        connectMedia()
        return join!!
    }

    suspend fun join(token: String, chatId: String, sessionId: String): CallJoinDto {
        this.token = required(token, "token")
        join = api.joinCall(this.token!!, chatId, sessionId)
        connectMedia()
        return join!!
    }

    fun mediaReady(): Boolean = media?.mediaReady() == true

    fun sendPcmu(payload: ByteArray) {
        media?.sendPcmu(payload)
    }

    fun onPcmu(listener: (ByteArray) -> Unit) {
        media?.onPcmu(listener)
    }

    fun onHangup(listener: () -> Unit) {
        hangupListener = listener
    }

    fun leave() {
        if (!leaving.compareAndSet(false, true)) {
            return
        }
        pollJob?.cancel()
        media?.close()
        media = null
        val activeJoin = join
        val activeToken = token
        if (activeJoin != null && activeToken != null) {
            scope.launch {
                runCatching { api.leaveCall(activeToken, activeJoin) }
            }
        }
        join = null
        hangupListener.invoke()
    }

    override fun close() {
        leave()
    }

    private suspend fun connectMedia() {
        val activeToken = required(token, "token")
        val activeJoin = join ?: throw IllegalStateException("join missing")
        media = mediaFactory()
        api.sendCallSignal(activeToken, activeJoin, type = "offer", sdp = media!!.createOffer())
        val answer = awaitAnswer(activeToken, activeJoin)
        media!!.connect(answer)
        pollJob = scope.launch { pollUntilHangup(activeToken, activeJoin) }
    }

    private suspend fun awaitAnswer(token: String, join: CallJoinDto): String {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (System.nanoTime() < deadline && !leaving.get()) {
            for (signal in api.pollCallSignals(token, join)) {
                when (signal.type) {
                    "answer" -> signal.sdp?.let { return it }
                    "error" -> throw IllegalStateException(signal.errorCode ?: "call media rejected")
                }
            }
            delay(200)
        }
        throw IllegalStateException("call answer timed out")
    }

    private suspend fun pollUntilHangup(token: String, join: CallJoinDto) {
        while (!leaving.get() && scope.isActive) {
            try {
                for (signal in api.pollCallSignals(token, join)) {
                    if (signal.type == "hangup" || signal.type == "session_ended") {
                        leave()
                        return
                    }
                }
                delay(400)
            } catch (_: Exception) {
                return
            }
        }
    }

    private fun required(value: String?, name: String): String {
        require(!value.isNullOrBlank()) { "$name required" }
        return value
    }
}
