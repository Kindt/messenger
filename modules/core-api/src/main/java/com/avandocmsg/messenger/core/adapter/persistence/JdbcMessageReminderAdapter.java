package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.port.MessageReminderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcMessageReminderAdapter implements MessageReminderPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcMessageReminderAdapter.class);
    private final DataSource dataSource;

    public JdbcMessageReminderAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UUID create(CreateReminder cmd) {
        if (cmd == null) {
            return null;
        }
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO message_reminders (id, user_id, chat_id, message_id, remind_at, status, created_at)
            VALUES (?, ?, ?, ?, ?, 'pending', CURRENT_TIMESTAMP)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            stmt.setObject(2, cmd.userId());
            stmt.setObject(3, cmd.chatId());
            stmt.setObject(4, cmd.messageId());
            stmt.setTimestamp(5, Timestamp.from(cmd.remindAt()));
            stmt.executeUpdate();
            return id;
        } catch (Exception e) {
            log.error("message reminder create failed user={}", cmd.userId(), e);
            return null;
        }
    }

    @Override
    public Optional<ReminderRow> find(UUID id) {
        var sql = """
            SELECT id, user_id, chat_id, message_id, remind_at, status, created_at
            FROM message_reminders WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("message reminder find failed {}", id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ReminderRow> listForUser(UUID userId, int limit) {
        var lim = Math.max(1, Math.min(limit, 100));
        var sql = """
            SELECT id, user_id, chat_id, message_id, remind_at, status, created_at
            FROM message_reminders
            WHERE user_id = ? AND status = 'pending'
            ORDER BY remind_at ASC
            LIMIT ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, userId);
            stmt.setInt(2, lim);
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<ReminderRow>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("message reminder list failed user={}", userId, e);
            return List.of();
        }
    }

    @Override
    public List<ReminderRow> listDue(Instant now, int limit) {
        var lim = Math.max(1, Math.min(limit, 100));
        var sql = """
            SELECT id, user_id, chat_id, message_id, remind_at, status, created_at
            FROM message_reminders
            WHERE status = 'pending' AND remind_at <= ?
            ORDER BY remind_at ASC
            LIMIT ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setTimestamp(1, Timestamp.from(now));
            stmt.setInt(2, lim);
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<ReminderRow>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("message reminder listDue failed", e);
            return List.of();
        }
    }

    @Override
    public boolean updateStatus(UUID id, String status) {
        var sql = "UPDATE message_reminders SET status = ? WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, status);
            stmt.setObject(2, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("message reminder updateStatus failed {}", id, e);
            return false;
        }
    }

    private static ReminderRow mapRow(java.sql.ResultSet rs) throws Exception {
        var remind = rs.getTimestamp("remind_at");
        var created = rs.getTimestamp("created_at");
        return new ReminderRow(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getObject("message_id", UUID.class),
            remind != null ? remind.toInstant() : null,
            rs.getString("status"),
            created != null ? created.toInstant() : null);
    }
}
