package com.avandocmsg.messenger.api.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record FederationTrustCreateRequest(
    @JsonProperty("partner_org_id") UUID partnerOrgId,
    @JsonProperty("status") String status,
    @JsonProperty("expires_at") Instant expiresAt
) {}
