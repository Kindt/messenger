package com.avandocmsg.messenger.worker.push;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushErrorsTest {

    @Test
    void isExpiredSubscription_detects410InMessage() {
        var ex = new RuntimeException("Push failed with status=410 Gone");
        assertTrue(WebPushErrors.isExpiredSubscription(ex));
    }

    @Test
    void isExpiredSubscription_ignoresOtherCodes() {
        var ex = new RuntimeException("Push failed with status=404");
        assertFalse(WebPushErrors.isExpiredSubscription(ex));
    }
}
