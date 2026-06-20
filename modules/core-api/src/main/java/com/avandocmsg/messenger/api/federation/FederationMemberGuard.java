package com.avandocmsg.messenger.api.federation;

import com.avandocmsg.messenger.core.port.FederationTrustPort;
import com.avandocmsg.messenger.core.port.UserLookupPort;

import java.util.Optional;
import java.util.UUID;

/** Validates cross-org chat membership when federation trust is active. */
public final class FederationMemberGuard {
    private final FederationTrustPort federationTrustPort;
    private final UserLookupPort userLookupPort;

    public FederationMemberGuard(FederationTrustPort federationTrustPort, UserLookupPort userLookupPort) {
        this.federationTrustPort = federationTrustPort;
        this.userLookupPort = userLookupPort;
    }

    public boolean canAddMember(UUID actorId, UUID targetUserId) {
        if (federationTrustPort == null || userLookupPort == null) {
            return true;
        }
        if (actorId.equals(targetUserId)) {
            return true;
        }
        var actor = userLookupPort.findById(actorId).orElse(null);
        var target = userLookupPort.findById(targetUserId).orElse(null);
        if (actor == null || target == null || actor.orgId() == null || target.orgId() == null) {
            return true;
        }
        if (actor.orgId().equals(target.orgId())) {
            return true;
        }
        var actorOrg = parseUuid(actor.orgId());
        var targetOrg = parseUuid(target.orgId());
        if (actorOrg == null || targetOrg == null) {
            return false;
        }
        return federationTrustPort.isTrusted(actorOrg, targetOrg)
            || federationTrustPort.isTrusted(targetOrg, actorOrg);
    }

    public Optional<String> denyReason(UUID actorId, UUID targetUserId) {
        return canAddMember(actorId, targetUserId)
            ? Optional.empty()
            : Optional.of("error.chat.federation_trust_required");
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
