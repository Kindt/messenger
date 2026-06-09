package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import org.junit.jupiter.api.Test;

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
        chatRepo.roles.put(chatId, "member");
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
        chatRepo.roles.put(chatId, "member");
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
        final java.util.Map<UUID, String> roles = new java.util.HashMap<>();

        StubChatRepository() {
            super(null, java.time.Clock.systemUTC(), com.avandocmsg.messenger.core.port.UuidGenerator.standard());
        }

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            return roles.get(chatId);
        }
    }

    static final class StubMessagePort implements MessageRepositoryPort {
        Message message;

        @Override
        public Optional<Message> findById(MessageId id) {
            return Optional.ofNullable(message);
        }
    }
}
