package com.avandocmsg.messenger.mobile.sdk.api

import com.avandocmsg.messenger.mobile.sdk.model.CapabilitiesDto
import com.avandocmsg.messenger.mobile.sdk.model.CallJoinDto
import com.avandocmsg.messenger.mobile.sdk.model.CallSignalDto
import com.avandocmsg.messenger.mobile.sdk.model.ChatDto
import com.avandocmsg.messenger.mobile.sdk.model.ContactDto
import com.avandocmsg.messenger.mobile.sdk.model.CreateChatRequestDto
import com.avandocmsg.messenger.mobile.sdk.model.CreateCallRequestDto
import com.avandocmsg.messenger.mobile.sdk.model.HealthDto
import com.avandocmsg.messenger.mobile.sdk.model.LoginRequestDto
import com.avandocmsg.messenger.mobile.sdk.model.LoginResponseDto
import com.avandocmsg.messenger.mobile.sdk.model.MessageDto
import com.avandocmsg.messenger.mobile.sdk.model.SendMessageRequestDto
import com.avandocmsg.messenger.mobile.sdk.model.UserMeDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KorusApiClient(
    private val http: HttpClient,
    private val apiBaseUrl: String
) {
    private fun api(path: String): String {
        val base = apiBaseUrl.trimEnd('/')
        val p = path.trimStart('/')
        return if (base.endsWith("/api")) "$base/$p" else "$base/api/$p"
    }

    suspend fun health(): HealthDto = http.get(api("v1/health")).body()

    suspend fun login(username: String, password: String): LoginResponseDto =
        http.post(api("v1/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequestDto(username, password))
        }.body()

    suspend fun me(token: String): UserMeDto =
        http.get(api("v1/users/me")) {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun capabilities(token: String): CapabilitiesDto =
        http.get(api("v1/platform/capabilities")) {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun createGroupChat(token: String, title: String, memberIds: List<String>): ChatDto =
        http.post(api("v1/chats")) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateChatRequestDto(type = "group", title = title, member_ids = memberIds))
        }.body()

    suspend fun sendMessage(token: String, chatId: String, content: String): MessageDto =
        http.post(api("v1/chats/$chatId/messages")) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SendMessageRequestDto(content = content))
        }.body()

    suspend fun listChats(token: String): List<ChatDto> =
        http.get(api("v1/chats")) {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun listMessages(token: String, chatId: String, limit: Int = 50): List<MessageDto> =
        http.get(api("v1/chats/$chatId/messages")) {
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
        }.body()

    suspend fun listContacts(token: String): List<ContactDto> =
        http.get(api("v1/contacts")) {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun createCall(
        token: String,
        chatId: String,
        kind: String = "group",
        mediaIntent: String = "audio"
    ): CallJoinDto =
        http.post(api("v1/chats/$chatId/calls")) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateCallRequestDto(kind = kind, mediaIntent = mediaIntent))
        }.body()

    suspend fun joinCall(token: String, chatId: String, sessionId: String): CallJoinDto =
        http.post(api("v1/chats/$chatId/calls/$sessionId/join")) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }.body()

    suspend fun sendCallSignal(
        token: String,
        join: CallJoinDto,
        type: String,
        sdp: String? = null,
        candidate: String? = null
    ) {
        http.post(api(callSignalPath(join))) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CallSignalDto(type = type, sdp = sdp, candidate = candidate))
        }
    }

    suspend fun pollCallSignals(token: String, join: CallJoinDto): List<CallSignalDto> =
        http.get(api(callSignalPath(join))) {
            header("Authorization", "Bearer $token")
        }.body()

    suspend fun leaveCall(token: String, join: CallJoinDto) {
        http.post(
            api(
                "v1/chats/${join.chatId}/calls/${join.sessionId}" +
                    "/participants/${join.participantId}/leave"
            )
        ) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }
    }

    suspend fun endCall(token: String, chatId: String, sessionId: String) {
        http.post(api("v1/chats/$chatId/calls/$sessionId/end")) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }
    }

    suspend fun searchMessages(token: String, query: String, limit: Int = 10): Map<String, kotlinx.serialization.json.JsonElement> =
        http.get(api("v1/search/messages")) {
            header("Authorization", "Bearer $token")
            parameter("q", query)
            parameter("limit", limit)
        }.body()

    private fun callSignalPath(join: CallJoinDto): String =
        "v1/chats/${join.chatId}/calls/${join.sessionId}/signals/${join.participantId}"
}
