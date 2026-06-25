package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;

import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;

/** JDBC write operations for messages (hex adapter; SQL extracted from legacy {@code MessageRepository}). */
public final class JdbcMessageWriteRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcMessageWriteRepository.class);

    private final DataSource dataSource;
    private final DataSource readDataSource;
    private final Clock clock;
    private final int queryTimeoutSeconds;

    public JdbcMessageWriteRepository(DataSource dataSource, DataSource readDataSource, Clock clock) {
        this(dataSource, readDataSource, clock, 0);
    }

    public JdbcMessageWriteRepository(
        DataSource dataSource,
        DataSource readDataSource,
        Clock clock,
        int queryTimeoutSeconds
    ) {
        this.dataSource = dataSource;
        this.readDataSource = readDataSource != null ? readDataSource : dataSource;
        this.clock = clock;
        this.queryTimeoutSeconds = Math.max(0, queryTimeoutSeconds);
    }

    DataSource dataSource() {
        return dataSource;
    }

    public MessageResponse insert(
        UUID id,
        UUID chatId,
        UUID senderId,
        String type,
        String content,
        UUID replyToMsgId,
        UUID threadId,
        String clientMsgId,
        Integer visibilityTtlSeconds,
        UUID attachmentFileId,
        Integer voiceDurationMs
    ) {
        var sql = """
            INSERT INTO messages (id, chat_id, sender_id, type, content, reply_to_msg_id, thread_id, client_msg_id,
                visibility_ttl_seconds, attachment_file_id, voice_duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            stmt.setObject(2, chatId);
            stmt.setObject(3, senderId);
            stmt.setString(4, type != null ? type : "text");
            var normalizedContent = normalizeContent(content);
            stmt.setString(5, normalizedContent);
            stmt.setObject(6, replyToMsgId);
            stmt.setObject(7, threadId);
            stmt.setString(8, clientMsgId);
            stmt.setObject(9, visibilityTtlSeconds);
            stmt.setObject(10, attachmentFileId);
            if (voiceDurationMs != null) {
                stmt.setInt(11, voiceDurationMs);
            } else {
                stmt.setObject(11, null);
            }
            stmt.executeUpdate();
            return new MessageResponse(id.toString(), chatId.toString(), senderId.toString(),
                type != null ? type : "text", normalizedContent, replyToMsgId != null ? replyToMsgId.toString() : null,
                false, clock.instant(), null, visibilityTtlSeconds,
                attachmentFileId != null ? attachmentFileId.toString() : null,
                threadId != null ? threadId.toString() : null, null, null, null,
                voiceDurationMs, null, null);
        } catch (SQLException e) {
            log.error("Failed to insert message", e);
            return null;
        }
    }

    public boolean existsClientMsgId(UUID chatId, UUID senderId, String clientMsgId) {
        if (clientMsgId == null || clientMsgId.isBlank()) {
            return false;
        }
        var sql = "SELECT 1 FROM messages WHERE chat_id = ? AND sender_id = ? AND client_msg_id = ?";
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, chatId);
            stmt.setObject(2, senderId);
            stmt.setString(3, clientMsgId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("existsClientMsgId failed chatId={}", chatId, e);
            return false;
        }
    }

    public boolean updateContent(UUID msgId, UUID editedBy, String newContent) {
        var selectSql = "SELECT content FROM messages WHERE id = ?";
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            conn.setAutoCommit(false);
            try {
                String oldContent;
                try (var stmt = conn.prepareStatement(selectSql)) {
                    JdbcQuerySupport.applyDefaultTimeout(stmt);
                    stmt.setObject(1, msgId);
                    try (var rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            return false;
                        }
                        oldContent = rs.getString("content");
                    }
                }
                var versionSql = "INSERT INTO message_versions (message_id, content, edited_by, created_at) VALUES (?, ?, ?, now())";
                try (var stmt = conn.prepareStatement(versionSql)) {
                    JdbcQuerySupport.applyDefaultTimeout(stmt);
                    stmt.setObject(1, msgId);
                    stmt.setString(2, oldContent);
                    stmt.setObject(3, editedBy);
                    stmt.executeUpdate();
                }
                var updateSql = "UPDATE messages SET content = ?, edited_at = now() WHERE id = ?";
                try (var stmt = conn.prepareStatement(updateSql)) {
                    JdbcQuerySupport.applyDefaultTimeout(stmt);
                    stmt.setString(1, newContent);
                    stmt.setObject(2, msgId);
                    stmt.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("Failed to update message {}", msgId, e);
            return false;
        }
    }

    public boolean softDelete(UUID msgId) {
        var sql = "UPDATE messages SET deleted = true WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, msgId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to delete message {}", msgId, e);
            return false;
        }
    }

    public boolean addReaction(UUID messageId, UUID userId, String reaction) {
        var sql = "INSERT INTO message_reactions (message_id, user_id, reaction, created_at) VALUES (?, ?, ?, now()) "
            + "ON CONFLICT (message_id, user_id, reaction) DO NOTHING";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, messageId);
            stmt.setObject(2, userId);
            stmt.setString(3, reaction);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to add reaction to message {}", messageId, e);
            return false;
        }
    }

    public boolean removeReaction(UUID messageId, UUID userId, String reaction) {
        var sql = "DELETE FROM message_reactions WHERE message_id = ? AND user_id = ? AND reaction = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, messageId);
            stmt.setObject(2, userId);
            stmt.setString(3, reaction);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to remove reaction from message {}", messageId, e);
            return false;
        }
    }

    public boolean pinMessage(UUID chatId, UUID messageId, UUID pinnedBy) {
        if (isPinned(chatId, messageId)) {
            return false;
        }
        var sql = "INSERT INTO pinned_messages (chat_id, message_id, pinned_by, created_at) VALUES (?, ?, ?, now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            stmt.setObject(2, messageId);
            stmt.setObject(3, pinnedBy);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to pin message {} in chat {}", messageId, chatId, e);
            return false;
        }
    }

    public boolean unpinMessage(UUID chatId, UUID messageId) {
        var sql = "DELETE FROM pinned_messages WHERE chat_id = ? AND message_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            stmt.setObject(2, messageId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Failed to unpin message {} from chat {}", messageId, chatId, e);
            return false;
        }
    }

    private boolean isPinned(UUID chatId, UUID messageId) {
        var sql = "SELECT 1 FROM pinned_messages WHERE chat_id = ? AND message_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            stmt.setObject(2, messageId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check pin state for message {} in chat {}", messageId, chatId, e);
            return false;
        }
    }

    private DataSource read() {
        return readDataSource;
    }

    private void applyQueryTimeout(PreparedStatement stmt) throws SQLException {
        JdbcQuerySupport.applyTimeout(stmt, queryTimeoutSeconds);
    }

    static String normalizeContent(String content) {
        return content != null ? content.strip() : null;
    }
}
