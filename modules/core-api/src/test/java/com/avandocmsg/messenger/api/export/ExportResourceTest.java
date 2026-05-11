package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.export.dto.ExportAcceptedResponse;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.common.dto.ExportReplayJob;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExportResourceTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    final UUID chatId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @Test
    void memberRoleForbidden() {
        var repo = new TestChatRepository(chatId, userId, "member");
        var nats = recordingOutbound();
        var res = new ExportResource(repo, nats, UuidGenerator.standard(), I18nTestFixtures.messagesEn()).requestExport(chatId.toString(), securityContext());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), res.getStatus());
        assertTrue(nats.subjects.isEmpty());
    }

    @Test
    void ownerAcceptsAndPublishesNats() throws Exception {
        var repo = new TestChatRepository(chatId, userId, "owner");
        var nats = recordingOutbound();
        var res = new ExportResource(repo, nats, UuidGenerator.standard(), I18nTestFixtures.messagesEn()).requestExport(chatId.toString(), securityContext());
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), res.getStatus());
        var entity = (ExportAcceptedResponse) res.getEntity();
        assertEquals(chatId.toString(), entity.chatId());
        assertEquals("accepted", entity.status());
        assertEquals(NatsSubjects.MSG_EXPORT_REPLAY, nats.subjects.getFirst());
        var job = MAPPER.readValue(nats.payloads.getFirst(), ExportReplayJob.class);
        assertEquals(chatId.toString(), job.chatId());
        assertEquals(userId.toString(), job.requestedBy());
        assertEquals(entity.jobId(), job.jobId());
    }

    @Test
    void invalidChatId_badRequest() {
        var repo = new TestChatRepository(chatId, userId, "owner");
        var nats = recordingOutbound();
        var ex = assertThrows(InvalidUuidParameterException.class,
            () -> new ExportResource(repo, nats, UuidGenerator.standard(), I18nTestFixtures.messagesEn()).requestExport("not-uuid", securityContext()));
        assertEquals("chat_id", ex.paramKey());
    }

    @Test
    void notMember_returns404() {
        var repo = new TestChatRepository(chatId, userId, null);
        var nats = recordingOutbound();
        var res = new ExportResource(repo, nats, UuidGenerator.standard(), I18nTestFixtures.messagesEn()).requestExport(chatId.toString(), securityContext());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), res.getStatus());
    }

    private SecurityContext securityContext() {
        return new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return userId::toString;
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return "Bearer";
            }
        };
    }

    private static RecordingOutbound recordingOutbound() {
        return new RecordingOutbound();
    }

    private static final class RecordingOutbound implements NatsOutboundPort {
        final List<String> subjects = new ArrayList<>();
        final List<byte[]> payloads = new ArrayList<>();

        @Override
        public void publish(String subject, byte[] payload) {
            subjects.add(subject);
            payloads.add(payload);
        }

        @Override
        public void flush(Duration timeout) {
        }

        @Override
        public void publishPipelineMessageSend(byte[] payload) {
        }
    }

    static final class TestChatRepository extends ChatRepository {
        private final UUID cid;
        private final UUID uid;
        private final String role;

        TestChatRepository(UUID cid, UUID uid, String role) {
            super(null, java.time.Clock.systemUTC(), UuidGenerator.standard());
            this.cid = cid;
            this.uid = uid;
            this.role = role;
        }

        @Override
        public String getMemberRole(UUID chatId, UUID userId) {
            if (this.cid.equals(chatId) && this.uid.equals(userId)) {
                return role;
            }
            return null;
        }
    }
}
