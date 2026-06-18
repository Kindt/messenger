package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.api.users.dto.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Org-scoped user directory writes for SCIM and LDAP sync (hex Phase 2c). */
public interface OrgUserDirectoryPort {

    Optional<UserProfile> findById(UUID id);

    List<UserProfile> listByOrg(UUID orgId, int offset, int limit);

    int countByOrg(UUID orgId);

    boolean upsertFromDirectory(UUID id, UUID orgId, String externalId, String username,
                                String email, String displayName);

    boolean upsertFromScim(UUID id, UUID orgId, String username, String email,
                           String externalId, String displayName, boolean active);

    boolean setActive(UUID id, boolean active);
}
