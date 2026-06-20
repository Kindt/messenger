package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.admin.AdminResource;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.export.dto.ExportAcceptedResponse;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.core.port.ChatPersistencePort;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRetentionPolicyAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcExportJobAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcRetentionPolicyAdapter;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminExportEnqueueTest {

    @Test
    void requestExport_disabled_returns404() {
        var chatId = UUID.randomUUID();
        var chats = mock(ChatPersistencePort.class);
        var resource = adminResource(chats, new AppConfig(), new ExportResourceTest.InMemoryExportJobs(),
            new ExportResourceTest.RecordingOutbound());
        var res = resource.requestExport(chatId.toString(), adminSecurityContext());
        assertEquals(404, res.getStatus());
    }

    @Test
    void requestExport_enqueuesWhenEnabled() throws Exception {
        var chatId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var chats = mock(ChatPersistencePort.class);
        when(chats.chatExists(chatId)).thenReturn(true);
        var jobs = new ExportResourceTest.InMemoryExportJobs();
        var nats = new ExportResourceTest.RecordingOutbound();
        var cfg = new AppConfig() {
            @Override
            public boolean exportAdminExportEnabled() {
                return true;
            }
        };
        var resource = adminResource(chats, cfg, jobs, nats);
        var sc = securityContext(actorId);
        var res = resource.requestExport(chatId.toString(), sc);
        assertEquals(202, res.getStatus());
        var body = (ExportAcceptedResponse) res.getEntity();
        assertEquals(chatId.toString(), body.chatId());
        assertTrue(jobs.contains(UUID.fromString(body.jobId())));
        assertEquals(NatsSubjects.MSG_EXPORT_REPLAY, nats.subjects.getFirst());
    }

    @Test
    void cancelExport_queued_admin() {
        var chatId = UUID.randomUUID();
        var jobId = UUID.randomUUID();
        var chats = mock(ChatPersistencePort.class);
        when(chats.chatExists(chatId)).thenReturn(true);
        var jobs = new ExportResourceTest.InMemoryExportJobs();
        jobs.put(jobId, chatId, UUID.randomUUID(), "queued", null);
        var cfg = new AppConfig() {
            @Override
            public boolean exportAdminExportEnabled() {
                return true;
            }
        };
        var nats = new ExportResourceTest.RecordingOutbound();
        var audit = new ExportResourceTest.RecordingAudit();
        var resource = adminResource(chats, cfg, jobs, nats, audit);
        var res = resource.cancelExportJob(chatId.toString(), jobId.toString(), adminSecurityContext());
        assertEquals(200, res.getStatus());
        assertEquals("export_cancelled", jobs.findByIdAndChat(jobId, chatId).orElseThrow().status());
        assertEquals(NatsSubjects.MSG_EXPORT_REPLAY_CANCEL, nats.subjects.getFirst());
        assertTrue(audit.cancellations.stream().anyMatch(c ->
            "export.admin_cancelled".equals(c.action()) && jobId.toString().equals(c.resourceId())));
    }

    @Test
    void cancelExport_processing_admin() {
        var chatId = UUID.randomUUID();
        var jobId = UUID.randomUUID();
        var chats = mock(ChatPersistencePort.class);
        when(chats.chatExists(chatId)).thenReturn(true);
        var jobs = new ExportResourceTest.InMemoryExportJobs();
        jobs.put(jobId, chatId, UUID.randomUUID(), "processing", null);
        var cfg = new AppConfig() {
            @Override
            public boolean exportAdminExportEnabled() {
                return true;
            }
        };
        var nats = new ExportResourceTest.RecordingOutbound();
        var resource = adminResource(chats, cfg, jobs, nats);
        var res = resource.cancelExportJob(chatId.toString(), jobId.toString(), adminSecurityContext());
        assertEquals(200, res.getStatus());
        assertEquals("export_cancelled", jobs.findByIdAndChat(jobId, chatId).orElseThrow().status());
        assertEquals(NatsSubjects.MSG_EXPORT_REPLAY_CANCEL, nats.subjects.getFirst());
    }

    @Test
    void requestExport_unknownChat_returns404() {
        var chatId = UUID.randomUUID();
        var chats = mock(ChatPersistencePort.class);
        when(chats.chatExists(chatId)).thenReturn(false);
        var cfg = new AppConfig() {
            @Override
            public boolean exportAdminExportEnabled() {
                return true;
            }
        };
        var resource = adminResource(chats, cfg, new ExportResourceTest.InMemoryExportJobs(),
            new ExportResourceTest.RecordingOutbound());
        var res = resource.requestExport(chatId.toString(), adminSecurityContext());
        assertEquals(404, res.getStatus());
    }

    private static AdminResource adminResource(
        ChatPersistencePort chats,
        AppConfig cfg,
        ExportResourceTest.InMemoryExportJobs jobs,
        NatsOutboundPort nats
    ) {
        return adminResource(chats, cfg, jobs, nats, new ExportResourceTest.RecordingAudit());
    }

    private static AdminResource adminResource(
        ChatPersistencePort chats,
        AppConfig cfg,
        ExportResourceTest.InMemoryExportJobs jobs,
        NatsOutboundPort nats,
        AuditPort audit
    ) {
        var auditPort = audit;
        var exportJobPort = new JdbcExportJobAdapter(jobs);
        var enqueuer = new ExportJobEnqueuer(exportJobPort, auditPort, nats, UuidGenerator.standard());
        return new AdminResource(cfg, auditPort,
            com.avandocmsg.messenger.core.bootstrap.CoreModule.organizationApplicationService(
                null, UuidGenerator.standard()),
            new JdbcRetentionPolicyAdapter((javax.sql.DataSource) null),
            chats,
            new JdbcChatRetentionPolicyAdapter((javax.sql.DataSource) null),
            new ExportSuggestedHandler(auditPort),
            mock(AdminExportComplianceSeed.class),
            enqueuer,
            exportJobPort,
            new ExportFileAccess(cfg),
            nats,
            null,
            null,
            null,
            null,
            null,
            null,
            I18nTestFixtures.messagesEn());
    }

    private static SecurityContext adminSecurityContext() {
        return securityContext(UUID.randomUUID());
    }

    private static SecurityContext securityContext(UUID userId) {
        var principal = new UserPrincipal(userId.toString(), "csadmin", Set.of("admin"));
        return new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public boolean isUserInRole(String role) {
                return principal.hasRealmRole(role);
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
}
