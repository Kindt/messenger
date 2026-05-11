package com.avandocmsg.messenger.api.config;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

/** Shared CORS response headers (methods aligned with фактическим REST API). */
public final class CorsHeaders {

    static final String ALLOW_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    static final String ALLOW_HEADERS = "Content-Type, Authorization, X-Requested-With";

    private CorsHeaders() {
    }

    public static void apply(Response.ResponseBuilder response, AppConfig appConfig, String requestOriginHeader) {
        var origin = CorsOriginPolicy.resolveAllowOrigin(appConfig.corsAllowedOrigins(), requestOriginHeader);
        if (origin == null) {
            return;
        }
        response.header("Access-Control-Allow-Origin", origin);
        response.header("Access-Control-Allow-Methods", ALLOW_METHODS);
        response.header("Access-Control-Allow-Headers", ALLOW_HEADERS);
        response.header("Access-Control-Max-Age", "3600");
    }

    public static void apply(MultivaluedMap<String, Object> headers, AppConfig appConfig, String requestOriginHeader) {
        var origin = CorsOriginPolicy.resolveAllowOrigin(appConfig.corsAllowedOrigins(), requestOriginHeader);
        if (origin == null) {
            return;
        }
        headers.putSingle("Access-Control-Allow-Origin", origin);
        headers.putSingle("Access-Control-Allow-Methods", ALLOW_METHODS);
        headers.putSingle("Access-Control-Allow-Headers", ALLOW_HEADERS);
        headers.putSingle("Access-Control-Max-Age", "3600");
    }
}
