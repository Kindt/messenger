package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.FileId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcFileMetadataAdapterH2Test {

    private HikariDataSource ds;
    private JdbcFileMetadataAdapter adapter;
    private UUID fileId;
    private UUID ownerId;

    @BeforeEach
    void init() throws Exception {
        fileId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:hex_file_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE file_metadata (
                  id UUID PRIMARY KEY,
                  filename VARCHAR(256),
                  mime_type VARCHAR(128) NOT NULL,
                  size BIGINT NOT NULL,
                  uploaded_by UUID NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO file_metadata (id, filename, mime_type, size, uploaded_by)
                 VALUES (?, ?, ?, ?, ?)
                 """)) {
            ps.setObject(1, fileId);
            ps.setString(2, "bench.txt");
            ps.setString(3, "text/plain");
            ps.setLong(4, 128);
            ps.setObject(5, ownerId);
            ps.executeUpdate();
        }
        adapter = new JdbcFileMetadataAdapter(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findById_mapsRow() {
        var file = adapter.findById(FileId.of(fileId)).orElseThrow();
        assertEquals("bench.txt", file.filename());
        assertEquals("text/plain", file.mimeType());
        assertEquals(128, file.size());
        assertEquals(ownerId, file.uploadedBy().value());
    }
}
