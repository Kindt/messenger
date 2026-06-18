package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.UUID;

/** Write-path command for persisting a new hot-row message (hex Phase 2b+). */
public record MessageInsert(
    MessageId id,
    ChatId chatId,
    UserId senderId,
    String type,
    String content,
    UUID replyToMsgId,
    UUID threadId,
    String clientMsgId,
    Integer visibilityTtlSeconds,
    UUID attachmentFileId,
    Integer voiceDurationMs
) {}
