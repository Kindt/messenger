package com.avandocmsg.messenger.api.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

@Provider
public class RequestContextMdcClearFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        MDC.remove(RequestContextMdcFilter.MDC_HTTP_METHOD);
        MDC.remove(RequestContextMdcFilter.MDC_HTTP_PATH);
    }
}
