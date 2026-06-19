package com.avandocmsg.messenger.core.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Org-scoped user directory writes for SCIM and LDAP sync (hex Phase 2c). */
public interface OrgUserDirectoryPort {

    /** Directory-facing user row (no JAX-RS / JSON DTO coupling). */
    record OrgDirectoryUser(
        UUID id,
        String username,
        String displayName,
        String email,
        String externalId,
        boolean hidden,
        UUID orgId
    ) {}

    Optional<OrgDirectoryUser> findById(UUID id);

    List<OrgDirectoryUser> listByOrg(UUID orgId, int offset, int limit);

    int countByOrg(UUID orgId);

    boolean upsertFromDirectory(UUID id, UUID orgId, String externalId, String username,
                                String email, String displayName);

    boolean upsertFromScim(UUID id, UUID orgId, String username, String email,
                           String externalId, String displayName, boolean active);

    boolean setActive(UUID id, boolean active);
}
