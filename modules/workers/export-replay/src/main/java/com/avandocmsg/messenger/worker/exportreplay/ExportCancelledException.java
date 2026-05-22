package com.avandocmsg.messenger.worker.exportreplay;

import java.util.UUID;

/** Thrown when {@code export_jobs.status} becomes {@code export_cancelled} during DB export. */
final class ExportCancelledException extends RuntimeException {

    private final UUID jobId;

    ExportCancelledException(UUID jobId) {
        super("export cancelled: " + jobId);
        this.jobId = jobId;
    }

    UUID jobId() {
        return jobId;
    }
}
