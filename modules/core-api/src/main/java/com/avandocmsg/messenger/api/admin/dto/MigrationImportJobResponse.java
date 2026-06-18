package com.avandocmsg.messenger.api.admin.dto;

import com.avandocmsg.messenger.api.repository.MigrationImportJobRepository;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MigrationImportJobResponse(
    String id,
    @JsonProperty("org_id") String orgId,
    String source,
    String status,
    @JsonProperty("config_json") String configJson,
    @JsonProperty("result_json") String resultJson
) {
    public static MigrationImportJobResponse from(MigrationImportJobRepository.JobRow row) {
        return new MigrationImportJobResponse(
            row.id().toString(),
            row.orgId().toString(),
            row.source(),
            row.status(),
            row.configJson(),
            row.resultJson());
    }
}
