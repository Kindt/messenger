package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Message")
public record MessageResponse(
    @Schema(description = "Message ID") String id,
    @Schema(description = "Chat ID") @JsonProperty("chat_id") String chatId,
    @Schema(description = "Sender user ID") @JsonProperty("sender_id") String senderId,
    @Schema(description = "Message type", example = "text") String type,
    @Schema(description = "Message content") String content,
    @Schema(description = "Replied-to message ID") @JsonProperty("reply_to_msg_id") String replyToMsgId,
    @Schema(description = "Whether the message is deleted") boolean deleted,
    @Schema(description = "When the message was created") @JsonProperty("created_at") Instant createdAt,
    @Schema(description = "When the message was last edited") @JsonProperty("edited_at") Instant editedAt,
    @Schema(description = "Срок видимости с момента создания (сек); null = без TTL (ТЗ п. 12.4)")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("ttl_seconds") Integer ttlSeconds
) {}
