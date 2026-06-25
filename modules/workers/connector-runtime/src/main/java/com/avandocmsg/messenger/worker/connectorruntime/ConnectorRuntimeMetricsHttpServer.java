package com.avandocmsg.messenger.worker.connectorruntime;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.io.IOException;

/** HTTP {@code /metrics} and {@code /health} on {@code CONNECTOR_RUNTIME_METRICS_PORT}. */
final class ConnectorRuntimeMetricsHttpServer implements AutoCloseable {

    @FunctionalInterface
    interface HealthProbe {
        boolean ready();
    }

    private final WorkerHealthHttpServer delegate;

    private ConnectorRuntimeMetricsHttpServer(WorkerHealthHttpServer delegate) {
        this.delegate = delegate;
    }

    static ConnectorRuntimeMetricsHttpServer start(int port, HealthProbe probe) throws IOException {
        return start(port, probe, null);
    }

    static ConnectorRuntimeMetricsHttpServer start(int port, HealthProbe probe, UserMessageSource messages)
            throws IOException {
        var delegate = WorkerHealthHttpServer.startWithMetrics(
            port,
            "connector-runtime-metrics-http",
            probe::ready,
            messages,
            ConnectorRuntimeMetrics::registerBuildInfoOnce);
        return new ConnectorRuntimeMetricsHttpServer(delegate);
    }

    int getPort() {
        return delegate.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
