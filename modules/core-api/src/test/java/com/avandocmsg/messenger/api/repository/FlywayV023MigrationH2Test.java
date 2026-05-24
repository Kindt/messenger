package com.avandocmsg.messenger.api.repository;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V023 renames {@code ttl_seconds} → {@code visibility_ttl_seconds} on existing rows.
 */
class FlywayV023MigrationH2Test {

    @Test
    void v023RenamePreservesExistingMessageTtl() throws Exception {
        var msgId = UUID.randomUUID();
        var chatId = UUID.randomUUID();
        var senderId = UUID.randomUUID();
        var jdbcUrl = "jdbc:h2:mem:flyway_v023_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        try (var conn = java.sql.DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute("CREATE TABLE chats (id UUID PRIMARY KEY)");
            st.execute("""
                CREATE TABLE messages (
                  id UUID PRIMARY KEY,
                  chat_id UUID NOT NULL,
                  sender_id UUID NOT NULL,
                  type VARCHAR(16) NOT NULL DEFAULT 'text',
                  content TEXT,
                  deleted BOOLEAN NOT NULL DEFAULT false,
                  ttl_seconds INT,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("INSERT INTO users (id) VALUES ('" + senderId + "')");
            st.execute("INSERT INTO chats (id) VALUES ('" + chatId + "')");
            st.execute("""
                INSERT INTO messages (id, chat_id, sender_id, type, content, ttl_seconds)
                VALUES ('%s', '%s', '%s', 'text', 'legacy', 90)
                """.formatted(msgId, chatId, senderId));

            var migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V023__rename_ttl_to_visibility.sql"), StandardCharsets.UTF_8);
            st.execute(migration);

            try (var rs = st.executeQuery(
                "SELECT visibility_ttl_seconds FROM messages WHERE id = '" + msgId + "'")) {
                assertTrue(rs.next());
                assertEquals(90, rs.getInt(1));
            }
        }
    }
}
