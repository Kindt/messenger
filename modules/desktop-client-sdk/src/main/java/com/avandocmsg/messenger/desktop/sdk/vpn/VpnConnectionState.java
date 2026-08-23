package com.avandocmsg.messenger.desktop.sdk.vpn;

import java.time.Instant;

public record VpnConnectionState(
    boolean connected,
    String serverId,
    String profileId,
    VpnProtocol protocol,
    String message,
    Instant since
) {
    public static VpnConnectionState disconnected() {
        return new VpnConnectionState(false, null, null, VpnProtocol.DISABLED, "disconnected", null);
    }
}
