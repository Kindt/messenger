package com.avandocmsg.messenger.ws;

/** WS connection caps (PS-1.1). Env: {@code WS_MAX_CONNECTIONS_PER_USER}, {@code WS_MAX_TOTAL_CONNECTIONS}. */
public record WsConnectionLimits(int maxPerUser, int maxTotal) {

    public WsConnectionLimits {
        maxPerUser = Math.max(1, maxPerUser);
        maxTotal = Math.max(1, maxTotal);
    }

    public static WsConnectionLimits fromEnv() {
        return new WsConnectionLimits(
            parsePositive(System.getenv("WS_MAX_CONNECTIONS_PER_USER"), 5),
            parsePositive(System.getenv("WS_MAX_TOTAL_CONNECTIONS"), 10_000));
    }

    private static int parsePositive(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
