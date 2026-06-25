package com.avandocmsg.messenger.worker.preview;

import io.prometheus.client.Counter;
import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

final class PreviewMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("preview_worker_build_info")
        .help("Build information for the preview worker (labels: version, name).")
        .register();

    private static final Counter FETCH_TOTAL = Counter.build()
        .name("preview_fetch_total")
        .help("Link preview HTTP fetch attempts")
        .register();

    private static final Counter FETCH_FAILURES = Counter.build()
        .name("preview_fetch_failures_total")
        .help("Link preview HTTP fetch failures")
        .register();

    private static final Counter CIRCUIT_SKIPS = Counter.build()
        .name("preview_circuit_open_skips_total")
        .help("Preview fetches skipped while circuit breaker is open")
        .register();

    private PreviewMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "preview-worker");
        }
    }

    static void fetchAttempt() {
        FETCH_TOTAL.inc();
    }

    static void fetchFailure() {
        FETCH_FAILURES.inc();
    }

    static void circuitOpenSkip() {
        CIRCUIT_SKIPS.inc();
    }
}
