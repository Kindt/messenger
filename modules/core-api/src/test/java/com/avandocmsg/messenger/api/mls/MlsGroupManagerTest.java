package com.avandocmsg.messenger.api.mls;

import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.mls.dto.EncryptedMessage;
import com.avandocmsg.messenger.api.mls.wire.MlsCommitPayload;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MlsGroupManagerTest {

    private InMemoryGroupStateRepository groupStateRepository;
    private RecordingWirePublisher wirePublisher;
    private MlsGroupManager manager;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
        groupStateRepository = new InMemoryGroupStateRepository();
        wirePublisher = new RecordingWirePublisher();
        var sessionRepository = new StubSessionRepository();
        var mlsService = new MlsService(sessionRepository, new E2EEService());
        var clock = Clock.fixed(Instant.parse("2026-05-24T12:00:00Z"), ZoneOffset.UTC);
        manager = new MlsGroupManager(groupStateRepository, mlsService, UuidGenerator.standard(), clock, wirePublisher);
    }

    @Test
    void createGroup_encrypt_decrypt_roundTrip() {
        var groupId = manager.createGroup(chatId, List.of(UUID.randomUUID(), UUID.randomUUID()));
        assertNotNull(groupId);

        var senderId = UUID.randomUUID();
        var encrypted = manager.encrypt(groupId, senderId, "hello mls stub");
        assertNotNull(encrypted);
        assertNotNull(encrypted.ciphertextBase64());
        assertNotNull(encrypted.nonceBase64());
        assertEquals(0L, encrypted.epoch());

        var body = java.util.Base64.getDecoder().decode(encrypted.ciphertextBase64());
        var nonce = java.util.Base64.getDecoder().decode(encrypted.nonceBase64());
        var plain = manager.decrypt(groupId, encrypted.epoch(), body, nonce);
        assertEquals("hello mls stub", plain);
    }

    @Test
    void addMember_bumpsEpoch() {
        var groupId = manager.createGroup(chatId, List.of(UUID.randomUUID()));
        var before = manager.findGroup(groupId).orElseThrow().epoch();
        assertTrue(manager.addMember(groupId, UUID.randomUUID()));
        var after = manager.findGroup(groupId).orElseThrow().epoch();
        assertEquals(before + 1, after);
    }

    @Test
    void createGroup_publishesWelcome() {
        var members = List.of(UUID.randomUUID(), UUID.randomUUID());
        manager.createGroup(chatId, members);
        assertEquals(1, wirePublisher.welcomeCount);
    }

    @Test
    void removeMember_bumpsEpochAndPublishesWire() {
        var groupId = manager.createGroup(chatId, List.of(UUID.randomUUID()));
        wirePublisher.resetCounts();
        var removed = UUID.randomUUID();
        assertTrue(manager.removeMember(groupId, removed));
        assertEquals(1, wirePublisher.commitCount);
        assertEquals(1, wirePublisher.epochCount);
        assertEquals(MlsCommitPayload.Action.REMOVE, wirePublisher.lastCommitAction);
    }

    @Test
    void addMember_publishesCommitAndEpoch() {
        var groupId = manager.createGroup(chatId, List.of(UUID.randomUUID()));
        wirePublisher.resetCounts();
        var newMember = UUID.randomUUID();
        assertTrue(manager.addMember(groupId, newMember));
        assertEquals(1, wirePublisher.commitCount);
        assertEquals(1, wirePublisher.epochCount);
        assertEquals(MlsCommitPayload.Action.ADD, wirePublisher.lastCommitAction);
        assertEquals(newMember, wirePublisher.lastCommitMember);
    }

    static final class RecordingWirePublisher extends MlsWirePublisher {
        int welcomeCount;
        int commitCount;
        int epochCount;
        MlsCommitPayload.Action lastCommitAction;
        UUID lastCommitMember;

        RecordingWirePublisher() {
            super(null, null);
        }

        void resetCounts() {
            welcomeCount = 0;
            commitCount = 0;
            epochCount = 0;
        }

        @Override
        public void publishWelcome(MlsGroupState state, List<UUID> memberUserIds, String cipherSuite) {
            welcomeCount++;
        }

        @Override
        public void publishCommit(MlsGroupState state, UUID memberUserId, MlsCommitPayload.Action action) {
            commitCount++;
            lastCommitAction = action;
            lastCommitMember = memberUserId;
        }

        @Override
        public void publishEpoch(MlsGroupState state) {
            epochCount++;
        }
    }

    static final class InMemoryGroupStateRepository extends MlsGroupStateRepository {
        private final java.util.Map<UUID, MlsGroupState> byGroup = new java.util.HashMap<>();

        InMemoryGroupStateRepository() {
            super(null, Clock.systemUTC());
        }

        @Override
        public boolean save(MlsGroupState state) {
            byGroup.put(state.groupId(), state);
            return true;
        }

        @Override
        public Optional<MlsGroupState> findByGroupId(UUID groupId) {
            return Optional.ofNullable(byGroup.get(groupId));
        }

        @Override
        public Optional<MlsGroupState> findByChatId(UUID chatId) {
            return byGroup.values().stream().filter(s -> chatId.equals(s.chatId())).findFirst();
        }

        @Override
        public long countAll() {
            return byGroup.size();
        }
    }

    static final class StubSessionRepository extends SessionRepository {
        private final java.util.Map<UUID, MlsSession> sessions = new java.util.HashMap<>();

        StubSessionRepository() {
            super(null, Clock.systemUTC(), UuidGenerator.standard());
        }

        @Override
        public Optional<MlsSession> findByChatId(UUID chatId, long epochValue) {
            var row = sessions.get(chatId);
            if (row == null || row.epoch() != epochValue) {
                return Optional.empty();
            }
            return Optional.of(row);
        }

        @Override
        public Optional<MlsSession> findLatestByChatId(UUID chatId) {
            return Optional.ofNullable(sessions.get(chatId));
        }

        @Override
        public boolean advanceEpoch(UUID sessionId, byte[] treeHash, byte[] transcriptHash) {
            for (var entry : sessions.entrySet()) {
                if (entry.getValue().id().equals(sessionId)) {
                    var prev = entry.getValue();
                    var next = new MlsSession(
                        prev.id(), prev.chatId(), prev.epoch() + 1, prev.cipherSuite(),
                        treeHash, transcriptHash, prev.groupContext(), prev.createdAt(), Instant.now());
                    sessions.put(entry.getKey(), next);
                    return true;
                }
            }
            return false;
        }

        @Override
        public MlsSession create(UUID chatId, String cipherSuite) {
            var id = UUID.randomUUID();
            var now = Instant.now();
            var row = new MlsSession(id, chatId, 0, cipherSuite, null, null, null, now, now);
            sessions.put(chatId, row);
            return row;
        }
    }
}
