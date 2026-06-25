package com.avandocmsg.messenger.worker.exchangebridge;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.io.IOException;

/** HTTP {@code /metrics} and {@code /health} on {@code EXCHANGE_BRIDGE_METRICS_PORT}. */
final class ExchangeBridgeMetricsHttpServer implements AutoCloseable {

    @FunctionalInterface
    interface HealthProbe {
        boolean ready();
    }

    private final WorkerHealthHttpServer delegate;

    private ExchangeBridgeMetricsHttpServer(WorkerHealthHttpServer delegate) {
        this.delegate = delegate;
    }

    static ExchangeBridgeMetricsHttpServer start(int port, HealthProbe probe) throws IOException {
        return start(port, probe, null);
    }

    static ExchangeBridgeMetricsHttpServer start(int port, HealthProbe probe, UserMessageSource messages)
            throws IOException {
        var delegate = WorkerHealthHttpServer.startWithMetrics(
            port,
            "exchange-bridge-metrics-http",
            probe::ready,
            messages,
            ExchangeBridgeMetrics::registerBuildInfoOnce);
        return new ExchangeBridgeMetricsHttpServer(delegate);
    }

    int getPort() {
        return delegate.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
