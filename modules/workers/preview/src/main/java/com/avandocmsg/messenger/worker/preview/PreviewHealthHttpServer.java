package com.avandocmsg.messenger.worker.preview;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.io.IOException;

/** Readiness HTTP endpoint {@code GET /health} for orchestrator probes. */
final class PreviewHealthHttpServer implements AutoCloseable {

    private final WorkerHealthHttpServer delegate;

    private PreviewHealthHttpServer(WorkerHealthHttpServer delegate) {
        this.delegate = delegate;
    }

    static PreviewHealthHttpServer start(int port, PreviewReadinessCheck probe) throws IOException {
        return start(port, probe, null);
    }

    static PreviewHealthHttpServer start(int port, PreviewReadinessCheck probe, UserMessageSource messages)
            throws IOException {
        var delegate = WorkerHealthHttpServer.startWithMetrics(
            port, "preview-metrics-http", probe::ready, messages, PreviewMetrics::registerBuildInfoOnce);
        return new PreviewHealthHttpServer(delegate);
    }

    int getPort() {
        return delegate.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
