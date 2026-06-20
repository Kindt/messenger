package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.api.platform.dto.FederationStatusResponse;
import com.avandocmsg.messenger.core.port.FederationTrustPort;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class FederationStatusService {
    private final FederationTrustPort federationTrustPort;

    public FederationStatusService(FederationTrustPort federationTrustPort) {
        this.federationTrustPort = federationTrustPort;
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
}
