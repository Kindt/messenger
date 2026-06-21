package com.avandocmsg.messenger.api.platform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FederationDirectoryResponse(
    @JsonProperty("home_org_id") String homeOrgId,
    @JsonProperty("partner_orgs") List<FederationDirectoryEntry> partnerOrgs,
    String note
) {
    public record FederationDirectoryEntry(
        @JsonProperty("org_id") String orgId,
        String name,
        String slug
    ) {}
}
