package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.adapter.cache.NoOpReadCacheAdapter;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.AvatarAccessPort;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AvatarApplicationServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID FILE = UUID.randomUUID();
    private static final String SECRET = "avatar-test-secret";

    @Test
    void enrichUserProfile_mintsSignedUrlWhenAllowed() {
        var service = service(true, true);
        var domain = sampleProfile(FileId.of(FILE));
        var api = UserDomainMapper.toResponse(domain);
        var enriched = service.enrichUserProfile(api, UserId.of(USER), FileId.of(FILE));
        assertNotNull(enriched.avatarUrl());
        assertTrue(enriched.avatarUrl().contains("avt="));
        assertEquals(FILE.toString(), enriched.avatarFileId());
    }

    @Test
    void enrichUserProfile_omitsUrlWhenAccessDenied() {
        var service = service(false, true);
        var domain = sampleProfile(FileId.of(FILE));
        var api = UserDomainMapper.toResponse(domain);
        var enriched = service.enrichUserProfile(api, UserId.of(USER), FileId.of(FILE));
        assertNull(enriched.avatarUrl());
    }

    @Test
    void setUserAvatar_requiresFileOwner() {
        var userPort = new StubUserPort();
        var filePort = new StubFilePort(false);
        var service = avatarService(appConfig(true), new StubAvatarAccess(true, true), userPort, filePort);
        assertTrue(service.setUserAvatar(UserId.of(USER), FileId.of(FILE)).isEmpty());
    }

    @Test
    void setUserAvatar_updatesProfileWhenOwner() {
        var userPort = new StubUserPort();
        var filePort = new StubFilePort(true);
        var service = avatarService(appConfig(true), new StubAvatarAccess(true, true), userPort, filePort);
        assertTrue(service.setUserAvatar(UserId.of(USER), FileId.of(FILE)).isPresent());
        assertEquals(FILE, userPort.lastAvatarId);
    }

    @Test
    void setUserAvatar_blockedWhenUploadDenied() {
        var userPort = new StubUserPort();
        var filePort = new StubFilePort(true);
        var service = avatarService(appConfig(true), new StubAvatarAccess(true, false), userPort, filePort);
        assertTrue(service.setUserAvatar(UserId.of(USER), FileId.of(FILE)).isEmpty());
    }

    private static AvatarApplicationService service(boolean allowAccess, boolean enabled) {
        return avatarService(appConfig(enabled), new StubAvatarAccess(allowAccess, true),
            new StubUserPort(), new StubFilePort(true));
    }

    private static AvatarApplicationService avatarService(
        AppConfig config, AvatarAccessPort access, UserRepositoryPort userPort, FileMetadataPort filePort) {
        return new AvatarApplicationService(new AvatarApplicationService.Dependencies(
            new AvatarApplicationService.Ports(
                config, access, tokenBuilder(), userPort, new StubChatPort(), filePort),
            new AvatarApplicationService.SideEffects(null, NoOpReadCacheAdapter.INSTANCE, null)));
    }

    private static AvatarUrlBuilder tokenBuilder() {
        return new AvatarUrlBuilder(new AvatarAccessTokenService(SECRET, null, 3600), appConfig(true));
    }

    private static AppConfig appConfig(boolean avatarsEnabled) {
        return new AppConfig() {
            @Override
            public boolean avatarsEnabled() {
                return avatarsEnabled;
            }

            @Override
            public boolean fileResizeEnabled() {
                return true;
            }

            @Override
            public String avatarTokenHmacSecret() {
                return SECRET;
            }
        };
    }

    private static UserProfile sampleProfile(FileId avatarFileId) {
        return new UserProfile(
            UserId.of(USER), "alice", "Alice", null, false,
            Instant.parse("2026-01-01T00:00:00Z"), "online", null,
            UUID.randomUUID().toString(), false, "ru", null, null, avatarFileId);
    }

    static final class StubAvatarAccess implements AvatarAccessPort {
        private final boolean allow;
        private final boolean uploadAllowed;

        StubAvatarAccess(boolean allow) {
            this(allow, true);
        }

        StubAvatarAccess(boolean allow, boolean uploadAllowed) {
            this.allow = allow;
            this.uploadAllowed = uploadAllowed;
        }

        @Override
        public boolean viewerMayAccessAsAvatar(UserId viewerId, FileId fileId) {
            return allow;
        }

        @Override
        public boolean userMayUploadAvatar(UserId userId) {
            return uploadAllowed;
        }

        @Override
        public Optional<UserId> findUserIdByAvatarFile(FileId fileId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.avandocmsg.messenger.core.domain.ChatId> findChatIdByAvatarFile(FileId fileId) {
            return Optional.empty();
        }
    }

    static final class StubUserPort implements UserRepositoryPort {
        UUID lastAvatarId;

        @Override
        public Optional<UserProfile> findById(UserId id) {
            return Optional.of(sampleProfile(lastAvatarId != null ? FileId.of(lastAvatarId) : null));
        }

        @Override
        public boolean updateProfile(UserId id, String displayName, String phone) {
            return false;
        }

        @Override
        public boolean updatePresence(UserId id, String presenceStatus) {
            return false;
        }

        @Override
        public boolean updateUserStatus(UserId id, String presenceStatus, String customStatusText,
                                        Instant dndUntil, boolean clearDndUntil) {
            return false;
        }

        @Override
        public boolean updatePrivacy(UserId id, boolean disableReadReceipts) {
            return false;
        }

        @Override
        public boolean updateUiLocale(UserId id, String uiLocale) {
            return false;
        }

        @Override
        public boolean updateAvatar(UserId id, UUID avatarFileId) {
            lastAvatarId = avatarFileId;
            return true;
        }

        @Override
        public boolean touchHeartbeat(UserId id) {
            return false;
        }

        @Override
        public void upsertFromKeycloak(UserId id, String username, String displayName) {
            // no-op stub: Keycloak sync not exercised in these tests
        }

        @Override
        public boolean createLocalUser(UserId id, String username, String displayName) {
            return false;
        }
    }

    static final class StubFilePort implements FileMetadataPort {
        private final boolean owned;

        StubFilePort(boolean owned) {
            this.owned = owned;
        }

        @Override
        public Optional<StoredFile> findById(FileId id) {
            if (!owned) {
                return Optional.empty();
            }
            return Optional.of(new StoredFile(id, "a.jpg", "image/jpeg", 10, UserId.of(USER)));
        }

        @Override
        public Optional<StoredFile> insert(FileId id, String filename, String mimeType, long size,
                                           UserId uploadedBy) {
            return Optional.empty();
        }

        @Override
        public boolean delete(FileId id) {
            return false;
        }

        @Override
        public Optional<com.avandocmsg.messenger.core.domain.FileBlob> findBlobByContentHash(String contentHash) {
            return Optional.empty();
        }

        @Override
        public boolean insertBlob(String contentHash, String storageKey, long blobSize) {
            return false;
        }

        @Override
        public boolean incrementBlobRefCount(String contentHash) {
            return false;
        }

        @Override
        public Optional<Integer> decrementBlobRefCount(String contentHash) {
            return Optional.empty();
        }

        @Override
        public Optional<StoredFile> insertWithStorage(FileId id, String filename, String mimeType, long size,
                                                      UserId uploadedBy, String contentHash, String storageKey) {
            return Optional.empty();
        }
    }

    static final class StubChatPort implements ChatPersistencePort {
        @Override
        public boolean chatExists(UUID chatId) {
            return false;
        }

        @Override
        public Optional<UUID> findOrgIdForRetentionOverlay(UUID chatId) {
            return Optional.empty();
        }

        @Override
        public com.avandocmsg.messenger.api.chats.dto.ChatResponse createGroup(UUID chatId, String title,
                                                                               UUID ownerId) {
            return null;
        }

        @Override
        public com.avandocmsg.messenger.api.chats.dto.ChatResponse createChannel(UUID chatId, String title,
                                                                                 UUID ownerId) {
            return null;
        }

        @Override
        public Optional<String> getChatType(UUID chatId) {
            return Optional.empty();
        }

        @Override
        public com.avandocmsg.messenger.api.chats.dto.ChatResponse createP2P(UUID chatId, UUID user1Id,
                                                                             UUID user2Id) {
            return null;
        }

        @Override
        public Optional<UUID> findP2PChat(UUID user1Id, UUID user2Id) {
            return Optional.empty();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.chats.dto.ChatResponse> listByUser(UUID userId) {
            return java.util.List.of();
        }

        @Override
        public Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.avandocmsg.messenger.api.chats.dto.ChatResponse> findById(UUID chatId, UUID userId) {
            return Optional.empty();
        }

        @Override
        public boolean updateTitle(UUID chatId, String title) {
            return false;
        }

        @Override
        public boolean updateAvatar(UUID chatId, UUID avatarFileId) {
            return false;
        }

        @Override
        public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
            return false;
        }

        @Override
        public boolean setArchived(UUID chatId, UUID userId, boolean archived) {
            return false;
        }

        @Override
        public boolean setFolderTag(UUID chatId, UUID userId, String folderTag) {
            return false;
        }

        @Override
        public boolean addMember(UUID chatId, UUID userId, String role) {
            return false;
        }

        @Override
        public boolean removeMember(UUID chatId, UUID userId) {
            return false;
        }

        @Override
        public boolean setRole(UUID chatId, UUID userId, String role) {
            return false;
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse> listMembers(UUID chatId) {
            return java.util.List.of();
        }

        @Override
        public Optional<UUID> findOwnerId(UUID chatId) {
            return Optional.empty();
        }

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return null;
        }

        @Override
        public boolean isMemberBanned(UUID chatId, UUID userId) {
            return false;
        }

        @Override
        public boolean setPersonalFilterActive(UUID chatId, UUID userId, boolean active) {
            return false;
        }

        @Override
        public boolean isPersonalFilterActive(UUID chatId, UUID userId) {
            return false;
        }

        @Override
        public boolean setBanned(UUID chatId, UUID userId, boolean banned) {
            return false;
        }

        @Override
        public java.util.List<UUID> listChatIdsForUser(UUID userId) {
            return java.util.List.of();
        }
    }
}
