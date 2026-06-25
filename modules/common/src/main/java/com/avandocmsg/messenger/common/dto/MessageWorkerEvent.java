package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.StandardCharsets;

/**
 * Safe metadata published to {@code msg.event.*} after pipeline fan-out.
 * Never carries message body or decrypted text; {@link #storageByteLength()} is UTF-8 byte length of the
 * persisted DB field (ciphertext length for E2EE types).
 */
public record MessageWorkerEvent(
    String messageId,
    String chatId,
    String senderId,
    String clientMsgId,
    Long createdAtEpochMs,
    String type,
    int flags,
    boolean encrypted,
    Integer storageByteLength,
    /** Plaintext snippet for Solr only when {@link #encrypted} is false; omitted in JSON when null. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String searchText,
    /**
     * Solr indexer operation: {@code null} or omitted — upsert (новое сообщение из pipeline);
     * {@code update} — полная переиндексация после правки в БД; {@code delete} — удалить документ из Solr (мягкое удаление сообщения).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("index_op")
    String indexOp
) {
    private static boolean isEncryptedType(String type) {
        return type != null && type.startsWith("e2ee-");
    }

    private static final int MAX_SEARCH_TEXT_CHARS = 8000;

    /** Max {@link #searchText()} chars on push/bot NATS subjects (preview only). */
    public static final int PUSH_BOT_SEARCH_TEXT_MAX = 128;

    /**
     * Slim copy for push/bot consumers that only need a short preview (spec 025 FR-044/FR-045).
     */
    public MessageWorkerEvent withSearchTextMaxChars(int maxChars) {
        if (maxChars <= 0 || searchText == null || searchText.length() <= maxChars) {
            return this;
        }
        return new MessageWorkerEvent(
            messageId,
            chatId,
            senderId,
            clientMsgId,
            createdAtEpochMs,
            type,
            flags,
            encrypted,
            storageByteLength,
            searchText.substring(0, maxChars),
            indexOp
        );
    }

    public static MessageWorkerEvent fromSendEvent(MessageSendEvent send) {
        boolean encrypted = isEncryptedType(send.type());
        Integer storageLen = null;
        if (send.content() != null) {
            storageLen = send.content().getBytes(StandardCharsets.UTF_8).length;
        }
        String searchText = null;
        if (!encrypted && send.content() != null && !send.content().isBlank()) {
            var c = send.content();
            searchText = c.length() <= MAX_SEARCH_TEXT_CHARS ? c : c.substring(0, MAX_SEARCH_TEXT_CHARS);
        }
        return new MessageWorkerEvent(
            send.messageId(),
            send.chatId(),
            send.senderId(),
            send.clientMsgId(),
            send.createdAt(),
            send.type(),
            0,
            encrypted,
            storageLen,
            searchText,
            null
        );
    }

    /**
     * Событие индексации из актуальной строки сообщения в БД (после {@code PATCH} и т.п.).
     */
    public static MessageWorkerEvent fromPersistedMessage(
        String messageId,
        String chatId,
        String senderId,
        String clientMsgId,
        Long createdAtEpochMs,
        String type,
        String content,
        String indexOp
    ) {
        boolean encrypted = isEncryptedType(type);
        Integer storageLen = null;
        if (content != null) {
            storageLen = content.getBytes(StandardCharsets.UTF_8).length;
        }
        String searchText = null;
        if (!encrypted && content != null && !content.isBlank()) {
            var c = content;
            searchText = c.length() <= MAX_SEARCH_TEXT_CHARS ? c : c.substring(0, MAX_SEARCH_TEXT_CHARS);
        }
        return new MessageWorkerEvent(
            messageId,
            chatId,
            senderId,
            clientMsgId,
            createdAtEpochMs,
            type,
            0,
            encrypted,
            storageLen,
            searchText,
            indexOp
        );
    }

    /** Удалить документ из Solr по id сообщения (мягкое удаление в PostgreSQL). */
    public static MessageWorkerEvent forIndexDelete(String messageId) {
        return new MessageWorkerEvent(
            messageId,
            null,
            null,
            null,
            null,
            null,
            0,
            false,
            null,
            null,
            "delete"
        );
    }
}
