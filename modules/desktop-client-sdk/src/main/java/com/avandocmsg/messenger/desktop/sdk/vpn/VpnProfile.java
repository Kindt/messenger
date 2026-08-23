package com.avandocmsg.messenger.desktop.sdk.vpn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VpnProfile(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("profile_id") String profileId,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("protocol") String protocol,
    @JsonProperty("auth_method") String authMethod,
    @JsonProperty("connect_mode") String connectMode,
    @JsonProperty("server_host") String serverHost,
    @JsonProperty("server_port") Integer serverPort,
    @JsonProperty("username") String username,
    @JsonProperty("totp_enabled") boolean totpEnabled,
    @JsonProperty("verify_server_cert") boolean verifyServerCert,
    @JsonProperty("pinned_cert_sha256") String pinnedCertSha256,
    @JsonProperty("wireguard_config") String wireguardConfig,
    @JsonProperty("openvpn_inline_config") String openvpnInlineConfig,
    @JsonProperty("protocol_options") Map<String, String> protocolOptions,
    @JsonProperty("custom_cli_template") String customCliTemplate
) {
    public VpnProtocol protocolEnum() {
        return VpnProtocol.fromWire(protocol);
    }

    public VpnAuthMethod authEnum() {
        return VpnAuthMethod.fromWire(authMethod);
    }

    public VpnConnectMode connectModeEnum() {
        return VpnConnectMode.fromWire(connectMode);
    }
}
