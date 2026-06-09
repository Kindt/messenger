package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.domain.UserId;

import java.util.List;
import java.util.Optional;

/** Port for organization reads and writes (Phase 2e / US2). */
public interface OrganizationRepositoryPort {
    boolean exists(OrganizationId id);

    Optional<Organization> findById(OrganizationId id);

    List<Organization> listAll();

    Optional<Organization> create(String name);

    boolean deleteIfUnused(OrganizationId id);

    boolean setUserOrg(UserId userId, OrganizationId orgId);
}
