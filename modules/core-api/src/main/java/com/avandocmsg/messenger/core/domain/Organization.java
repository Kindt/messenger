package com.avandocmsg.messenger.core.domain;

import java.time.Instant;

/** Minimal organization aggregate for read path (Phase 2e). */
public record Organization(
    OrganizationId id,
    String name,
    Instant createdAt
) {}
