package com.avandocmsg.messenger.api.platform;

public enum PlatformModuleState {
    REQUIRED("required"),
    ENABLED("enabled"),
    DISABLED("disabled"),
    DEGRADED("degraded"),
    INSTALLING("installing");

    private final String code;

    PlatformModuleState(String code) {
        this.code = code;
    }

    /** Wire / API value (lowercase). */
    public String code() {
        return code;
    }

    public static PlatformModuleState fromCode(String code) {
        for (var value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown PlatformModuleState: " + code);
    }
}
