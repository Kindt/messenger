package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** NATS user.presence payload when avatar changes (spec 068). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserAvatarEvent(
    @JsonProperty("type") String type,
    @JsonProperty("user_id") String userId,
    @JsonProperty("org_id") String orgId,
    @JsonProperty("avatar_file_id") String avatarFileId,
    @JsonProperty("avatar_url") String avatarUrl,
    long ts
) {
    public static final String TYPE = "avatar";

    public static UserAvatarEvent of(String userId, String orgId, String avatarFileId, String avatarUrl, long ts) {
        return new UserAvatarEvent(TYPE, userId, orgId, avatarFileId, avatarUrl, ts);
    }
}
