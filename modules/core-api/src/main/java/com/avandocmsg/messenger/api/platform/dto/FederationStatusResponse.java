package com.avandocmsg.messenger.api.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Cross-org federation scaffold status (spec 021 Phase 8.3)")
public record FederationStatusResponse(
    @JsonProperty("mode") String mode,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("partner_org_ids") List<String> partnerOrgIds,
    @JsonProperty("note") String note
) {}
