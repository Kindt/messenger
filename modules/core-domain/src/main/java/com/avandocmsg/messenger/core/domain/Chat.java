package com.avandocmsg.messenger.core.domain;

import java.time.Instant;

/** Minimal chat aggregate root (Phase 2a hexagonal scaffold). */
public record Chat(
    ChatId id,
    String title,
    ChatType type,
    Instant createdAt
) {}
