package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.core.domain.Message;

public final class MessageDomainMapper {
    private MessageDomainMapper() {
    }

    public static MessageResponse toResponse(Message message) {
        return new MessageResponse(
            message.id().value().toString(),
            message.chatId().value().toString(),
            message.senderId().value().toString(),
            message.type(),
            message.content(),
            message.replyToMessageId(),
            message.deleted(),
            message.createdAt(),
            message.editedAt(),
            message.visibilityTtlSeconds(),
            message.attachmentFileId());
    }
}
