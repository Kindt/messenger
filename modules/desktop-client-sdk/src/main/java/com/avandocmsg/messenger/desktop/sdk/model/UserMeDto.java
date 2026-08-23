package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserMeDto(
    String id,
    @JsonProperty("user_id") String userId,
    String login,
    @JsonProperty("display_name") String displayName
) {
    public String resolvedId() {
        if (id != null && !id.isBlank()) {
            return id;
        }
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        throw new IllegalStateException("me missing id");
    }
}
