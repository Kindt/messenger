package com.avandocmsg.messenger.common.dto;

/**
 * Realtime edit/delete fan-out (NATS {@code msg.change} → pipeline → {@code msg.deliver.*}).
 */
public record MessageChangeEvent(
    String change,
    String messageId,
    String chatId,
    String senderId,
    String type,
    String content,
    Long createdAt,
    Long editedAt
) {}
