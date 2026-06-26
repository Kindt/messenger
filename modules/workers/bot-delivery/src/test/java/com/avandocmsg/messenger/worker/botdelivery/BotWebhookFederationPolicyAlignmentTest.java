package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.federation.FederationDeliveryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** FR-176: bot webhook outbox aligns with federation delivery policy constants. */
class BotWebhookFederationPolicyAlignmentTest {

    @Test
    void outboxConstants_matchFederationDeliveryPolicy() {
        assertEquals(FederationDeliveryPolicy.MAX_ATTEMPTS, BotWebhookOutbox.MAX_ATTEMPTS);
        assertEquals(FederationDeliveryPolicy.BASE_BACKOFF, BotWebhookOutbox.BASE_BACKOFF);
        assertEquals(
            FederationDeliveryPolicy.backoffForAttempt(3),
            BotWebhookOutbox.backoffForAttempt(3));
    }
}
