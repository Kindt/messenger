package com.avandocmsg.messenger.mobile.sdk.offline

import com.avandocmsg.messenger.mobile.sdk.identity.ChatRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.avandocmsg.messenger.mobile.sdk.io.KorusFileIo
import java.nio.file.Path

@Serializable
data class OutgoingMessage(
    val chatRef: String,
    val content: String,
    val createdAt: String
)

@Serializable
data class OutgoingQueueDocument(val messages: List<OutgoingMessage> = emptyList())

class OutgoingMessageQueue(private val queueFile: Path, private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun enqueue(chatRef: ChatRef, content: String) {
        val list = load()
        val updated = list + OutgoingMessage(
            chatRef = "${chatRef.serverId.value}:${chatRef.chatId}",
            content = content,
            createdAt = java.time.Instant.now().toString()
        )
        write(updated)
    }

    fun drain(): List<OutgoingMessage> {
        val list = load()
        write(emptyList())
        return list
    }

    fun pendingCount(): Int = load().size

    private fun load(): List<OutgoingMessage> {
        if (!KorusFileIo.exists(queueFile)) return emptyList()
        return json.decodeFromString<OutgoingQueueDocument>(KorusFileIo.readText(queueFile)).messages
    }

    private fun write(list: List<OutgoingMessage>) {
        KorusFileIo.writeText(queueFile, json.encodeToString(OutgoingQueueDocument(list)))
    }
}
