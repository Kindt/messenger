package com.avandocmsg.messenger.core.port;

/** Lightweight DB liveness probe for health/readiness endpoints. */
public interface DatabaseHealthPort {

    boolean ping();
}
