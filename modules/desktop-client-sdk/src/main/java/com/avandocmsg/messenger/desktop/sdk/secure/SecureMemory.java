package com.avandocmsg.messenger.desktop.sdk.secure;

import java.util.Arrays;

public final class SecureMemory {

    private SecureMemory() {}

    public static void wipe(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }

    public static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
