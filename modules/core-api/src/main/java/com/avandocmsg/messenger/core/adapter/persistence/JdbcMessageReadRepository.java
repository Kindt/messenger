package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;

import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.api.metrics.JdbcQueryMetrics;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import com.avandocmsg.messenger.core.port.MessageMentionRepositoryPort;
import com.avandocmsg.messenger.core.port.FileMessageRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** JDBC read/query operations for messages (hex adapter). */
public final class JdbcMessageReadRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcMessageReadRepository.class);

    private static final String SQL_LIST_CONTENT_PROJECTION =
        "CASE WHEN m.type NOT LIKE 'e2ee-%' THEN m.content END AS content";
    private static final String SQL_LIST_REPLY_PREVIEW_CONTENT =
        "CASE WHEN p.type NOT LIKE 'e2ee-%' THEN p.content END AS reply_preview_content";
    private static final String SQL_MESSAGE_COLUMNS =
        "m.id, m.chat_id, m.sender_id, m.type, m.content, m.reply_to_msg_id, m.thread_id, m.deleted, m.created_at, "
            + "m.edited_at, m.visibility_ttl_seconds, m.attachment_file_id, m.voice_duration_ms";
    private static final String SQL_LIST_MESSAGE_COLUMNS =
        "m.id, m.chat_id, m.sender_id, m.type, "
            + SQL_LIST_CONTENT_PROJECTION
            + ", m.reply_to_msg_id, m.thread_id, m.deleted, m.created_at, "
            + "m.edited_at, m.visibility_ttl_seconds, m.attachment_file_id, m.voice_duration_ms";
    private static final String SQL_REPLY_PREVIEW_COLUMNS =
        ", p.id AS reply_preview_id, p.sender_id AS reply_preview_sender_id, "
            + "p.type AS reply_preview_type, p.content AS reply_preview_content, p.deleted AS reply_preview_deleted";
    private static final String SQL_LIST_REPLY_PREVIEW_COLUMNS =
        ", p.id AS reply_preview_id, p.sender_id AS reply_preview_sender_id, "
            + "p.type AS reply_preview_type, "
            + SQL_LIST_REPLY_PREVIEW_CONTENT
            + ", p.deleted AS reply_preview_deleted";
    private static final String SQL_REPLY_PREVIEW_JOIN = " LEFT JOIN messages p ON p.id = m.reply_to_msg_id";

    private final DataSource dataSource;
    private final DataSource readDataSource;
    private final int queryTimeoutSeconds;
    private MessageMentionRepositoryPort mentionRepositoryPort;

    public JdbcMessageReadRepository(DataSource dataSource, DataSource readDataSource, int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.readDataSource = readDataSource != null ? readDataSource : dataSource;
        this.queryTimeoutSeconds = Math.max(0, queryTimeoutSeconds);
    }

    public void setMentionRepositoryPort(MessageMentionRepositoryPort mentionRepositoryPort) {
        this.mentionRepositoryPort = mentionRepositoryPort;
    }

    public Optional<UUID> findLatestMessageId(UUID chatId) {
        var sql = """
            SELECT m.id FROM messages m
            WHERE m.chat_id = ? AND m.deleted = false AND """ + MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE + """
             ORDER BY m.created_at DESC LIMIT 1
            """;
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("id", UUID.class));
                }
            }
        } catch (SQLException e) {
            log.error("findLatestMessageId failed", e);
        }
        return Optional.empty();
    }

    public Optional<MessageResponse> findById(UUID id) {
        var sql = "SELECT " + SQL_MESSAGE_COLUMNS + SQL_REPLY_PREVIEW_COLUMNS
            + " FROM messages m" + SQL_REPLY_PREVIEW_JOIN
            + " WHERE m.id = ? AND " + MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE;
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    var msg = MessageResponseJdbcMapper.mapMessage(rs);
                    enrichSingle(msg);
                    return Optional.of(msg);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find message {}", id, e);
        }
        return Optional.empty();
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {
        var sql = new StringBuilder(
            "SELECT " + SQL_LIST_MESSAGE_COLUMNS + SQL_LIST_REPLY_PREVIEW_COLUMNS
                + " FROM messages m" + SQL_REPLY_PREVIEW_JOIN + " WHERE m.chat_id = ? AND "
                + MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE);
        if (threadId == null) {
            sql.append(" AND (m.thread_id IS NOT DISTINCT FROM NULL)");
        } else {
            sql.append(" AND (m.thread_id = ? OR m.id = ?)");
        }
        if (before != null) {
            sql.append(" AND m.created_at < (SELECT m2.created_at FROM messages m2 WHERE m2.id = ?)");
        }
        if (filterUserId != null) {
            sql.append("""
                 AND m.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
                 AND m.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)""");
        }
        sql.append(" ORDER BY m.created_at DESC LIMIT ?");

        var result = new ArrayList<MessageResponse>(Math.max(limit, 16));
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql.toString())) {
            applyQueryTimeout(stmt);
            int idx = 1;
            stmt.setObject(idx++, chatId);
            if (threadId != null) {
                stmt.setObject(idx++, threadId);
                stmt.setObject(idx++, threadId);
            }
            if (before != null) {
                stmt.setObject(idx++, before);
            }
            if (filterUserId != null) {
                stmt.setObject(idx++, filterUserId);
                stmt.setObject(idx++, filterUserId);
            }
            stmt.setInt(idx++, limit);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(MessageResponseJdbcMapper.mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            if (e instanceof SQLException sqlEx && isQueryTimeout(sqlEx)) {
                JdbcQueryMetrics.queryTimeout();
            }
            log.error("Failed to find messages for chat {}", chatId, e);
        }
        enrichList(result, threadId == null);
        return result;
    }

    public List<MessageVersionResponse> findVersions(UUID msgId) {
        var sql = "SELECT id, message_id, content, edited_by, created_at FROM message_versions "
            + "WHERE message_id = ? ORDER BY created_at DESC LIMIT ?";
        var result = new ArrayList<MessageVersionResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, msgId);
            stmt.setInt(2, JdbcListLimits.MESSAGE_VERSIONS);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new MessageVersionResponse(
                        rs.getLong("id"),
                        rs.getObject("message_id", UUID.class).toString(),
                        rs.getString("content"),
                        rs.getObject("edited_by", UUID.class).toString(),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find versions for message {}", msgId, e);
        }
        return result;
    }

    public List<ReactionResponse> getReactions(UUID messageId) {
        var sql = "SELECT message_id, user_id, reaction, created_at FROM message_reactions "
            + "WHERE message_id = ? ORDER BY created_at LIMIT ?";
        var result = new ArrayList<ReactionResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, messageId);
            stmt.setInt(2, JdbcListLimits.MESSAGE_REACTIONS);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new ReactionResponse(
                        rs.getObject("message_id", UUID.class).toString(),
                        rs.getObject("user_id", UUID.class).toString(),
                        rs.getString("reaction"),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get reactions for message {}", messageId, e);
        }
        return result;
    }

    public List<PinnedMessageResponse> getPinnedMessages(UUID chatId) {
        var sql = "SELECT chat_id, message_id, pinned_by, created_at FROM pinned_messages "
            + "WHERE chat_id = ? ORDER BY created_at DESC LIMIT ?";
        var result = new ArrayList<PinnedMessageResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, chatId);
            stmt.setInt(2, JdbcListLimits.PINNED_MESSAGES);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new PinnedMessageResponse(
                        rs.getObject("chat_id", UUID.class).toString(),
                        rs.getObject("message_id", UUID.class).toString(),
                        rs.getObject("pinned_by", UUID.class).toString(),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get pinned messages for chat {}", chatId, e);
        }
        return result;
    }

    public Optional<FileMessageRef> findLatestMessageRefForViewer(UUID fileId, UUID viewerId) {
        var sql = """
            SELECT m.id, m.chat_id
            FROM messages m
            INNER JOIN chat_members cm ON cm.chat_id = m.chat_id AND cm.user_id = ? AND cm.banned = false
            WHERE (m.content = ? OR m.attachment_file_id = ?)
              AND m.deleted = false
              AND """ + MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE + """
              AND m.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
              AND m.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
            ORDER BY m.created_at DESC
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, viewerId);
            stmt.setString(2, fileId.toString());
            stmt.setObject(3, fileId);
            stmt.setObject(4, viewerId);
            stmt.setObject(5, viewerId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new FileMessageRef(
                        rs.getObject("id", UUID.class),
                        rs.getObject("chat_id", UUID.class)));
                }
            }
        } catch (SQLException e) {
            log.error("findLatestMessageRefForViewer failed", e);
        }
        return Optional.empty();
    }

    public boolean viewerMayAccessFileViaSharedNonE2eeMessage(UUID fileId, UUID viewerId) {
        var sql = """
            SELECT 1
            FROM messages m
            INNER JOIN chat_members cm ON cm.chat_id = m.chat_id AND cm.user_id = ? AND cm.banned = false
            WHERE (
                (m.content = ? AND m.type NOT LIKE 'e2ee-%')
                OR m.attachment_file_id = ?
              )
              AND m.deleted = false
              AND """ + MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE + """
              AND m.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
              AND m.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, viewerId);
            stmt.setString(2, fileId.toString());
            stmt.setObject(3, fileId);
            stmt.setObject(4, viewerId);
            stmt.setObject(5, viewerId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("viewerMayAccessFileViaSharedNonE2eeMessage failed", e);
            return false;
        }
    }

    public List<MessageResponse> searchPlaintextForUser(UUID userId, List<UUID> chatIds, String queryText, int limit) {
        if (chatIds.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<MessageResponse>();
        try (var conn = read().getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            var searchClause = plaintextSearchClause(isPostgres(conn));
            var sql = """
                SELECT m.id, m.chat_id, m.sender_id, m.type, m.content, m.reply_to_msg_id, m.deleted, m.created_at,
                    m.edited_at, m.visibility_ttl_seconds, m.attachment_file_id
                FROM messages m
                WHERE m.chat_id = ANY (?)
                  AND m.deleted = false
                  AND """ + MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE + """
                  AND m.type NOT LIKE 'e2ee-%'
                  AND """ + searchClause + """
                  AND EXISTS (SELECT 1 FROM chat_members cm WHERE cm.chat_id = m.chat_id AND cm.user_id = ? AND cm.banned = false)
                  AND m.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
                  AND m.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
                ORDER BY m.created_at DESC
                LIMIT ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                applyQueryTimeout(stmt);
                var arr = conn.createArrayOf("uuid", chatIds.toArray(new UUID[0]));
                stmt.setArray(1, arr);
                stmt.setString(2, queryText);
                stmt.setObject(3, userId);
                stmt.setObject(4, userId);
                stmt.setObject(5, userId);
                stmt.setInt(6, limit);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(MessageResponseJdbcMapper.mapMessage(rs));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("searchPlaintextForUser failed", e);
        }
        return result;
    }

    static String plaintextSearchClause(boolean postgres) {
        if (postgres) {
            return "to_tsvector('russian', coalesce(m.content, '')) @@ plainto_tsquery('russian', ?)";
        }
        return "POSITION(lower(CAST (? AS text)) IN lower(coalesce(m.content, ''))) > 0";
    }

    private static boolean isPostgres(Connection conn) throws SQLException {
        var product = conn.getMetaData().getDatabaseProductName();
        return product != null && product.toLowerCase(Locale.ROOT).contains("postgresql");
    }

    public List<MessageResponse> loadMessagesForSearchResults(UUID userId, List<String> orderedIds, int limit) {
        var uuids = new ArrayList<UUID>();
        for (var s : orderedIds) {
            try {
                uuids.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        if (uuids.isEmpty()) {
            return List.of();
        }
        var sql = """
            SELECT m.id, m.chat_id, m.sender_id, m.type, m.content, m.reply_to_msg_id, m.deleted, m.created_at,
                m.edited_at, m.visibility_ttl_seconds, m.attachment_file_id
            FROM messages m
            WHERE m.id = ANY (?)
              AND m.deleted = false
              AND """ + MessageJdbcSql.MSG_VISIBILITY_TTL_VISIBLE + """
              AND EXISTS (SELECT 1 FROM chat_members cm WHERE cm.chat_id = m.chat_id AND cm.user_id = ? AND cm.banned = false)
              AND m.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
              AND m.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
            """;
        var byId = new HashMap<UUID, MessageResponse>();
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setArray(1, conn.createArrayOf("uuid", uuids.toArray(new UUID[0])));
            stmt.setObject(2, userId);
            stmt.setObject(3, userId);
            stmt.setObject(4, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var row = MessageResponseJdbcMapper.mapMessage(rs);
                    byId.put(UUID.fromString(row.id()), row);
                }
            }
        } catch (SQLException e) {
            log.error("loadMessagesForSearchResults failed", e);
            return List.of();
        }
        var out = new ArrayList<MessageResponse>();
        for (var s : orderedIds) {
            try {
                var id = UUID.fromString(s);
                var m = byId.get(id);
                if (m != null) {
                    out.add(m);
                    if (out.size() >= limit) {
                        break;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        return out;
    }

    private void enrichSingle(MessageResponse msg) {
        var list = new ArrayList<MessageResponse>(1);
        list.add(msg);
        MessageMentionEnrichment.attachMentions(mentionRepositoryPort, list);
        MessageLinkPreviewEnrichment.attach(read(), list);
    }

    private void enrichList(List<MessageResponse> messages, boolean attachThreadCounts) {
        if (attachThreadCounts) {
            MessageThreadReplyEnrichment.attach(read(), messages);
        }
        MessageMentionEnrichment.attachMentions(mentionRepositoryPort, messages);
        MessageLinkPreviewEnrichment.attach(read(), messages);
    }

    private DataSource read() {
        return readDataSource;
    }

    private void applyQueryTimeout(PreparedStatement stmt) throws SQLException {
        JdbcQuerySupport.applyTimeout(stmt, queryTimeoutSeconds);
    }

    private static boolean isQueryTimeout(SQLException e) {
        if (e instanceof java.sql.SQLTimeoutException) {
            return true;
        }
        var sqlState = e.getSQLState();
        return sqlState != null && sqlState.startsWith("570");
    }
}
