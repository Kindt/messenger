package com.avandocmsg.messenger.worker.botdelivery;

import com.avandocmsg.messenger.common.http.WorkerHealthHttpServer;
import com.avandocmsg.messenger.common.i18n.UserMessageSource;

import java.io.IOException;

/** HTTP {@code /metrics} and {@code /health} when {@code BOT_DELIVERY_METRICS_PORT} is set. */
final class BotDeliveryMetricsHttpServer implements AutoCloseable {

    @FunctionalInterface
    interface HealthProbe {
        boolean ready();
    }

    private final WorkerHealthHttpServer delegate;

    private BotDeliveryMetricsHttpServer(WorkerHealthHttpServer delegate) {
        this.delegate = delegate;
    }

    static BotDeliveryMetricsHttpServer start(int port, HealthProbe probe) throws IOException {
        return start(port, probe, null);
    }

    static BotDeliveryMetricsHttpServer start(int port, HealthProbe probe, UserMessageSource messages)
            throws IOException {
        var delegate = WorkerHealthHttpServer.startWithMetrics(
            port, "bot-delivery-metrics-http", probe::ready, messages, BotDeliveryMetrics::registerBuildInfoOnce);
        return new BotDeliveryMetricsHttpServer(delegate);
    }

    int getPort() {
        return delegate.getPort();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
