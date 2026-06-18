package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeSettingsResponse(
    @JsonProperty("push") PushSettings push
) {
    public record PushSettings(
        @JsonProperty("vapid_public_key") String vapidPublicKey
    ) {
    }
}
