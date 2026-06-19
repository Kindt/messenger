package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.common.dto.MessageWorkerEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.application.IndexerEventPublisher;
import com.avandocmsg.messenger.core.application.MessageApplicationService;
import com.avandocmsg.messenger.core.application.MessageDeleteCoordinator;
import com.avandocmsg.messenger.core.application.MessageEditCoordinator;
import com.avandocmsg.messenger.core.application.MessagePinCoordinator;
import com.avandocmsg.messenger.core.application.MessageReactionCoordinator;
import com.avandocmsg.messenger.core.application.MessageSendCoordinator;
import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageInsert;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class MessageApplicationServiceWriteTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StubMessageRepository msgRepo = new StubMessageRepository();
    private final StubMessageRepositoryPort msgPort = new StubMessageRepositoryPort(msgRepo);
    private final StubChatPort chatPort = new StubChatPort();
    private final StubBlockPort blockRepo = new StubBlockPort();
    private final StubMlsService mlsService = new StubMlsService();
    private final RecordingNats recordingNats = new RecordingNats();
    private MessageApplicationService messageService;

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
        chatPort.bannedUsers.clear();
        chatPort.p2pChatId = null;
        chatPort.p2pPeerId = null;
        mlsService.encryptResult = null;
        msgRepo.lastInsertVisibilityTtl = null;
        msgRepo.lastInsertReplyTo = null;
        recordingNats.clear();
        messageService = buildApp(() -> true);
    }

    private MessageApplicationService buildApp(BooleanSupplier indexerAvailable) {
        var indexer = new IndexerEventPublisher(recordingNats, indexerAvailable);
        var sendCoord = new MessageSendCoordinator(
            msgPort, chatPort, mlsService, null, recordingNats, UuidGenerator.standard(), null);
        var editCoord = new MessageEditCoordinator(msgPort, indexer);
        var deleteCoord = new MessageDeleteCoordinator(msgPort, recordingNats, indexer);
        var reactionCoord = new MessageReactionCoordinator(msgPort, recordingNats);
        var pinCoord = new MessagePinCoordinator(msgPort, recordingNats);
        return new MessageApplicationService(
            msgPort, chatPort, blockRepo, sendCoord, editCoord, deleteCoord, reactionCoord, pinCoord, msgPort, mlsService);
    }

    @Test
    void sendMessage_rejectsBannedUser() {
        chatPort.bannedUsers.add(bannedUserId);

        var result = messageService.sendMessage(chatId, bannedUserId,
            new SendMessageRequest("text", "hello", null, null, null, null, null, null, null), null);

        assertNull(result);
    }

    @Test
    void sendMessage_allowsNonBannedUser() {
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null, null, null, null, null), null);

        assertNotNull(result);
        assertEquals("text", result.type());
    }

    @Test
    void sendMessage_rejectsP2PWhenMutuallyBlocked() {
        var peer = UUID.randomUUID();
        chatPort.p2pChatId = chatId;
        chatPort.p2pPeerId = peer;
        blockRepo.blockedPairs.add(userId.toString() + ":" + peer);

        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null, null, null, null, null), null);

        assertNull(result);
        assertTrue(messageService.sendBlockedReason(chatId, userId).isPresent());
    }

    @Test
    void sendMessage_encryptsWithMls() {
        mlsService.encryptResult = "encrypted_hello";

        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null, null, null, null, null), null);

        assertNotNull(result);
        assertEquals("e2ee-text", result.type());
    }

    @Test
    void sendMessage_passesTtlToRepository() {
        msgRepo.lastInsertVisibilityTtl = null;
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null, 120, null, null, null), null);

        assertNotNull(result);
        assertEquals(120, msgRepo.lastInsertVisibilityTtl);
        assertEquals(120, result.visibilityTtlSeconds());
    }

    @Test
    void sendMessage_passesReplyToToRepository() {
        var replyId = UUID.randomUUID();
        msgRepo.lastInsertReplyTo = null;
        var result = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("text", "hello", replyId.toString(), null, null, null, null, null, null), replyId);

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
        var constrained = buildApp(() -> false);
        msgRepo.messages.add(msg(chatId, userId, "original"));

        var edited = constrained.editMessage(chatId, msgId, userId, "edited");

        assertNotNull(edited);
        assertNull(recordingNats.lastPayload(NatsSubjects.MSG_EVENT_INDEX));
    }

    @Test
    void plaintextPreview_returnsDecryptedForE2ee() {
        var previewMsgId = UUID.randomUUID();
        msgRepo.messages.add(new MessageResponse(
            previewMsgId.toString(), chatId.toString(), userId.toString(), "e2ee-text", "cipher", null,
            false, Instant.now(), null, null, null));
        mlsService.decryptResult = "secret";

        var plain = messageService.plaintextPreview(chatId, previewMsgId, userId);

        assertEquals("secret", plain);
    }

    @Test
    void sendMessage_storesAttachmentFileIdBeforeEncryption() {
        var fileId = UUID.randomUUID();
        mlsService.encryptResult = "encrypted_blob";
        var sent = messageService.sendMessage(chatId, userId,
            new SendMessageRequest("e2ee-file", fileId.toString(), null, null, null, null, null, null, null), null);
        assertNotNull(sent);
        assertEquals(fileId.toString(), sent.attachmentFileId());
        assertEquals(1, msgRepo.messages.size());
        assertEquals(fileId.toString(), msgRepo.messages.get(0).attachmentFileId());
    }

    @Test
    void plaintextPreview_nullForPlainText() {
        var previewMsgId = UUID.randomUUID();
        msgRepo.messages.add(new MessageResponse(
            previewMsgId.toString(), chatId.toString(), userId.toString(), "text", "hello", null,
            false, Instant.now(), null, null, null));

        assertNull(messageService.plaintextPreview(chatId, previewMsgId, userId));
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

    static final class StubMessageRepositoryPort implements MessageRepositoryPort, MessageQueryPort {
        private final StubMessageRepository legacy;

        StubMessageRepositoryPort(StubMessageRepository legacy) {
            this.legacy = legacy;
        }

        @Override
        public Optional<Message> findById(MessageId id) {
            return legacy.findById(id.value()).map(StubMessageRepositoryPort::toDomain);
        }

        @Override
        public Optional<Message> insert(MessageInsert command) {
            var resp = legacy.insert(
                command.id().value(),
                command.chatId().value(),
                command.senderId().value(),
                command.type(),
                command.content(),
                command.replyToMsgId(),
                command.clientMsgId(),
                command.visibilityTtlSeconds(),
                command.attachmentFileId());
            return Optional.of(toDomain(resp));
        }

        @Override
        public boolean existsClientMsgId(ChatId chatId, UserId senderId, String clientMsgId) {
            return legacy.existsClientMsgId(chatId.value(), senderId.value(), clientMsgId);
        }

        @Override
        public boolean updateContent(MessageId id, UserId senderId, String content) {
            return legacy.updateContent(id.value(), senderId.value(), content);
        }

        @Override
        public boolean softDelete(MessageId id) {
            return legacy.delete(id.value());
        }

        @Override
        public boolean addReaction(MessageId messageId, UserId userId, String reaction) {
            return false;
        }

        @Override
        public boolean removeReaction(MessageId messageId, UserId userId, String reaction) {
            return false;
        }

        @Override
        public boolean pinMessage(ChatId chatId, MessageId messageId, UserId pinnedBy) {
            return legacy.pinMessage(chatId.value(), messageId.value(), pinnedBy.value());
        }

        @Override
        public boolean unpinMessage(ChatId chatId, MessageId messageId) {
            return legacy.unpinMessage(chatId.value(), messageId.value());
        }

        @Override
        public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {
            return legacy.findByChatId(chatId, limit, before, filterUserId, threadId);
        }

        @Override
        public List<MessageVersionResponse> findVersions(UUID msgId) {
            return legacy.findVersions(msgId);
        }

        @Override
        public List<ReactionResponse> getReactions(UUID messageId) {
            return legacy.getReactions(messageId);
        }

        @Override
        public List<PinnedMessageResponse> getPinnedMessages(UUID chatId) {
            return legacy.getPinnedMessages(chatId);
        }

        @Override
        public boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId) {
            return legacy.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId);
        }

        @Override
        public Optional<com.avandocmsg.messenger.core.port.FileMessageRef> findLatestMessageRefForViewer(
            UUID fileId, UUID viewerId) {
            return legacy.findLatestMessageRefForViewer(fileId, viewerId)
                .map(ref -> new com.avandocmsg.messenger.core.port.FileMessageRef(ref.messageId(), ref.chatId()));
        }

        @Override
        public Optional<MessageId> findLatestMessageId(ChatId chatId) {
            return legacy.findLatestMessageId(chatId.value()).map(MessageId::of);
        }

        @Override
        public List<MessageResponse> searchPlaintextForUser(
            UserId userId, List<UUID> chatIds, String queryText, int limit) {
            return legacy.searchPlaintextForUser(userId.value(), chatIds, queryText, limit);
        }

        @Override
        public List<MessageResponse> loadMessagesForSearchResults(
            UserId userId, List<String> messageIdsInOrder, int limit) {
            return legacy.loadMessagesForSearchResults(userId.value(), messageIdsInOrder, limit);
        }

        private static Message toDomain(MessageResponse resp) {
            UUID reply = null;
            if (resp.replyToMsgId() != null && !resp.replyToMsgId().isBlank()) {
                reply = UUID.fromString(resp.replyToMsgId());
            }
            return new Message(
                MessageId.of(UUID.fromString(resp.id())),
                ChatId.of(UUID.fromString(resp.chatId())),
                UserId.of(UUID.fromString(resp.senderId())),
                resp.type(),
                resp.content(),
                reply != null ? reply.toString() : null,
                resp.threadId(),
                resp.deleted(),
                resp.createdAt(),
                resp.editedAt(),
                resp.visibilityTtlSeconds(),
                resp.attachmentFileId());
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
                                       UUID replyToMsgId, UUID threadId, String clientMsgId,
                                       Integer visibilityTtlSeconds, UUID attachmentFileId, Integer voiceDurationMs) {
            return insert(id, chatId, senderId, type, content, replyToMsgId, clientMsgId, visibilityTtlSeconds,
                attachmentFileId);
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
            return findByChatId(chatId, limit, before, filterUserId, null);
        }

        @Override
        public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId,
                                                   UUID threadId) {
            lastFilterUserId = filterUserId;
            return messages.stream()
                .filter(m -> m.chatId().equals(chatId.toString()))
                .filter(m -> threadId == null || threadId.toString().equals(m.threadId())
                    || threadId.toString().equals(m.id()))
                .filter(m -> threadId != null || m.threadId() == null)
                .limit(limit).toList();
        }

        @Override
        public boolean updateContent(UUID msgId, UUID editedBy, String newContent) {
            var msg = findById(msgId);
            if (msg.isEmpty()) {
                return false;
            }
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

    static class StubBlockPort implements com.avandocmsg.messenger.core.port.BlockRepositoryPort {
        final java.util.Set<String> blockedPairs = new java.util.HashSet<>();

        @Override
        public boolean exists(com.avandocmsg.messenger.core.domain.UserId blockerId,
                              com.avandocmsg.messenger.core.domain.UserId blockedId) {
            return blockedPairs.contains(blockerId.value() + ":" + blockedId.value());
        }

        @Override
        public boolean block(com.avandocmsg.messenger.core.domain.UserId blockerId,
                             com.avandocmsg.messenger.core.domain.UserId blockedId) {
            return false;
        }

        @Override
        public boolean unblock(com.avandocmsg.messenger.core.domain.UserId blockerId,
                               com.avandocmsg.messenger.core.domain.UserId blockedId) {
            return false;
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.core.domain.BlockedUser> listBlockedUsers(
            com.avandocmsg.messenger.core.domain.UserId blockerId) {
            return java.util.List.of();
        }
    }

    static class StubChatPort implements ChatRepositoryPort {
        final List<UUID> bannedUsers = new ArrayList<>();
        UUID p2pChatId;
        UUID p2pPeerId;

        @Override
        public Optional<Chat> findById(ChatId chatId) {
            var at = Instant.parse("2026-01-01T00:00:00Z");
            if (p2pChatId != null && p2pChatId.equals(chatId.value())) {
                return Optional.of(new Chat(chatId, "", ChatType.P2P, at));
            }
            return Optional.of(new Chat(chatId, "", ChatType.GROUP, at));
        }

        @Override
        public boolean isMember(ChatId chatId, UserId userId) {
            return memberRole(chatId, userId).isPresent();
        }

        @Override
        public Optional<String> memberRole(ChatId chatId, UserId userId) {
            return Optional.of("member");
        }

        @Override
        public boolean isMemberBanned(ChatId chatId, UserId userId) {
            return bannedUsers.contains(userId.value());
        }

        @Override
        public Optional<UserId> findOtherP2pMember(ChatId chatId, UserId userId) {
            if (p2pChatId != null && p2pChatId.equals(chatId.value()) && p2pPeerId != null) {
                return Optional.of(UserId.of(p2pPeerId));
            }
            return Optional.empty();
        }

        @Override
        public List<UserId> listMemberUserIds(ChatId chatId) {
            return List.of();
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
}
