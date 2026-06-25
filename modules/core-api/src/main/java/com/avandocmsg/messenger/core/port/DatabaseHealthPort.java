package com.avandocmsg.messenger.core.port;

/** Lightweight DB liveness probe for health/readiness endpoints. */
public interface DatabaseHealthPort {

    /** JDBC {@code isValid} — cheap probe for frequent readiness checks (FR-096). */
    boolean lightPing();

    /** {@code SELECT 1} — deeper probe when validating query path. */
    boolean ping();
}
