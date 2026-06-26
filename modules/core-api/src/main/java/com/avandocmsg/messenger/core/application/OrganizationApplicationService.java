package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Hexagonal application service for organization reads and writes (Phase 2e / US2). */
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

    public List<Organization> listAll() {
        return organizationRepositoryPort.listAll();
    }

    public Optional<Organization> create(String name) {
        return organizationRepositoryPort.create(name);
    }

    public boolean deleteIfUnused(OrganizationId orgId) {
        return organizationRepositoryPort.deleteIfUnused(orgId);
    }

    public boolean setUserOrg(UserId userId, OrganizationId orgId) {
        return organizationRepositoryPort.setUserOrg(userId, orgId);
    }

    public boolean updateLogo(OrganizationId orgId, UUID logoFileId) {
        return organizationRepositoryPort.updateLogo(orgId, logoFileId);
    }
}
