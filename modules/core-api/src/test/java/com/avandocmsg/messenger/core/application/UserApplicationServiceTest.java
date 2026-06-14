package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.SavedChatPort;
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
    private final StubSavedChatPort savedChatPort = new StubSavedChatPort();
    private final UserApplicationService service = new UserApplicationService(userPort, savedChatPort);

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
        assertNull(result.uiLocale());
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

    @Test
    void updateProfile_returnsUpdatedSelfProfile() {
        userPort.profile = sampleProfile(targetId, false);
        userPort.updateProfileOk = true;

        var result = service.updateProfile(UserId.of(targetId), "New Name", "+200").orElseThrow();
        assertEquals("New Name", result.displayName());
        assertEquals("+200", result.phone());
    }

    @Test
    void updateProfile_emptyWhenWriteFails() {
        userPort.updateProfileOk = false;
        assertTrue(service.updateProfile(UserId.of(targetId), "X", null).isEmpty());
    }

    @Test
    void updatePresence_returnsUpdatedProfile() {
        userPort.profile = sampleProfile(targetId, false);
        userPort.updatePresenceOk = true;

        var result = service.updatePresence(UserId.of(targetId), "away").orElseThrow();
        assertEquals("away", result.presenceStatus());
    }

    @Test
    void updatePrivacy_returnsUpdatedProfile() {
        userPort.profile = sampleProfile(targetId, false);
        userPort.updatePrivacyOk = true;

        var result = service.updatePrivacy(UserId.of(targetId), false).orElseThrow();
        assertFalse(result.privacyDisableReadReceipts());
    }

    @Test
    void updateUiLocale_returnsUpdatedProfile() {
        userPort.profile = sampleProfile(targetId, false);
        userPort.updateUiLocaleOk = true;

        var result = service.updateUiLocale(UserId.of(targetId), "en").orElseThrow();
        assertEquals("en", result.uiLocale());
    }

    @Test
    void touchHeartbeat_delegatesToPort() {
        service.touchHeartbeat(UserId.of(targetId));
        assertTrue(userPort.heartbeatTouched);
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
            true,
            "ru");
    }

    static final class StubSavedChatPort implements SavedChatPort {
        @Override
        public java.util.Optional<ChatId> getSavedChatId(UserId userId) {
            return java.util.Optional.empty();
        }
    }

    static final class StubUserPort implements UserRepositoryPort {
        UserProfile profile;
        boolean updateProfileOk;
        boolean updatePresenceOk;
        boolean updatePrivacyOk;
        boolean updateUiLocaleOk;
        boolean heartbeatTouched;

        @Override
        public Optional<UserProfile> findById(UserId id) {
            return Optional.ofNullable(profile);
        }

        @Override
        public boolean updateProfile(UserId id, String displayName, String phone) {
            if (!updateProfileOk || profile == null) {
                return false;
            }
            profile = new UserProfile(
                profile.id(),
                profile.username(),
                displayName != null ? displayName : profile.displayName(),
                phone != null ? phone : profile.phone(),
                profile.hidden(),
                profile.createdAt(),
                profile.presenceStatus(),
                profile.lastSeenAt(),
                profile.orgId(),
                profile.privacyDisableReadReceipts(),
                profile.uiLocale());
            return true;
        }

        @Override
        public boolean updatePresence(UserId id, String presenceStatus) {
            if (!updatePresenceOk || profile == null) {
                return false;
            }
            profile = new UserProfile(
                profile.id(),
                profile.username(),
                profile.displayName(),
                profile.phone(),
                profile.hidden(),
                profile.createdAt(),
                presenceStatus,
                profile.lastSeenAt(),
                profile.orgId(),
                profile.privacyDisableReadReceipts(),
                profile.uiLocale());
            return true;
        }

        @Override
        public boolean updatePrivacy(UserId id, boolean disableReadReceipts) {
            if (!updatePrivacyOk || profile == null) {
                return false;
            }
            profile = new UserProfile(
                profile.id(),
                profile.username(),
                profile.displayName(),
                profile.phone(),
                profile.hidden(),
                profile.createdAt(),
                profile.presenceStatus(),
                profile.lastSeenAt(),
                profile.orgId(),
                disableReadReceipts,
                profile.uiLocale());
            return true;
        }

        @Override
        public boolean updateUiLocale(UserId id, String uiLocale) {
            if (!updateUiLocaleOk || profile == null) {
                return false;
            }
            profile = new UserProfile(
                profile.id(),
                profile.username(),
                profile.displayName(),
                profile.phone(),
                profile.hidden(),
                profile.createdAt(),
                profile.presenceStatus(),
                profile.lastSeenAt(),
                profile.orgId(),
                profile.privacyDisableReadReceipts(),
                uiLocale);
            return true;
        }

        @Override
        public boolean touchHeartbeat(UserId id) {
            heartbeatTouched = true;
            return true;
        }
    }
}
