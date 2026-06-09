package com.avandocmsg.messenger.core.port;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;

import java.util.Optional;

/** Port for organization reads (Phase 2e). */
public interface OrganizationRepositoryPort {
    boolean exists(OrganizationId id);

    Optional<Organization> findById(OrganizationId id);
}
