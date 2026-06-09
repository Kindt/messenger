package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    static final class StubOrgPort implements OrganizationRepositoryPort {
        boolean exists = false;
        Organization organization;

        @Override
        public boolean exists(OrganizationId id) {
            return exists;
        }

        @Override
        public Optional<Organization> findById(OrganizationId id) {
            return Optional.ofNullable(organization);
        }
    }
}
