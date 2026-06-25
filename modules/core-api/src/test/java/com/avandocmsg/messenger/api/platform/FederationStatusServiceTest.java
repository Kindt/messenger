package com.avandocmsg.messenger.api.platform;

import com.avandocmsg.messenger.core.port.FederationTrustPort;
import com.avandocmsg.messenger.core.port.OrganizationLookupPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FederationStatusServiceTest {

    @Test
    void globalStatus_returnsScaffoldDisabled() {
        var svc = new FederationStatusService(null, null);
        var res = svc.globalStatus();
        assertEquals("scaffold", res.mode());
        assertFalse(res.enabled());
        assertEquals(0, res.partnerOrgIds().size());
    }

    @Test
    void directoryForOrg_batchLoadsPartnerOrganizations() {
        var homeId = UUID.randomUUID();
        var partnerA = UUID.randomUUID();
        var partnerB = UUID.randomUUID();
        var trustPort = new FederationTrustPort() {
            @Override
            public UUID insert(UUID orgId, UUID partnerOrgId, String status, Instant expiresAt) {
                return null;
            }

            @Override
            public List<TrustRow> listForOrg(UUID orgId) {
                return List.of();
            }

            @Override
            public List<TrustRow> listActiveForOrg(UUID orgId) {
                return List.of(
                    new TrustRow(UUID.randomUUID(), homeId, partnerA, "active", null),
                    new TrustRow(UUID.randomUUID(), homeId, partnerB, "active", null));
            }

            @Override
            public boolean isTrusted(UUID orgId, UUID partnerOrgId) {
                return false;
            }

            @Override
            public boolean anyActiveTrust() {
                return true;
            }

            @Override
            public Optional<TrustRow> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public boolean updateStatus(UUID id, String status) {
                return false;
            }
        };
        var lookupCalls = new AtomicInteger();
        var orgPort = new OrganizationLookupPort() {
            @Override
            public boolean exists(UUID orgId) {
                return false;
            }

            @Override
            public Optional<OrgSummary> findById(UUID orgId) {
                if (homeId.equals(orgId)) {
                    return Optional.of(new OrgSummary(homeId.toString(), "Home", "home", Instant.EPOCH));
                }
                return Optional.empty();
            }

            @Override
            public Map<UUID, OrgSummary> findByIds(Collection<UUID> orgIds) {
                lookupCalls.incrementAndGet();
                return Map.of(
                    partnerA, new OrgSummary(partnerA.toString(), "Partner A", "a", Instant.EPOCH),
                    partnerB, new OrgSummary(partnerB.toString(), "Partner B", "b", Instant.EPOCH));
            }

            @Override
            public Optional<OrgSummary> findBySlug(String slug) {
                return Optional.empty();
            }

            @Override
            public Optional<OrgSummary> findSingle() {
                return Optional.empty();
            }

            @Override
            public List<OrgSummary> listAll() {
                return List.of();
            }
        };
        var svc = new FederationStatusService(trustPort, orgPort);
        var dir = svc.directoryForOrg(homeId);
        assertEquals(1, lookupCalls.get());
        assertEquals(2, dir.partnerOrgs().size());
        assertEquals("Partner A", dir.partnerOrgs().get(0).name());
        assertEquals("Partner B", dir.partnerOrgs().get(1).name());
    }
}
