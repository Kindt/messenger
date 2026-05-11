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

class ChatRetentionPolicyRepositoryH2Test {

    private HikariDataSource ds;
    private ChatRetentionPolicyRepository repo;
    private UUID chatId;
    private UUID userId;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:chatret_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE users (
                  id UUID PRIMARY KEY
                )
                """);
            st.execute("""
                CREATE TABLE chats (
                  id UUID PRIMARY KEY,
                  title VARCHAR(256) NOT NULL DEFAULT '',
                  type VARCHAR(16) NOT NULL DEFAULT 'p2p',
                  owner_id UUID REFERENCES users(id)
                )
                """);
            st.execute("""
                CREATE TABLE chat_retention_policy (
                  chat_id UUID PRIMARY KEY REFERENCES chats(id) ON DELETE CASCADE,
                  hot_message_body_max_age_days INT,
                  hot_metadata_min_age_days INT,
                  archive_metadata_enabled BOOLEAN NOT NULL DEFAULT true,
                  deep_archive_enabled BOOLEAN NOT NULL DEFAULT true,
                  legal_hold BOOLEAN NOT NULL DEFAULT false,
                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_by UUID REFERENCES users(id) ON DELETE SET NULL
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chats (id, title, type, owner_id) VALUES (?, 't', 'group', ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
        repo = new ChatRetentionPolicyRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findByChatId_emptyWhenNoRow() {
        assertTrue(repo.findByChatId(chatId).isEmpty());
    }

    @Test
    void upsert_insertsThenUpdates() throws Exception {
        assertTrue(repo.upsert(chatId, 5, 1, false, true, false, userId));
        var first = repo.findByChatId(chatId).orElseThrow();
        assertEquals(5, first.hotMessageBodyMaxAgeDays());
        assertEquals(1, first.hotMetadataMinAgeDays());
        assertFalse(first.archiveMetadataEnabled());
        assertTrue(first.deepArchiveEnabled());
        assertFalse(first.legalHold());
        assertEquals(userId.toString(), first.updatedBy());

        assertTrue(repo.upsert(chatId, null, null, true, false, true, userId));
        var second = repo.findByChatId(chatId).orElseThrow();
        assertNull(second.hotMessageBodyMaxAgeDays());
        assertNull(second.hotMetadataMinAgeDays());
        assertTrue(second.archiveMetadataEnabled());
        assertFalse(second.deepArchiveEnabled());
        assertTrue(second.legalHold());
    }

    @Test
    void findByChatId_returnsRow() throws Exception {
        var ts = Instant.parse("2025-03-01T00:00:00Z");
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO chat_retention_policy (
                   chat_id, hot_message_body_max_age_days, hot_metadata_min_age_days,
                   archive_metadata_enabled, deep_archive_enabled, legal_hold, updated_at, updated_by
                 ) VALUES (?, 14, NULL, true, false, true, ?, ?)
                 """)) {
            ps.setObject(1, chatId);
            ps.setObject(2, java.sql.Timestamp.from(ts));
            ps.setObject(3, userId);
            ps.executeUpdate();
        }
        var opt = repo.findByChatId(chatId);
        assertTrue(opt.isPresent());
        var r = opt.get();
        assertEquals(chatId, r.chatId());
        assertEquals(14, r.hotMessageBodyMaxAgeDays());
        assertNull(r.hotMetadataMinAgeDays());
        assertTrue(r.archiveMetadataEnabled());
        assertFalse(r.deepArchiveEnabled());
        assertTrue(r.legalHold());
        assertEquals(userId.toString(), r.updatedBy());
    }
}
