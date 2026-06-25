package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Slug-aware organization reads for auth routing and directory sync. */
public interface OrganizationLookupPort {
    boolean exists(UUID orgId);

    Optional<OrgSummary> findById(UUID orgId);

    /** Batch org lookup for federation directory and other list enrichments (spec 025 FR-176). */
    default Map<UUID, OrgSummary> findByIds(Collection<UUID> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Map.of();
        }
        var out = new HashMap<UUID, OrgSummary>();
        for (var id : orgIds) {
            if (id != null) {
                findById(id).ifPresent(summary -> out.put(id, summary));
            }
        }
        return out;
    }

    Optional<OrgSummary> findBySlug(String slug);

    Optional<OrgSummary> findSingle();

    List<OrgSummary> listAll();

    record OrgSummary(String id, String name, String slug, Instant createdAt) {}
}
