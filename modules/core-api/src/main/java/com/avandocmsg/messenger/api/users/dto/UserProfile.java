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
    @JsonProperty("ui_locale") String uiLocale
) {}
