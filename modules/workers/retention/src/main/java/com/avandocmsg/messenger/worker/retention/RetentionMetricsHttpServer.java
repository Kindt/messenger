package com.avandocmsg.messenger.worker.retention;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.io.IOException;

/**
 * Single HTTP server for Prometheus {@code /metrics} and readiness {@code /health}.
 */
final class RetentionMetricsHttpServer implements AutoCloseable {

    private final WorkerHealthHttpServer delegate;

    private RetentionMetricsHttpServer(WorkerHealthHttpServer delegate) {
        this.delegate = delegate;
    }

    static RetentionMetricsHttpServer start(int port, RetentionHealthProbe probe) throws IOException {
        return start(port, probe, null);
    }

    static RetentionMetricsHttpServer start(int port, RetentionHealthProbe probe, UserMessageSource messages)
            throws IOException {
        var delegate = WorkerHealthHttpServer.startWithMetrics(
            port, "retention-metrics-http", probe::ready, messages, RetentionMetrics::registerBuildInfoOnce);
        return new RetentionMetricsHttpServer(delegate);
    }

    int getPort() {
        return delegate.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
