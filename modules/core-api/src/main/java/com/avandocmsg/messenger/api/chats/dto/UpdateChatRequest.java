package com.avandocmsg.messenger.api.chats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateChatRequest(
    String title,
    @JsonProperty("avatar_file_id") String avatarFileId,
    @JsonProperty("remove_avatar") Boolean removeAvatar
) {}
