package com.avandocmsg.messenger.common.federation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Documented limits for future cross-org federation delivery outbox (spec 025 FR-176).
 * <p>
 * MVP stores trust in {@code federation_trust} only; there is no {@code federation_outbox} table yet.
 * When a delivery worker is added, reuse these constants for batch prefetch, exponential backoff,
 * and payload size guards (aligned with {@code BotWebhookOutbox} in bot-delivery worker).
 */
public final class FederationDeliveryPolicy {
    /** Rows prefetched per outbox scan (match bot webhook worker default). */
    public static final int OUTBOX_FETCH_BATCH = 20;
    /** Max delivery attempts before marking a federation event failed. */
    public static final int MAX_ATTEMPTS = 5;
    /** Base delay before first retry; doubles per attempt up to {@link #MAX_BACKOFF_EXPONENT}. */
    public static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    /** Cap exponent so backoff does not exceed ~128× base (~64 min with 30s base). */
    public static final int MAX_BACKOFF_EXPONENT = 8;
    /** Reject federation HTTP payloads above this size (UTF-8 bytes). */
    public static final int MAX_PAYLOAD_BYTES = 256 * 1024;

    private FederationDeliveryPolicy() {
    }

    /**
     * Exponential backoff: {@code BASE_BACKOFF * 2^(attempt-1)}, capped at {@link #MAX_BACKOFF_EXPONENT}.
     *
     * @param attemptsAfterFailure attempt count after a failed delivery (1 = first retry)
     */
    public static Duration backoffForAttempt(int attemptsAfterFailure) {
        var exponent = Math.min(Math.max(attemptsAfterFailure, 1), MAX_BACKOFF_EXPONENT);
        return BASE_BACKOFF.multipliedBy(1L << Math.max(0, exponent - 1));
    }

    public static Instant nextRetryAt(Instant now, int attemptsAfterFailure) {
        return Objects.requireNonNull(now).plus(backoffForAttempt(attemptsAfterFailure));
    }

    /** Returns {@code true} when payload is within {@link #MAX_PAYLOAD_BYTES}. */
    public static boolean isPayloadAcceptable(String payloadJson) {
        if (payloadJson == null) {
            return true;
        }
        return payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= MAX_PAYLOAD_BYTES;
    }
}
