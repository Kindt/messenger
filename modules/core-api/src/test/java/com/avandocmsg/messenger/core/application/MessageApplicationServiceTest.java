package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.Chat;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.ChatType;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.port.MessageQueryPort;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageApplicationServiceTest {

    private final UUID chatId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();

    private final StubChatPort chatPort = new StubChatPort();
    private final StubMessagePort messagePort = new StubMessagePort();
    private final MessageApplicationService service = new MessageApplicationService(messagePort, chatPort);

    @Test
    void canAccessChat_deniesNonMemberAndBanned() {
        chatPort.put(chatId, memberId, "member");
        assertFalse(service.canAccessChat(chatId, outsiderId));
        assertTrue(service.canAccessChat(chatId, memberId));
        chatPort.banned.add(chatId + ":" + memberId);
        assertFalse(service.canAccessChat(chatId, memberId));
    }

    @Test
    void listMessages_returnsEmptyWhenNotMember() {
        var legacyRepo = new ReadStubMessageRepository();
        var queryPort = new DelegatingMessageQueryPort(legacyRepo);
        var readService = new MessageApplicationService(messagePort, chatPort, null, null, null, null, null, null, queryPort, null);
        assertTrue(readService.listMessages(chatId, outsiderId, 50, null).isEmpty());
        assertFalse(legacyRepo.listCalled);
    }

    @Test
    void listMessages_delegatesWhenMember() {
        chatPort.put(chatId, memberId, "member");
        var legacyRepo = new ReadStubMessageRepository();
        var queryPort = new DelegatingMessageQueryPort(legacyRepo);
        var readService = new MessageApplicationService(messagePort, chatPort, null, null, null, null, null, null, queryPort, null);
        readService.listMessages(chatId, memberId, 25, null);
        assertTrue(legacyRepo.listCalled);
        assertEquals(25, legacyRepo.lastLimit);
    }

    @Test
    void getPinnedMessages_delegatesWhenMember() {
        chatPort.put(chatId, memberId, "member");
        var legacyRepo = new ReadStubMessageRepository();
        var queryPort = new DelegatingMessageQueryPort(legacyRepo);
        var readService = new MessageApplicationService(messagePort, chatPort, null, null, null, null, null, null, queryPort, null);
        readService.getPinnedMessages(chatId, memberId);
        assertTrue(legacyRepo.pinnedCalled);
    }

    @Test
    void getReactions_requiresVisibleMessage() {
        chatPort.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var legacyRepo = new ReadStubMessageRepository();
        var queryPort = new DelegatingMessageQueryPort(legacyRepo);
        var readService = new MessageApplicationService(messagePort, chatPort, null, null, null, null, null, null, queryPort, null);
        readService.getReactions(chatId, messageId, memberId);
        assertTrue(legacyRepo.reactionsCalled);
    }

    @Test
    void getMessageVersions_requiresVisibleMessage() {
        chatPort.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var legacyRepo = new ReadStubMessageRepository();
        var queryPort = new DelegatingMessageQueryPort(legacyRepo);
        var readService = new MessageApplicationService(messagePort, chatPort, null, null, null, null, null, null, queryPort, null);
        readService.getMessageVersions(chatId, messageId, memberId);
        assertTrue(legacyRepo.versionsCalled);
    }

    @Test
    void getMessageForMember_returnsMessageForMember() {
        chatPort.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();

        var result = service.getMessageForMember(ChatId.of(chatId), MessageId.of(messageId), UserId.of(memberId));
        assertTrue(result.isPresent());
        assertEquals(messageId, result.get().id().value());
    }

    @Test
    void getMessageForMember_deniesNonMember() {
        messagePort.message = sampleMessage();

        assertTrue(service.getMessageForMember(ChatId.of(chatId), MessageId.of(messageId), UserId.of(outsiderId)).isEmpty());
    }

    @Test
    void getMessageForMember_rejectsWrongChat() {
        chatPort.put(chatId, memberId, "member");
        messagePort.message = new Message(
            MessageId.of(messageId),
            ChatId.of(UUID.randomUUID()),
            UserId.of(memberId),
            "text",
            "hi",
            null,
            null,
            false,
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            null,
            null);

        assertTrue(service.getMessageForMember(ChatId.of(chatId), MessageId.of(messageId), UserId.of(memberId)).isEmpty());
    }

    @Test
    void isChatMember_reflectsRepositoryRole() {
        chatPort.put(chatId, memberId, "member");
        assertTrue(service.isChatMember(ChatId.of(chatId), UserId.of(memberId)));
        assertFalse(service.isChatMember(ChatId.of(chatId), UserId.of(outsiderId)));
    }

    @Test
    void sendBlockedReason_deniesNonMember() {
        assertEquals(
            Optional.of("error.message.send_denied.not_member"),
            service.sendBlockedReason(chatId, outsiderId));
    }

    @Test
    void sendBlockedReason_deniesBannedMember() {
        chatPort.put(chatId, memberId, "member");
        chatPort.banned.add(chatId + ":" + memberId);
        var serviceWithBan = new MessageApplicationService(messagePort, chatPort);
        assertEquals(
            Optional.of("error.message.send_denied.banned"),
            serviceWithBan.sendBlockedReason(chatId, memberId));
    }

    @Test
    void editMessage_updatesWhenSender() {
        chatPort.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var editCoordinator = new MessageEditCoordinator(messagePort, NatsOutboundPort.noop());
        var editService = new MessageApplicationService(messagePort, chatPort, null, null, editCoordinator);
        var edited = editService.editMessage(chatId, messageId, memberId, "updated");
        assertNotNull(edited);
        assertEquals("updated", edited.content());
        assertTrue(messagePort.updated);
    }

    @Test
    void deleteMessage_softDeletesWhenSender() {
        chatPort.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var deleteCoordinator = new MessageDeleteCoordinator(messagePort, NatsOutboundPort.noop());
        var svc = new MessageApplicationService(messagePort, chatPort, null, null, null, deleteCoordinator, null);
        assertTrue(svc.deleteMessage(chatId, messageId, memberId));
        assertTrue(messagePort.deleted);
    }

    @Test
    void addReaction_publishesWhenMember() {
        chatPort.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var reactionCoordinator = new MessageReactionCoordinator(messagePort, NatsOutboundPort.noop());
        var svc = new MessageApplicationService(messagePort, chatPort, null, null, null, null, reactionCoordinator);
        assertTrue(svc.addReaction(chatId, messageId, memberId, "👍"));
        assertTrue(messagePort.reactionAdded);
    }

    @Test
    void pinMessage_delegatesToCoordinatorWhenMember() {
        chatPort.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        messagePort.pinned = false;
        var pinCoordinator = new MessagePinCoordinator(messagePort, NatsOutboundPort.noop());
        var svc = new MessageApplicationService(messagePort, chatPort, null, null, null, null, null, pinCoordinator);
        assertTrue(svc.pinMessage(chatId, messageId, memberId));
        assertTrue(messagePort.pinned);
    }

    private Message sampleMessage() {
        return new Message(
            MessageId.of(messageId),
            ChatId.of(chatId),
            UserId.of(memberId),
            "text",
            "hello",
            null,
            null,
            false,
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            null,
            null);
    }

    static final class StubChatPort implements ChatRepositoryPort {
        final java.util.Map<String, String> roles = new java.util.HashMap<>();
        final java.util.Map<UUID, ChatType> types = new java.util.HashMap<>();
        final java.util.Set<String> banned = new java.util.HashSet<>();

        void put(UUID chatId, UUID userId, String role) {
            roles.put(chatId + ":" + userId, role);
        }

        @Override
        public Optional<Chat> findById(ChatId id) {
            var type = types.getOrDefault(id.value(), ChatType.GROUP);
            return Optional.of(new Chat(id, "", type, Instant.parse("2026-01-01T00:00:00Z")));
        }

        @Override
        public boolean isMember(ChatId chatId, UserId userId) {
            return memberRole(chatId, userId).isPresent();
        }

        @Override
        public Optional<String> memberRole(ChatId chatId, UserId userId) {
            return Optional.ofNullable(roles.get(chatId.value() + ":" + userId.value()));
        }

        @Override
        public boolean isMemberBanned(ChatId chatId, UserId userId) {
            return banned.contains(chatId.value() + ":" + userId.value());
        }

        @Override
        public Optional<UserId> findOtherP2pMember(ChatId chatId, UserId userId) {
            return Optional.empty();
        }

        @Override
        public List<UserId> listMemberUserIds(ChatId chatId) {
            return List.of();
        }
    }

    static final class StubMessagePort implements MessageRepositoryPort {
        Message message;

        @Override
        public Optional<Message> findById(MessageId id) {
            return Optional.ofNullable(message);
        }

        @Override
        public Optional<Message> insert(com.avandocmsg.messenger.core.port.MessageInsert command) {
            return Optional.empty();
        }

        @Override
        public boolean existsClientMsgId(ChatId chatId, UserId senderId, String clientMsgId) {
            return false;
        }

        boolean updated;

        @Override
        public boolean updateContent(MessageId id, UserId senderId, String content) {
            updated = true;
            if (message != null) {
                message = new Message(
                    message.id(), message.chatId(), message.senderId(), message.type(), content,
                    message.replyToMessageId(), message.threadId(), message.deleted(), message.createdAt(),
                    Instant.parse("2026-01-02T00:00:00Z"), message.visibilityTtlSeconds(), message.attachmentFileId());
            }
            return true;
        }

        boolean deleted;

        @Override
        public boolean softDelete(MessageId id) {
            deleted = true;
            return true;
        }

        boolean reactionAdded;

        boolean pinned;

        @Override
        public boolean addReaction(MessageId messageId, UserId userId, String reaction) {
            reactionAdded = true;
            return true;
        }

        @Override
        public boolean removeReaction(MessageId messageId, UserId userId, String reaction) {
            return true;
        }

        @Override
        public boolean pinMessage(ChatId chatId, MessageId messageId, UserId pinnedBy) {
            pinned = true;
            return true;
        }

        @Override
        public boolean unpinMessage(ChatId chatId, MessageId messageId) {
            return true;
        }
    }

    static final class ReadStubMessageRepository extends MessageRepository {
        boolean listCalled;
        int lastLimit;
        boolean pinnedCalled;
        boolean reactionsCalled;
        boolean versionsCalled;

        ReadStubMessageRepository() {
            super(null, Clock.systemUTC());
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> findByChatId(
            UUID chatId, int limit, UUID before, UUID filterUserId) {
            return findByChatId(chatId, limit, before, filterUserId, null);
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> findByChatId(
            UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {
            listCalled = true;
            lastLimit = limit;
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse> getPinnedMessages(UUID chatId) {
            pinnedCalled = true;
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.ReactionResponse> getReactions(UUID messageId) {
            reactionsCalled = true;
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse> findVersions(UUID msgId) {
            versionsCalled = true;
            return java.util.List.of();
        }
    }

    static final class DelegatingMessageQueryPort implements MessageQueryPort {
        private final MessageRepository repo;

        DelegatingMessageQueryPort(MessageRepository repo) {
            this.repo = repo;
        }

        @Override
        public List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> findByChatId(
            UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {
            return repo.findByChatId(chatId, limit, before, filterUserId, threadId);
        }

        @Override
        public List<com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse> findVersions(UUID msgId) {
            return repo.findVersions(msgId);
        }

        @Override
        public List<com.avandocmsg.messenger.api.messages.dto.ReactionResponse> getReactions(UUID messageId) {
            return repo.getReactions(messageId);
        }

        @Override
        public List<com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse> getPinnedMessages(UUID chatId) {
            return repo.getPinnedMessages(chatId);
        }

        @Override
        public boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId) {
            return repo.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId);
        }

        @Override
        public Optional<com.avandocmsg.messenger.core.port.FileMessageRef> findLatestMessageRefForViewer(
            UUID fileId, UUID viewerId) {
            return repo.findLatestMessageRefForViewer(fileId, viewerId)
                .map(ref -> new com.avandocmsg.messenger.core.port.FileMessageRef(ref.messageId(), ref.chatId()));
        }

        @Override
        public Optional<MessageId> findLatestMessageId(ChatId chatId) {
            return repo.findLatestMessageId(chatId.value()).map(MessageId::of);
        }

        @Override
        public List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> searchPlaintextForUser(
            UserId userId, List<UUID> chatIds, String queryText, int limit) {
            return repo.searchPlaintextForUser(userId.value(), chatIds, queryText, limit);
        }

        @Override
        public List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> loadMessagesForSearchResults(
            UserId userId, List<String> messageIdsInOrder, int limit) {
            return repo.loadMessagesForSearchResults(userId.value(), messageIdsInOrder, limit);
        }
    }
}
