package com.avandocmsg.messenger.api.platform.stack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DeploymentMode {
    BUNDLED("bundled"),
    EXTERNAL_BYO("external_byo"),
    MANAGED_BY_CUSTOMER("managed_by_customer"),
    RF_CANDIDATE("rf_candidate"),
    UNSUPPORTED("unsupported");

    private final String wire;

    DeploymentMode(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static DeploymentMode fromWire(String value) {
        if (value == null) {
            return null;
        }
        for (var mode : values()) {
            if (mode.wire.equals(value) || mode.name().equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown DeploymentMode: " + value);
    }
}
