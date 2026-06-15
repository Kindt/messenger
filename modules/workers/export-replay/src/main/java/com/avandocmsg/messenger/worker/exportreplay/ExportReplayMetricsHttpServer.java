package com.avandocmsg.messenger.worker.exportreplay;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.io.IOException;

/** HTTP {@code /metrics} and {@code /health} when {@code EXPORT_REPLAY_METRICS_PORT} is set. */
final class ExportReplayMetricsHttpServer implements AutoCloseable {

    @FunctionalInterface
    interface HealthProbe {
        boolean ready();
    }

    private final WorkerHealthHttpServer delegate;

    private ExportReplayMetricsHttpServer(WorkerHealthHttpServer delegate) {
        this.delegate = delegate;
    }

    static ExportReplayMetricsHttpServer start(int port, HealthProbe probe) throws IOException {
        return start(port, probe, null);
    }

    static ExportReplayMetricsHttpServer start(int port, HealthProbe probe, UserMessageSource messages)
            throws IOException {
        var delegate = WorkerHealthHttpServer.startWithMetrics(
            port, "export-replay-metrics-http", probe::ready, messages, ExportReplayMetrics::registerBuildInfoOnce);
        return new ExportReplayMetricsHttpServer(delegate);
    }

    int getPort() {
        return delegate.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
