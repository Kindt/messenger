package com.avandocmsg.messenger.api.admin.ui.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminServerStatsResponse(
    @JsonProperty("api_version") String apiVersion,
    @JsonProperty("jvm") JvmStats jvm,
    @JsonProperty("dependencies") DependencyHealth dependencies,
    @JsonProperty("counts") TableCounts counts,
    @JsonProperty("export_compliance") ExportCompliance exportCompliance
) {
    public record JvmStats(
        @JsonProperty("heap_used_bytes") long heapUsedBytes,
        @JsonProperty("heap_committed_bytes") long heapCommittedBytes,
        @JsonProperty("heap_max_bytes") long heapMaxBytes,
        @JsonProperty("processors") int processors,
        @JsonProperty("uptime_ms") long uptimeMs
    ) {}

    public record DependencyHealth(
        @JsonProperty("database_ok") boolean databaseOk,
        @JsonProperty("redis_ok") boolean redisOk,
        @JsonProperty("nats_ok") boolean natsOk
    ) {}

    public record TableCounts(
        @JsonProperty("users") long users,
        @JsonProperty("chats") long chats,
        @JsonProperty("messages") long messages,
        @JsonProperty("counts_available") boolean countsAvailable
    ) {}

    public record ExportCompliance(
        @JsonProperty("available") boolean available,
        @JsonProperty("jobs_total") long jobsTotal,
        @JsonProperty("jobs_queued") long jobsQueued,
        @JsonProperty("jobs_processing") long jobsProcessing,
        @JsonProperty("jobs_processing_stale") long jobsProcessingStale,
        @JsonProperty("processing_stale_minutes") int processingStaleMinutes,
        @JsonProperty("jobs_completed") long jobsCompleted,
        @JsonProperty("jobs_failed") long jobsFailed,
        @JsonProperty("jobs_cancelled") long jobsCancelled,
        @JsonProperty("audit_export_events_7d") long auditExportEvents7d,
        @JsonProperty("audit_export_cancelled_7d") long auditExportCancelled7d
    ) {
        public static ExportCompliance unavailable() {
            return new ExportCompliance(false, 0, 0, 0, 0, 30, 0, 0, 0, 0, 0);
        }
    }
}
