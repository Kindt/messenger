package com.avandocmsg.messenger.mobile.sdk.identity

import java.util.UUID

@JvmInline
value class ServerId(val value: String) {
    init {
        require(value.isNotBlank()) { "serverId blank" }
    }

    companion object {
        fun random(): ServerId = ServerId(UUID.randomUUID().toString())
    }
}

data class ContactRef(val serverId: ServerId, val userId: String) {
    init {
        require(userId.isNotBlank()) { "userId blank" }
    }
}

data class ChatRef(val serverId: ServerId, val chatId: String) {
    init {
        require(chatId.isNotBlank()) { "chatId blank" }
    }
}
