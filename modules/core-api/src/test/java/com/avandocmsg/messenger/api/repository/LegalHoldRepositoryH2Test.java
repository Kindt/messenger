package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LegalHoldRepositoryH2Test {

    private HikariDataSource ds;
    private LegalHoldRepository repo;
    private UUID orgId;

    @BeforeEach
    void init() throws Exception {
        orgId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:lh_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE org_retention_policy (
                  org_id UUID PRIMARY KEY,
                  archive_metadata_enabled BOOLEAN NOT NULL DEFAULT true,
                  deep_archive_enabled BOOLEAN NOT NULL DEFAULT true,
                  legal_hold BOOLEAN NOT NULL DEFAULT false,
                  legal_hold_files BOOLEAN NOT NULL DEFAULT false,
                  legal_hold_deep_archive BOOLEAN NOT NULL DEFAULT false,
                  updated_at TIMESTAMP NOT NULL,
                  updated_by UUID
                )
                """);
        }
        repo = new LegalHoldRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void upsertOrg_persistsExtendedFlags() {
        var actor = UUID.randomUUID();
        var row = new LegalHoldRepository.LegalHoldRow(true, true, false);
        assertTrue(repo.upsertOrg(orgId, row, actor));
        var loaded = repo.findOrg(orgId).orElseThrow();
        assertTrue(loaded.legalHold());
        assertTrue(loaded.legalHoldFiles());
        assertFalse(loaded.legalHoldDeepArchive());
    }
}
