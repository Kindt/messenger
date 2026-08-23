package com.avandocmsg.messenger.desktop.sdk.vpn;

public enum VpnConnectMode {
    MANUAL("manual"),
    BEFORE_API("before_api"),
    ON_DEMAND("on_demand");

    private final String wireId;

    VpnConnectMode(String wireId) {
        this.wireId = wireId;
    }

    public String wireId() {
        return wireId;
    }

    public static VpnConnectMode fromWire(String id) {
        if (id == null || id.isBlank()) {
            return MANUAL;
        }
        for (var m : values()) {
            if (m.wireId.equalsIgnoreCase(id)) {
                return m;
            }
        }
        return MANUAL;
    }
}
