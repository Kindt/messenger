package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageApplicationServiceTest {

    private final UUID chatId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();

    private final StubChatRepository chatRepo = new StubChatRepository();
    private final StubMessagePort messagePort = new StubMessagePort();
    private final MessageApplicationService service = new MessageApplicationService(messagePort, chatRepo);

    @Test
    void getMessageForMember_returnsMessageForMember() {
        chatRepo.put(chatId, memberId, "member");
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
        chatRepo.put(chatId, memberId, "member");
        messagePort.message = new Message(
            MessageId.of(messageId),
            ChatId.of(UUID.randomUUID()),
            UserId.of(memberId),
            "text",
            "hi",
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
        chatRepo.put(chatId, memberId, "member");
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
        chatRepo.put(chatId, memberId, "member");
        chatRepo.banned.add(chatId + ":" + memberId);
        var serviceWithBan = new MessageApplicationService(messagePort, chatRepo);
        assertEquals(
            Optional.of("error.message.send_denied.banned"),
            serviceWithBan.sendBlockedReason(chatId, memberId));
    }

    @Test
    void editMessage_updatesWhenSender() {
        chatRepo.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var editCoordinator = new MessageEditCoordinator(messagePort, NatsOutboundPort.noop());
        var editService = new MessageApplicationService(messagePort, chatRepo, null, null, editCoordinator);
        var edited = editService.editMessage(chatId, messageId, memberId, "updated");
        assertNotNull(edited);
        assertEquals("updated", edited.content());
        assertTrue(messagePort.updated);
    }

    @Test
    void deleteMessage_softDeletesWhenSender() {
        chatRepo.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var deleteCoordinator = new MessageDeleteCoordinator(messagePort, NatsOutboundPort.noop());
        var svc = new MessageApplicationService(messagePort, chatRepo, null, null, null, deleteCoordinator, null);
        assertTrue(svc.deleteMessage(chatId, messageId, memberId));
        assertTrue(messagePort.deleted);
    }

    @Test
    void addReaction_publishesWhenMember() {
        chatRepo.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var reactionCoordinator = new MessageReactionCoordinator(messagePort, NatsOutboundPort.noop());
        var svc = new MessageApplicationService(messagePort, chatRepo, null, null, null, null, reactionCoordinator);
        assertTrue(svc.addReaction(chatId, messageId, memberId, "👍"));
        assertTrue(messagePort.reactionAdded);
    }

    @Test
    void pinMessage_delegatesToCoordinatorWhenMember() {
        chatRepo.put(chatId, memberId, "member");
        messagePort.message = sampleMessage();
        var pinRepo = new PinStubMessageRepository();
        var pinCoordinator = new MessagePinCoordinator(pinRepo, NatsOutboundPort.noop());
        var svc = new MessageApplicationService(messagePort, chatRepo, null, null, null, null, null, pinCoordinator);
        assertTrue(svc.pinMessage(chatId, messageId, memberId));
        assertTrue(pinRepo.pinned);
    }

    private Message sampleMessage() {
        return new Message(
            MessageId.of(messageId),
            ChatId.of(chatId),
            UserId.of(memberId),
            "text",
            "hello",
            null,
            false,
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            null,
            null);
    }

    static final class StubChatRepository extends ChatRepository {
        final java.util.Map<String, String> roles = new java.util.HashMap<>();

        StubChatRepository() {
            super(null, java.time.Clock.systemUTC(), com.avandocmsg.messenger.core.port.UuidGenerator.standard());
        }

        void put(UUID chatId, UUID userId, String role) {
            roles.put(chatId + ":" + userId, role);
        }

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return roles.get(chatId + ":" + userId);
        }

        final java.util.Set<String> banned = new java.util.HashSet<>();

        @Override
        public boolean isMemberBanned(UUID chatId, UUID userId) {
            return banned.contains(chatId + ":" + userId);
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

        boolean updated;

        @Override
        public boolean updateContent(MessageId id, UserId senderId, String content) {
            updated = true;
            if (message != null) {
                message = new Message(
                    message.id(), message.chatId(), message.senderId(), message.type(), content,
                    message.replyToMessageId(), message.deleted(), message.createdAt(),
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

        @Override
        public boolean addReaction(MessageId messageId, UserId userId, String reaction) {
            reactionAdded = true;
            return true;
        }

        @Override
        public boolean removeReaction(MessageId messageId, UserId userId, String reaction) {
            return true;
        }
    }

    static final class PinStubMessageRepository extends MessageRepository {
        boolean pinned;

        PinStubMessageRepository() {
            super(null, Clock.systemUTC());
        }

        @Override
        public boolean pinMessage(UUID chatId, UUID messageId, UUID pinnedBy) {
            pinned = true;
            return true;
        }
    }
}
