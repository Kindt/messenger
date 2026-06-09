package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.repository.MessageRepository;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.Message;
import com.avandocmsg.messenger.core.domain.MessageId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.MessageRepositoryPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link MessageRepositoryPort} (single-message read with visibility TTL). */
public final class JdbcMessageRepositoryAdapter implements MessageRepositoryPort {
    private final DataSource dataSource;

    public JdbcMessageRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Message> findById(MessageId id) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = """
            SELECT id, chat_id, sender_id, type, content, reply_to_msg_id, deleted, created_at, edited_at,
                visibility_ttl_seconds, attachment_file_id
            FROM messages m WHERE m.id = ? AND """ + MessageRepository.SQL_MSG_VISIBILITY_TTL_VISIBLE;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id.value());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Message mapRow(java.sql.ResultSet rs) throws Exception {
        var reply = rs.getString("reply_to_msg_id");
        var attachment = rs.getString("attachment_file_id");
        var ttl = rs.getObject("visibility_ttl_seconds");
        return new Message(
            MessageId.of(rs.getObject("id", UUID.class)),
            ChatId.of(rs.getObject("chat_id", UUID.class)),
            UserId.of(rs.getObject("sender_id", UUID.class)),
            rs.getString("type"),
            rs.getString("content"),
            reply,
            rs.getBoolean("deleted"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("edited_at") != null ? rs.getTimestamp("edited_at").toInstant() : null,
            ttl != null ? rs.getInt("visibility_ttl_seconds") : null,
            attachment);
    }
}
