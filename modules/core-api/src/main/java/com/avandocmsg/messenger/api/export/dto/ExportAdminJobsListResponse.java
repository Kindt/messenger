package com.avandocmsg.messenger.api.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Admin global list of export jobs across chats. */
public record ExportAdminJobsListResponse(
    @JsonProperty("status_filter") String statusFilter,
    @JsonProperty("chat_id_filter") String chatIdFilter,
    @JsonProperty("job_count") int jobCount,
    @JsonProperty("jobs") List<ExportAdminJobListItem> jobs
) {
    public record ExportAdminJobListItem(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("chat_id") String chatId,
        String status,
        @JsonProperty("output_path") String outputPath,
        @JsonProperty("output_storage") String outputStorage,
        @JsonProperty("output_format") String outputFormat,
        @JsonProperty("requested_by") String requestedBy,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("completed_at") String completedAt
    ) {}
}
