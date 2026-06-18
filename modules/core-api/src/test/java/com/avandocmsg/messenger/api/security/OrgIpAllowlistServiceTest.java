package com.avandocmsg.messenger.api.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrgIpAllowlistServiceTest {

    @Test
    void isAllowed_whenDisabled_allowsAnyIp() {
        var repo = new OrgIpAllowlistRepository(null);
        var service = new OrgIpAllowlistService(repo);
        var orgId = UUID.randomUUID();
        assertTrue(service.isAllowed(orgId, "203.0.113.9"));
    }

    @Test
    void isAllowed_exactIpMatch() {
        var repo = new InMemoryRepo();
        var orgId = UUID.randomUUID();
        repo.store = new OrgIpAllowlistRepository.Row(orgId, true, "192.168.1.10,10.0.0.5");
        var service = new OrgIpAllowlistService(repo);
        assertTrue(service.isAllowed(orgId, "192.168.1.10"));
        assertFalse(service.isAllowed(orgId, "192.168.1.11"));
    }

  static final class InMemoryRepo extends OrgIpAllowlistRepository {
        OrgIpAllowlistRepository.Row store;

        InMemoryRepo() {
            super(null);
        }

        @Override
        public java.util.Optional<OrgIpAllowlistRepository.Row> findByOrgId(UUID orgId) {
            return java.util.Optional.ofNullable(store);
        }
    }
}
