package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.port.ChatReadStatePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.UUID;

import static com.avandocmsg.messenger.core.adapter.persistence.MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE;

public final class JdbcChatReadStateAdapter implements ChatReadStatePort {
    private static final Logger log = LoggerFactory.getLogger(JdbcChatReadStateAdapter.class);
    private final DataSource dataSource;

    public JdbcChatReadStateAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean upsertLastRead(UUID userId, UUID chatId, UUID lastReadMessageId) {
        if (dataSource == null) {
            return false;
        }
        try (var conn = dataSource.getConnection()) {
            var sql = upsertSql(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setObject(1, userId);
                stmt.setObject(2, chatId);
                stmt.setObject(3, lastReadMessageId);
                stmt.executeUpdate();
                return true;
            }
        } catch (Exception e) {
            log.error("upsertLastRead failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    private static String upsertSql(java.sql.Connection conn) throws java.sql.SQLException {
        if (JdbcDialect.isPostgres(conn)) {
            return """
                INSERT INTO chat_read_state (user_id, chat_id, last_read_message_id, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (user_id, chat_id) DO UPDATE SET
                  last_read_message_id = EXCLUDED.last_read_message_id,
                  updated_at = now()
                """;
        }
        // H2 (incl. MODE=PostgreSQL): MERGE is the portable upsert
        return """
            MERGE INTO chat_read_state (user_id, chat_id, last_read_message_id, updated_at)
            KEY (user_id, chat_id)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;
    }

    @Override
    public int countUnreadFromOthers(UUID userId, UUID chatId) {
        if (dataSource == null) {
            return 0;
        }
        var sql = """
            SELECT COUNT(*)::int FROM messages m
            WHERE m.chat_id = ?
              AND m.deleted = false
              AND """ + MSG_VISIBILITY_TTL_VISIBLE + """
              AND m.sender_id <> ?
              AND (
                NOT EXISTS (SELECT 1 FROM chat_read_state s WHERE s.user_id = ? AND s.chat_id = ?)
                OR EXISTS (
                  SELECT 1 FROM chat_read_state s
                  WHERE s.user_id = ? AND s.chat_id = ? AND s.last_read_message_id IS NULL
                )
                OR m.created_at > (
                  SELECT m2.created_at FROM chat_read_state s
                  INNER JOIN messages m2 ON m2.id = s.last_read_message_id
                  WHERE s.user_id = ? AND s.chat_id = ?
                )
              )
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            int i = 1;
            stmt.setObject(i++, chatId);
            stmt.setObject(i++, userId);
            stmt.setObject(i++, userId);
            stmt.setObject(i++, chatId);
            stmt.setObject(i++, userId);
            stmt.setObject(i++, chatId);
            stmt.setObject(i++, userId);
            stmt.setObject(i++, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("countUnread failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return 0;
    }
}
