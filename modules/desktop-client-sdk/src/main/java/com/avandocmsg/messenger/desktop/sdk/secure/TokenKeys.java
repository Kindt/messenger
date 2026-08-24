package com.avandocmsg.messenger.desktop.sdk.secure;

public final class TokenKeys {
    private TokenKeys() {}

    public static String tokenKey(String serverId, String username) {
        return "token::" + serverId + "::" + username;
    }

    public static String refreshTokenKey(String serverId, String username) {
        return "refresh::" + serverId + "::" + username;
    }

    public static String tokenExpiresKey(String serverId, String username) {
        return "expires::" + serverId + "::" + username;
    }
}
