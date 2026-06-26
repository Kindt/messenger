package com.avandocmsg.messenger.common.avatar;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** HMAC-SHA256 avatar access token minting (spec 068). */
public final class AvatarTokenMint {

    private AvatarTokenMint() {
    }

    public static String payload(UUID viewerId, UUID fileId, int w, int h, long expEpochSeconds) {
        return viewerId + "|" + fileId + "|" + w + "|" + h + "|" + expEpochSeconds;
    }

    public static String mint(String secret, UUID viewerId, UUID fileId, int w, int h, long expEpochSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("avatar token secret required");
        }
        var payload = payload(viewerId, fileId, w, h, expEpochSeconds);
        var payloadB64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        var sig = hmacSha256(secret.trim(), payload);
        var sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        return payloadB64 + "." + sigB64;
    }

    static byte[] hmacSha256(String secret, String payloadUtf8) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payloadUtf8.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("avatar token HMAC failed", e);
        }
    }
}
