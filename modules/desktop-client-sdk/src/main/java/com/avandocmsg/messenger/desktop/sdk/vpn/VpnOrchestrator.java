package com.avandocmsg.messenger.desktop.sdk.vpn;

import java.util.concurrent.ConcurrentHashMap;

public final class VpnOrchestrator {

    private final VpnProfileStore store;
    private final VpnProviderRegistry registry;
    private final ConcurrentHashMap<String, VpnConnectionState> states = new ConcurrentHashMap<>();

    public VpnOrchestrator(VpnProfileStore store, VpnProviderRegistry registry) {
        this.store = store;
        this.registry = registry;
    }

    public VpnConnectionState connectServer(String serverId, String totpCode) throws Exception {
        var profile = store.profileForServer(serverId).orElse(null);
        if (profile == null || profile.protocolEnum() == VpnProtocol.DISABLED) {
            return new VpnConnectionState(false, serverId, null, VpnProtocol.DISABLED, "no vpn profile", null);
        }
        var secrets = new VpnSecrets(
            store.password(profile.profileId()).orElse(null),
            totpCode,
            store.totpSecret(profile.profileId()).orElse(null)
        );
        var provider = registry.resolve(profile.protocolEnum());
        var state = provider.connect(serverId, profile, secrets);
        if (state.connected()) {
            states.put(serverId, state);
        }
        return state;
    }

    public VpnConnectionState disconnectServer(String serverId) throws Exception {
        var profile = store.profileForServer(serverId);
        var protocol = profile.map(VpnProfile::protocolEnum).orElse(VpnProtocol.DISABLED);
        var provider = registry.resolve(protocol);
        var state = provider.disconnect(serverId);
        states.remove(serverId);
        return state;
    }

    public VpnConnectionState state(String serverId) {
        return states.getOrDefault(serverId, VpnConnectionState.disconnected());
    }

    /** Connect VPN before API if binding says BEFORE_API. */
    public VpnConnectionState ensureBeforeApi(String serverId, String totpCode) throws Exception {
        var binding = store.bindingForServer(serverId).orElse(null);
        if (binding == null || !binding.enabled()) {
            return VpnConnectionState.disconnected();
        }
        if (binding.connectModeEnum() != VpnConnectMode.BEFORE_API) {
            return state(serverId);
        }
        if (state(serverId).connected()) {
            return state(serverId);
        }
        return connectServer(serverId, totpCode);
    }
}
