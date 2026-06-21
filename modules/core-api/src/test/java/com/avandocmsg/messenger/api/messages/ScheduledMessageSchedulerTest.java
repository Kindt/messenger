package com.avandocmsg.messenger.api.messages;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.core.application.MessageSendCoordinator;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageInsert;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.ScheduledMessagePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledMessageSchedulerTest {

    private final StubScheduledPort port = new StubScheduledPort();
    private final StubMessagePort messagePort = new StubMessagePort();
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void tick_marksSentWhenCoordinatorSucceeds() {
        var chatId = UUID.randomUUID();
        var senderId = UUID.randomUUID();
        var sentId = UUID.randomUUID();
        messagePort.nextId = sentId;
        var row = new ScheduledMessagePort.ScheduledRow(
            UUID.randomUUID(), chatId, senderId,
            "text", "hello", clock.instant(), "pending",
            null, null, null, null, clock.instant());
        port.due.add(row);

        var coordinator = new MessageSendCoordinator(
            messagePort, null, new StubMlsService(), null, new RecordingNats(), () -> sentId, null);
        var scheduler = new ScheduledMessageScheduler(appConfig(), port, coordinator, clock);
        scheduler.tick();

        assertEquals("sent", port.lastStatus);
        assertEquals(sentId, port.lastSentMessageId);
        scheduler.close();
    }

    @Test
    void tick_marksFailedWhenCoordinatorReturnsNull() {
        messagePort.failInsert = true;
        var row = new ScheduledMessagePort.ScheduledRow(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "text", "hello", clock.instant(), "pending",
            null, null, null, null, clock.instant());
        port.due.add(row);

        var coordinator = new MessageSendCoordinator(
            messagePort, null, new StubMlsService(), null, new RecordingNats(), UuidGenerator.standard(), null);
        var scheduler = new ScheduledMessageScheduler(appConfig(), port, coordinator, clock);
        scheduler.tick();

        assertEquals("failed", port.lastStatus);
        assertTrue(port.lastSentMessageId == null);
        scheduler.close();
    }

    private static AppConfig appConfig() {
        return new AppConfig() {
            @Override
            public long scheduledMessagePollSeconds() {
                return 0;
            }

            @Override
            public int scheduledMessageBatchSize() {
                return 10;
            }
        };
    }

    static final class StubScheduledPort implements ScheduledMessagePort {
        final List<ScheduledRow> due = new ArrayList<>();
        String lastStatus;
        UUID lastSentMessageId;

        @Override
        public UUID create(CreateScheduled cmd) {
            return null;
        }

        @Override
        public Optional<ScheduledRow> find(UUID id) {
            return Optional.empty();
        }

        @Override
        public List<ScheduledRow> listForChat(UUID chatId, int limit) {
            return List.of();
        }

        @Override
        public List<ScheduledRow> listForSender(UUID senderId, int limit) {
            return List.of();
        }

        @Override
        public List<ScheduledRow> listDue(Instant now, int limit) {
            return due;
        }

        @Override
        public boolean cancelPending(UUID id, UUID senderId) {
            return true;
        }

        @Override
        public boolean updateStatus(UUID id, String status, UUID sentMessageId) {
            lastStatus = status;
            lastSentMessageId = sentMessageId;
            return true;
        }
    }

    static final class StubMessagePort implements MessageRepositoryPort {
        UUID nextId = UUID.randomUUID();
        boolean failInsert;

        @Override
        public Optional<Message> findById(MessageId id) {
            return Optional.empty();
        }

        @Override
        public Optional<Message> insert(MessageInsert command) {
            if (failInsert) {
                return Optional.empty();
            }
            var id = nextId != null ? nextId : command.id().value();
            return Optional.of(new Message(
                MessageId.of(id),
                command.chatId(),
                command.senderId(),
                command.type(),
                command.content(),
                command.replyToMsgId() != null ? command.replyToMsgId().toString() : null,
                command.threadId() != null ? command.threadId().toString() : null,
                false,
                Instant.now(),
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

    static final class RecordingNats implements NatsOutboundPort {
        @Override
        public void publish(String subject, byte[] payload) {
        }

        @Override
        public void flush(java.time.Duration timeout) {
        }

        @Override
        public void publishPipelineMessageSend(byte[] payload) {
        }
    }
}
