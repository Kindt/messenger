package com.avandocmsg.messenger.common.model;

import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) {
    public MessageId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static MessageId generate() {
        return new MessageId(UUID.randomUUID());
    }

    public static MessageId fromString(String s) {
        return new MessageId(UUID.fromString(s));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
