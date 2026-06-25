package com.avandocmsg.messenger.common.logging;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** MDC helpers for worker NATS consumers (FR-058 / FR-059). */
public final class WorkerMdcSupport {

    public static final String X_REQUEST_ID = "X_REQUEST_ID";
    public static final String USER_ID = "userId";

    private WorkerMdcSupport() {
    }

    public static void applyCorrelation(String requestId, String userId) {
        if (requestId != null && !requestId.isBlank()) {
            MDC.put(X_REQUEST_ID, requestId);
        } else {
            MDC.put(X_REQUEST_ID, UUID.randomUUID().toString());
        }
        putIfPresent(USER_ID, userId);
    }

    public static void clear() {
        MDC.remove(X_REQUEST_ID);
        MDC.remove(USER_ID);
    }

    public static Map<String, String> correlationMap() {
        var map = new HashMap<String, String>();
        var requestId = MDC.get(X_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            map.put(X_REQUEST_ID, requestId);
        }
        var userId = MDC.get(USER_ID);
        if (userId != null && !userId.isBlank()) {
            map.put(USER_ID, userId);
        }
        return map;
    }

    private static void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
