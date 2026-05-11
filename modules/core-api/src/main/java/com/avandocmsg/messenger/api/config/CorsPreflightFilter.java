package com.avandocmsg.messenger.api.config;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Handles browser CORS preflight ({@code OPTIONS}) before auth and resource matching.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 500)
public class CorsPreflightFilter implements ContainerRequestFilter {

    private final AppConfig appConfig;

    @Inject
    public CorsPreflightFilter(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!"OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }
        var rb = Response.noContent();
        CorsHeaders.apply(rb, appConfig, requestContext.getHeaderString("Origin"));
        requestContext.abortWith(rb.build());
    }
}
