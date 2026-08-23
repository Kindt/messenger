package com.avandocmsg.messenger.desktop.sdk.secure;

public final class TokenKeys {
    private TokenKeys() {}

    public static String tokenKey(String serverId, String username) {
        return "token::" + serverId + "::" + username;
    }
}
