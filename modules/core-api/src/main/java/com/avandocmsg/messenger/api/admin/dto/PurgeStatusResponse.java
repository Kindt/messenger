package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Hot-row purge observability (audit-derived)")
public record PurgeStatusResponse(
    @JsonProperty("total_purged") long totalPurged,
    @JsonProperty("last_pass_at") Instant lastPassAt,
    @JsonProperty("errors_count") long errorsCount,
    @JsonProperty("pending_count") long pendingCount
) {
}
