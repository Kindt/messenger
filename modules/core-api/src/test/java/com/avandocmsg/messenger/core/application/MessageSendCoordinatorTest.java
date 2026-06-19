package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageInsert;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.ChatRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageSendCoordinatorTest {

    private final StubMessagePort msgPort = new StubMessagePort();
    private final StubChatPort chatPort = new StubChatPort();
    private final RecordingNats nats = new RecordingNats();
    private final StubMlsService mlsService = new StubMlsService();
    private final MessageSendCoordinator coordinator = new MessageSendCoordinator(
        msgPort,
        chatPort,
        mlsService,
        null,
        nats,
        UuidGenerator.standard(),
        null);

    final UUID chatId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void reset() {
        msgPort.messages.clear();
        nats.pipelinePayloads.clear();
    }

    @Test
    void send_persistsAndPublishesPipelineEvent() {
        var result = coordinator.send(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null, null, null, null, null), null);

        assertNotNull(result);
        assertEquals("hello", result.content());
        assertEquals(1, msgPort.messages.size());
        assertEquals(1, nats.pipelinePayloads.size());
    }

    @Test
    void forward_persistsCopyAndPublishesPipelineEvent() {
        var sourceMsg = coordinator.send(chatId, userId,
            new SendMessageRequest("text", "original", null, null, null, null, null, null, null), null);
        assertNotNull(sourceMsg);
        nats.pipelinePayloads.clear();

        var forwarded = coordinator.forward(chatId, UUID.fromString(sourceMsg.id()), userId, UUID.randomUUID());
        assertNotNull(forwarded);
        assertEquals("original", forwarded.content());
        assertEquals(2, msgPort.messages.size());
        assertEquals(1, nats.pipelinePayloads.size());
    }

    static final class RecordingNats implements NatsOutboundPort {
        final List<byte[]> pipelinePayloads = new ArrayList<>();

        @Override
        public void publish(String subject, byte[] payload) {
        }

        @Override
        public void flush(java.time.Duration timeout) {
        }

        @Override
        public void publishPipelineMessageSend(byte[] payload) {
            pipelinePayloads.add(payload != null ? payload.clone() : null);
        }
    }

    static class StubMlsService extends com.avandocmsg.messenger.api.mls.MlsService {
        StubMlsService() {
            super(null, null);
        }

        @Override
        public com.avandocmsg.messenger.api.mls.dto.EncryptedMessage encrypt(
            UUID chatId, UUID senderId, String plaintext) {
            return null;
        }
    }

    static class StubMessagePort implements MessageRepositoryPort {
        final List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> messages = new ArrayList<>();

        @Override
        public Optional<Message> findById(MessageId id) {
            return messages.stream()
                .filter(m -> m.id().equals(id.value().toString()))
                .findFirst()
                .map(m -> new Message(
                    id,
                    ChatId.of(UUID.fromString(m.chatId())),
                    UserId.of(UUID.fromString(m.senderId())),
                    m.type(),
                    m.content(),
                    m.replyToMsgId(),
                    m.threadId(),
                    m.deleted(),
                    m.createdAt(),
                    m.editedAt(),
                    m.visibilityTtlSeconds(),
                    m.attachmentFileId()));
        }

        @Override
        public Optional<Message> insert(MessageInsert command) {
            var msg = new com.avandocmsg.messenger.api.messages.dto.MessageResponse(
                command.id().value().toString(),
                command.chatId().value().toString(),
                command.senderId().value().toString(),
                command.type(),
                command.content(),
                command.replyToMsgId() != null ? command.replyToMsgId().toString() : null,
                false,
                Instant.now(),
                null,
                command.visibilityTtlSeconds(),
                command.attachmentFileId() != null ? command.attachmentFileId().toString() : null,
                command.threadId() != null ? command.threadId().toString() : null,
                null,
                null,
                null,
                command.voiceDurationMs(),
                null,
                null);
            messages.add(msg);
            return Optional.of(new Message(
                command.id(),
                command.chatId(),
                command.senderId(),
                command.type(),
                command.content(),
                command.replyToMsgId() != null ? command.replyToMsgId().toString() : null,
                command.threadId() != null ? command.threadId().toString() : null,
                false,
                msg.createdAt(),
                null,
                command.visibilityTtlSeconds(),
                command.attachmentFileId() != null ? command.attachmentFileId().toString() : null));
        }

        @Override
        public boolean existsClientMsgId(ChatId chatId, UserId senderId, String clientMsgId) {
            return false;
        }

        @Override
        public boolean updateContent(MessageId id, UserId senderId, String content) {
            return false;
        }

        @Override
        public boolean softDelete(MessageId id) {
            return false;
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
            return false;
        }

        @Override
        public boolean unpinMessage(ChatId chatId, MessageId messageId) {
            return false;
        }
    }

    static class StubChatPort implements ChatRepositoryPort {
        @Override
        public java.util.Optional<com.avandocmsg.messenger.core.domain.Chat> findById(
            com.avandocmsg.messenger.core.domain.ChatId id) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean isMember(
            com.avandocmsg.messenger.core.domain.ChatId chatId,
            com.avandocmsg.messenger.core.domain.UserId userId) {
            return false;
        }

        @Override
        public java.util.Optional<String> memberRole(
            com.avandocmsg.messenger.core.domain.ChatId chatId,
            com.avandocmsg.messenger.core.domain.UserId userId) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean isMemberBanned(
            com.avandocmsg.messenger.core.domain.ChatId chatId,
            com.avandocmsg.messenger.core.domain.UserId userId) {
            return false;
        }

        @Override
        public java.util.Optional<com.avandocmsg.messenger.core.domain.UserId> findOtherP2pMember(
            com.avandocmsg.messenger.core.domain.ChatId chatId,
            com.avandocmsg.messenger.core.domain.UserId userId) {
            return java.util.Optional.empty();
        }

        @Override
        public List<com.avandocmsg.messenger.core.domain.UserId> listMemberUserIds(
            com.avandocmsg.messenger.core.domain.ChatId chatId) {
            return List.of();
        }
    }
}
