package com.avandocmsg.messenger.api.admin.dto;

import com.avandocmsg.messenger.core.port.FederationTrustPort;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record FederationTrustResponse(
    @JsonProperty("id") UUID id,
    @JsonProperty("org_id") UUID orgId,
    @JsonProperty("partner_org_id") UUID partnerOrgId,
    @JsonProperty("status") String status,
    @JsonProperty("expires_at") Instant expiresAt
) {
    public static FederationTrustResponse from(FederationTrustPort.TrustRow row) {
        return new FederationTrustResponse(row.id(), row.orgId(), row.partnerOrgId(), row.status(), row.expiresAt());
    }
}
