package com.avandocmsg.messenger.core.domain;

import java.util.UUID;

/** Chat aggregate identifier (hexagonal domain type). */
public record ChatId(UUID value) {
    public ChatId {
        if (value == null) {
            throw new IllegalArgumentException("chat id required");
        }
    }

    public static ChatId of(UUID value) {
        return new ChatId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
