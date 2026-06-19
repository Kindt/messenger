package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.core.adapter.persistence.JdbcAuditAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatPersistenceAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcExportJobAdapter;
import com.avandocmsg.messenger.api.repository.AuditRepository;
import com.avandocmsg.messenger.api.repository.ChatRepository;
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

class ExportSuggestedHandlerTest {

    @Test
    void handle_recordsSuggestedAudit_onlyWhenAutoQueueDisabled() throws Exception {
        var chatId = UUID.randomUUID();
        var audit = new RecordingAudit();
        var handler = new ExportSuggestedHandler(new JdbcAuditAdapter(audit));
        var event = new ExportSuggestedEvent(
            chatId.toString(),
            ExportSuggestedEvent.REASON_HOT_BODY_CANDIDATES,
            3,
            Instant.now().toEpochMilli());

        assertTrue(handler.handle(event).isEmpty());
        assertTrue(audit.actions.contains(ExportSuggestedHandler.AUDIT_ACTION));
    }

    @Test
    void handle_withAutoQueue_enqueuesAndReturnsJobId() throws Exception {
        var chatId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();
        var jobs = new ExportResourceTest.InMemoryExportJobs();
        var nats = new RecordingNats();
        var audit = new RecordingAudit();
        var enqueuer = new ExportJobEnqueuer(new JdbcExportJobAdapter(jobs), new JdbcAuditAdapter(audit), nats, UuidGenerator.standard());
        var auto = new ExportAutoQueueOnSuggested(
            enqueuer,
            new JdbcExportJobAdapter(jobs),
            new JdbcChatPersistenceAdapter(ownerRepo(chatId, ownerId)),
            new JdbcAuditAdapter(audit),
            Optional.of(ownerId),
            1440
        );
        var handler = new ExportSuggestedHandler(new JdbcAuditAdapter(audit), Optional.of(auto));
        var event = new ExportSuggestedEvent(
            chatId.toString(),
            ExportSuggestedEvent.REASON_HOT_BODY_CANDIDATES,
            2,
            Instant.now().toEpochMilli());

        var jobId = handler.handle(event);
        assertTrue(jobId.isPresent());
        assertEquals("queued", jobs.findByIdAndChat(jobId.get(), chatId).orElseThrow().status());
        assertEquals(NatsSubjects.MSG_EXPORT_REPLAY, nats.subjects.getFirst());
        assertTrue(audit.actions.contains(ExportSuggestedHandler.AUDIT_ACTION));
        assertTrue(audit.actions.contains(ExportAutoQueueOnSuggested.AUDIT_AUTO_QUEUED));
    }

    private static ChatRepository ownerRepo(UUID chatId, UUID ownerId) {
        return new ExportResourceTest.TestChatRepository(chatId, ownerId, "owner") {
            @Override
            public Optional<UUID> findOwnerId(UUID id) {
                return id.equals(chatId) ? Optional.of(ownerId) : Optional.empty();
            }
        };
    }

    static final class RecordingAudit extends AuditRepository {
        final List<String> actions = new ArrayList<>();

        RecordingAudit() {
            super(null);
        }

        @Override
        public void record(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson) {
            actions.add(action);
        }
    }

    static final class RecordingNats implements NatsOutboundPort {
        final List<String> subjects = new ArrayList<>();

        @Override
        public void publish(String subject, byte[] payload) {
            subjects.add(subject);
        }

        @Override
        public void flush(Duration timeout) {
        }

        @Override
        public void publishPipelineMessageSend(byte[] payload) {
        }
    }
}
