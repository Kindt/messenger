package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;

import java.util.Optional;

/** Hexagonal application service for organization reads (Phase 2e). */
public final class OrganizationApplicationService {
    private final OrganizationRepositoryPort organizationRepositoryPort;

    public OrganizationApplicationService(OrganizationRepositoryPort organizationRepositoryPort) {
        this.organizationRepositoryPort = organizationRepositoryPort;
    }

    public boolean exists(OrganizationId orgId) {
        return organizationRepositoryPort.exists(orgId);
    }

    public Optional<Organization> findById(OrganizationId orgId) {
        return organizationRepositoryPort.findById(orgId);
    }
}
