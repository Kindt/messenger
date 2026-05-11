package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileRepositoryH2Test {

    private HikariDataSource ds;
    private FileRepository repo;
    private UUID userId;

    @BeforeEach
    void init() throws Exception {
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:fmeta_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute("""
                CREATE TABLE file_metadata (
                  id UUID PRIMARY KEY,
                  filename VARCHAR(512) NOT NULL DEFAULT '',
                  mime_type VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
                  size BIGINT NOT NULL DEFAULT 0,
                  uploaded_by UUID NOT NULL REFERENCES users(id),
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
        repo = new FileRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void insert_findById_delete() {
        var fileId = UUID.randomUUID();
        var inserted = repo.insert(fileId, "doc.pdf", "application/pdf", 42L, userId);
        assertNotNull(inserted);
        assertEquals(fileId.toString(), inserted.id());
        assertEquals("doc.pdf", inserted.filename());
        assertEquals(42L, inserted.size());
        assertEquals(userId.toString(), inserted.uploadedBy());

        var found = repo.findById(fileId).orElseThrow();
        assertEquals("doc.pdf", found.filename());
        assertEquals("application/pdf", found.mimeType());

        assertTrue(repo.delete(fileId));
        assertTrue(repo.findById(fileId).isEmpty());
        assertFalse(repo.delete(fileId));
    }
}
