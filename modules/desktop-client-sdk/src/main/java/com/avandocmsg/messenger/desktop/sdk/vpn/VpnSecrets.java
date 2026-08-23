package com.avandocmsg.messenger.desktop.sdk.vpn;

public record VpnSecrets(String password, String totpCode, String totpSecret) {
    public static VpnSecrets empty() {
        return new VpnSecrets(null, null, null);
    }
}
