package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LifecycleStatus {
    SUPPORTED_BUNDLED("supported_bundled"),
    SUPPORTED_EXTERNAL_BYO("supported_external_byo"),
    CANDIDATE("candidate"),
    INTEGRATION_CANDIDATE("integration_candidate"),
    REJECTED("rejected");

    private final String code;

    LifecycleStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static LifecycleStatus fromCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("lifecycle_status is required");
        }
        for (var status : values()) {
            if (status.code.equals(value) || status.name().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown lifecycle_status: " + value);
    }
}
