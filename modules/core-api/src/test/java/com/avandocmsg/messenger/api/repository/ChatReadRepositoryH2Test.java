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
 * {@link ChatReadRepository#countUnreadFromOthers} в H2 с {@code MODE=PostgreSQL} (каст {@code COUNT(*)::int}).
 * Состояние {@code chat_read_state} задаётся через JDBC — {@link ChatReadRepository#upsertLastRead} использует
 * {@code ON CONFLICT … DO UPDATE}, который в H2 не парсится как в PostgreSQL.
 */
class ChatReadRepositoryH2Test {

    private HikariDataSource ds;
    private ChatReadRepository repo;
    private UUID viewerId;
    private UUID otherId;
    private UUID chatId;
    private UUID msgOld;
    private UUID msgNew;

    @BeforeEach
    void init() throws Exception {
        viewerId = UUID.randomUUID();
        otherId = UUID.randomUUID();
        chatId = UUID.randomUUID();
        msgOld = UUID.randomUUID();
        msgNew = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:readst_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute("""
                CREATE TABLE chats (
                  id UUID PRIMARY KEY,
                  title VARCHAR(256) NOT NULL DEFAULT '',
                  type VARCHAR(16) NOT NULL DEFAULT 'group',
                  owner_id UUID REFERENCES users(id)
                )
                """);
            st.execute("""
                CREATE TABLE messages (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  sender_id UUID NOT NULL REFERENCES users(id),
                  client_msg_id VARCHAR(64),
                  type VARCHAR(16) NOT NULL DEFAULT 'text',
                  content TEXT,
                  reply_to_msg_id UUID,
                  deleted BOOLEAN NOT NULL DEFAULT false,
                  visibility_ttl_seconds INT,
                  created_at TIMESTAMP NOT NULL,
                  edited_at TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE chat_read_state (
                  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                  chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
                  last_read_message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (user_id, chat_id)
                )
                """);
        }
        insertUser(viewerId);
        insertUser(otherId);
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chats (id, title, type, owner_id) VALUES (?, 't', 'group', ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, viewerId);
            ps.executeUpdate();
        }
        insertMessage(msgOld, chatId, otherId, "a", Instant.parse("2024-01-01T10:00:00Z"));
        insertMessage(msgNew, chatId, otherId, "b", Instant.parse("2024-01-01T11:00:00Z"));
        repo = new ChatReadRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void countUnreadFromOthers_noReadState_countsAllFromOthers() {
        assertEquals(2, repo.countUnreadFromOthers(viewerId, chatId));
    }

    @Test
    void lastReadStateViaJdbc_reducesUnreadCount() throws Exception {
        assertEquals(2, repo.countUnreadFromOthers(viewerId, chatId));
        insertReadState(viewerId, chatId, msgOld);
        assertEquals(1, repo.countUnreadFromOthers(viewerId, chatId));
        updateReadStateLastMessage(viewerId, chatId, msgNew);
        assertEquals(0, repo.countUnreadFromOthers(viewerId, chatId));
    }

    private void insertUser(UUID id) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private void insertReadState(UUID userId, UUID chatId, UUID lastReadMessageId) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO chat_read_state (user_id, chat_id, last_read_message_id, updated_at)
                 VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                 """)) {
            ps.setObject(1, userId);
            ps.setObject(2, chatId);
            ps.setObject(3, lastReadMessageId);
            ps.executeUpdate();
        }
    }

    private void updateReadStateLastMessage(UUID userId, UUID chatId, UUID lastReadMessageId) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 UPDATE chat_read_state SET last_read_message_id = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND chat_id = ?
                 """)) {
            ps.setObject(1, lastReadMessageId);
            ps.setObject(2, userId);
            ps.setObject(3, chatId);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private void insertMessage(UUID id, UUID chat, UUID sender, String content, Instant createdAt) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO messages (id, chat_id, sender_id, type, content, deleted, visibility_ttl_seconds, created_at)
                  VALUES (?, ?, ?, 'text', ?, false, NULL, ?)
                 """)) {
            ps.setObject(1, id);
            ps.setObject(2, chat);
            ps.setObject(3, sender);
            ps.setString(4, content);
            ps.setTimestamp(5, java.sql.Timestamp.from(createdAt));
            ps.executeUpdate();
        }
    }
}
