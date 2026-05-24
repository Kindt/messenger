package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Per-message read receipts")
public record ReadReceiptResponse(
    @JsonProperty("message_id") String messageId,
    @JsonProperty("read_by") List<ReadReceiptUserInfo> readBy
) {}
