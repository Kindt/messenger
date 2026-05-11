package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MessageServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StubMessageRepository msgRepo = new StubMessageRepository();
    private final StubChatRepository chatRepo = new StubChatRepository();
    private final StubBlockRepository blockRepo = new StubBlockRepository();
    private final StubMlsService mlsService = new StubMlsService();
    private final RecordingNats recordingNats = new RecordingNats();
    private final MessageService messageService = new MessageService(msgRepo, chatRepo, blockRepo, mlsService,
        recordingNats, UuidGenerator.standard());

    final UUID chatId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID msgId = UUID.randomUUID();
    final UUID bannedUserId = UUID.randomUUID();
    final Instant now = Instant.now();

    @BeforeEach
    void reset() {
        msgRepo.messages.clear();
        msgRepo.lastFilterUserId = null;
        blockRepo.blockedPairs.clear();
        chatRepo.bannedUsers.clear();
        chatRepo.p2pChatId = null;
        chatRepo.p2pPeerId = null;
        mlsService.encryptResult = null;
        msgRepo.lastInsertTtl = null;
        msgRepo.lastInsertReplyTo = null;
        recordingNats.clear();
    }

    @Test
    void sendMessage_rejectsBannedUser() {
        chatRepo.bannedUsers.add(bannedUserId);

        var result = messageService.sendMessage(chatId, bannedUserId,
            new SendMessageRequest("text", "hello", null, null, null), null);

        assertNull(result);
    }

    @Test
    void sendMessage_allowsNonBannedUser() {
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null), null);

        assertNotNull(result);
        assertEquals("text", result.type());
    }

    @Test
    void sendMessage_rejectsP2PWhenMutuallyBlocked() {
        var peer = UUID.randomUUID();
        chatRepo.p2pChatId = chatId;
        chatRepo.p2pPeerId = peer;
        blockRepo.blockedPairs.add(userId.toString() + ":" + peer);

        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null), null);

        assertNull(result);
        assertTrue(messageService.sendBlockedReason(chatId, userId).isPresent());
    }

    @Test
    void sendMessage_encryptsWithMls() {
        mlsService.encryptResult = "encrypted_hello";

        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null), null);

        assertNotNull(result);
        assertEquals("e2ee-text", result.type());
    }

    @Test
    void sendMessage_passesTtlToRepository() {
        msgRepo.lastInsertTtl = null;
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, 120), null);

        assertNotNull(result);
        assertEquals(120, msgRepo.lastInsertTtl);
        assertEquals(120, result.ttlSeconds());
    }

    @Test
    void sendMessage_passesReplyToToRepository() {
        var replyId = UUID.randomUUID();
        msgRepo.lastInsertReplyTo = null;
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", replyId.toString(), null, null), replyId);

        assertNotNull(result);
        assertEquals(replyId, msgRepo.lastInsertReplyTo);
        assertEquals(replyId.toString(), result.replyToMsgId());
    }

    @Test
    void listMessages_alwaysPassesViewerForBlockFilter() {
        msgRepo.messages.add(msg(chatId, userId, "msg1"));

        messageService.listMessages(chatId, userId, 50, null);

        assertEquals(userId, msgRepo.lastFilterUserId);
    }

    @Test
    void editMessage_deniedForNonSender() {
        var otherUser = UUID.randomUUID();
        msgRepo.messages.add(msg(chatId, userId, "original"));

        var result = messageService.editMessage(chatId, msgId, otherUser, "edited");

        assertNull(result);
    }

    @Test
    void editMessage_allowedForSender() {
        msgRepo.messages.add(msg(chatId, userId, "original"));

        var result = messageService.editMessage(chatId, msgId, userId, "edited");

        assertNotNull(result);
        assertEquals("edited", result.content());
    }

    @Test
    void editMessage_publishesSolrIndexUpdateOnNats() throws Exception {
        msgRepo.messages.add(msg(chatId, userId, "original"));

        messageService.editMessage(chatId, msgId, userId, "edited");

        var payload = recordingNats.lastPayload(NatsSubjects.MSG_EVENT_INDEX);
        assertNotNull(payload);
        var ev = MAPPER.readValue(payload, MessageWorkerEvent.class);
        assertEquals("update", ev.indexOp());
        assertEquals("edited", ev.searchText());
        assertEquals(msgId.toString(), ev.messageId());
    }

    @Test
    void deleteMessage_deniedForNonSender() {
        var otherUser = UUID.randomUUID();
        msgRepo.messages.add(msg(chatId, userId, "msg"));

        assertFalse(messageService.deleteMessage(chatId, msgId, otherUser));
    }

    @Test
    void deleteMessage_allowedForSender() {
        msgRepo.messages.add(msg(chatId, userId, "msg"));

        assertTrue(messageService.deleteMessage(chatId, msgId, userId));
    }

    @Test
    void deleteMessage_publishesSolrIndexDeleteOnNats() throws Exception {
        msgRepo.messages.add(msg(chatId, userId, "msg"));

        assertTrue(messageService.deleteMessage(chatId, msgId, userId));

        var payload = recordingNats.lastPayload(NatsSubjects.MSG_EVENT_INDEX);
        assertNotNull(payload);
        var ev = MAPPER.readValue(payload, MessageWorkerEvent.class);
        assertEquals("delete", ev.indexOp());
        assertEquals(msgId.toString(), ev.messageId());
    }

    private MessageResponse msg(UUID chatId, UUID senderId, String content) {
        return new MessageResponse(msgId.toString(), chatId.toString(), senderId.toString(),
            "text", content, null, false, now, null, null);
    }

    static final class RecordingNats implements NatsOutboundPort {
        private final List<Published> publishes = new ArrayList<>();

        void clear() {
            publishes.clear();
        }

        byte[] lastPayload(String subject) {
            for (int i = publishes.size() - 1; i >= 0; i--) {
                var p = publishes.get(i);
                if (subject.equals(p.subject())) {
                    return p.payload();
                }
            }
            return null;
        }

        @Override
        public void publish(String subject, byte[] payload) {
            publishes.add(new Published(subject, payload != null ? payload.clone() : null));
        }

        @Override
        public void flush(Duration timeout) {
        }

        @Override
        public void publishPipelineMessageSend(byte[] payload) {
        }

        record Published(String subject, byte[] payload) {
        }
    }

    static class StubMessageRepository extends com.avandocmsg.messenger.api.repository.MessageRepository {
        final List<MessageResponse> messages = new ArrayList<>();
        UUID lastFilterUserId;
        Integer lastInsertTtl;
        UUID lastInsertReplyTo;

        StubMessageRepository() {
            super(null, java.time.Clock.systemUTC());
        }

        @Override
        public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                       UUID replyToMsgId, String clientMsgId, Integer ttlSeconds) {
            lastInsertTtl = ttlSeconds;
            lastInsertReplyTo = replyToMsgId;
            var msg = new MessageResponse(id.toString(), chatId.toString(), senderId.toString(), type, content,
                replyToMsgId != null ? replyToMsgId.toString() : null, false, Instant.now(), null, ttlSeconds);
            messages.add(msg);
            return msg;
        }

        @Override
        public Optional<MessageResponse> findById(UUID id) {
            return messages.stream().filter(m -> m.id().equals(id.toString())).findFirst();
        }

        @Override
        public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before) {
            return findByChatId(chatId, limit, before, null);
        }

        @Override
        public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId) {
            lastFilterUserId = filterUserId;
            return messages.stream()
                .filter(m -> m.chatId().equals(chatId.toString()))
                .limit(limit).toList();
        }

        @Override
        public boolean updateContent(UUID msgId, UUID editedBy, String newContent) {
            var msg = findById(msgId);
            if (msg.isEmpty()) return false;
            var m = msg.get();
            messages.remove(m);
            messages.add(new MessageResponse(m.id(), m.chatId(), m.senderId(), m.type(), newContent,
                m.replyToMsgId(), m.deleted(), m.createdAt(), Instant.now(), m.ttlSeconds()));
            return true;
        }

        @Override
        public boolean delete(UUID msgId) {
            return messages.removeIf(m -> m.id().equals(msgId.toString()));
        }
    }

    static class StubBlockRepository extends com.avandocmsg.messenger.api.repository.BlockRepository {
        final java.util.Set<String> blockedPairs = new java.util.HashSet<>();

        StubBlockRepository() {
            super(null);
        }

        @Override
        public boolean exists(UUID blockerId, UUID blockedId) {
            return blockedPairs.contains(blockerId + ":" + blockedId);
        }
    }

    static class StubChatRepository extends com.avandocmsg.messenger.api.repository.ChatRepository {
        final List<UUID> bannedUsers = new ArrayList<>();
        UUID p2pChatId;
        UUID p2pPeerId;

        StubChatRepository() {
            super(null, java.time.Clock.systemUTC(), com.avandocmsg.messenger.core.port.UuidGenerator.standard());
        }

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return "member";
        }

        @Override
        public boolean isMemberBanned(UUID chatId, UUID userId) {
            return bannedUsers.contains(userId);
        }

        @Override
        public java.util.Optional<String> getChatType(UUID chatId) {
            if (p2pChatId != null && p2pChatId.equals(chatId)) {
                return java.util.Optional.of("p2p");
            }
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId) {
            if (p2pChatId != null && p2pChatId.equals(chatId) && p2pPeerId != null) {
                return java.util.Optional.of(p2pPeerId);
            }
            return java.util.Optional.empty();
        }
    }

    static class StubMlsService extends com.avandocmsg.messenger.api.mls.MlsService {
        String encryptResult;

        StubMlsService() {
            super(null, null);
        }

        @Override
        public com.avandocmsg.messenger.api.mls.dto.EncryptedMessage encrypt(UUID chatId, UUID senderId, String plaintext) {
            if (encryptResult != null) {
                return new com.avandocmsg.messenger.api.mls.dto.EncryptedMessage(
                    encryptResult.getBytes(), UUID.randomUUID().toString(), 0);
            }
            return null;
        }
    }
}
