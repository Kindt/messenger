package com.avandocmsg.messenger.core.domain;

import java.time.Instant;

/** User profile aggregate for read path (Phase 2c). */
public record UserProfile(
    UserId id,
    String username,
    String displayName,
    String phone,
    boolean hidden,
    Instant createdAt,
    String presenceStatus,
    Instant lastSeenAt,
    String orgId,
    boolean privacyDisableReadReceipts,
    String uiLocale,
    String customStatusText,
    Instant dndUntil,
    boolean avatarHidden,
    FileId avatarFileId
) {
    public UserProfile(
        UserId id,
        String username,
        String displayName,
        String phone,
        boolean hidden,
        Instant createdAt,
        String presenceStatus,
        Instant lastSeenAt,
        String orgId,
        boolean privacyDisableReadReceipts,
        String uiLocale,
        String customStatusText,
        Instant dndUntil,
        FileId avatarFileId
    ) {
        this(id, username, displayName, phone, hidden, createdAt, presenceStatus, lastSeenAt, orgId,
            privacyDisableReadReceipts, uiLocale, customStatusText, dndUntil, false, avatarFileId);
    }

    public UserProfile(
        UserId id,
        String username,
        String displayName,
        String phone,
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
        this(id, username, displayName, phone, hidden, createdAt, presenceStatus, lastSeenAt, orgId,
            privacyDisableReadReceipts, uiLocale, customStatusText, dndUntil, false, null);
    }
}
