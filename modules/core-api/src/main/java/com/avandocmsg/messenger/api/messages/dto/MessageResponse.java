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
    @Schema(description = "Срок видимости с момента создания (сек); null = без TTL (ТЗ п. 12.4)", example = "3600")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("visibility_ttl_seconds") Integer visibilityTtlSeconds,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("attachment_file_id") String attachmentFileId,
    @Schema(description = "Preview of parent message when replying")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("reply_preview") MessageReplyPreview replyPreview
) {
    public MessageResponse(
        String id,
        String chatId,
        String senderId,
        String type,
        String content,
        String replyToMsgId,
        boolean deleted,
        Instant createdAt,
        Instant editedAt,
        Integer visibilityTtlSeconds,
        String attachmentFileId
    ) {
        this(id, chatId, senderId, type, content, replyToMsgId, deleted, createdAt, editedAt,
            visibilityTtlSeconds, attachmentFileId, null);
    }
}
