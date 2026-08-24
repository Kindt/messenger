package com.avandocmsg.messenger.mobile.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateCallRequestDto(
    val kind: String = "group",
    @SerialName("media_intent") val mediaIntent: String = "audio"
)

@Serializable
data class CallIceServerDto(
    val urls: List<String> = emptyList(),
    val username: String? = null,
    val credential: String? = null
)

@Serializable
data class CallJoinDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("participant_id") val participantId: String,
    @SerialName("chat_id") val chatId: String,
    val kind: String,
    val role: String,
    val status: String,
    @SerialName("media_node_id") val mediaNodeId: String,
    @SerialName("signaling_path") val signalingPath: String,
    @SerialName("ice_servers") val iceServers: List<CallIceServerDto> = emptyList()
)

@Serializable
data class CallSignalDto(
    val id: String? = null,
    val type: String,
    val sdp: String? = null,
    val candidate: String? = null,
    @SerialName("error_code") val errorCode: String? = null
)
