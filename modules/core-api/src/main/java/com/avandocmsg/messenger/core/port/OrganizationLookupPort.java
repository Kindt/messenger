package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Slug-aware organization reads for auth routing and directory sync. */
public interface OrganizationLookupPort {
    boolean exists(UUID orgId);

    Optional<OrgSummary> findById(UUID orgId);

    Optional<OrgSummary> findBySlug(String slug);

    Optional<OrgSummary> findSingle();

    List<OrgSummary> listAll();

    record OrgSummary(String id, String name, String slug, Instant createdAt) {}
}
