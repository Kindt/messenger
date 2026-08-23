package com.avandocmsg.messenger.desktop.sdk.vpn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VpnProfileDocument(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("profiles") List<VpnProfile> profiles,
    @JsonProperty("server_bindings") List<ServerVpnBinding> serverBindings
) {
    public VpnProfileDocument() {
        this(1, List.of(), List.of());
    }
}
