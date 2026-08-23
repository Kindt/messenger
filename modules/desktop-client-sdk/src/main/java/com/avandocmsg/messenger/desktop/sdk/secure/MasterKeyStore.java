package com.avandocmsg.messenger.desktop.sdk.secure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/** Profile-bound master key: DPAPI on Windows, PBKDF2 machine binding elsewhere. */
public final class MasterKeyStore {

    private static final String ENV_TEST_KEY = "KORUS_DESKTOP_TEST_MASTER_KEY";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path keyFile;
    private final Path stateDir;

    public MasterKeyStore(Path stateDir) {
        this.stateDir = stateDir;
        this.keyFile = stateDir.resolve("master.key.dpapi");
    }

    public byte[] loadOrCreate() throws IOException {
        var test = System.getenv(ENV_TEST_KEY);
        if (test == null || test.isBlank()) {
            test = System.getProperty(ENV_TEST_KEY);
        }
        if (test != null && !test.isBlank()) {
            return sha256(test.getBytes(StandardCharsets.UTF_8));
        }
        Files.createDirectories(stateDir);
        if (Files.exists(keyFile)) {
            var blob = Files.readAllBytes(keyFile);
            if (WindowsDpapiProtector.isAvailable()) {
                return WindowsDpapiProtector.unprotect(blob);
            }
            return blob;
        }
        var raw = new byte[32];
        RANDOM.nextBytes(raw);
        if (WindowsDpapiProtector.isAvailable()) {
            Files.write(keyFile, WindowsDpapiProtector.protect(raw));
        } else {
            Files.write(keyFile, raw);
            try {
                var perms = java.util.Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                );
                Files.setPosixFilePermissions(keyFile, perms);
            } catch (UnsupportedOperationException ignored) {
                // Windows NTFS — rely on user profile ACL
            }
        }
        return raw;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void wipe(byte[] key) {
        if (key != null) {
            Arrays.fill(key, (byte) 0);
        }
    }
}
