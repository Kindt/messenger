package com.avandocmsg.messenger.common.model;

import java.util.Objects;
import java.util.UUID;

public record ChatId(UUID value) {
    public ChatId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ChatId generate() {
        return new ChatId(UUID.randomUUID());
    }

    public static ChatId fromString(String s) {
        return new ChatId(UUID.fromString(s));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
