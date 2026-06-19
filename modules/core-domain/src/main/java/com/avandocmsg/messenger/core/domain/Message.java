package com.avandocmsg.messenger.core.domain;

import java.time.Instant;

/** Minimal message aggregate for read path (Phase 2b). */
public record Message(
    MessageId id,
    ChatId chatId,
    UserId senderId,
    String type,
    String content,
    String replyToMessageId,
    String threadId,
    boolean deleted,
    Instant createdAt,
    Instant editedAt,
    Integer visibilityTtlSeconds,
    String attachmentFileId
) {}
