package com.avandocmsg.messenger.api.mls.openmls;

import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.crypto.E2EEService;
import com.avandocmsg.messenger.api.mls.MlsGroupManager;
import com.avandocmsg.messenger.api.mls.MlsGroupState;
import com.avandocmsg.messenger.api.mls.MlsGroupStateRepository;
import com.avandocmsg.messenger.api.mls.MlsMigrationService;
import com.avandocmsg.messenger.api.mls.MlsService;
import com.avandocmsg.messenger.api.mls.SessionRepository.MlsSession;
import com.avandocmsg.messenger.api.mls.SessionRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.application.MessageSendSupport;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OpenMlsInteropTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MlsService mlsService;
    private MlsGroupManager groupManager;
    private UUID chatId;

    @BeforeEach
    void setUp() {
        chatId = UUID.randomUUID();
        var sessionRepository = new InMemorySessionRepository();
        mlsService = new MlsService(sessionRepository, new E2EEService());
        var clock = Clock.fixed(Instant.parse("2026-06-18T12:00:00Z"), ZoneOffset.UTC);
        groupManager = new MlsGroupManager(
            new InMemoryGroupStateRepository(),
            mlsService,
            UuidGenerator.standard(),
            clock);
    }

    @Test
    void vectors_encryptDecryptRoundTrip() throws Exception {
        var vectors = loadVectors();
        assertFalse(vectors.vectors().isEmpty());
        assertEquals(OpenMlsWireLayout.DEFAULT_CIPHER_SUITE, vectors.cipher_suite_default());

        for (var vector : vectors.vectors()) {
            var localChatId = UUID.randomUUID();
            var groupId = groupManager.createGroup(localChatId, List.of(UUID.randomUUID(), UUID.randomUUID()));
            assertNotNull(groupId, vector.id());

            var senderId = UUID.randomUUID();
            var plaintext = vector.plaintext();
            var encrypted = groupManager.encrypt(groupId, senderId, plaintext);
            assertNotNull(encrypted, vector.id());

            var combined = concat(
                Base64.getDecoder().decode(encrypted.nonceBase64()),
                Base64.getDecoder().decode(encrypted.ciphertextBase64()));
            var combinedB64 = Base64.getEncoder().encodeToString(combined);

            var decrypted = mlsService.decryptContentBase64(localChatId, combinedB64);
            assertEquals(plaintext, decrypted, vector.id());

            if (vector.message_type() != null && vector.message_type().startsWith("e2ee-file")) {
                var fileId = MessageSendSupport.parseAttachmentFileId(vector.message_type(), vector.plaintext());
                assertNotNull(fileId, vector.id());
            }
        }
    }

    @Test
    void wireLayout_validatesCombinedCiphertext() {
        var encrypted = mlsService.encrypt(chatId, UUID.randomUUID(), "layout-check");
        assertNotNull(encrypted);
        var combined = concat(
            Base64.getDecoder().decode(encrypted.nonceBase64()),
            Base64.getDecoder().decode(encrypted.ciphertextBase64()));
        assertTrue(OpenMlsWireLayout.isValidCombined(combined));
    }

    @Test
    void migrateToOpenMlsGroup_isIdempotent() {
        var member = UUID.randomUUID();
        var chatRepo = new StubChatRepository();
        chatRepo.members.put(chatId, List.of(new ChatMemberResponse(
            member.toString(), "alice", "Alice", "member", false, false, Instant.now())));
        var migration = new MlsMigrationService(null, groupManager, chatRepo);

        var first = migration.migrateToOpenMlsGroup(chatId).orElseThrow();
        var second = migration.migrateToOpenMlsGroup(chatId).orElseThrow();
        assertEquals(first, second);
        assertEquals(1, groupManager.groupCount());
    }

    private static VectorsFile loadVectors() throws Exception {
        try (InputStream in = OpenMlsInteropTest.class.getClassLoader().getResourceAsStream("openmls-vectors.json")) {
            assertNotNull(in, "openmls-vectors.json");
            return MAPPER.readValue(in, VectorsFile.class);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        var out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    record VectorsFile(
        String schema_version,
        String cipher_suite_default,
        List<VectorCase> vectors
    ) {
    }

    record VectorCase(
        String id,
        int group_size,
        String message_type,
        String plaintext,
        String attachment_file_id,
        String notes
    ) {
    }

    static final class InMemoryGroupStateRepository extends MlsGroupStateRepository {
        private final Map<UUID, MlsGroupState> byGroup = new HashMap<>();

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

    static final class InMemorySessionRepository extends SessionRepository {
        private final Map<UUID, MlsSession> sessions = new HashMap<>();

        InMemorySessionRepository() {
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

    static final class StubChatRepository extends ChatRepository {
        final Map<UUID, List<ChatMemberResponse>> members = new HashMap<>();

        StubChatRepository() {
            super(null, Clock.systemUTC(), UuidGenerator.standard());
        }

        @Override
        public List<ChatMemberResponse> listMembers(UUID chatId) {
            return members.getOrDefault(chatId, List.of());
        }
    }
}
