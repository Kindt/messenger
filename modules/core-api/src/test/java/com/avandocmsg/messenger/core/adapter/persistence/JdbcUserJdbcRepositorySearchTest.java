package com.avandocmsg.messenger.core.adapter.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcUserJdbcRepositorySearchTest {

    private HikariDataSource ds;
    private JdbcUserJdbcRepository repo;
    private UUID viewerId;
    private UUID targetId;

    @BeforeEach
    void init() throws Exception {
        viewerId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:user_search_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  username VARCHAR(32) NOT NULL,
                  display_name VARCHAR(128) NOT NULL,
                  phone VARCHAR(20),
                  email VARCHAR(256),
                  external_id VARCHAR(128),
                  hidden BOOLEAN NOT NULL DEFAULT false,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  presence_status VARCHAR(16) NOT NULL DEFAULT 'offline',
                  last_seen_at TIMESTAMP,
                  org_id UUID,
                  privacy_disable_read_receipts BOOLEAN NOT NULL DEFAULT false,
                  ui_locale VARCHAR(8),
                  custom_status_text VARCHAR(128) NOT NULL DEFAULT '',
                  dnd_until TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE blocks (
                  blocker_id UUID NOT NULL,
                  blocked_id UUID NOT NULL,
                  PRIMARY KEY (blocker_id, blocked_id)
                )
                """);
        }
        repo = new JdbcUserJdbcRepository(ds);
        repo.create(viewerId, "viewer", "Viewer User");
        repo.create(targetId, "alice-smith", "Alice Smith");
        var hiddenId = UUID.randomUUID();
        repo.create(hiddenId, "bob", "Bob Hidden");
        repo.setActive(hiddenId, false);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void userSearchClause_usesLowerLikeOnPostgres() {
        var clause = JdbcUserJdbcRepository.userSearchClause(true, "username", "display_name");
        assertTrue(clause.contains("lower(username) LIKE lower(?)"));
        assertTrue(clause.contains("lower(display_name) LIKE lower(?)"));
        assertFalse(clause.contains("ILIKE"));
    }

    @Test
    void userSearchClause_fallsBackToPositionOnH2() {
        var clause = JdbcUserJdbcRepository.userSearchClause(false, "u.username", "u.display_name");
        assertTrue(clause.contains("POSITION"));
        assertTrue(clause.contains("u.username"));
        assertTrue(clause.contains("u.display_name"));
    }

    @Test
    void userSearchBindValue_wrapsPatternOnPostgres() {
        assertEquals("%alice%", JdbcUserJdbcRepository.userSearchBindValue(true, "alice"));
    }

    @Test
    void userSearchBindValue_lowercasesTermOnH2() {
        assertEquals("alice", JdbcUserJdbcRepository.userSearchBindValue(false, " Alice "));
    }

    @Test
    void search_findsByUsernameSubstringOnH2() {
        var hits = repo.search("alice", 10);
        assertEquals(1, hits.size());
        assertEquals("alice-smith", hits.get(0).username());
    }

    @Test
    void search_findsByDisplayNameSubstringOnH2() {
        var hits = repo.search("smith", 10);
        assertEquals(1, hits.size());
        assertEquals("Alice Smith", hits.get(0).displayName());
    }

    @Test
    void searchForViewer_excludesSelf() {
        var hits = repo.searchForViewer(viewerId, "viewer", 10);
        assertTrue(hits.isEmpty());

        var alice = repo.searchForViewer(viewerId, "alice", 10);
        assertEquals(1, alice.size());
        assertEquals("alice-smith", alice.get(0).username());
    }

    @Test
    void search_excludesHiddenUsers() {
        assertTrue(repo.search("bob", 10).isEmpty());
    }
}
