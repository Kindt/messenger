package com.avandocmsg.messenger.mobile.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class LocalProfile(
    val schemaVersion: Int = 1,
    val profileId: String,
    val displayName: String,
    val avatarPath: String? = null,
    val createdAt: String,
    val lastUsedAt: String? = null,
    val settings: ProfileSettings = ProfileSettings()
)

@Serializable
data class ProfileSettings(
    val locale: String = "ru",
    val theme: String = "system",
    val attachmentsRoot: String? = null,
    val updateChannel: String = "stable",
    val updatePolicy: String = "notify",
    val updateFeedUrl: String? = null,
    val biometricLock: Boolean = false
)

@Serializable
data class ServerEntry(
    val serverId: String,
    val displayName: String,
    val apiBaseUrl: String,
    val wsPublicUrl: String? = null,
    val trustSelfSigned: Boolean = false,
    val pinnedCertSha256: String? = null,
    val colorToken: String? = null,
    val paused: Boolean = false,
    val lastHealthOkAt: String? = null
)

@Serializable
data class ServerRegistryDocument(
    val schemaVersion: Int = 1,
    val servers: List<ServerEntry> = emptyList()
)

@Serializable
data class LoginRequestDto(val username: String, val password: String)

@Serializable
data class LoginResponseDto(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Int? = null
) {
    fun accessToken(): String = access_token.orEmpty()
}

@Serializable
data class UserMeDto(
    val id: String? = null,
    val user_id: String? = null,
    val login: String? = null,
    val display_name: String? = null
) {
    fun resolvedId(): String = id ?: user_id ?: error("me missing id")
}

@Serializable
data class HealthDto(val status: String? = null)

@Serializable
data class CapabilitiesDto(
    val addons: Map<String, AddonCapabilityDto> = emptyMap(),
    val capabilities: List<String> = emptyList()
)

@Serializable
data class AddonCapabilityDto(val enabled: Boolean = false)

@Serializable
data class CreateChatRequestDto(
    val type: String,
    val title: String? = null,
    val member_ids: List<String> = emptyList()
)

@Serializable
data class ChatDto(val id: String? = null, val chat_id: String? = null, val title: String? = null) {
    fun resolvedId(): String = id ?: chat_id ?: error("chat missing id")
}

@Serializable
data class SendMessageRequestDto(
    val type: String = "text",
    val content: String,
    val reply_to_msg_id: String? = null
)

@Serializable
data class MessageDto(val id: String? = null, val content: String? = null)

@Serializable
data class ContactDto(
    val id: String? = null,
    val user_id: String? = null,
    val login: String? = null,
    val display_name: String? = null
) {
    fun resolvedId(): String = id ?: user_id ?: ""
    fun label(): String = display_name ?: login ?: resolvedId().ifBlank { "?" }
}
