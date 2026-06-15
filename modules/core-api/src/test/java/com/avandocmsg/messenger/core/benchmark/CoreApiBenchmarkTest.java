package com.avandocmsg.messenger.core.benchmark;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
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
import java.time.Clock;
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
        var port = (java.util.function.Function<ChatId, Optional<Chat>>) id ->
            Optional.of(new Chat(id, "Bench", ChatType.P2P, Instant.parse("2026-01-01T00:00:00Z")));
        var legacy = new ChatRepository(null, Clock.systemUTC(), UuidGenerator.standard()) {
            @Override
            public String getMemberRole(UUID c, UUID u) {
                return "member";
            }
        };
        var service = new ChatApplicationService(port::apply, legacy);

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
                    null));
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
        var service = new UserApplicationService(port, savedChatPort, NoOpReadCacheAdapter.INSTANCE, new AppConfig());

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
        };
        MessageRepository legacy = new MessageRepository(null, Clock.systemUTC());
        ObjectStoragePort storage = new ObjectStoragePort() {
            @Override
            public void put(String objectName, InputStream data, long size, String contentType) throws Exception {}

            @Override
            public InputStream get(String objectName) throws Exception {
                return InputStream.nullInputStream();
            }

            @Override
            public void delete(String objectName) throws Exception {}
        };
        var service = new FileApplicationService(port, legacy, storage, UuidGenerator.standard(), 1_000_000);

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
