package com.avandocmsg.messenger.desktop.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocalProfile(
    int schemaVersion,
    String profileId,
    String displayName,
    String avatarPath,
    String createdAt,
    String lastUsedAt,
    ProfileSettings settings
) {
    public LocalProfile(String profileId, String displayName, String createdAt, String lastUsedAt) {
        this(1, profileId, displayName, null, createdAt, lastUsedAt, new ProfileSettings());
    }
}
