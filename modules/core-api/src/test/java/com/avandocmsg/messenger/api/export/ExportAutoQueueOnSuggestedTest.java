package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcAuditAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcExportJobAdapter;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.common.dto.ExportSuggestedEvent;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportAutoQueueOnSuggestedTest {

    @Test
    void tryQueue_enqueuesWhenNoPriorJob() {
        var chatId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var jobs = new ExportResourceTest.InMemoryExportJobs();
        var nats = new RecordingNats();
        var auditPort = new JdbcAuditAdapter(null);
        var exportJobPort = new JdbcExportJobAdapter(jobs);
        var enqueuer = new ExportJobEnqueuer(exportJobPort, auditPort, nats, UuidGenerator.standard());
        var auto = new ExportAutoQueueOnSuggested(
            enqueuer,
            exportJobPort,
            ownerRepo(chatId, ownerId),
            auditPort,
            Optional.of(ownerId),
            1440
        );

        assertTrue(auto.tryQueue(new ExportSuggestedEvent(
            chatId.toString(),
            ExportSuggestedEvent.REASON_HOT_BODY_CANDIDATES,
            2,
            Instant.now().toEpochMilli()
        )).isPresent());
        assertEquals(NatsSubjects.MSG_EXPORT_REPLAY, nats.subjects.getFirst());
    }

    @Test
    void tryQueue_skipsWhenPendingJob() {
        var chatId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var jobs = new ExportResourceTest.InMemoryExportJobs();
        jobs.put(UUID.randomUUID(), chatId, ownerId, "queued", null);
        var nats = new RecordingNats();
        var auditPort = new JdbcAuditAdapter(null);
        var exportJobPort = new JdbcExportJobAdapter(jobs);
        var enqueuer = new ExportJobEnqueuer(exportJobPort, auditPort, nats, UuidGenerator.standard());
        var auto = new ExportAutoQueueOnSuggested(
            enqueuer,
            exportJobPort,
            ownerRepo(chatId, ownerId),
            auditPort,
            Optional.of(ownerId),
            1440
        );

        assertTrue(auto.tryQueue(new ExportSuggestedEvent(
            chatId.toString(), ExportSuggestedEvent.REASON_HOT_BODY_CANDIDATES, 1, 0L
        )).isEmpty());
        assertTrue(nats.subjects.isEmpty());
    }

    private static ChatPersistencePort ownerRepo(UUID chatId, UUID ownerId) {
        return new ExportResourceTest.TestChatRepository(chatId, ownerId, "owner") {
            @Override
            public Optional<UUID> findOwnerId(UUID id) {
                return id.equals(chatId) ? Optional.of(ownerId) : Optional.empty();
            }
        };
    }

    private static final class RecordingNats implements NatsOutboundPort {
        final List<String> subjects = new ArrayList<>();

        @Override
        public void publish(String subject, byte[] payload) {
            subjects.add(subject);
        }

        @Override
        public void flush(Duration timeout) {
        }

        @Override
        public void publishPipelineMessageSend(byte[] payload, String userId) {
        }
    }
}
