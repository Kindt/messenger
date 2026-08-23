package com.avandocmsg.messenger.desktop.sdk.vpn;

/** Supported VPN / tunnel protocols (desktop client registry). */
public enum VpnProtocol {
    DISABLED("disabled", false),
    WIREGUARD("wireguard", true),
    OPENVPN("openvpn", true),
    IKEV2_EAP("ikev2_eap", true),
    IKEV2_CERT("ikev2_cert", true),
    L2TP_IPSEC_PSK("l2tp_ipsec_psk", true),
    L2TP_IPSEC_CERT("l2tp_ipsec_cert", true),
    SSTP("sstp", true),
    PPTP("pptp", true),
    OPENCONNECT("openconnect", true),
    CISCO_ANYCONNECT("cisco_anyconnect", true),
    GLOBALPROTECT("globalprotect", true),
    FORTINET_SSL("fortinet_ssl", true),
    CHECKPOINT_MOBILE("checkpoint_mobile", true),
    JUNIPER_PULSE("juniper_pulse", true),
    SHADOWSOCKS("shadowsocks", true),
    SOCKS5("socks5", true),
    HTTP_PROXY("http_proxy", false),
    CUSTOM_CLI("custom_cli", true);

    private final String wireId;
    private final boolean tunnel;

    VpnProtocol(String wireId, boolean tunnel) {
        this.wireId = wireId;
        this.tunnel = tunnel;
    }

    public String wireId() {
        return wireId;
    }

    public boolean isTunnel() {
        return tunnel;
    }

    public static VpnProtocol fromWire(String id) {
        if (id == null || id.isBlank()) {
            return DISABLED;
        }
        for (var p : values()) {
            if (p.wireId.equalsIgnoreCase(id)) {
                return p;
            }
        }
        return CUSTOM_CLI;
    }
}
