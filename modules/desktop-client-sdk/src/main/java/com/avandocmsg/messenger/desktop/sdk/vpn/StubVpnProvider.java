package com.avandocmsg.messenger.desktop.sdk.vpn;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Simulated VPN connect for lab/demo (no OS tunnel). */
public final class StubVpnProvider implements VpnProvider {

    private final ConcurrentHashMap<String, VpnConnectionState> active = new ConcurrentHashMap<>();

    @Override
    public VpnProtocol protocol() {
        return VpnProtocol.CUSTOM_CLI;
    }

    @Override
    public Optional<String> validate(VpnProfile profile, VpnSecrets secrets) {
        var errors = VpnProfileValidator.validate(profile);
        if (!errors.isEmpty()) {
            return Optional.of(String.join("; ", errors));
        }
        if (profile.authEnum().requiresTotp()) {
            if (secrets.totpCode() == null || secrets.totpCode().isBlank()) {
                return Optional.of("totp_code required for 2FA");
            }
        }
        return Optional.empty();
    }

    @Override
    public VpnConnectionState connect(String serverId, VpnProfile profile, VpnSecrets secrets) {
        var err = validate(profile, secrets);
        if (err.isPresent()) {
            return new VpnConnectionState(false, serverId, profile.profileId(), profile.protocolEnum(), err.get(), null);
        }
        var state = new VpnConnectionState(
            true,
            serverId,
            profile.profileId(),
            profile.protocolEnum(),
            "stub connected (" + profile.protocolEnum().wireId() + ")",
            Instant.now()
        );
        active.put(serverId, state);
        return state;
    }

    @Override
    public VpnConnectionState disconnect(String serverId) {
        active.remove(serverId);
        return VpnConnectionState.disconnected();
    }

    public Optional<VpnConnectionState> state(String serverId) {
        return Optional.ofNullable(active.get(serverId));
    }
}
