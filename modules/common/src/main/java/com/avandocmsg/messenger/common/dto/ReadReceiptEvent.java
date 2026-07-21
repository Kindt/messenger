package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Realtime read receipt (NATS {@code msg.read_receipt} → WS {@code msg.deliver.*}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadReceiptEvent(
    @JsonProperty("type") String type,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("message_id") String messageId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("read_at") long readAt,
    @JsonProperty("batch_message_ids") List<String> batchMessageIds
) {
    public static final String TYPE = "read_receipt"; // NOSONAR java:S1845 -- wire constant matches JSON field name type

    public static ReadReceiptEvent single(String chatId, String messageId, String userId, long readAt) {
        return new ReadReceiptEvent(TYPE, chatId, messageId, userId, readAt, null);
    }

    public static ReadReceiptEvent batch(String chatId, String userId, long readAt, List<String> messageIds) {
        var primary = messageIds == null || messageIds.isEmpty() ? null : messageIds.get(0);
        return new ReadReceiptEvent(TYPE, chatId, primary, userId, readAt, messageIds);
    }
}
