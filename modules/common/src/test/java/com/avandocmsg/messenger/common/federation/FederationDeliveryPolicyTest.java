package com.avandocmsg.messenger.common.federation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationDeliveryPolicyTest {

    @Test
    void backoff_doublesUntilCap() {
        assertEquals(FederationDeliveryPolicy.BASE_BACKOFF, FederationDeliveryPolicy.backoffForAttempt(1));
        assertEquals(FederationDeliveryPolicy.BASE_BACKOFF.multipliedBy(2),
            FederationDeliveryPolicy.backoffForAttempt(2));
        assertEquals(FederationDeliveryPolicy.BASE_BACKOFF.multipliedBy(128),
            FederationDeliveryPolicy.backoffForAttempt(99));
    }

    @Test
    void nextRetryAt_addsBackoff() {
        var now = Instant.parse("2026-06-25T12:00:00Z");
        var next = FederationDeliveryPolicy.nextRetryAt(now, 1);
        assertEquals(now.plus(FederationDeliveryPolicy.BASE_BACKOFF), next);
    }

    @Test
    void payloadLimit_rejectsOversizedJson() {
        var ok = "a".repeat(FederationDeliveryPolicy.MAX_PAYLOAD_BYTES);
        assertTrue(FederationDeliveryPolicy.isPayloadAcceptable(ok));
        assertFalse(FederationDeliveryPolicy.isPayloadAcceptable(ok + "x"));
        assertTrue(FederationDeliveryPolicy.isPayloadAcceptable(null));
    }
}
