package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Extended legal-hold flags (V025)")
public record LegalHoldResponse(
    @JsonProperty("legal_hold") boolean legalHold,
    @JsonProperty("legal_hold_files") boolean legalHoldFiles,
    @JsonProperty("legal_hold_deep_archive") boolean legalHoldDeepArchive
) {
}
