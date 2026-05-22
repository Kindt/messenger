package com.avandocmsg.messenger.common.dto;

/**
 * Realtime reaction add/remove (NATS {@code msg.reaction} → pipeline → {@code msg.deliver.*}).
 */
public record ReactionChangeEvent(
    String change,
    String messageId,
    String chatId,
    String userId,
    String reaction
) {}
