package com.avandocmsg.messenger.api.federation;

import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.core.port.FederationTrustPort;
import com.avandocmsg.messenger.core.port.UserLookupPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationMemberGuardTest {

    @Test
    void allowsSameOrg() {
        var org = UUID.randomUUID().toString();
        var guard = new FederationMemberGuard(null, user(org, org));
        assertTrue(guard.canAddMember(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void deniesCrossOrgWithoutTrust() {
        var orgA = UUID.randomUUID();
        var orgB = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var target = UUID.randomUUID();
        var guard = new FederationMemberGuard(noTrust(), users(orgA, orgB, actor, target));
        assertFalse(guard.canAddMember(actor, target));
    }

    @Test
    void allowsCrossOrgWithTrust() {
        var orgA = UUID.randomUUID();
        var orgB = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var target = UUID.randomUUID();
        var guard = new FederationMemberGuard(trust(orgA, orgB), users(orgA, orgB, actor, target));
        assertTrue(guard.canAddMember(actor, target));
    }

    private static UserLookupPort users(UUID orgA, UUID orgB, UUID actor, UUID target) {
        return new UserLookupPort() {
            @Override
            public Optional<UserProfile> findById(UUID id) {
                if (id.equals(actor)) {
                    return Optional.of(profile(id, orgA));
                }
                if (id.equals(target)) {
                    return Optional.of(profile(id, orgB));
                }
                return Optional.empty();
            }

            @Override
            public Optional<UserProfile> findByUsername(String username) {
                return Optional.empty();
            }

            @Override
            public Optional<UserProfile> findByExternalId(String externalId) {
                return Optional.empty();
            }

            @Override
            public boolean isReadReceiptsDisabled(UUID id) {
                return false;
            }

            @Override
            public List<com.avandocmsg.messenger.api.users.dto.UserSearchHit> searchForViewer(
                UUID viewerId, String query, int limit) {
                return List.of();
            }
        };
    }

    private static UserLookupPort user(String org, String org2) {
        return new UserLookupPort() {
            @Override
            public Optional<UserProfile> findById(UUID id) {
                return Optional.of(profile(id, UUID.fromString(org)));
            }

            @Override
            public Optional<UserProfile> findByUsername(String username) {
                return Optional.empty();
            }

            @Override
            public Optional<UserProfile> findByExternalId(String externalId) {
                return Optional.empty();
            }

            @Override
            public boolean isReadReceiptsDisabled(UUID id) {
                return false;
            }

            @Override
            public List<com.avandocmsg.messenger.api.users.dto.UserSearchHit> searchForViewer(
                UUID viewerId, String query, int limit) {
                return List.of();
            }
        };
    }

    private static UserProfile profile(UUID id, UUID orgId) {
        return new UserProfile(
            id.toString(), "u", "User", null, null, null, false, null, null, null, orgId.toString(),
            false, null, null, null);
    }

    private static FederationTrustPort noTrust() {
        return new FederationTrustPort() {
            @Override
            public UUID insert(UUID orgId, UUID partnerOrgId, String status, java.time.Instant expiresAt) {
                return null;
            }

            @Override
            public List<TrustRow> listForOrg(UUID orgId) {
                return List.of();
            }

            @Override
            public List<TrustRow> listActiveForOrg(UUID orgId) {
                return List.of();
            }

            @Override
            public boolean isTrusted(UUID orgId, UUID partnerOrgId) {
                return false;
            }

            @Override
            public boolean anyActiveTrust() {
                return false;
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
    }

    private static FederationTrustPort trust(UUID orgA, UUID orgB) {
        return new FederationTrustPort() {
            @Override
            public UUID insert(UUID orgId, UUID partnerOrgId, String status, java.time.Instant expiresAt) {
                return null;
            }

            @Override
            public List<TrustRow> listForOrg(UUID orgId) {
                return List.of();
            }

            @Override
            public List<TrustRow> listActiveForOrg(UUID orgId) {
                return List.of();
            }

            @Override
            public boolean isTrusted(UUID orgId, UUID partnerOrgId) {
                return orgA.equals(orgId) && orgB.equals(partnerOrgId);
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
    }
}
