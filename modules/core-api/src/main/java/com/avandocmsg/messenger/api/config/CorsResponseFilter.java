package com.avandocmsg.messenger.api.config;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CorsResponseFilter implements ContainerResponseFilter {

    private final AppConfig appConfig;

    @Inject
    public CorsResponseFilter(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        CorsHeaders.apply(responseContext.getHeaders(), appConfig, requestContext.getHeaderString("Origin"));
    }
}
