package com.avandocmsg.messenger.core.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SCIM 2.0 Groups persistence (hex Phase 2c). */
public interface ScimGroupRepositoryPort {

    record ScimGroupRow(
        UUID id,
        UUID orgId,
        String displayName,
        String externalId,
        String membersJson,
        Instant createdAt,
        Instant updatedAt
    ) {}

    Optional<ScimGroupRow> findById(UUID id);

    List<ScimGroupRow> listByOrg(UUID orgId, int offset, int limit);

    int countByOrg(UUID orgId);

    boolean insert(UUID id, UUID orgId, String displayName, String externalId, String membersJson);

    boolean update(UUID id, String displayName, String externalId, String membersJson);

    boolean delete(UUID id);
}
