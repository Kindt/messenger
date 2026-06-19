package com.avandocmsg.messenger.api.export;

import com.avandocmsg.messenger.api.config.AppConfig;
import com.avandocmsg.messenger.api.i18n.I18nTestFixtures;
import com.avandocmsg.messenger.api.export.dto.ExportAcceptedResponse;
import com.avandocmsg.messenger.api.export.dto.ExportAttachmentsListResponse;
import com.avandocmsg.messenger.api.export.dto.ExportJobStatusResponse;
import com.avandocmsg.messenger.api.params.InvalidUuidParameterException;
import com.avandocmsg.messenger.core.port.AuditPort;
import com.avandocmsg.messenger.api.repository.ChatRepository;
import com.avandocmsg.messenger.api.repository.ExportJobRepository;
import com.avandocmsg.messenger.common.dto.ExportReplayJob;
import com.avandocmsg.messenger.common.export.ExportOutputRef;
import com.avandocmsg.messenger.common.nats.NatsSubjects;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcAuditAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcChatPersistenceAdapter;
import com.avandocmsg.messenger.core.adapter.persistence.JdbcExportJobAdapter;
import com.avandocmsg.messenger.core.port.NatsOutboundPort;
import com.avandocmsg.messenger.core.port.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ExportResourceTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    final UUID chatId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();

    @Test
    void memberRoleForbidden() {
        var repo = new TestChatRepository(chatId, userId, "member");
        var res = resource(repo, new InMemoryExportJobs(), noopAudit(), recordingOutbound(), unconfiguredExportFiles())
            .requestExport(chatId.toString(), securityContext());
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), res.getStatus());
    }

    @Test
    void ownerAcceptsAndPublishesNats() throws Exception {
        var jobs = new InMemoryExportJobs();
        var nats = recordingOutbound();
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, noopAudit(), nats, unconfiguredExportFiles())
            .requestExport(chatId.toString(), securityContext());
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), res.getStatus());
        var entity = (ExportAcceptedResponse) res.getEntity();
        assertEquals(chatId.toString(), entity.chatId());
        assertEquals("accepted", entity.status());
        assertEquals(NatsSubjects.MSG_EXPORT_REPLAY, nats.subjects.getFirst());
        var job = MAPPER.readValue(nats.payloads.getFirst(), ExportReplayJob.class);
        assertEquals(entity.jobId(), job.jobId());
        assertTrue(jobs.contains(UUID.fromString(entity.jobId())));
    }

    @Test
    void getStatus_returnsJob() {
        var jobId = UUID.randomUUID();
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "export_v1", "/tmp/out.json");
        var res = resource(new TestChatRepository(chatId, userId, "admin"), jobs, noopAudit(), recordingOutbound(),
            unconfiguredExportFiles())
            .getExportStatus(chatId.toString(), jobId.toString(), securityContext());
        assertEquals(200, res.getStatus());
        var body = (ExportJobStatusResponse) res.getEntity();
        assertEquals("export_v1", body.status());
        assertEquals("/tmp/out.json", body.outputPath());
    }

    @Test
    void getStatus_notFound() {
        var res = resource(new TestChatRepository(chatId, userId, "owner"), new InMemoryExportJobs(), noopAudit(),
            recordingOutbound(), unconfiguredExportFiles())
            .getExportStatus(chatId.toString(), UUID.randomUUID().toString(), securityContext());
        assertEquals(404, res.getStatus());
    }

    @Test
    void invalidChatId_badRequest() {
        var ex = assertThrows(InvalidUuidParameterException.class,
            () -> resource(new TestChatRepository(chatId, userId, "owner"), new InMemoryExportJobs(), noopAudit(),
                recordingOutbound(), unconfiguredExportFiles())
                .requestExport("not-uuid", securityContext()));
        assertEquals("chat_id", ex.paramKey());
    }

    @Test
    void notMember_returns404() {
        var res = resource(new TestChatRepository(chatId, userId, null), new InMemoryExportJobs(), noopAudit(),
            recordingOutbound(), unconfiguredExportFiles())
            .requestExport(chatId.toString(), securityContext());
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), res.getStatus());
    }

    @Test
    void cancelExport_queued_ok() {
        var jobId = UUID.randomUUID();
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "queued", null);
        var audit = new RecordingAudit();
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, audit, recordingOutbound(),
            unconfiguredExportFiles())
            .cancelExport(chatId.toString(), jobId.toString(), securityContext());
        assertEquals(200, res.getStatus());
        assertEquals("export_cancelled", jobs.findByIdAndChat(jobId, chatId).orElseThrow().status());
        assertTrue(audit.cancellations.stream().anyMatch(c ->
            "export.cancelled".equals(c.action()) && jobId.toString().equals(c.resourceId())));
    }

    @Test
    void cancelExport_processing_ok_publishesCancel() throws Exception {
        var jobId = UUID.randomUUID();
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "processing", null);
        var nats = recordingOutbound();
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, noopAudit(), nats,
            unconfiguredExportFiles())
            .cancelExport(chatId.toString(), jobId.toString(), securityContext());
        assertEquals(200, res.getStatus());
        assertEquals("export_cancelled", jobs.findByIdAndChat(jobId, chatId).orElseThrow().status());
        assertEquals(1, nats.subjects.size());
        assertEquals(NatsSubjects.MSG_EXPORT_REPLAY_CANCEL, nats.subjects.get(0));
        var event = MAPPER.readValue(nats.payloads.get(0), com.avandocmsg.messenger.common.dto.ExportReplayCancelEvent.class);
        assertEquals(jobId.toString(), event.jobId());
    }

    @Test
    void download_notReady_returns409() {
        var jobId = UUID.randomUUID();
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "processing", null);
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, noopAudit(), recordingOutbound(),
            unconfiguredExportFiles())
            .downloadExport(chatId.toString(), jobId.toString(), "bundle", null, null, securityContext());
        assertEquals(409, res.getStatus());
    }

    @Test
    void download_unconfigured_returns503() {
        var jobId = UUID.randomUUID();
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "export_v1", null);
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, noopAudit(), recordingOutbound(),
            unconfiguredExportFiles())
            .downloadExport(chatId.toString(), jobId.toString(), "bundle", null, null, securityContext());
        assertEquals(503, res.getStatus());
    }

    @Test
    void download_recordsAudit(@TempDir java.nio.file.Path exportDir) throws Exception {
        var jobId = UUID.randomUUID();
        var file = exportDir.resolve(ExportFileAccess.safeExportFileName(jobId.toString()));
        Files.writeString(file, "{}");
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "export_v1", file.toString());
        var audit = new RecordingAudit();
        var access = ExportFileAccessTest.accessWithDir(exportDir);
        resource(new TestChatRepository(chatId, userId, "owner"), jobs, audit, recordingOutbound(), access)
            .downloadExport(chatId.toString(), jobId.toString(), "json", null, null, securityContext());
        assertEquals(1, audit.downloads.size());
        assertEquals("export.downloaded", audit.downloads.getFirst().action());
    }

    @Test
    void download_invalidPart_returns400() {
        var jobId = UUID.randomUUID();
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "export_v1", "/x.export.json");
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, noopAudit(), recordingOutbound(),
            ExportFileAccessTest.accessWithDir(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"))))
            .downloadExport(chatId.toString(), jobId.toString(), "nope", null, null, securityContext());
        assertEquals(400, res.getStatus());
    }

    @Test
    void listAttachments_jsonOnly_returnsEmptyZipFlag(@TempDir java.nio.file.Path exportDir) throws Exception {
        var jobId = UUID.randomUUID();
        var file = exportDir.resolve(ExportFileAccess.safeExportFileName(jobId.toString()));
        Files.writeString(file, "{}");
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "export_v1", file.toString());
        var access = ExportFileAccessTest.accessWithDir(exportDir);
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, noopAudit(), recordingOutbound(), access)
            .listAttachments(chatId.toString(), jobId.toString(), 0, 0, securityContext());
        assertEquals(200, res.getStatus());
        var body = (ExportAttachmentsListResponse) res.getEntity();
        assertFalse(body.zipBundle());
        assertEquals(0, body.totalCount());
        assertEquals(0, body.fileCount());
    }

    @Test
    void listAttachments_zipManifest(@TempDir java.nio.file.Path exportDir) throws Exception {
        var jobId = UUID.randomUUID();
        var fileId = UUID.randomUUID();
        var zipPath = exportDir.resolve(ExportOutputRef.safeJobIdForFilename(jobId.toString()) + ".export.zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new java.util.zip.ZipEntry(ExportOutputRef.ZIP_ATTACHMENTS_MANIFEST));
            var manifest = """
                {"files":[{"fileId":"%s","filename":"a.txt","mimeType":"text/plain","zipPath":"attachments/x","sizeBytes":3,"sha256":"abc"}]}
                """.formatted(fileId);
            zos.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "export_v1", zipPath.toString());
        var access = ExportFileAccessTest.accessWithDir(exportDir);
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, noopAudit(), recordingOutbound(), access)
            .listAttachments(chatId.toString(), jobId.toString(), 0, 0, securityContext());
        assertEquals(200, res.getStatus());
        var body = (ExportAttachmentsListResponse) res.getEntity();
        assertTrue(body.zipBundle());
        assertEquals(1, body.totalCount());
        assertEquals(1, body.fileCount());
        assertEquals(fileId.toString(), body.files().getFirst().fileId());
        assertEquals(3, body.files().getFirst().sizeBytes());
    }

    @Test
    void download_streamsFile(@TempDir java.nio.file.Path exportDir) throws Exception {
        var jobId = UUID.randomUUID();
        var file = exportDir.resolve(ExportFileAccess.safeExportFileName(jobId.toString()));
        Files.writeString(file, "{\"ok\":true}");
        var jobs = new InMemoryExportJobs();
        jobs.put(jobId, chatId, userId, "export_v1", file.toString());
        var access = ExportFileAccessTest.accessWithDir(exportDir);
        var res = resource(new TestChatRepository(chatId, userId, "owner"), jobs, noopAudit(), recordingOutbound(), access)
            .downloadExport(chatId.toString(), jobId.toString(), "bundle", null, null, securityContext());
        assertEquals(200, res.getStatus());
        var stream = (jakarta.ws.rs.core.StreamingOutput) res.getEntity();
        var baos = new java.io.ByteArrayOutputStream();
        stream.write(baos);
        assertEquals("{\"ok\":true}", baos.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ExportResource resource(ChatRepository chats, ExportJobRepository jobs, AuditPort auditPort,
                                    NatsOutboundPort nats, ExportFileAccess exportFiles) {
        var exportJobPort = new JdbcExportJobAdapter(jobs);
        var enqueuer = new ExportJobEnqueuer(exportJobPort, auditPort, nats, UuidGenerator.standard());
        return new ExportResource(new JdbcChatPersistenceAdapter(chats), exportJobPort, enqueuer, auditPort,
            I18nTestFixtures.messagesEn(), exportFiles, nats);
    }

    private static ExportFileAccess unconfiguredExportFiles() {
        return new ExportFileAccess(new AppConfig());
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

    private static AuditPort noopAudit() {
        return new JdbcAuditAdapter(null);
    }

    static final class RecordingOutbound implements NatsOutboundPort {
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

    static final class RecordingAudit implements AuditPort {
        record DownloadAudit(String action, String resourceId) {
        }

        record CancelAudit(String action, String resourceType, String resourceId) {
        }

        final java.util.List<DownloadAudit> downloads = new java.util.ArrayList<>();
        final java.util.List<CancelAudit> cancellations = new java.util.ArrayList<>();

        RecordingAudit() {
        }

        @Override
        public void record(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson) {
            if ("export.downloaded".equals(action)) {
                downloads.add(new DownloadAudit(action, resourceId));
            }
            if ("export.cancelled".equals(action) || "export.admin_cancelled".equals(action)) {
                cancellations.add(new CancelAudit(action, resourceType, resourceId));
            }
        }

        @Override
        public java.util.List<AuditPort.AuditRow> listRecent(int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<AuditPort.AuditRow> listRecent(int limit, String actionEquals) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<AuditPort.AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<AuditPort.AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals,
                                                               String resourceIdEquals) {
            return java.util.List.of();
        }

        @Override
        public long countByAction(String action) {
            return 0L;
        }

        @Override
        public java.util.Optional<Instant> latestOccurredAtByAction(String action) {
            return java.util.Optional.empty();
        }
    }

    static final class InMemoryExportJobs extends ExportJobRepository {
        private final ConcurrentHashMap<UUID, ExportJobRow> rows = new ConcurrentHashMap<>();

        InMemoryExportJobs() {
            super((javax.sql.DataSource) null);
        }

        void put(UUID jobId, UUID chatId, UUID requestedBy, String status, String outputPath) {
            var now = Instant.now();
            rows.put(jobId, new ExportJobRow(jobId, chatId, requestedBy, status, outputPath, null, now, now, now));
        }

        boolean contains(UUID jobId) {
            return rows.containsKey(jobId);
        }

        @Override
        public void insertQueued(UUID jobId, UUID chatId, UUID requestedBy) {
            var now = Instant.now();
            rows.put(jobId, new ExportJobRow(jobId, chatId, requestedBy, "queued", null, null, now, now, null));
        }

        @Override
        public Optional<ExportJobRow> findByIdAndChat(UUID jobId, UUID chatId) {
            var row = rows.get(jobId);
            if (row != null && row.chatId().equals(chatId)) {
                return Optional.of(row);
            }
            return Optional.empty();
        }

        @Override
        public Optional<ExportJobRow> findLatestForChat(UUID chatId) {
            return rows.values().stream()
                .filter(r -> r.chatId().equals(chatId))
                .max(java.util.Comparator.comparing(ExportJobRow::createdAt));
        }

        @Override
        public java.util.List<ExportJobRow> listRecent(String statusFilter, UUID chatIdFilter, int limit) {
            var stream = rows.values().stream();
            if (chatIdFilter != null) {
                stream = stream.filter(r -> r.chatId().equals(chatIdFilter));
            }
            if (statusFilter != null && !statusFilter.isBlank()) {
                stream = stream.filter(r -> statusFilter.equals(r.status()));
            }
            return stream
                .sorted(java.util.Comparator.comparing(ExportJobRow::createdAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
        }

        @Override
        public java.util.List<ExportJobRow> listForChat(UUID chatId, String statusFilter, int limit) {
            var stream = rows.values().stream().filter(r -> r.chatId().equals(chatId));
            if (statusFilter != null && !statusFilter.isBlank()) {
                stream = stream.filter(r -> statusFilter.equals(r.status()));
            }
            return stream
                .sorted(java.util.Comparator.comparing(ExportJobRow::createdAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
        }

        @Override
        public boolean hasBlockingJobForChat(UUID chatId, int cooldownMinutes) {
            var latest = findLatestForChat(chatId);
            if (latest.isEmpty()) {
                return false;
            }
            var row = latest.get();
            if ("queued".equals(row.status()) || "processing".equals(row.status())) {
                return true;
            }
            if ("export_failed".equals(row.status()) || "export_cancelled".equals(row.status()) || cooldownMinutes <= 0) {
                return false;
            }
            var cutoff = Instant.now().minusSeconds(cooldownMinutes * 60L);
            return row.createdAt() != null && row.createdAt().isAfter(cutoff);
        }

        @Override
        public boolean cancelIfQueued(UUID jobId, UUID chatId) {
            var row = rows.get(jobId);
            if (row == null || !row.chatId().equals(chatId) || !"queued".equals(row.status())) {
                return false;
            }
            return cancelIfActive(jobId, chatId);
        }

        @Override
        public boolean cancelIfActive(UUID jobId, UUID chatId) {
            var row = rows.get(jobId);
            if (row == null || !row.chatId().equals(chatId)) {
                return false;
            }
            if (!"queued".equals(row.status()) && !"processing".equals(row.status())) {
                return false;
            }
            var now = Instant.now();
            rows.put(jobId, new ExportJobRow(
                row.id(), row.chatId(), row.requestedBy(), "export_cancelled",
                row.outputPath(), row.messageTtlFilterApplied(), row.createdAt(), now, now));
            return true;
        }
    }

    static class TestChatRepository extends ChatRepository {
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
