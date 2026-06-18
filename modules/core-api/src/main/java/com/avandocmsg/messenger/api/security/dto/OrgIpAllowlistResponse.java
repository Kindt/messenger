package com.avandocmsg.messenger.api.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrgIpAllowlistResponse(
    @JsonProperty("org_id") String orgId,
    boolean enabled,
    @JsonProperty("allowed_cidrs") String allowedCidrs
) {}
