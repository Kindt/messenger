package com.avandocmsg.messenger.api.bots;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class BotTokenHasher {
    private static final SecureRandom RNG = new SecureRandom();

    private BotTokenHasher() {
    }

    public static String generateToken() {
        var bytes = new byte[32];
        RNG.nextBytes(bytes);
        return "kbt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
