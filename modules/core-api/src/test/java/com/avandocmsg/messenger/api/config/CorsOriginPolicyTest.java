package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorsOriginPolicyTest {

    @Test
    void wildcard() {
        assertEquals("*", CorsOriginPolicy.resolveAllowOrigin(List.of("*"), null));
        assertEquals("*", CorsOriginPolicy.resolveAllowOrigin(List.of("*"), "https://evil.example"));
    }

    @Test
    void strictEchoesMatchingOrigin() {
        var allowed = List.of("http://localhost:3000", "https://app.example");
        assertEquals("http://localhost:3000",
            CorsOriginPolicy.resolveAllowOrigin(allowed, "http://localhost:3000"));
        assertEquals("https://app.example",
            CorsOriginPolicy.resolveAllowOrigin(allowed, "https://app.example"));
    }

    @Test
    void strictRejectsUnknownOrigin() {
        assertNull(CorsOriginPolicy.resolveAllowOrigin(List.of("http://localhost:3000"), "https://other"));
        assertNull(CorsOriginPolicy.resolveAllowOrigin(List.of("http://localhost:3000"), null));
        assertNull(CorsOriginPolicy.resolveAllowOrigin(List.of("http://localhost:3000"), ""));
    }

    @Test
    void parseTrimsAndSkipsEmpty() {
        var list = CorsOriginPolicy.parseOriginsList(" http://a , ,http://b ");
        assertEquals(List.of("http://a", "http://b"), list);
    }
}
