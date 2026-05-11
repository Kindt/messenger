package com.avandocmsg.messenger.worker.retention;

/**
 * Lightweight readiness for {@code GET /health} on the metrics HTTP server (no secrets in response).
 */
@FunctionalInterface
interface RetentionHealthProbe {

    /** @return {@code true} if dependencies for an enabled worker are usable */
    boolean ready();
}
