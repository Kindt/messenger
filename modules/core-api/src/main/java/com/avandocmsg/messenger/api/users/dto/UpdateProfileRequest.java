package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateProfileRequest(
    @JsonProperty("display_name") String displayName,
    String phone,
    @JsonProperty("avatar_file_id") String avatarFileId,
    @JsonProperty("remove_avatar") Boolean removeAvatar,
    @JsonProperty("avatar_hidden") Boolean avatarHidden
) {}
