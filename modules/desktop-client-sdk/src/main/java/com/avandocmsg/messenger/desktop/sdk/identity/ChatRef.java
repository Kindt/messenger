package com.avandocmsg.messenger.desktop.sdk.identity;

import java.util.Objects;

public record ChatRef(ServerId serverId, String chatId) {
    public ChatRef {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(chatId, "chatId");
        if (chatId.isBlank()) {
            throw new IllegalArgumentException("chatId blank");
        }
    }
}
