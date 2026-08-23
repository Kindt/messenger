package com.avandocmsg.messenger.desktop.sdk.identity;

import java.util.Objects;

public record ContactRef(ServerId serverId, String userId) {
    public ContactRef {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(userId, "userId");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId blank");
        }
    }

    public String cacheKey() {
        return serverId.value() + ":" + userId;
    }
}
