package com.avandocmsg.messenger.worker.botdelivery;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** HMAC-SHA256 for outbound bot webhook POSTs (spec 014 S2-3). */
final class BotWebhookSigner {

    static final String SIGNATURE_HEADER = "X-Korus-Webhook-Signature";

    private BotWebhookSigner() {
    }

    static String signSha256Hex(String secret, String payloadUtf8) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var digest = mac.doFinal(payloadUtf8.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("webhook HMAC failed", e);
        }
    }
}
