package com.avandocmsg.messenger.api.bots.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RotateBotTokenResponse(
    @JsonProperty("bot_id") String botId,
    @JsonProperty("access_token") String accessToken
) {}
