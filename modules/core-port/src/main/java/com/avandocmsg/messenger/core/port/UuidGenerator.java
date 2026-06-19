package com.avandocmsg.messenger.core.port;

import java.util.UUID;

@FunctionalInterface
public interface UuidGenerator {

    UUID randomUuid();

    static UuidGenerator standard() {
        return UUID::randomUUID;
    }
}
