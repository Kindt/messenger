package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.ScheduledMessagePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcScheduledMessageAdapter implements ScheduledMessagePort {
    private static final Logger log = LoggerFactory.getLogger(JdbcScheduledMessageAdapter.class);
    private final DataSource dataSource;

    public JdbcScheduledMessageAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UUID create(CreateScheduled cmd) {
        if (cmd == null) {
            return null;
        }
        var id = UUID.randomUUID();
        var type = cmd.messageType() != null && !cmd.messageType().isBlank() ? cmd.messageType() : "text";
        var sql = """
            INSERT INTO scheduled_messages
              (id, chat_id, sender_id, message_type, content, scheduled_at, status,
               reply_to_msg_id, thread_id, client_msg_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, cmd.chatId());
            stmt.setObject(3, cmd.senderId());
            stmt.setString(4, type);
            stmt.setString(5, cmd.content());
            stmt.setTimestamp(6, Timestamp.from(cmd.scheduledAt()));
            setUuid(stmt, 7, cmd.replyToMsgId());
            setUuid(stmt, 8, cmd.threadId());
            if (cmd.clientMsgId() != null) {
                stmt.setString(9, cmd.clientMsgId());
            } else {
                stmt.setNull(9, java.sql.Types.VARCHAR);
            }
            stmt.executeUpdate();
            return id;
        } catch (Exception e) {
            log.error("scheduled message create failed chat={}", cmd.chatId(), e);
            return null;
        }
    }

    @Override
    public Optional<ScheduledRow> find(UUID id) {
        var sql = """
            SELECT id, chat_id, sender_id, message_type, content, scheduled_at, status,
                   reply_to_msg_id, thread_id, client_msg_id, sent_message_id, created_at
            FROM scheduled_messages WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("scheduled message find failed {}", id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ScheduledRow> listForChat(UUID chatId, int limit) {
        var lim = Math.max(1, Math.min(limit, 100));
        var sql = """
            SELECT id, chat_id, sender_id, message_type, content, scheduled_at, status,
                   reply_to_msg_id, thread_id, client_msg_id, sent_message_id, created_at
            FROM scheduled_messages WHERE chat_id = ?
            ORDER BY scheduled_at ASC
            LIMIT ?
            """;
        return queryList(sql, chatId, lim);
    }

    @Override
    public List<ScheduledRow> listDue(Instant now, int limit) {
        var lim = Math.max(1, Math.min(limit, 100));
        var sql = """
            SELECT id, chat_id, sender_id, message_type, content, scheduled_at, status,
                   reply_to_msg_id, thread_id, client_msg_id, sent_message_id, created_at
            FROM scheduled_messages
            WHERE status = 'pending' AND scheduled_at <= ?
            ORDER BY scheduled_at ASC
            LIMIT ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(now));
            stmt.setInt(2, lim);
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<ScheduledRow>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("scheduled message listDue failed", e);
            return List.of();
        }
    }

    @Override
    public boolean updateStatus(UUID id, String status, UUID sentMessageId) {
        var sql = sentMessageId != null
            ? "UPDATE scheduled_messages SET status = ?, sent_message_id = ? WHERE id = ?"
            : "UPDATE scheduled_messages SET status = ? WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            if (sentMessageId != null) {
                stmt.setObject(2, sentMessageId);
                stmt.setObject(3, id);
            } else {
                stmt.setObject(2, id);
            }
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("scheduled message updateStatus failed {}", id, e);
            return false;
        }
    }

    private List<ScheduledRow> queryList(String sql, UUID chatId, int limit) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setInt(2, limit);
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<ScheduledRow>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("scheduled message list failed chat={}", chatId, e);
            return List.of();
        }
    }

    private static ScheduledRow mapRow(java.sql.ResultSet rs) throws Exception {
        var scheduled = rs.getTimestamp("scheduled_at");
        var created = rs.getTimestamp("created_at");
        return new ScheduledRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chat_id", UUID.class),
            rs.getObject("sender_id", UUID.class),
            rs.getString("message_type"),
            rs.getString("content"),
            scheduled != null ? scheduled.toInstant() : null,
            rs.getString("status"),
            rs.getObject("reply_to_msg_id", UUID.class),
            rs.getObject("thread_id", UUID.class),
            rs.getString("client_msg_id"),
            rs.getObject("sent_message_id", UUID.class),
            created != null ? created.toInstant() : null);
    }

    private static void setUuid(java.sql.PreparedStatement stmt, int idx, UUID value) throws Exception {
        if (value != null) {
            stmt.setObject(idx, value);
        } else {
            stmt.setNull(idx, java.sql.Types.OTHER);
        }
    }
}
