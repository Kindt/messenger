package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "PATCH legal-hold flags")
public record LegalHoldUpdateRequest(
    @JsonProperty("legal_hold") Boolean legalHold,
    @JsonProperty("legal_hold_files") Boolean legalHoldFiles,
    @JsonProperty("legal_hold_deep_archive") Boolean legalHoldDeepArchive
) {
}
