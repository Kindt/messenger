package com.avandocmsg.messenger.desktop.sdk.secure;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.Crypt32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinCrypt;

/** Windows DPAPI CryptProtectData / CryptUnprotectData wrapper. */
public final class WindowsDpapiProtector {

    private WindowsDpapiProtector() {}

    public static boolean isAvailable() {
        var os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    public static byte[] protect(byte[] plain) {
        if (!isAvailable()) {
            throw new IllegalStateException("DPAPI only on Windows");
        }
        var in = new WinCrypt.DATA_BLOB();
        var out = new WinCrypt.DATA_BLOB();
        var mem = new Memory(plain.length);
        mem.write(0, plain, 0, plain.length);
        in.cbData = plain.length;
        in.pbData = mem;
        if (!Crypt32.INSTANCE.CryptProtectData(in, null, null, null, null, 0, out)) {
            throw new IllegalStateException("CryptProtectData failed: " + Kernel32.INSTANCE.GetLastError());
        }
        try {
            return out.pbData.getByteArray(0, out.cbData);
        } finally {
            if (out.pbData != null) {
                Kernel32.INSTANCE.LocalFree(out.pbData);
            }
        }
    }

    public static byte[] unprotect(byte[] cipher) {
        if (!isAvailable()) {
            throw new IllegalStateException("DPAPI only on Windows");
        }
        var in = new WinCrypt.DATA_BLOB();
        var out = new WinCrypt.DATA_BLOB();
        var mem = new Memory(cipher.length);
        mem.write(0, cipher, 0, cipher.length);
        in.cbData = cipher.length;
        in.pbData = mem;
        if (!Crypt32.INSTANCE.CryptUnprotectData(in, null, null, null, null, 0, out)) {
            throw new IllegalStateException("CryptUnprotectData failed: " + Kernel32.INSTANCE.GetLastError());
        }
        try {
            return out.pbData.getByteArray(0, out.cbData);
        } finally {
            if (out.pbData != null) {
                Kernel32.INSTANCE.LocalFree(out.pbData);
            }
        }
    }
}
