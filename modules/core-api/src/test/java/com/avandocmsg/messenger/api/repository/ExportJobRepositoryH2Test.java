package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportJobRepositoryH2Test {

    private HikariDataSource ds;
    private ExportJobRepository repo;
    private UUID chatId;
    private UUID userId;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:export_jobs_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute("CREATE TABLE chats (id UUID PRIMARY KEY)");
            st.execute("""
                CREATE TABLE export_jobs (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  requested_by UUID NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'queued',
                  output_path TEXT,
                  message_ttl_filter_applied BOOLEAN,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  completed_at TIMESTAMP WITH TIME ZONE
                )
                """);
            st.execute("INSERT INTO users (id) VALUES ('" + userId + "')");
            st.execute("INSERT INTO chats (id) VALUES ('" + chatId + "')");
        }
        repo = new ExportJobRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void insertFindAndMarkTerminal() {
        var jobId = UUID.randomUUID();
        repo.insertQueued(jobId, chatId, userId);
        var row = repo.findByIdAndChat(jobId, chatId);
        assertTrue(row.isPresent());
        assertEquals("queued", row.get().status());

        repo.markProcessing(jobId);
        assertEquals("processing", repo.findByIdAndChat(jobId, chatId).orElseThrow().status());

        repo.markTerminal(jobId, "export_v1", "/export/job.json", true);
        var done = repo.findByIdAndChat(jobId, chatId).orElseThrow();
        assertEquals("export_v1", done.status());
        assertEquals("/export/job.json", done.outputPath());
        assertEquals(true, done.messageTtlFilterApplied());
        assertNotNull(done.completedAt());
    }

    @Test
    void listForChat_ordersNewestFirst_andFiltersStatus() {
        var oldId = UUID.randomUUID();
        var newId = UUID.randomUUID();
        repo.insertQueued(oldId, chatId, userId);
        repo.markTerminal(oldId, "export_v1", "/old.json");
        repo.insertQueued(newId, chatId, userId);
        repo.markTerminal(newId, "export_failed", null);

        var all = repo.listForChat(chatId, null, 10);
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(r -> r.id().equals(newId)));
        assertTrue(all.stream().anyMatch(r -> r.id().equals(oldId)));

        var failedOnly = repo.listForChat(chatId, "export_failed", 10);
        assertEquals(1, failedOnly.size());
        assertEquals("export_failed", failedOnly.get(0).status());
    }

    @Test
    void cancelIfQueued_onlyWhenQueued() {
        var jobId = UUID.randomUUID();
        repo.insertQueued(jobId, chatId, userId);
        assertTrue(repo.cancelIfQueued(jobId, chatId));
        assertEquals("export_cancelled", repo.findByIdAndChat(jobId, chatId).orElseThrow().status());
        assertFalse(repo.hasBlockingJobForChat(chatId, 1440));

        var jobId2 = UUID.randomUUID();
        repo.insertQueued(jobId2, chatId, userId);
        repo.markProcessing(jobId2);
        assertFalse(repo.cancelIfQueued(jobId2, chatId));
    }

    @Test
    void listRecent_filtersStatusAndChat() {
        var chatA = chatId;
        var chatB = UUID.randomUUID();
        repo.insertQueued(UUID.randomUUID(), chatA, userId);
        repo.insertQueued(UUID.randomUUID(), chatB, userId);
        var processingId = UUID.randomUUID();
        repo.insertQueued(processingId, chatA, userId);
        repo.markProcessing(processingId);

        var processingOnly = repo.listRecent("processing", null, 10);
        assertEquals(1, processingOnly.size());
        assertEquals(processingId, processingOnly.get(0).id());

        var chatBOnly = repo.listRecent(null, chatB, 10);
        assertEquals(1, chatBOnly.size());
        assertEquals(chatB, chatBOnly.get(0).chatId());
    }

    @Test
    void cancelIfActive_cancelsProcessing() {
        var jobId = UUID.randomUUID();
        repo.insertQueued(jobId, chatId, userId);
        repo.markProcessing(jobId);
        assertTrue(repo.cancelIfActive(jobId, chatId));
        assertEquals("export_cancelled", repo.findByIdAndChat(jobId, chatId).orElseThrow().status());
    }

    @Test
    void hasBlockingJobForChat_pendingAndCooldown() {
        var jobId = UUID.randomUUID();
        repo.insertQueued(jobId, chatId, userId);
        assertTrue(repo.hasBlockingJobForChat(chatId, 1440));

        repo.markTerminal(jobId, "export_v1", "/x.json");
        assertTrue(repo.hasBlockingJobForChat(chatId, 1440));

        var failedId = UUID.randomUUID();
        repo.insertQueued(failedId, chatId, userId);
        repo.markTerminal(failedId, "export_failed", null);
        assertFalse(repo.hasBlockingJobForChat(chatId, 1440));
    }

    @Test
    void applyCompleteIfPending_onlyWhenQueuedOrProcessing() {
        var jobId = UUID.randomUUID();
        repo.insertQueued(jobId, chatId, userId);
        assertTrue(repo.applyCompleteIfPending(jobId, "export_v1", "/export/a.json", false));
        assertEquals("export_v1", repo.findByIdAndChat(jobId, chatId).orElseThrow().status());
        assertEquals(false, repo.findByIdAndChat(jobId, chatId).orElseThrow().messageTtlFilterApplied());

        var jobId2 = UUID.randomUUID();
        repo.insertQueued(jobId2, chatId, userId);
        repo.markProcessing(jobId2);
        assertTrue(repo.applyCompleteIfPending(jobId2, "stub_written", "/export/b.json"));

        assertFalse(repo.applyCompleteIfPending(jobId, "export_failed", "/other.json"));
        assertFalse(repo.applyCompleteIfPending(jobId, "invalid", "/x.json"));
    }
}
