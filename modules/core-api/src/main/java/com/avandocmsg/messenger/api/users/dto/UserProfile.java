package com.avandocmsg.messenger.api.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserProfile(
    String id,
    String username,
    @JsonProperty("display_name") String displayName,
    String phone,
    String email,
    @JsonProperty("external_id") String externalId,
    boolean hidden,
    @JsonProperty("created_at") Instant createdAt,
    /** {@code online}, {@code away}, {@code dnd}, {@code offline} */
    @JsonProperty("presence_status") String presenceStatus,
    @JsonProperty("last_seen_at") Instant lastSeenAt,
    @JsonProperty("org_id") String orgId,
    @JsonProperty("privacy_disable_read_receipts") boolean privacyDisableReadReceipts,
    @JsonProperty("ui_locale") String uiLocale,
    @JsonProperty("custom_status_text") String customStatusText,
    @JsonProperty("dnd_until") Instant dndUntil,
    @JsonProperty("avatar_hidden") boolean avatarHidden,
    @JsonProperty("avatar_file_id") String avatarFileId,
    @JsonProperty("avatar_url") String avatarUrl
) {
    public UserProfile(
        String id,
        String username,
        String displayName,
        String phone,
        String email,
        String externalId,
        boolean hidden,
        Instant createdAt,
        String presenceStatus,
        Instant lastSeenAt,
        String orgId,
        boolean privacyDisableReadReceipts,
        String uiLocale,
        String customStatusText,
        Instant dndUntil
    ) {
        this(id, username, displayName, phone, email, externalId, hidden, createdAt, presenceStatus, lastSeenAt,
            orgId, privacyDisableReadReceipts, uiLocale, customStatusText, dndUntil, false, null, null);
    }

    public UserProfile(
        String id,
        String username,
        String displayName,
        String phone,
        String email,
        String externalId,
        boolean hidden,
        Instant createdAt,
        String presenceStatus,
        Instant lastSeenAt,
        String orgId,
        boolean privacyDisableReadReceipts,
        String uiLocale,
        String customStatusText,
        Instant dndUntil,
        String avatarFileId,
        String avatarUrl
    ) {
        this(id, username, displayName, phone, email, externalId, hidden, createdAt, presenceStatus, lastSeenAt,
            orgId, privacyDisableReadReceipts, uiLocale, customStatusText, dndUntil, false, avatarFileId, avatarUrl);
    }
}
