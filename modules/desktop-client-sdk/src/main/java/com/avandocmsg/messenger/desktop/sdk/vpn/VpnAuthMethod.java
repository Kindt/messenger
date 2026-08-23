package com.avandocmsg.messenger.desktop.sdk.vpn;

public enum VpnAuthMethod {
    NONE("none"),
    PASSWORD("password"),
    CERTIFICATE("certificate"),
    PKCS12("pkcs12"),
    SMART_CARD("smart_card"),
    TOTP_2FA("totp_2fa"),
    USER_CERT_TOTP("user_cert_totp"),
    SAML_WEB("saml_web");

    private final String wireId;

    VpnAuthMethod(String wireId) {
        this.wireId = wireId;
    }

    public String wireId() {
        return wireId;
    }

    public boolean requiresTotp() {
        return this == TOTP_2FA || this == USER_CERT_TOTP;
    }

    public static VpnAuthMethod fromWire(String id) {
        if (id == null || id.isBlank()) {
            return NONE;
        }
        for (var m : values()) {
            if (m.wireId.equalsIgnoreCase(id)) {
                return m;
            }
        }
        return PASSWORD;
    }
}
