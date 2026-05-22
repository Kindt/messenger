package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageRepositoryH2Test {

    private HikariDataSource ds;
    private MessageRepository repo;
    private UUID chatId;
    private UUID senderId;

    @BeforeEach
    void init() throws Exception {
        chatId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:msgrepo_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
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
                  ttl_seconds INT,
                  attachment_file_id UUID,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  edited_at TIMESTAMP
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, senderId);
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO chats (id, title, type, owner_id) VALUES (?, 't', 'group', ?)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, senderId);
            ps.executeUpdate();
        }
        repo = new MessageRepository(ds, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void insert_findById_findByChatId_findLatestMessageId() throws Exception {
        var m1 = UUID.randomUUID();
        var m2 = UUID.randomUUID();
        assertNotNull(repo.insert(m1, chatId, senderId, "text", "one", null, "c1", null));
        assertNotNull(repo.insert(m2, chatId, senderId, "text", "two", null, "c2", null));
        // Оба insert используют now(); без явных меток ORDER BY / latest нестабильны между драйверами/мс.
        setMessageCreatedAt(m1, Instant.parse("2024-01-01T10:00:00Z"));
        setMessageCreatedAt(m2, Instant.parse("2024-01-01T11:00:00Z"));

        var one = repo.findById(m1).orElseThrow();
        assertEquals("one", one.content());
        assertNull(one.ttlSeconds());

        var list = repo.findByChatId(chatId, 10, null);
        assertEquals(2, list.size());
        assertEquals("two", list.get(0).content());
        assertEquals("one", list.get(1).content());

        assertEquals(m2, repo.findLatestMessageId(chatId).orElseThrow());
    }

    @Test
    void findById_emptyWhenTtlExpired() throws Exception {
        var msgId = UUID.randomUUID();
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("""
                 INSERT INTO messages (id, chat_id, sender_id, type, content, deleted, ttl_seconds, created_at)
                 VALUES (?, ?, ?, 'text', 'old', false, 60, TIMESTAMPADD(MINUTE, -5, CURRENT_TIMESTAMP))
                 """)) {
            ps.setObject(1, msgId);
            ps.setObject(2, chatId);
            ps.setObject(3, senderId);
            ps.executeUpdate();
        }
        assertTrue(repo.findById(msgId).isEmpty());
    }

    @Test
    void findLatestMessageRef_matchesPlaintextContentOrAttachmentFileId() throws Exception {
        var viewerId = UUID.randomUUID();
        var fileId = UUID.randomUUID();
        var plainMsgId = UUID.randomUUID();
        var e2eeMsgId = UUID.randomUUID();
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE chat_members (
                  chat_id UUID NOT NULL,
                  user_id UUID NOT NULL,
                  banned BOOLEAN NOT NULL DEFAULT FALSE
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
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            ps.setObject(1, viewerId);
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO chat_members (chat_id, user_id, banned) VALUES (?, ?, false)")) {
            ps.setObject(1, chatId);
            ps.setObject(2, senderId);
            ps.executeUpdate();
            ps.setObject(2, viewerId);
            ps.executeUpdate();
        }
        assertNotNull(repo.insert(plainMsgId, chatId, senderId, "file", fileId.toString(), null, null, null));
        assertNotNull(repo.insert(e2eeMsgId, chatId, senderId, "e2ee-file", "ciphertext", null, null, null,
            fileId));
        setMessageCreatedAt(plainMsgId, Instant.parse("2024-01-01T10:00:00Z"));
        setMessageCreatedAt(e2eeMsgId, Instant.parse("2024-01-01T11:00:00Z"));

        var ref = repo.findLatestMessageRefForViewer(fileId, viewerId).orElseThrow();
        assertEquals(e2eeMsgId, ref.messageId());
    }

    private void setMessageCreatedAt(UUID messageId, Instant createdAt) throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("UPDATE messages SET created_at = ? WHERE id = ?")) {
            ps.setTimestamp(1, Timestamp.from(createdAt));
            ps.setObject(2, messageId);
            assertEquals(1, ps.executeUpdate());
        }
    }
}
