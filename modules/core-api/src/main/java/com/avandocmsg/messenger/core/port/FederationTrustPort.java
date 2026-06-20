package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cross-org federation trust between organizations. */
public interface FederationTrustPort {
    UUID insert(UUID orgId, UUID partnerOrgId, String status, Instant expiresAt);

    List<TrustRow> listForOrg(UUID orgId);

    List<TrustRow> listActiveForOrg(UUID orgId);

    boolean isTrusted(UUID orgId, UUID partnerOrgId);

    /** True when at least one non-expired active trust row exists. */
    boolean anyActiveTrust();

    Optional<TrustRow> findById(UUID id);

    boolean updateStatus(UUID id, String status);

    record TrustRow(
        UUID id,
        UUID orgId,
        UUID partnerOrgId,
        String status,
        Instant expiresAt
    ) {}
}
