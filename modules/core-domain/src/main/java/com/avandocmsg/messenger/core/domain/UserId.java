package com.avandocmsg.messenger.core.domain;

import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("user id required");
        }
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }
}
