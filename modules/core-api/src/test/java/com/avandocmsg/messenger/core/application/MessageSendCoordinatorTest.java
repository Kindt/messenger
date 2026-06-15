package com.avandocmsg.messenger.core.application;

import com.avandocmsg.messenger.api.messages.dto.SendMessageRequest;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcMessageRepositoryAdapter;
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

    private final StubMessageRepository msgRepo = new StubMessageRepository();
    private final StubChatRepository chatRepo = new StubChatRepository();
    private final RecordingNats nats = new RecordingNats();
    private final StubMlsService mlsService = new StubMlsService();
    private final MessageSendCoordinator coordinator = new MessageSendCoordinator(
        new JdbcMessageRepositoryAdapter(msgRepo),
        chatRepo,
        mlsService,
        null,
        nats,
        UuidGenerator.standard(),
        null);

    final UUID chatId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void reset() {
        msgRepo.messages.clear();
        nats.pipelinePayloads.clear();
    }

    @Test
    void send_persistsAndPublishesPipelineEvent() {
        var result = coordinator.send(chatId, userId,
            new SendMessageRequest("text", "hello", null, null, null, null, null), null);

        assertNotNull(result);
        assertEquals("hello", result.content());
        assertEquals(1, msgRepo.messages.size());
        assertEquals(1, nats.pipelinePayloads.size());
    }

    @Test
    void forward_persistsCopyAndPublishesPipelineEvent() {
        var sourceMsg = coordinator.send(chatId, userId,
            new SendMessageRequest("text", "original", null, null, null, null, null), null);
        assertNotNull(sourceMsg);
        nats.pipelinePayloads.clear();

        var forwarded = coordinator.forward(chatId, UUID.fromString(sourceMsg.id()), userId, UUID.randomUUID());
        assertNotNull(forwarded);
        assertEquals("original", forwarded.content());
        assertEquals(2, msgRepo.messages.size());
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

    static class StubMessageRepository extends MessageRepository {
        final List<com.avandocmsg.messenger.api.messages.dto.MessageResponse> messages = new ArrayList<>();

        StubMessageRepository() {
            super(null, java.time.Clock.systemUTC());
        }

        @Override
        public com.avandocmsg.messenger.api.messages.dto.MessageResponse insert(
            UUID id, UUID chatId, UUID senderId, String type, String content,
            UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds, UUID attachmentFileId) {
            var msg = new com.avandocmsg.messenger.api.messages.dto.MessageResponse(
                id.toString(), chatId.toString(), senderId.toString(), type, content,
                replyToMsgId != null ? replyToMsgId.toString() : null, false, Instant.now(), null,
                visibilityTtlSeconds, attachmentFileId != null ? attachmentFileId.toString() : null);
            messages.add(msg);
            return msg;
        }

        @Override
        public Optional<com.avandocmsg.messenger.api.messages.dto.MessageResponse> findById(UUID id) {
            return messages.stream().filter(m -> m.id().equals(id.toString())).findFirst();
        }
    }

    static class StubChatRepository extends ChatRepository {
        StubChatRepository() {
            super(null, java.time.Clock.systemUTC(), UuidGenerator.standard());
        }

        @Override
        public List<com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse> listMembers(UUID chatId) {
            return List.of();
        }
    }
}
