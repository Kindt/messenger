package com.avandocmsg.messenger.worker.archiver;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.io.IOException;

/** HTTP {@code /metrics} and {@code /health} when {@code ARCHIVER_METRICS_PORT} is set. */
final class ArchiverMetricsHttpServer implements AutoCloseable {

    @FunctionalInterface
    interface HealthProbe {
        boolean ready();
    }

    private final WorkerHealthHttpServer delegate;

    private ArchiverMetricsHttpServer(WorkerHealthHttpServer delegate) {
        this.delegate = delegate;
    }

    static ArchiverMetricsHttpServer start(int port, HealthProbe probe) throws IOException {
        return start(port, probe, null);
    }

    static ArchiverMetricsHttpServer start(int port, HealthProbe probe, UserMessageSource messages)
            throws IOException {
        var delegate = WorkerHealthHttpServer.startWithMetrics(
            port, "archiver-metrics-http", probe::ready, messages, ArchiverMetrics::registerBuildInfoOnce);
        return new ArchiverMetricsHttpServer(delegate);
    }

    int getPort() {
        return delegate.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
