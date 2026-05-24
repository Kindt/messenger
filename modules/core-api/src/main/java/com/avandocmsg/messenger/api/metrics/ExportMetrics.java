package com.avandocmsg.messenger.api.metrics;

import com.avandocmsg.messenger.api.export.ExportJobCancelSupport;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

import java.util.List;

/** Export job enqueue/cancel counters on the default Prometheus registry. */
public final class ExportMetrics {

    private static final Counter JOBS_ENQUEUED = Counter.build()
        .name("export_jobs_enqueued_total")
        .labelNames("source")
        .help("Export jobs queued (export_jobs insert + msg.export.replay publish)")
        .register();

    private static final Counter JOBS_CANCELLED = Counter.build()
        .name("export_jobs_cancelled_total")
        .labelNames("source", "previous_status")
        .help("Export jobs cancelled via DELETE (status export_cancelled)")
        .register();

    private static final Counter JOBS_CANCEL_REJECTED = Counter.build()
        .name("export_jobs_cancel_rejected_total")
        .labelNames("source", "reason")
        .help("Export cancel rejected (409 — not cancellable or lost race)")
        .register();

    private static final Counter COMPLETENESS_CHECKS = Counter.build()
        .name("export_completeness_check_total")
        .help("Export completeness validations (API-side registration mirror)")
        .register();

    private static final Counter COMPLETENESS_FAILED = Counter.build()
        .name("export_completeness_failed_total")
        .labelNames("reason")
        .help("Export completeness validation failures")
        .register();

    private static final Histogram COMPLETENESS_DURATION = Histogram.build()
        .name("export_completeness_duration_seconds")
        .help("Export completeness validation duration")
        .register();

    static {
        for (var source : List.of("user", "admin", "auto")) {
            JOBS_ENQUEUED.labels(source).inc(0);
        }
        for (var source : List.of("user", "admin")) {
            for (var status : List.of("queued", "processing", "other", "unknown")) {
                JOBS_CANCELLED.labels(source, status).inc(0);
            }
        }
        for (var source : List.of("user", "admin")) {
            for (var reason : List.of("not_cancellable", "db_race")) {
                JOBS_CANCEL_REJECTED.labels(source, reason).inc(0);
            }
        }
    }

    private ExportMetrics() {
    }

    /** Idempotent: ensures labeled counter families exist before /metrics scrape. */
    public static void ensureRegistered() {
        // Triggers static initializer (inc(0) for all label combinations).
    }

    public static void jobEnqueued(String trigger) {
        JOBS_ENQUEUED.labels(enqueueSource(trigger)).inc();
    }

    public static void jobCancelled(String auditAction, String previousStatus) {
        JOBS_CANCELLED.labels(cancelSource(auditAction), sanitizeStatus(previousStatus)).inc();
    }

    public static void jobCancelRejected(String auditAction, String reason) {
        JOBS_CANCEL_REJECTED.labels(cancelSource(auditAction), reason).inc();
    }

    public static void completenessChecked() {
        COMPLETENESS_CHECKS.inc();
    }

    public static void completenessFailed(String reason) {
        COMPLETENESS_FAILED.labels(reason == null || reason.isBlank() ? "unknown" : reason).inc();
    }

    public static void observeCompletenessDuration(double seconds) {
        COMPLETENESS_DURATION.observe(seconds);
    }

    static String enqueueSource(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return "user";
        }
        return switch (trigger) {
            case "admin_api" -> "admin";
            case "retention_suggested" -> "auto";
            default -> "user";
        };
    }

    static String cancelSource(String auditAction) {
        if (ExportJobCancelSupport.AUDIT_ADMIN_CANCEL.equals(auditAction)) {
            return "admin";
        }
        return "user";
    }

    static String sanitizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        return switch (status) {
            case "queued", "processing" -> status;
            default -> "other";
        };
    }
}
