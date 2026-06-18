package com.avandocmsg.messenger.api.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateOrgIpAllowlistRequest(
    Boolean enabled,
    @JsonProperty("allowed_cidrs") String allowedCidrs
) {}
