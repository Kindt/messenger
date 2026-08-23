package com.avandocmsg.messenger.desktop.sdk.vpn;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of VPN providers. v1 uses stub/simulation; OS bridges in W4.
 */
public final class VpnProviderRegistry {

    private final Map<VpnProtocol, VpnProvider> providers = new EnumMap<>(VpnProtocol.class);
    private final VpnProvider fallback;

    public VpnProviderRegistry() {
        var stub = new StubVpnProvider();
        fallback = stub;
        for (var protocol : VpnProtocol.values()) {
            if (protocol == VpnProtocol.DISABLED) {
                continue;
            }
            providers.put(protocol, switch (protocol) {
                case WIREGUARD -> new WireGuardVpnProvider();
                case OPENVPN -> new OpenVpnVpnProvider();
                default -> stub;
            });
        }
    }

    public VpnProvider resolve(VpnProtocol protocol) {
        if (protocol == null || protocol == VpnProtocol.DISABLED) {
            return fallback;
        }
        return providers.getOrDefault(protocol, fallback);
    }
}
