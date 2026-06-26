package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.admin.AdminResource;
import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.export.dto.ExportAttachmentsListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobStatusResponse;
import com.avandocmsg.messenger.api.filter.UserPrincipal;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcAuditAdapter;
import com.avandocmsg.messenger.testsupport.EmptyChatPersistencePort;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatRetentionPolicyAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcExportJobAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcRetentionPolicyAdapter;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AdminExportInspectTest {

    @TempDir
    java.nio.file.Path exportDir;

    @Test
    void adminExportJobStatus_andAttachments() throws Exception {
        var chatId = UUID.randomUUID();
        var jobId = UUID.randomUUID();
        var fileId = UUID.randomUUID();
        var zipPath = exportDir.resolve(ExportOutputRef.safeJobIdForFilename(jobId.toString()) + ".export.zip");
        try (var zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST));
            var manifest = """
                {"files":[{"fileId":"%s","filename":"a.txt","mimeType":"text/plain","zipPath":"attachments/x","sizeBytes":1,"sha256":""}]}
                """.formatted(fileId);
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var jobs = new ExportResourceTest.InMemoryExportJobs();
        jobs.put(jobId, chatId, UUID.randomUUID(), "export_v1", zipPath.toString());
        var resource = adminResource(jobs, ExportFileAccessTest.accessWithDir(exportDir));
        var sc = adminSecurityContext();

        var statusRes = resource.exportJobStatus(chatId.toString(), jobId.toString(), sc);
        assertEquals(200, statusRes.getStatus());
        assertEquals("export_v1", ((ExportJobStatusResponse) statusRes.getEntity()).status());

        var attRes = resource.exportJobAttachments(chatId.toString(), jobId.toString(), 0, 0, sc);
        assertEquals(200, attRes.getStatus());
        var att = (ExportAttachmentsListResponse) attRes.getEntity();
        assertTrue(att.zipBundle());
        assertEquals(1, att.totalCount());

        var latestRes = resource.exportLatestStatus(chatId.toString(), sc);
        assertEquals(200, latestRes.getStatus());
        assertEquals(jobId.toString(), ((ExportJobStatusResponse) latestRes.getEntity()).jobId());

        var listRes = resource.listExportJobs(chatId.toString(), null, 10, sc);
        assertEquals(200, listRes.getStatus());
        var list = (com.avandocmsg.messenger.api.export.dto.ExportJobListResponse) listRes.getEntity();
        assertEquals(1, list.jobCount());

        var dlRes = resource.exportJobDownload(
            chatId.toString(), jobId.toString(), "bundle", null, null, sc);
        assertEquals(200, dlRes.getStatus());

        var globalRes = resource.listAllExportJobs(null, chatId.toString(), 10, sc);
        assertEquals(200, globalRes.getStatus());
        var global = (com.avandocmsg.messenger.api.export.dto.ExportAdminJobsListResponse) globalRes.getEntity();
        assertEquals(1, global.jobCount());
        assertEquals(chatId.toString(), global.jobs().getFirst().chatId());
    }

    private AdminResource adminResource(ExportResourceTest.InMemoryExportJobs jobs, ExportFileAccess access) {
        var cfg = new AppConfig() {
            @Override
            public java.util.Optional<java.nio.file.Path> exportDir() {
                return java.util.Optional.of(exportDir);
            }
        };
        var audit = new JdbcAuditAdapter((javax.sql.DataSource) null);
        var nats = mock(NatsOutboundPort.class);
        var exportJobPort = new JdbcExportJobAdapter(jobs);
        var enqueuer = new ExportJobEnqueuer(exportJobPort, audit, nats, UuidGenerator.standard());
        return new AdminResource(cfg, audit,
            com.avandocmsg.messenger.core.bootstrap.CoreModule.organizationApplicationService(
                null, UuidGenerator.standard()),
            null,
            new JdbcRetentionPolicyAdapter((javax.sql.DataSource) null),
            new EmptyChatPersistencePort(),
            new JdbcChatRetentionPolicyAdapter((javax.sql.DataSource) null),
            new ExportSuggestedHandler(audit),
            mock(AdminExportComplianceSeed.class),
            enqueuer,
            exportJobPort,
            access,
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
        var principal = new UserPrincipal(UUID.randomUUID().toString(), "csadmin", Set.of("admin"));
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
