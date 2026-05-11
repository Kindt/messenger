package com.avandocmsg.messenger.common.model;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId fromString(String s) {
        return new UserId(UUID.fromString(s));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
