package com.avandocmsg.messenger.api.directory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record DirectorySyncStatusResponse(
    @JsonProperty("org_id") String orgId,
    @JsonProperty("run_id") String runId,
    @JsonProperty("status") String status,
    @JsonProperty("users_upserted") int usersUpserted,
    @JsonProperty("error") String error,
    @JsonProperty("started_at") Instant startedAt,
    @JsonProperty("finished_at") Instant finishedAt
) {
    static DirectorySyncStatusResponse from(UUID orgId, DirectorySyncRunRow row) {
        return new DirectorySyncStatusResponse(
            orgId.toString(),
            row.id().toString(),
            row.status(),
            row.usersUpserted(),
            row.error(),
            row.startedAt(),
            row.finishedAt());
    }

    static DirectorySyncStatusResponse empty(UUID orgId) {
        return new DirectorySyncStatusResponse(orgId.toString(), null, "never", 0, null, null, null);
    }
}
