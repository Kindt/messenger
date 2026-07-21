package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.chats.dto.ReadReceiptUserInfo;
import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;
import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.port.MessageReadReceiptPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcMessageReadReceiptAdapter implements MessageReadReceiptPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcMessageReadReceiptAdapter.class);
    private final DataSource dataSource;

    public JdbcMessageReadReceiptAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean insert(UUID messageId, UUID userId, Instant readAt) {
        if (dataSource == null) {
            return false;
        }
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            var sql = insertSql(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setObject(1, messageId);
                stmt.setObject(2, userId);
                stmt.setTimestamp(3, Timestamp.from(readAt));
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            log.error("insert read receipt failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public int insertBatch(List<UUID> messageIds, UUID userId, Instant readAt) {
        if (dataSource == null || messageIds == null || messageIds.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            var sql = insertSql(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setObject(2, userId);
                stmt.setTimestamp(3, Timestamp.from(readAt));
                for (var messageId : messageIds) {
                    stmt.setObject(1, messageId);
                    stmt.addBatch();
                }
                var counts = stmt.executeBatch();
                for (var n : counts) {
                    if (n > 0) {
                        inserted += n;
                    } else if (n == Statement.SUCCESS_NO_INFO) {
                        inserted++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("insertBatch read receipts failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return inserted;
    }

    private static String insertSql(java.sql.Connection conn) throws java.sql.SQLException {
        // ON CONFLICT is PostgreSQL; H2 (even MODE=PostgreSQL) does not parse it.
        if (JdbcDialect.isPostgres(conn)) {
            return """
                INSERT INTO message_read_receipts (message_id, user_id, read_at)
                VALUES (?, ?, ?)
                ON CONFLICT (message_id, user_id) DO NOTHING
                """;
        }
        return """
            INSERT INTO message_read_receipts (message_id, user_id, read_at)
            VALUES (?, ?, ?)
            """;
    }

    @Override
    public List<ReadReceiptUserInfo> findByMessageId(UUID messageId, int offset, int limit) {
        if (dataSource == null) {
            return List.of();
        }
        var sql = """
            SELECT r.user_id, u.display_name, r.read_at
            FROM message_read_receipts r
            INNER JOIN users u ON u.id = r.user_id
            WHERE r.message_id = ?
            ORDER BY r.read_at ASC
            LIMIT ? OFFSET ?
            """;
        var rows = new ArrayList<ReadReceiptUserInfo>();
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setObject(1, messageId);
                stmt.setInt(2, Math.max(1, limit));
                stmt.setInt(3, Math.max(0, offset));
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new ReadReceiptUserInfo(
                            rs.getObject("user_id", UUID.class).toString(),
                            rs.getString("display_name"),
                            rs.getTimestamp("read_at").toInstant()));
                    }
                }
            }
        } catch (Exception e) {
            log.error("findByMessageId read receipts failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return rows;
    }

    @Override
    public long countAll() {
        if (dataSource == null) {
            return 0L;
        }
        var sql = """
            SELECT COUNT(*) AS c FROM (
                SELECT 1 FROM message_read_receipts
                LIMIT ?
            ) capped
            """;
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setInt(1, JdbcListLimits.COUNT_CAP_ADMIN);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("c");
                    }
                }
            }
        } catch (Exception e) {
            log.error("countAll read receipts failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return 0L;
    }

    @Override
    public int deleteOlderThanDays(int days) {
        if (dataSource == null || days <= 0) {
            return 0;
        }
        var cutoff = Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
        var sql = """
            DELETE FROM message_read_receipts
            WHERE read_at < ?
            """;
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setTimestamp(1, Timestamp.from(cutoff));
                return stmt.executeUpdate();
            }
        } catch (Exception e) {
            log.error("deleteOlderThanDays read receipts failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }
}
