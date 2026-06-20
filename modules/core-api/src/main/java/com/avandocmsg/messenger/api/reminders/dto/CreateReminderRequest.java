package com.avandocmsg.messenger.api.reminders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Create message reminder")
public record CreateReminderRequest(
    @Schema(description = "Chat id") @JsonProperty("chat_id") String chatId,
    @Schema(description = "Message id") @JsonProperty("message_id") String messageId,
    @Schema(description = "Remind at (ISO-8601)") @JsonProperty("remind_at") String remindAt
) {}
