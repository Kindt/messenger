package com.avandocmsg.messenger.core.benchmark;

import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.adapter.cache.NoOpReadCacheAdapter;
import com.avandocmsg.messenger.core.application.ChatApplicationService;
import com.avandocmsg.messenger.core.application.FileApplicationService;
import com.avandocmsg.messenger.core.application.OrganizationApplicationService;
import com.avandocmsg.messenger.core.application.UserApplicationService;
import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.Organization;
import com.avandocmsg.messenger.core.domain.OrganizationId;
import com.avandocmsg.messenger.core.domain.StoredFile;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.FileMetadataPort;
import com.avandocmsg.messenger.core.port.ObjectStoragePort;
import com.avandocmsg.messenger.core.port.OrganizationRepositoryPort;
import com.avandocmsg.messenger.core.port.SavedChatPort;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreApiBenchmarkTest {

    @Test
    void getChatForMember_1000Calls_underBudget() {
        var chatId = UUID.randomUUID();
        var viewerId = UUID.randomUUID();
        var port = new com.avandocmsg.messenger.core.port.ChatRepositoryPort() {
            @Override
            public Optional<Chat> findById(ChatId id) {
                return Optional.of(new Chat(id, "Bench", ChatType.P2P, Instant.parse("2026-01-01T00:00:00Z")));
            }

            @Override
            public boolean isMember(ChatId c, UserId u) {
                return true;
            }

            @Override
            public Optional<String> memberRole(ChatId c, UserId u) {
                return Optional.of("member");
            }

            @Override
            public boolean isMemberBanned(ChatId c, UserId u) {
                return false;
            }

            @Override
            public Optional<UserId> findOtherP2pMember(ChatId c, UserId u) {
                return Optional.empty();
            }

            @Override
            public List<UserId> listMemberUserIds(ChatId c) {
                return List.of();
            }
        };
        var service = new ChatApplicationService(port);

        var start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            assertTrue(service.getChatForMember(ChatId.of(chatId), UserId.of(viewerId)).isPresent());
        }
        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 500, "1000 getChatForMember calls took " + elapsedMs + "ms");
    }

    @Test
    void getProfileForViewer_1000Calls_underBudget() {
        var viewerId = UUID.randomUUID();
        UserRepositoryPort port = new UserRepositoryPort() {
            @Override
            public Optional<UserProfile> findById(UserId id) {
                return Optional.of(new UserProfile(
                    id,
                    "bench",
                    "Bench User",
                    null,
                    false,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    "online",
                    null,
                    null,
                    false,
                    null,
                    null,
                    null));
            }

            @Override
            public boolean updateUserStatus(UserId id, String presenceStatus, String customStatusText,
                                            java.time.Instant dndUntil, boolean clearDndUntil) {
                return false;
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
            public boolean updatePrivacy(UserId id, boolean disableReadReceipts) {
                return false;
            }

            @Override
            public boolean updateUiLocale(UserId id, String uiLocale) {
                return false;
            }

            @Override
            public boolean updateAvatar(UserId id, UUID avatarFileId) {
                return false;
            }

            @Override
            public boolean touchHeartbeat(UserId id) {
                return false;
            }

            @Override
            public void upsertFromKeycloak(UserId id, String username, String displayName) {
            }

            @Override
            public boolean createLocalUser(UserId id, String username, String displayName) {
                return false;
            }
        };
        SavedChatPort savedChatPort = new SavedChatPort() {
            @Override
            public Optional<ChatId> getSavedChatId(UserId userId) {
                return Optional.empty();
            }

            @Override
            public Optional<ChatId> ensureSavedVaultChat(UserId userId) {
                return Optional.empty();
            }
        };
        var service = new UserApplicationService(
            port, savedChatPort, NoOpReadCacheAdapter.INSTANCE, new AppConfig(),
            new com.avandocmsg.messenger.core.application.UserPresencePublisher(null));

        var start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            assertTrue(service.getProfileForViewer(UserId.of(viewerId), UserId.of(viewerId)).isPresent());
        }
        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 500, "1000 getProfileForViewer calls took " + elapsedMs + "ms");
    }

    @Test
    void getMetadataForUser_1000Calls_underBudget() {
        var userId = UserId.of(UUID.randomUUID());
        var fileId = FileId.of(UUID.randomUUID());
        FileMetadataPort port = new FileMetadataPort() {
            @Override
            public Optional<StoredFile> findById(FileId id) {
                return Optional.of(new StoredFile(fileId, "bench.txt", "text/plain", 42, userId));
            }

            @Override
            public Optional<StoredFile> insert(FileId id, String filename, String mimeType, long size, UserId uploadedBy) {
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
        };
        ObjectStoragePort storage = new ObjectStoragePort() {
            @Override
            public void put(String objectName, InputStream data, long size, String contentType) throws Exception {}

            @Override
            public InputStream get(String objectName) throws Exception {
                return InputStream.nullInputStream();
            }

            @Override
            public void delete(String objectName) throws Exception {}

            @Override
            public java.util.Optional<String> presignedGetUrl(String objectName, int ttlSeconds) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<String> presignedPutUrl(String objectName, int ttlSeconds, String contentType) {
                return java.util.Optional.empty();
            }
        };
        MessageQueryPort queryPort = new MessageQueryPort() {
            @Override
            public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> findByChatId(
                UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse> findVersions(
                UUID msgId) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<com.avandocmsg.messenger.api.messages.dto.ReactionResponse> getReactions(
                UUID messageId) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse> getPinnedMessages(
                UUID chatId) {
                return java.util.List.of();
            }

            @Override
            public boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId) {
                return false;
            }

            @Override
            public java.util.Optional<com.avandocmsg.messenger.core.port.FileMessageRef> findLatestMessageRefForViewer(
                UUID fileId, UUID viewerId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<com.avandocmsg.messenger.core.domain.MessageId> findLatestMessageId(
                com.avandocmsg.messenger.core.domain.ChatId chatId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> searchPlaintextForUser(
                com.avandocmsg.messenger.core.domain.UserId userId, java.util.List<UUID> chatIds, String queryText,
                int limit) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> loadMessagesForSearchResults(
                com.avandocmsg.messenger.core.domain.UserId userId, java.util.List<String> messageIdsInOrder, int limit) {
                return java.util.List.of();
            }
        };
        var service = new FileApplicationService(port, queryPort, storage, UuidGenerator.standard(), 1_000_000, false);

        var start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            assertTrue(service.getMetadataForUser(userId, fileId).isPresent());
        }
        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 500, "1000 getMetadataForUser calls took " + elapsedMs + "ms");
    }

    @Test
    void findById_1000Calls_underBudget() {
        var orgId = OrganizationId.of(UUID.randomUUID());
        OrganizationRepositoryPort port = new OrganizationRepositoryPort() {
            @Override
            public boolean exists(OrganizationId id) {
                return orgId.equals(id);
            }

            @Override
            public Optional<Organization> findById(OrganizationId id) {
                return Optional.of(new Organization(orgId, "Bench Org", Instant.parse("2026-01-01T00:00:00Z")));
            }

            @Override
            public List<Organization> listAll() {
                return List.of();
            }

            @Override
            public Optional<Organization> create(String name) {
                return Optional.empty();
            }

            @Override
            public boolean deleteIfUnused(OrganizationId id) {
                return false;
            }

            @Override
            public boolean setUserOrg(UserId userId, OrganizationId organizationId) {
                return false;
            }

            @Override
            public boolean updateLogo(OrganizationId organizationId, UUID logoFileId) {
                return false;
            }
        };
        var service = new OrganizationApplicationService(port);

        var start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            assertTrue(service.findById(orgId).isPresent());
        }
        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 500, "1000 findById calls took " + elapsedMs + "ms");
    }
}
