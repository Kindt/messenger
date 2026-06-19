package com.avandocmsg.messenger.api.admin.dto;

import com.avandocmsg.messenger.core.port.MigrationImportJobPort;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MigrationImportJobResponse(
    String id,
    @JsonProperty("org_id") String orgId,
    String source,
    String status,
    @JsonProperty("config_json") String configJson,
    @JsonProperty("result_json") String resultJson
) {
    public static MigrationImportJobResponse from(MigrationImportJobPort.JobRow row) {
        return new MigrationImportJobResponse(
            row.id().toString(),
            row.orgId().toString(),
            row.source(),
            row.status(),
            row.configJson(),
            row.resultJson());
    }
}
