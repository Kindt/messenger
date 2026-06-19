package com.avandocmsg.messenger.core.domain;

import java.time.Instant;

/** User contact row (contacts aggregate read model). */
public record Contact(
    UserId contactUserId,
    String username,
    String displayName,
    String phone,
    Instant addedAt
) {}
