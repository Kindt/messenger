package com.avandocmsg.messenger.desktop.sdk.identity;

import java.util.Objects;
import java.util.UUID;

public record ServerId(String value) {
    public ServerId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("serverId blank");
        }
    }

    public static ServerId random() {
        return new ServerId(UUID.randomUUID().toString());
    }
}
