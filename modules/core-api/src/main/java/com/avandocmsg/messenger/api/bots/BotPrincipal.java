package com.avandocmsg.messenger.api.bots;

import java.security.Principal;

public record BotPrincipal(String botId, String botName) implements Principal {
    @Override
    public String getName() {
        return botId;
    }
}
