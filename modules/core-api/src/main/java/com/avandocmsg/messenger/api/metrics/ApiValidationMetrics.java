package com.avandocmsg.messenger.api.metrics;

import io.prometheus.client.Counter;

/** Counters for client validation failures (4xx) other than policy denials. */
public final class ApiValidationMetrics {

    private static final Counter INVALID_UUID_PARAMETER = Counter.build()
        .name("api_invalid_uuid_parameter_total")
        .help("Bad requests from invalid or missing UUID path/query/body fields (400)")
        .register();

    private ApiValidationMetrics() {
    }

    public static void invalidUuidParameter() {
        INVALID_UUID_PARAMETER.inc();
    }
}
