package com.avandocmsg.messenger.core.domain;

import java.time.Instant;

/** User blocked by another user (blocks aggregate read model). */
public record BlockedUser(
    UserId userId,
    String username,
    String displayName,
    Instant blockedAt
) {}
