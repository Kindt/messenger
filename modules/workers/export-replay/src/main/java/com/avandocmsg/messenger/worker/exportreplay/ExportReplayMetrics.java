package com.avandocmsg.messenger.worker.exportreplay;

import io.prometheus.client.Counter;
import io.prometheus.client.Info;

import java.util.concurrent.atomic.AtomicBoolean;

/** Prometheus counters for {@link ExportReplayWorker}; scrape via {@link ExportReplayMetricsHttpServer}. */
final class ExportReplayMetrics {

    private static final AtomicBoolean BUILD_INFO_LABELED = new AtomicBoolean(false);

    private static final Info BUILD_INFO = Info.build()
        .name("export_replay_worker_build_info")
        .help("Build information for the export-replay worker (labels: version, name).")
        .register();

    private static final Counter JOBS_STARTED = Counter.build()
        .name("export_replay_worker_jobs_started_total")
        .help("Export jobs moved to processing (markProcessingIfQueued succeeded)")
        .register();

    private static final Counter JOBS_COMPLETED = Counter.build()
        .name("export_replay_worker_jobs_completed_total")
        .labelNames("terminal_status")
        .help("Export jobs finished with a terminal status written by the worker")
        .register();

    private static final Counter JOBS_CANCELLED = Counter.build()
        .name("export_replay_worker_jobs_cancelled_total")
        .help("Export jobs aborted because export_jobs.status is export_cancelled")
        .register();

    private static final Counter JOBS_SKIPPED = Counter.build()
        .name("export_replay_worker_jobs_skipped_total")
        .labelNames("reason")
        .help("Export replay messages not processed (invalid payload, not queued, etc.)")
        .register();

    private static final Counter CANCEL_HINTS = Counter.build()
        .name("export_replay_worker_cancel_hints_total")
        .help("NATS msg.export.replay.cancel hints received")
        .register();

    private ExportReplayMetrics() {
    }

    static void registerBuildInfoOnce() {
        if (BUILD_INFO_LABELED.compareAndSet(false, true)) {
            BUILD_INFO.info("version", "1", "name", "export-replay-worker");
        }
    }

    static void jobStarted() {
        JOBS_STARTED.inc();
    }

    static void jobCompleted(String terminalStatus) {
        JOBS_COMPLETED.labels(sanitizeTerminalStatus(terminalStatus)).inc();
    }

    static void jobCancelled() {
        JOBS_CANCELLED.inc();
    }

    static void jobSkipped(String reason) {
        JOBS_SKIPPED.labels(reason == null || reason.isBlank() ? "unknown" : reason).inc();
    }

    static void cancelHint() {
        CANCEL_HINTS.inc();
    }

    static String sanitizeTerminalStatus(String status) {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        return switch (status) {
            case "export_v1", "export_failed", "stub_written" -> status;
            default -> "other";
        };
    }
}
