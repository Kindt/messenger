package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationApplicationServiceTest {

    private final UUID orgId = UUID.randomUUID();
    private final StubOrgPort orgPort = new StubOrgPort();
    private final OrganizationApplicationService service = new OrganizationApplicationService(orgPort);

    @Test
    void exists_delegatesToPort() {
        orgPort.exists = true;
        assertTrue(service.exists(OrganizationId.of(orgId)));
    }

    @Test
    void findById_returnsOrganization() {
        orgPort.organization = new Organization(
            OrganizationId.of(orgId),
            "Acme",
            Instant.parse("2026-01-01T00:00:00Z"));

        var org = service.findById(OrganizationId.of(orgId)).orElseThrow();
        assertEquals("Acme", org.name());
    }

    @Test
    void listAll_delegatesToPort() {
        orgPort.organization = new Organization(
            OrganizationId.of(orgId),
            "Acme",
            Instant.parse("2026-01-01T00:00:00Z"));
        orgPort.organizations = List.of(orgPort.organization);
        assertEquals(1, service.listAll().size());
    }

    @Test
    void create_delegatesToPort() {
        orgPort.organization = new Organization(
            OrganizationId.of(orgId),
            "New Org",
            Instant.parse("2026-01-01T00:00:00Z"));
        orgPort.createResult = Optional.of(orgPort.organization);

        var created = service.create("New Org").orElseThrow();
        assertEquals("New Org", created.name());
    }

    @Test
    void deleteIfUnused_delegatesToPort() {
        orgPort.deleteOk = true;
        assertTrue(service.deleteIfUnused(OrganizationId.of(orgId)));
    }

    @Test
    void setUserOrg_delegatesToPort() {
        orgPort.setUserOrgOk = true;
        var userId = UUID.randomUUID();
        assertTrue(service.setUserOrg(UserId.of(userId), OrganizationId.of(orgId)));
    }

    static final class StubOrgPort implements OrganizationRepositoryPort {
        boolean exists = false;
        Organization organization;
        List<Organization> organizations = List.of();
        Optional<Organization> createResult = Optional.empty();
        boolean deleteOk;
        boolean setUserOrgOk;

        @Override
        public boolean exists(OrganizationId id) {
            return exists;
        }

        @Override
        public Optional<Organization> findById(OrganizationId id) {
            return Optional.ofNullable(organization);
        }

        @Override
        public List<Organization> listAll() {
            return organizations;
        }

        @Override
        public Optional<Organization> create(String name) {
            return createResult;
        }

        @Override
        public boolean deleteIfUnused(OrganizationId id) {
            return deleteOk;
        }

        @Override
        public boolean setUserOrg(UserId userId, OrganizationId orgId) {
            return setUserOrgOk;
        }

        @Override
        public boolean updateLogo(OrganizationId orgId, UUID logoFileId) {
            return true;
        }
    }
}
