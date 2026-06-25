package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mention fan-out (NATS {@code msg.mention} → pipeline → {@code msg.deliver.*}). */
public record MentionEvent(
    String type,
    @JsonProperty("message_id") String messageId,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("sender_id") String senderId,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("mentioned_user_id") String mentionedUserId,
    @JsonProperty("mention_all") boolean mentionAll,
    @JsonProperty("created_at") Long createdAt
) {
    public static final String TYPE = "mention";

    public MentionEvent(String messageId, String chatId, String senderId, String mentionedUserId,
                        boolean mentionAll, Long createdAt) {
        this(TYPE, messageId, chatId, senderId, mentionedUserId, mentionAll, createdAt);
    }
}
