package com.avandocmsg.messenger.common.model;

public record DeviceId(String value) {
    public DeviceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
