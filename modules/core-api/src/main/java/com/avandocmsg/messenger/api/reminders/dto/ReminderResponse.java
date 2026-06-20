package com.avandocmsg.messenger.api.reminders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Message reminder")
public record ReminderResponse(
    String id,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("message_id") String messageId,
    @JsonProperty("remind_at") String remindAt,
    String status,
    @JsonProperty("created_at") String createdAt
) {}
