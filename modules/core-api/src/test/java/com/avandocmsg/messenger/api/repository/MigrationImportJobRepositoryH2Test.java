package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.admin.MigrationImportProcessor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationImportJobRepositoryH2Test {

    private HikariDataSource ds;
    private MigrationImportJobRepository repository;
    private UUID orgId;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:mig_import_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE migration_import_jobs (
                  id UUID PRIMARY KEY,
                  org_id UUID NOT NULL,
                  source VARCHAR(32) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  config_json JSONB,
                  result_json JSONB,
                  created_by UUID,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        repository = new MigrationImportJobRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void insertAndProcessJob() {
        var id = repository.insert(orgId, "telegram_export_v1", "{}", userId);
        var processor = new MigrationImportProcessor(repository);
        var done = processor.process(id);
        assertEquals("completed", done.orElseThrow().status());
        assertEquals("completed", repository.findById(id).orElseThrow().status());
    }
}
