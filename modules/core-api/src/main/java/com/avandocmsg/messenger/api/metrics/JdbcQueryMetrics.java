package com.avandocmsg.messenger.api.metrics;

import io.prometheus.client.Counter;

/** JDBC guardrail metrics (PS-2.4). */
public final class JdbcQueryMetrics {

    private static final Counter TIMEOUTS = Counter.build()
        .name("jdbc_query_timeout_total")
        .help("SQL queries terminated by JDBC query timeout")
        .register();

    private JdbcQueryMetrics() {
    }

    public static void queryTimeout() {
        TIMEOUTS.inc();
    }
}
