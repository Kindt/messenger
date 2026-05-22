package com.avandocmsg.messenger.api.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Export job status")
public record ExportJobStatusResponse(
    @Schema(description = "Job ID") @JsonProperty("job_id") String jobId,
    @Schema(description = "Chat ID") @JsonProperty("chat_id") String chatId,
    @Schema(description = "Status: queued, processing, export_v1, stub_written, export_failed")
    String status,
    @Schema(description = "Absolute path on export-replay worker (when finished)") @JsonProperty("output_path")
    String outputPath,
    @Schema(description = "Where output lives: minio or filesystem") @JsonProperty("output_storage")
    String outputStorage,
    @Schema(description = "Artifact shape: json (single file) or zip (bundle with attachments)") @JsonProperty("output_format")
    String outputFormat,
    @Schema(description = "Whether per-message TTL filter was applied in export JSON") @JsonProperty("message_ttl_filter_applied")
    Boolean messageTtlFilterApplied,
    @Schema(description = "User who requested export") @JsonProperty("requested_by") String requestedBy,
    @Schema(description = "Created at (ISO-8601)") @JsonProperty("created_at") String createdAt,
    @Schema(description = "Last update (ISO-8601)") @JsonProperty("updated_at") String updatedAt,
    @Schema(description = "Completed at (ISO-8601), if terminal") @JsonProperty("completed_at") String completedAt
) {}
