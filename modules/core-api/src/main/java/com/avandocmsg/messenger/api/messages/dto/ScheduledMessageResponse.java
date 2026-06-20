package com.avandocmsg.messenger.api.messages.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Scheduled message row")
public record ScheduledMessageResponse(
    String id,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("sender_id") String senderId,
    String type,
    String content,
    @JsonProperty("scheduled_at") String scheduledAt,
    String status,
    @JsonProperty("reply_to_msg_id") String replyToMsgId,
    @JsonProperty("thread_id") String threadId,
    @JsonProperty("client_msg_id") String clientMsgId,
    @JsonProperty("sent_message_id") String sentMessageId,
    @JsonProperty("created_at") String createdAt
) {}
