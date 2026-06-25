package com.avandocmsg.messenger.worker.push;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.io.IOException;

/** Readiness HTTP endpoint {@code GET /health} for orchestrator probes. */
final class PushHealthHttpServer implements AutoCloseable {

    private final WorkerHealthHttpServer delegate;

    private PushHealthHttpServer(WorkerHealthHttpServer delegate) {
        this.delegate = delegate;
    }

    static PushHealthHttpServer start(int port, PushReadinessCheck probe) throws IOException {
        return start(port, probe, null);
    }

    static PushHealthHttpServer start(int port, PushReadinessCheck probe, UserMessageSource messages)
            throws IOException {
        var delegate = WorkerHealthHttpServer.startWithMetrics(
            port, "push-metrics-http", probe::ready, messages, PushMetrics::registerBuildInfoOnce);
        return new PushHealthHttpServer(delegate);
    }

    int getPort() {
        return delegate.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
