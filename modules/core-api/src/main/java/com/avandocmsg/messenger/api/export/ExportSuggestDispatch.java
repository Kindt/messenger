package com.avandocmsg.messenger.api.export;

/** How {@link com.avandocmsg.messenger.api.admin.AdminResource} delivers an export suggestion. */
public enum ExportSuggestDispatch {
    LOCAL,
    NATS,
    BOTH;

    public static ExportSuggestDispatch parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LOCAL;
        }
        return switch (raw.trim().toLowerCase()) {
            case "local" -> LOCAL;
            case "nats" -> NATS;
            case "both" -> BOTH;
            default -> throw new IllegalArgumentException(raw);
        };
    }
}
