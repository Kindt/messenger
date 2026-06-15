package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record BotBanRequest(
    @JsonAlias("user_id") String userId,
    String reason
) {}
