package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.avandocmsg.messenger.testsupport.EmptyChatPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MlsMigrationServiceTest {

    private MlsGroupManagerTest.InMemoryGroupStateRepository groupStateRepository;
    private StubChatRepository chatRepository;
    private MlsGroupManager groupManager;
    private MlsMigrationService migrationService;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
        groupStateRepository = new MlsGroupManagerTest.InMemoryGroupStateRepository();
        chatRepository = new StubChatRepository();
        var sessionRepository = new MlsGroupManagerTest.StubSessionRepository();
        var mlsService = new MlsService(sessionRepository, new E2EEService());
        var clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        groupManager = new MlsGroupManager(groupStateRepository, mlsService, UuidGenerator.standard(), clock);
        migrationService = new MlsMigrationService(null, groupManager, chatRepository);
    }

    @Test
    void migrateToMls_createsGroupForChatMembers() {
        var member = UUID.randomUUID();
        chatRepository.members.put(chatId, List.of(new ChatMemberResponse(
            member.toString(), "alice", "Alice", "member", false, false, Instant.now())));

        var groupId = migrationService.migrateToMls(chatId);

        assertTrue(groupId.isPresent());
        assertEquals(groupId.get(), groupManager.findGroupByChatId(chatId).orElseThrow().groupId());
    }

    @Test
    void batchMigrateToMls_migratesPendingChats() {
        var member = UUID.randomUUID();
        var chat2 = UUID.randomUUID();
        chatRepository.members.put(chatId, List.of(new ChatMemberResponse(
            member.toString(), "alice", "Alice", "member", false, false, Instant.now())));
        chatRepository.members.put(chat2, List.of(new ChatMemberResponse(
            member.toString(), "bob", "Bob", "member", false, false, Instant.now())));
        var batchService = new MlsMigrationService(null, groupManager, chatRepository) {
            @Override
            List<UUID> listPendingChatIds(int limit) {
                return List.of(chatId, chat2);
            }
        };

        var result = batchService.batchMigrateToMls(10);

        assertEquals(2, result.migratedCount());
        assertEquals(0, result.failedCount());
        assertTrue(groupManager.findGroupByChatId(chatId).isPresent());
        assertTrue(groupManager.findGroupByChatId(chat2).isPresent());
    }

    @Test
    void migrateToMls_idempotentWhenGroupExists() {
        var member = UUID.randomUUID();
        chatRepository.members.put(chatId, List.of(new ChatMemberResponse(
            member.toString(), "alice", "Alice", "member", false, false, Instant.now())));
        var first = migrationService.migrateToMls(chatId).orElseThrow();
        var second = migrationService.migrateToMls(chatId).orElseThrow();
        assertEquals(first, second);
        assertEquals(1, groupManager.groupCount());
    }

    static final class StubChatRepository extends EmptyChatPersistencePort {
        final java.util.Map<UUID, List<ChatMemberResponse>> members = new java.util.HashMap<>();

        @Override
        public List<ChatMemberResponse> listMembers(UUID chatId) {
            return members.getOrDefault(chatId, List.of());
        }
    }
}
