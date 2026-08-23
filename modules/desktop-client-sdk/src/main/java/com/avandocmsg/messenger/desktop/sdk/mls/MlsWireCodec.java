package com.avandocmsg.messenger.desktop.sdk.mls;

import java.util.Base64;
import java.util.regex.Pattern;

public final class MlsWireCodec {

    private static final Pattern B64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    private MlsWireCodec() {}

    public static boolean looksEncrypted(String content) {
        if (content == null || content.length() < 24) {
            return false;
        }
        var trimmed = content.trim();
        if (!B64.matcher(trimmed).matches()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(trimmed).length >= 28;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
