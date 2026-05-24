package com.avandocmsg.messenger.api.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Adds standard security headers to API responses when enabled. */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    private final boolean enabled;
    private final String cspPolicy;

    public SecurityHeadersFilter(AppConfig config) {
        this.enabled = config.securityHeadersEnabled();
        this.cspPolicy = config.cspPolicy();
    }

    SecurityHeadersFilter(boolean enabled, String cspPolicy) {
        this.enabled = enabled;
        this.cspPolicy = cspPolicy;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (!enabled) {
            return;
        }
        var headers = response.getHeaders();
        headers.putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        headers.putSingle("X-Content-Type-Options", "nosniff");
        headers.putSingle("X-Frame-Options", "DENY");
        headers.putSingle("Referrer-Policy", "no-referrer");
        if (cspPolicy != null && !cspPolicy.isBlank()) {
            headers.putSingle("Content-Security-Policy", cspPolicy);
        }
    }
}
