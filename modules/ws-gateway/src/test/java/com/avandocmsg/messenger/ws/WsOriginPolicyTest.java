package com.avandocmsg.messenger.ws;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsOriginPolicyTest {

    @Test
    void wildcardAllowsAnyOrigin() {
        assertTrue(WsOriginPolicy.isAllowed("http://localhost:9088", List.of("*")));
    }

    @Test
    void restrictedListMatchesCaseInsensitive() {
        var allowed = WsOriginPolicy.parseAllowedOrigins("http://localhost:9088,https://app.example.com");
        assertTrue(WsOriginPolicy.isAllowed("http://localhost:9088", allowed));
        assertFalse(WsOriginPolicy.isAllowed("http://evil.example.com", allowed));
    }
}
