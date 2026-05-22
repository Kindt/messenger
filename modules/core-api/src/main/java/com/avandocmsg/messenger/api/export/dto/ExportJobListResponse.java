package com.avandocmsg.messenger.api.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExportJobListResponse(
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("status_filter") String statusFilter,
    @JsonProperty("job_count") int jobCount,
    @JsonProperty("jobs") List<ExportJobListItem> jobs
) {
    public record ExportJobListItem(
        @JsonProperty("job_id") String jobId,
        String status,
        @JsonProperty("output_path") String outputPath,
        @JsonProperty("output_storage") String outputStorage,
        @JsonProperty("output_format") String outputFormat,
        @JsonProperty("requested_by") String requestedBy,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("completed_at") String completedAt
    ) {}
}
