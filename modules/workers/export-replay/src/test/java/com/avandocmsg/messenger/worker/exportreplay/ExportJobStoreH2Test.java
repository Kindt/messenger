package com.avandocmsg.messenger.worker.exportreplay;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import com.avandocmsg.messenger.common.i18n.WorkerMessageSources;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportJobStoreH2Test {

    private HikariDataSource ds;
    private ExportJobStore store;
    private UUID jobId;

    @BeforeEach
    void init() throws Exception {
        jobId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:export_store_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE export_jobs (
                  id UUID PRIMARY KEY,
                  status VARCHAR(32) NOT NULL,
                  output_path TEXT,
                  message_ttl_filter_applied BOOLEAN,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  completed_at TIMESTAMP
                )
                """);
            st.execute("INSERT INTO export_jobs (id, status) VALUES ('" + jobId + "', 'queued')");
        }
        store = new ExportJobStore(ds, WorkerMessageSources.forWorker(
            ExportReplayWorker.class, "com.avandocmsg.messenger.i18n.messages_worker_export_replay"));
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void markProcessingIfQueued_succeedsOnlyWhenQueued() {
        assertTrue(store.markProcessingIfQueued(jobId));
        assertEquals("processing", store.loadStatus(jobId).orElseThrow());
        assertFalse(store.markProcessingIfQueued(jobId));
    }

    @Test
    void markTerminal_doesNotOverwriteCancelled() throws Exception {
        assertTrue(store.markProcessingIfQueued(jobId));
        try (var c = ds.getConnection(); var st = c.prepareStatement(
            "UPDATE export_jobs SET status = 'export_cancelled' WHERE id = ?")) {
            st.setObject(1, jobId);
            st.executeUpdate();
        }
        store.markTerminal(jobId, "export_v1", "/out.json", false);
        assertEquals("export_cancelled", store.loadStatus(jobId).orElseThrow());
        assertTrue(store.isCancelled(jobId));
    }
}
