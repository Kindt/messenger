package com.avandocmsg.messenger.api.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

/**
 * Puts minimal request context into {@link MDC} for log correlation (structured prefix in {@code logback.xml}).
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 1000)
public class RequestContextMdcFilter implements ContainerRequestFilter {

    public static final String MDC_HTTP_METHOD = "http.method";
    public static final String MDC_HTTP_PATH = "http.path";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        MDC.put(MDC_HTTP_METHOD, requestContext.getMethod());
        MDC.put(MDC_HTTP_PATH, requestContext.getUriInfo().getPath());
    }
}
