package com.avandocmsg.messenger.api.metrics;

import com.avandocmsg.messenger.api.export.ExportJobCancelSupport;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

import java.util.List;

/** Export job enqueue/cancel counters on the default Prometheus registry. */
public final class ExportMetrics {

    private static final String LABEL_SOURCE = "source";
    private static final String SOURCE_ADMIN = "admin";
    private static final String SOURCE_USER = "user";
    private static final String SOURCE_UNKNOWN = "unknown";
    private static final String SOURCE_AUTO = "auto";
    private static final String TRIGGER_ADMIN_API = "admin_api";
    private static final String TRIGGER_RETENTION = "retention_suggested";

    private static final Counter JOBS_ENQUEUED = Counter.build()
        .name("export_jobs_enqueued_total")
        .labelNames(LABEL_SOURCE)
        .help("Export jobs queued (export_jobs insert + msg.export.replay publish)")
        .register();

    private static final Counter JOBS_CANCELLED = Counter.build()
        .name("export_jobs_cancelled_total")
        .labelNames(LABEL_SOURCE, "previous_status")
        .help("Export jobs cancelled via DELETE (status export_cancelled)")
        .register();

    private static final Counter JOBS_CANCEL_REJECTED = Counter.build()
        .name("export_jobs_cancel_rejected_total")
        .labelNames(LABEL_SOURCE, "reason")
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
        for (var source : List.of(SOURCE_USER, SOURCE_ADMIN, SOURCE_AUTO)) {
            JOBS_ENQUEUED.labels(source).inc(0);
        }
        for (var source : List.of(SOURCE_USER, SOURCE_ADMIN)) {
            for (var status : List.of("queued", "processing", "other", SOURCE_UNKNOWN)) {
                JOBS_CANCELLED.labels(source, status).inc(0);
            }
        }
        for (var source : List.of(SOURCE_USER, SOURCE_ADMIN)) {
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
        COMPLETENESS_FAILED.labels(reason == null || reason.isBlank() ? SOURCE_UNKNOWN : reason).inc();
    }

    public static void observeCompletenessDuration(double seconds) {
        COMPLETENESS_DURATION.observe(seconds);
    }

    static String enqueueSource(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return SOURCE_USER;
        }
        return switch (trigger) {
            case TRIGGER_ADMIN_API -> SOURCE_ADMIN;
            case TRIGGER_RETENTION -> SOURCE_AUTO;
            default -> SOURCE_USER;
        };
    }

    static String cancelSource(String auditAction) {
        if (ExportJobCancelSupport.AUDIT_ADMIN_CANCEL.equals(auditAction)) {
            return SOURCE_ADMIN;
        }
        return SOURCE_USER;
    }

    static String sanitizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return SOURCE_UNKNOWN;
        }
        return switch (status) {
            case "queued", "processing" -> status;
            default -> "other";
        };
    }
}
