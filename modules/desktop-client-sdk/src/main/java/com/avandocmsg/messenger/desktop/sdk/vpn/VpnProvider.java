package com.avandocmsg.messenger.desktop.sdk.vpn;

import java.util.Optional;

public interface VpnProvider {
    VpnProtocol protocol();

    /** Validate config; return error message if invalid. */
    Optional<String> validate(VpnProfile profile, VpnSecrets secrets);

    VpnConnectionState connect(String serverId, VpnProfile profile, VpnSecrets secrets);

    VpnConnectionState disconnect(String serverId);
}
