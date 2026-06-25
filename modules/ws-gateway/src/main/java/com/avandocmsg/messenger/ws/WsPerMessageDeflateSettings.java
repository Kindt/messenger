package com.avandocmsg.messenger.ws;

/** WebSocket permessage-deflate (RFC 7692). Env: {@code WS_PER_MESSAGE_DEFLATE_ENABLED}. */
public record WsPerMessageDeflateSettings(boolean enabled) {

    /** Default {@code true}: negotiate compression when the client offers it (prod/lab). */
    public static WsPerMessageDeflateSettings fromEnv() {
        return new WsPerMessageDeflateSettings(parseEnabled(System.getenv("WS_PER_MESSAGE_DEFLATE_ENABLED"), true));
    }

    static boolean parseEnabled(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
