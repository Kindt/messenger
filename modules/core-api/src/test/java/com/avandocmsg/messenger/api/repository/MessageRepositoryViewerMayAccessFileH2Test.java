package com.avandocmsg.messenger.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 regression for {@link MessageRepository#viewerMayAccessFileViaSharedNonE2eeMessage(UUID, UUID)} (shared file ACL).
 */
class MessageRepositoryViewerMayAccessFileH2Test {

    private final UUID chatId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();
    private final UUID msgId = UUID.randomUUID();

    private HikariDataSource ds;
    private MessageRepository repo;

    @BeforeEach
    void init() throws Exception {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:file_acl_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
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
            st.execute("""
                CREATE TABLE messages (
                  id UUID NOT NULL PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  sender_id UUID NOT NULL,
                  type VARCHAR(64) NOT NULL,
                  content VARCHAR(4096),
                  attachment_file_id UUID,
                  deleted BOOLEAN NOT NULL DEFAULT FALSE,
                  ttl_seconds INT,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
        insertMember(chatId, senderId, false);
        insertMember(chatId, viewerId, false);
        insertMessage(msgId, chatId, senderId, "text", "  " + fileId + "  ", false);
        repo = new MessageRepository(ds, Clock.systemUTC());
    }

    private void insertMember(UUID chat, UUID user, boolean banned) throws Exception {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO chat_members (chat_id, user_id, banned) VALUES (?, ?, ?)")) {
            ps.setObject(1, chat);
            ps.setObject(2, user);
            ps.setBoolean(3, banned);
            ps.executeUpdate();
        }
    }

    private void insertMessage(UUID id, UUID chat, UUID sender, String type, String content, boolean deleted) throws Exception {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement(
                 "INSERT INTO messages (id, chat_id, sender_id, type, content, deleted) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, chat);
            ps.setObject(3, sender);
            ps.setString(4, type);
            ps.setString(5, content);
            ps.setBoolean(6, deleted);
            ps.executeUpdate();
        }
    }

    private void insertBlock(UUID blocker, UUID blocked) throws Exception {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)")) {
            ps.setObject(1, blocker);
            ps.setObject(2, blocked);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void viewerMayAccess_whenSharedNonE2eeMessage() {
        assertTrue(repo.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId));
    }

    @Test
    void viewerMayAccess_falseWhenViewerBanned() throws Exception {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement(
                 "UPDATE chat_members SET banned = true WHERE chat_id = ? AND user_id = ?")) {
            ps.setObject(1, chatId);
            ps.setObject(2, viewerId);
            ps.executeUpdate();
        }
        assertFalse(repo.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId));
    }

    @Test
    void viewerMayAccess_falseWhenBlockedWithSender() throws Exception {
        insertBlock(viewerId, senderId);
        assertFalse(repo.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId));
    }

    @Test
    void viewerMayAccess_falseForE2eeTypeWithoutAttachment() throws Exception {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("UPDATE messages SET type = 'e2ee-text' WHERE id = ?")) {
            ps.setObject(1, msgId);
            ps.executeUpdate();
        }
        assertFalse(repo.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId));
    }

    @Test
    void viewerMayAccess_trueForE2eeFileWhenAttachmentFileIdSet() throws Exception {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement(
                 "UPDATE messages SET type = 'e2ee-file', content = 'cipher', attachment_file_id = ? WHERE id = ?")) {
            ps.setObject(1, fileId);
            ps.setObject(2, msgId);
            ps.executeUpdate();
        }
        assertTrue(repo.viewerMayAccessFileViaSharedNonE2eeMessage(fileId, viewerId));
    }
}
