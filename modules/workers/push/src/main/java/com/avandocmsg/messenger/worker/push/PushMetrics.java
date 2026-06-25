package com.avandocmsg.messenger.worker.push;

import io.prometheus.client.Counter;
import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

final class PushMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("push_worker_build_info")
        .help("Build information for the push worker (labels: version, name).")
        .register();

    private static final Counter WEB_PUSH_SENT = Counter.build()
        .name("push_web_sent_total")
        .help("Successful Web Push deliveries")
        .register();

    private static final Counter WEB_PUSH_FAILED = Counter.build()
        .name("push_web_failed_total")
        .help("Failed Web Push deliveries")
        .register();

    private static final Counter WEB_PUSH_EXPIRED = Counter.build()
        .name("push_web_expired_total")
        .help("Expired Web Push tokens cleared")
        .register();

    private static final Counter WEBHOOK_SKIPS = Counter.build()
        .name("push_webhook_circuit_open_skips_total")
        .help("Webhook posts skipped while circuit breaker is open")
        .register();

    private PushMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "push-worker");
        }
    }

    static void webPushSent(int count) {
        if (count > 0) {
            WEB_PUSH_SENT.inc(count);
        }
    }

    static void webPushFailed(int count) {
        if (count > 0) {
            WEB_PUSH_FAILED.inc(count);
        }
    }

    static void webPushExpired(int count) {
        if (count > 0) {
            WEB_PUSH_EXPIRED.inc(count);
        }
    }

    static void webhookCircuitSkip() {
        WEBHOOK_SKIPS.inc();
    }
}
