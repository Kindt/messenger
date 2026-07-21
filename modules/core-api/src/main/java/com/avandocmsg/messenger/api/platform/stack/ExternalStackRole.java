package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExternalStackRole {
    ACTIVE("active"),
    STANDBY("standby"),
    MIGRATION_SOURCE("migration_source"),
    MIGRATION_TARGET("migration_target"),
    DISABLED("disabled");

    private final String code;

    ExternalStackRole(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ExternalStackRole fromCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        for (var role : values()) {
            if (role.code.equals(value) || role.name().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
