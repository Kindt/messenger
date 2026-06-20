package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Schedule a message for future delivery")
public record ScheduleMessageRequest(
    @Schema(description = "Message type", example = "text") String type,
    @Schema(description = "Message content") String content,
    @Schema(description = "Send time (ISO-8601)") @JsonProperty("scheduled_at") String scheduledAt,
    @Schema(description = "Reply target message id") @JsonProperty("reply_to_msg_id") String replyToMsgId,
    @Schema(description = "Thread root message id") @JsonProperty("thread_id") String threadId,
    @Schema(description = "Client deduplication id") @JsonProperty("client_msg_id") String clientMsgId
) {}
