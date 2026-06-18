package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MigrationImportCreateRequest(
    String source,
    @JsonProperty("config_json") String configJson
) {}
