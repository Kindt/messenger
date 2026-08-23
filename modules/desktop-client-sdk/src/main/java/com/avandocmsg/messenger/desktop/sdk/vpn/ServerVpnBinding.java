package com.avandocmsg.messenger.desktop.sdk.vpn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerVpnBinding(
    @JsonProperty("server_id") String serverId,
    @JsonProperty("vpn_profile_id") String vpnProfileId,
    @JsonProperty("connect_mode") String connectMode,
    @JsonProperty("enabled") boolean enabled
) {
    public VpnConnectMode connectModeEnum() {
        return VpnConnectMode.fromWire(connectMode);
    }
}
