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
        msgRepo.lastInsertVisibilityTtl = null;
        msgRepo.lastInsertReplyTo = null;
        recordingNats.clear();
    }

    @Test
    void sendMessage_rejectsBannedUser() {
        chatRepo.bannedUsers.add(bannedUserId);

        var result = messageService.sendMessage(chatId, bannedUserId,
            new SendMessageRequest("text", "hello", null, null, null, null), null);

        assertNull(result);
    }

    @Test
    void sendMessage_allowsNonBannedUser() {
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null, null), null);

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
            new SendMessageRequest("text", "hello", null, null, null, null), null);

        assertNull(result);
        assertTrue(messageService.sendBlockedReason(chatId, userId).isPresent());
    }

    @Test
    void sendMessage_encryptsWithMls() {
        mlsService.encryptResult = "encrypted_hello";

        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null, null), null);

        assertNotNull(result);
        assertEquals("e2ee-text", result.type());
    }

    @Test
    void sendMessage_passesTtlToRepository() {
        msgRepo.lastInsertVisibilityTtl = null;
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, 120, null), null);

        assertNotNull(result);
        assertEquals(120, msgRepo.lastInsertVisibilityTtl);
        assertEquals(120, result.visibilityTtlSeconds());
    }

    @Test
    void sendMessage_passesReplyToToRepository() {
        var replyId = UUID.randomUUID();
        msgRepo.lastInsertReplyTo = null;
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", replyId.toString(), null, null, null), replyId);

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
    void pinMessage_publishesMsgPinOnNats() throws Exception {
        msgRepo.messages.add(msg(chatId, userId, "pin-me"));
        msgRepo.pinResult = true;

        assertTrue(messageService.pinMessage(chatId, msgId, userId));

        var payload = recordingNats.lastPayload(NatsSubjects.MSG_PIN);
        assertNotNull(payload);
        var ev = MAPPER.readValue(payload, com.avandocmsg.messenger.common.dto.PinChangeEvent.class);
        assertEquals("pin", ev.change());
        assertEquals(chatId.toString(), ev.chatId());
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

    @Test
    void editMessage_skipsIndexPublishWhenIndexerUnavailable() {
        var constrained = new MessageService(
            msgRepo,
            chatRepo,
            blockRepo,
            mlsService,
            recordingNats,
            UuidGenerator.standard(),
            () -> false
        );
        msgRepo.messages.add(msg(chatId, userId, "original"));

        var edited = constrained.editMessage(chatId, msgId, userId, "edited");

        assertNotNull(edited);
        assertNull(recordingNats.lastPayload(NatsSubjects.MSG_EVENT_INDEX));
    }

    private MessageResponse msg(UUID chatId, UUID senderId, String content) {
        return new MessageResponse(msgId.toString(), chatId.toString(), senderId.toString(),
            "text", content, null, false, now, null, null, null);
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
        Integer lastInsertVisibilityTtl;
        UUID lastInsertReplyTo;

        StubMessageRepository() {
            super(null, java.time.Clock.systemUTC());
        }

        @Override
        public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                       UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds) {
            return insert(id, chatId, senderId, type, content, replyToMsgId, clientMsgId, visibilityTtlSeconds, null);
        }

        @Override
        public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                       UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds,
                                       UUID attachmentFileId) {
            lastInsertVisibilityTtl = visibilityTtlSeconds;
            lastInsertReplyTo = replyToMsgId;
            var msg = new MessageResponse(id.toString(), chatId.toString(), senderId.toString(), type, content,
                replyToMsgId != null ? replyToMsgId.toString() : null, false, Instant.now(), null, visibilityTtlSeconds,
                attachmentFileId != null ? attachmentFileId.toString() : null);
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
                m.replyToMsgId(), m.deleted(), m.createdAt(), Instant.now(), m.visibilityTtlSeconds(), m.attachmentFileId()));
            return true;
        }

        @Override
        public boolean delete(UUID msgId) {
            return messages.removeIf(m -> m.id().equals(msgId.toString()));
        }

        boolean pinResult = true;

        @Override
        public boolean pinMessage(UUID chatId, UUID messageId, UUID pinnedBy) {
            return pinResult;
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

        String decryptResult;

        @Override
        public String decryptContentBase64(UUID chatId, String contentBase64) {
            return decryptResult;
        }
    }

    @Test
    void plaintextPreview_returnsDecryptedForE2ee() {
        var msgId = UUID.randomUUID();
        msgRepo.messages.add(new MessageResponse(
            msgId.toString(), chatId.toString(), userId.toString(), "e2ee-text", "cipher", null,
            false, java.time.Instant.now(), null, null, null));
        mlsService.decryptResult = "secret";

        var plain = messageService.plaintextPreview(chatId, msgId, userId);

        assertEquals("secret", plain);
    }

    @Test
    void parseAttachmentFileId_acceptsFileTypesAndE2eePrefix() {
        var fileId = UUID.randomUUID();
        assertEquals(fileId, MessageService.parseAttachmentFileId("file", fileId.toString()));
        assertEquals(fileId, MessageService.parseAttachmentFileId("e2ee-image", " " + fileId + " "));
        assertNull(MessageService.parseAttachmentFileId("text", fileId.toString()));
        assertNull(MessageService.parseAttachmentFileId("file", "not-a-uuid"));
    }

    @Test
    void sendMessage_storesAttachmentFileIdBeforeEncryption() {
        var fileId = UUID.randomUUID();
        mlsService.encryptResult = "encrypted_blob";
        var sent = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("e2ee-file", fileId.toString(), null, null, null, null), null);
        assertNotNull(sent);
        assertEquals(fileId.toString(), sent.attachmentFileId());
        assertEquals(1, msgRepo.messages.size());
        assertEquals(fileId.toString(), msgRepo.messages.get(0).attachmentFileId());
    }

    @Test
    void plaintextPreview_nullForPlainText() {
        var msgId = UUID.randomUUID();
        msgRepo.messages.add(new MessageResponse(
            msgId.toString(), chatId.toString(), userId.toString(), "text", "hello", null,
            false, java.time.Instant.now(), null, null, null));

        assertNull(messageService.plaintextPreview(chatId, msgId, userId));
    }
}
