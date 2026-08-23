package com.avandocmsg.messenger.desktop.sdk.vpn;

import java.time.Instant;
import java.util.Optional;

public final class OpenVpnVpnProvider implements VpnProvider {

    private final StubVpnProvider delegate = new StubVpnProvider();

    @Override
    public VpnProtocol protocol() {
        return VpnProtocol.OPENVPN;
    }

    @Override
    public Optional<String> validate(VpnProfile profile, VpnSecrets secrets) {
        return delegate.validate(profile, secrets);
    }

    @Override
    public VpnConnectionState connect(String serverId, VpnProfile profile, VpnSecrets secrets) {
        var err = validate(profile, secrets);
        if (err.isPresent()) {
            return new VpnConnectionState(false, serverId, profile.profileId(), protocol(), err.get(), null);
        }
        return new VpnConnectionState(
            true, serverId, profile.profileId(), protocol(),
            "openvpn stub ready (openvpn binary pending OS bridge)", Instant.now()
        );
    }

    @Override
    public VpnConnectionState disconnect(String serverId) {
        return delegate.disconnect(serverId);
    }
}
