package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserApplicationServiceTest {

    private final UUID viewerId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final StubUserPort userPort = new StubUserPort();
    private final UserApplicationService service = new UserApplicationService(userPort);

    @Test
    void getProfileForViewer_returnsFullProfileForSelf() {
        userPort.profile = sampleProfile(targetId, false);

        var result = service.getProfileForViewer(UserId.of(targetId), UserId.of(targetId)).orElseThrow();
        assertEquals("+100", result.phone());
        assertTrue(result.privacyDisableReadReceipts());
        assertEquals("org-1", result.orgId());
    }

    @Test
    void getProfileForViewer_returnsPublicProfileForOther() {
        userPort.profile = sampleProfile(targetId, false);

        var result = service.getProfileForViewer(UserId.of(viewerId), UserId.of(targetId)).orElseThrow();
        assertNull(result.phone());
        assertNull(result.orgId());
        assertFalse(result.privacyDisableReadReceipts());
        assertEquals("alice", result.username());
    }

    @Test
    void getProfileForViewer_hidesHiddenUserFromOthers() {
        userPort.profile = sampleProfile(targetId, true);

        assertTrue(service.getProfileForViewer(UserId.of(viewerId), UserId.of(targetId)).isEmpty());
    }

    @Test
    void getProfileForViewer_allowsHiddenUserForSelf() {
        userPort.profile = sampleProfile(targetId, true);

        assertTrue(service.getProfileForViewer(UserId.of(targetId), UserId.of(targetId)).isPresent());
    }

    private static UserProfile sampleProfile(UUID id, boolean hidden) {
        return new UserProfile(
            UserId.of(id),
            "alice",
            "Alice",
            "+100",
            hidden,
            Instant.parse("2026-01-01T00:00:00Z"),
            "online",
            Instant.parse("2026-01-02T00:00:00Z"),
            "org-1",
            true);
    }

    static final class StubUserPort implements UserRepositoryPort {
        UserProfile profile;

        @Override
        public Optional<UserProfile> findById(UserId id) {
            return Optional.ofNullable(profile);
        }
    }
}
