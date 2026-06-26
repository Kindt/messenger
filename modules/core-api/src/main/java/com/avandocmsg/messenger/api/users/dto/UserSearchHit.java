package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Усечённая карточка для подстановки в UI (без телефона и служебных полей). */
public record UserSearchHit(
    @JsonProperty("user_id") String userId,
    String username,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("avatar_url") String avatarUrl
) {
    public UserSearchHit(String userId, String username, String displayName) {
        this(userId, username, displayName, null);
    }
}
