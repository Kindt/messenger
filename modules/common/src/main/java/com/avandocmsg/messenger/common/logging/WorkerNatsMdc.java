package com.avandocmsg.messenger.common.logging;

import io.nats.client.Message;
import io.nats.client.impl.Headers;

import java.util.List;

/** Bridges NATS message headers to {@link WorkerMdcSupport}. */
public final class WorkerNatsMdc {

    private WorkerNatsMdc() {
    }

    public static void applyFromMessage(Message msg) {
        String requestId = null;
        String userId = null;
        if (msg != null && msg.getHeaders() != null) {
            requestId = firstHeader(msg.getHeaders(), WorkerMdcSupport.X_REQUEST_ID);
            userId = firstHeader(msg.getHeaders(), WorkerMdcSupport.USER_ID);
        }
        WorkerMdcSupport.applyCorrelation(requestId, userId);
    }

    public static Headers toNatsHeaders() {
        var headers = new Headers();
        for (var entry : WorkerMdcSupport.correlationMap().entrySet()) {
            headers.add(entry.getKey(), entry.getValue());
        }
        return headers;
    }

    private static String firstHeader(Headers headers, String key) {
        List<String> values = headers.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
