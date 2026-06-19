package com.avandocmsg.messenger.core.domain;

import java.util.UUID;

/** Organization aggregate identifier (hexagonal domain type). */
public record OrganizationId(UUID value) {
    public OrganizationId {
        if (value == null) {
            throw new IllegalArgumentException("organization id required");
        }
    }

    public static OrganizationId of(UUID value) {
        return new OrganizationId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
