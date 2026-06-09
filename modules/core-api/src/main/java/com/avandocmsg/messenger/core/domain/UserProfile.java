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
    boolean privacyDisableReadReceipts
) {}
