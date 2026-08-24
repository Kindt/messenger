package com.avandocmsg.messenger.mobile.sdk.call

import com.avandocmsg.messenger.media.NativeWebRtcAudioClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NativeCallAudioMedia : CallAudioMedia {
    private val client = NativeWebRtcAudioClient.create()

    override fun createOffer(): String = client.createOffer()

    override suspend fun connect(answerSdp: String) {
        withContext(Dispatchers.IO) {
            client.connect(answerSdp)
        }
    }

    override fun sendPcmu(payload: ByteArray) {
        client.sendPcmu(payload)
    }

    override fun onPcmu(listener: (ByteArray) -> Unit) {
        client.onPcmu { payload -> listener(payload) }
    }

    override fun mediaReady(): Boolean = client.mediaReady()

    override fun close() {
        client.close()
    }
}
