package com.avandocmsg.messenger.common.dto;

public record MessageSendEvent(
    String messageId,
    String chatId,
    String senderId,
    String type,
    String content,
    String clientMsgId,
    Long createdAt
) {}
