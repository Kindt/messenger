package com.avandocmsg.messenger.api.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Puts minimal request context into {@link MDC} for log correlation (structured prefix in {@code logback.xml}).
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 1000)
public class RequestContextMdcFilter implements ContainerRequestFilter {

    public static final String MDC_HTTP_METHOD = "http.method";
    public static final String MDC_HTTP_PATH = "http.path";
    public static final String MDC_REQUEST_ID = "X_REQUEST_ID";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        MDC.put(MDC_HTTP_METHOD, requestContext.getMethod());
        MDC.put(MDC_HTTP_PATH, requestContext.getUriInfo().getPath());
        var requestId = requestContext.getHeaderString("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_REQUEST_ID, requestId);
    }
}
