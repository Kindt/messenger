package com.avandocmsg.messenger.api.chats.bans.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatBanRequest(
    @JsonProperty(value = "user_id", required = true) String userId,
    String reason
) {}
