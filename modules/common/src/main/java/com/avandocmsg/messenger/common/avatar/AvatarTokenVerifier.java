package com.avandocmsg.messenger.common.avatar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Validates signed avatar access tokens (`avt`). */
public final class AvatarTokenVerifier {

    public record ParsedToken(UUID viewerId, UUID fileId, int width, int height, long expEpochSeconds) {
    }

    private AvatarTokenVerifier() {
    }

    public static Optional<ParsedToken> verify(String token, String currentSecret, String previousSecret) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        var dot = token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) {
            return Optional.empty();
        }
        var payloadB64 = token.substring(0, dot);
        var sigB64 = token.substring(dot + 1);
        byte[] payloadBytes;
        byte[] sigBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            sigBytes = Base64.getUrlDecoder().decode(sigB64);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        var payload = new String(payloadBytes, StandardCharsets.UTF_8);
        if (!signatureValid(payload, sigBytes, currentSecret, previousSecret)) {
            return Optional.empty();
        }
        return parsePayload(payload);
    }

    private static boolean signatureValid(String payload, byte[] sigBytes, String current, String previous) {
        if (current != null && !current.isBlank()
            && MessageDigest.isEqual(sigBytes, AvatarTokenMint.hmacSha256(current.trim(), payload))) {
            return true;
        }
        if (previous != null && !previous.isBlank()) {
            return MessageDigest.isEqual(sigBytes, AvatarTokenMint.hmacSha256(previous.trim(), payload));
        }
        return false;
    }

    static Optional<ParsedToken> parsePayload(String payload) {
        var parts = payload.split("\\|", -1);
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            var viewerId = UUID.fromString(parts[0]);
            var fileId = UUID.fromString(parts[1]);
            var w = Integer.parseInt(parts[2]);
            var h = Integer.parseInt(parts[3]);
            var exp = Long.parseLong(parts[4]);
            if (w < 1 || h < 1) {
                return Optional.empty();
            }
            if (exp < System.currentTimeMillis() / 1000) {
                return Optional.empty();
            }
            return Optional.of(new ParsedToken(viewerId, fileId, w, h, exp));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
