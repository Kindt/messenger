package com.avandocmsg.messenger.api.config;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityHeadersFilterTest {

    @Test
    void addsSecurityHeadersWhenEnabled() {
        var filter = new SecurityHeadersFilter(true, null);
        var response = mock(ContainerResponseContext.class);
        var headers = new MultivaluedHashMap<String, Object>();
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(null, response);

        assertEquals("max-age=31536000; includeSubDomains", headers.getFirst("Strict-Transport-Security"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("no-referrer", headers.getFirst("Referrer-Policy"));
        assertNull(headers.getFirst("Content-Security-Policy"));
    }

    @Test
    void addsCspWhenConfigured() {
        var filter = new SecurityHeadersFilter(true, "default-src 'self'");
        var response = mock(ContainerResponseContext.class);
        var headers = new MultivaluedHashMap<String, Object>();
        when(response.getHeaders()).thenReturn(headers);

        filter.filter(null, response);

        assertEquals("default-src 'self'", headers.getFirst("Content-Security-Policy"));
    }
}
