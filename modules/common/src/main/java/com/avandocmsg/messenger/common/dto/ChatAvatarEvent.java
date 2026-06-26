package com.avandocmsg.messenger.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Per-member chat avatar update (spec 068). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatAvatarEvent(
    @JsonProperty("type") String type,
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("avatar_file_id") String avatarFileId,
    @JsonProperty("display_avatar_url") String displayAvatarUrl,
    long ts
) {
    public static final String TYPE = "chat.avatar";

    public static ChatAvatarEvent of(String chatId, String avatarFileId, String displayAvatarUrl, long ts) {
        return new ChatAvatarEvent(TYPE, chatId, avatarFileId, displayAvatarUrl, ts);
    }
}
