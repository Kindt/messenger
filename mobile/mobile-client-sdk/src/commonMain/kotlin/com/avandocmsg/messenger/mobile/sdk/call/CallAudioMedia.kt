package com.avandocmsg.messenger.mobile.sdk.call

interface CallAudioMedia {
    fun createOffer(): String
    suspend fun connect(answerSdp: String)
    fun sendPcmu(payload: ByteArray)
    fun onPcmu(listener: (ByteArray) -> Unit)
    fun mediaReady(): Boolean
    fun close()
}
