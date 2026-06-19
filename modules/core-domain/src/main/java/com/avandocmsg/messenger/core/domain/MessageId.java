package com.avandocmsg.messenger.core.domain;

import java.util.UUID;

public record MessageId(UUID value) {
    public static MessageId of(UUID id) {
        return new MessageId(id);
    }

    public static MessageId parse(String raw) {
        return of(UUID.fromString(raw));
    }
}
