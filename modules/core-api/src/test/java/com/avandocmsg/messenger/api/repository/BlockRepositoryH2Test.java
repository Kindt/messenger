package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Читает/удаляет блокировки через {@link BlockRepository}; строки в {@code blocks} создаются прямым INSERT —
 * H2 в тестах не совместим с {@code INSERT … ON CONFLICT DO NOTHING} из {@link BlockRepository#block}.
 */
class BlockRepositoryH2Test {

    private HikariDataSource ds;
    private BlockRepository repo;
    private UUID blockerId;
    private UUID blockedId;

    @BeforeEach
    void init() throws Exception {
        blockerId = UUID.randomUUID();
        blockedId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:blockrepo_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY,
                  username VARCHAR(32) NOT NULL,
                  display_name VARCHAR(128) NOT NULL
                )
                """);
            st.execute("""
                CREATE TABLE blocks (
                  blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (blocker_id, blocked_id)
                )
                """);
        }
        insertUser(blockerId, "a", "A");
        insertUser(blockedId, "b", "B");
        repo = new BlockRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void exists_list_unblock_afterJdbcInsert() throws Exception {
        insertBlock(blockerId, blockedId, Instant.parse("2024-06-01T12:00:00Z"));
        assertTrue(repo.exists(blockerId, blockedId));
        var list = repo.listBlockedUsers(blockerId);
        assertEquals(1, list.size());
        assertEquals(blockedId.toString(), list.get(0).userId());
        assertTrue(repo.unblock(blockerId, blockedId));
        assertFalse(repo.exists(blockerId, blockedId));
        assertFalse(repo.unblock(blockerId, blockedId));
    }

    @Test
    void listBlockedUsers_ordersByCreatedDesc() throws Exception {
        var c2 = UUID.randomUUID();
        insertUser(c2, "c2", "C2");
        insertBlock(blockerId, blockedId, Instant.parse("2024-01-01T00:00:00Z"));
        insertBlock(blockerId, c2, Instant.parse("2025-01-01T00:00:00Z"));
        var list = repo.listBlockedUsers(blockerId);
        assertEquals(2, list.size());
        assertEquals(c2.toString(), list.get(0).userId());
        assertEquals("C2", list.get(0).displayName());
        assertEquals(blockedId.toString(), list.get(1).userId());
    }

    private void insertUser(UUID id, String username, String display) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id, username, display_name) VALUES (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, username);
            ps.setString(3, display);
            ps.executeUpdate();
        }
    }

    private void insertBlock(UUID blocker, UUID blocked, Instant createdAt) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)")) {
            ps.setObject(1, blocker);
            ps.setObject(2, blocked);
            ps.setTimestamp(3, java.sql.Timestamp.from(createdAt));
            ps.executeUpdate();
        }
    }
}
