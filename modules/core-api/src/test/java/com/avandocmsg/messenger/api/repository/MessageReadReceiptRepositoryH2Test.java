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
 * Query-side tests for {@link MessageReadReceiptRepository}. Inserts use JDBC directly because
 * {@code ON CONFLICT} in {@link MessageReadReceiptRepository#insert} is PostgreSQL-specific (see {@link ChatReadRepositoryH2Test}).
 */
class MessageReadReceiptRepositoryH2Test {

    private HikariDataSource ds;
    private MessageReadReceiptRepository repo;
    private UUID userId;
    private UUID messageId;

    @BeforeEach
    void init() throws Exception {
        userId = UUID.randomUUID();
        messageId = UUID.randomUUID();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:rr_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY, display_name VARCHAR(128) NOT NULL DEFAULT 'u')");
            st.execute("""
                CREATE TABLE messages (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  sender_id UUID NOT NULL,
                  type VARCHAR(16) NOT NULL DEFAULT 'text',
                  content TEXT,
                  deleted BOOLEAN NOT NULL DEFAULT false,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE message_read_receipts (
                  message_id UUID NOT NULL,
                  user_id UUID NOT NULL,
                  read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (message_id, user_id)
                )
                """);
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO users (id, display_name) VALUES (?, 'Alice')")) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        }
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("INSERT INTO messages (id, chat_id, sender_id) VALUES (?, ?, ?)")) {
            ps.setObject(1, messageId);
            ps.setObject(2, UUID.randomUUID());
            ps.setObject(3, UUID.randomUUID());
            ps.executeUpdate();
        }
        repo = new MessageReadReceiptRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findByMessageId_joinsUserDisplayName() throws Exception {
        var at = Instant.parse("2025-01-01T12:00:00Z");
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO message_read_receipts (message_id, user_id, read_at) VALUES (?, ?, ?)")) {
            ps.setObject(1, messageId);
            ps.setObject(2, userId);
            ps.setTimestamp(3, java.sql.Timestamp.from(at));
            ps.executeUpdate();
        }
        var rows = repo.findByMessageId(messageId, 0, 10);
        assertEquals(1, rows.size());
        assertEquals(userId.toString(), rows.get(0).userId());
        assertEquals("Alice", rows.get(0).displayName());
    }

    @Test
    void countAll_andDeleteOlderThan() throws Exception {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(
                 "INSERT INTO message_read_receipts (message_id, user_id, read_at) VALUES (?, ?, TIMESTAMP '2020-01-01 00:00:00')")) {
            ps.setObject(1, messageId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }
        assertEquals(1L, repo.countAll());
        assertEquals(1, repo.deleteOlderThanDays(30));
        assertEquals(0L, repo.countAll());
    }
}
