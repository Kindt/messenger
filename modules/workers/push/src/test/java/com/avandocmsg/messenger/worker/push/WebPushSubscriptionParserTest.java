package com.avandocmsg.messenger.worker.push;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushSubscriptionParserTest {

    @Test
    void parse_validSubscriptionJson() {
        var json = """
            {"endpoint":"https://push.example/send/abc","keys":{"p256dh":"BKx","auth":"auth1"}}
            """;
        assertTrue(WebPushSubscriptionParser.parse(json).isPresent());
    }

    @Test
    void parse_rejectsEndpointOnly() {
        assertFalse(WebPushSubscriptionParser.parse("https://push.example/send/abc").isPresent());
    }

    @Test
    void parse_rejectsBlank() {
        assertFalse(WebPushSubscriptionParser.parse("  ").isPresent());
    }
}
