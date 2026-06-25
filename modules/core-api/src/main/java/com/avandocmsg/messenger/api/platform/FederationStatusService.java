package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.platform.dto.FederationStatusResponse;
import com.avandocmsg.messenger.core.port.FederationTrustPort;
import com.avandocmsg.messenger.core.port.OrganizationLookupPort;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class FederationStatusService {
    private final FederationTrustPort federationTrustPort;
    private final OrganizationLookupPort organizationLookupPort;

    public FederationStatusService(
        FederationTrustPort federationTrustPort,
        OrganizationLookupPort organizationLookupPort
    ) {
        this.federationTrustPort = federationTrustPort;
        this.organizationLookupPort = organizationLookupPort;
    }

    public FederationStatusResponse globalStatus() {
        if (federationTrustPort != null && federationTrustPort.anyActiveTrust()) {
            return new FederationStatusResponse(
                "mvp",
                true,
                List.of(),
                "Federation trusts configured; use org-scoped status for partner list.");
        }
        return scaffold();
    }

    public FederationStatusResponse statusForOrg(UUID orgId) {
        if (federationTrustPort == null || orgId == null) {
            return scaffold();
        }
        var trusts = federationTrustPort.listActiveForOrg(orgId);
        if (trusts.isEmpty()) {
            return scaffold();
        }
        var partners = trusts.stream()
            .map(t -> t.partnerOrgId().toString())
            .distinct()
            .collect(Collectors.toList());
        return new FederationStatusResponse(
            "mvp",
            true,
            partners,
            "Cross-org trust active; add partner org members to shared chats when both orgs trust each other.");
    }

    private static FederationStatusResponse scaffold() {
        return new FederationStatusResponse(
            "scaffold",
            false,
            List.of(),
            "Cross-org federation MVP pending; see docs/adr/ADR-federation-scaffold.md");
    }

    public com.avandocmsg.messenger.api.platform.dto.FederationDirectoryResponse directoryForOrg(UUID orgId) {
        if (orgId == null) {
            return new com.avandocmsg.messenger.api.platform.dto.FederationDirectoryResponse(
                null, List.of(), "Sign in to view federation directory.");
        }
        if (federationTrustPort == null) {
            return new com.avandocmsg.messenger.api.platform.dto.FederationDirectoryResponse(
                orgId.toString(), List.of(), "Federation directory scaffold.");
        }
        var home = organizationLookupPort != null
            ? organizationLookupPort.findById(orgId).orElse(null)
            : null;
        var partnerIds = federationTrustPort.listActiveForOrg(orgId).stream()
            .map(FederationTrustPort.TrustRow::partnerOrgId)
            .distinct()
            .toList();
        var orgsById = organizationLookupPort != null
            ? organizationLookupPort.findByIds(partnerIds)
            : Map.<UUID, OrganizationLookupPort.OrgSummary>of();
        var partners = partnerIds.stream()
            .map(pid -> {
                var org = orgsById.get(pid);
                return new com.avandocmsg.messenger.api.platform.dto.FederationDirectoryResponse.FederationDirectoryEntry(
                    pid.toString(),
                    org != null ? org.name() : pid.toString(),
                    org != null ? org.slug() : null);
            })
            .toList();
        return new com.avandocmsg.messenger.api.platform.dto.FederationDirectoryResponse(
            home != null ? home.id() : orgId.toString(),
            partners,
            partners.isEmpty()
                ? "No trusted partner orgs; configure federation trust in admin."
                : "Holding catalog: trusted partner organizations.");
    }
}
