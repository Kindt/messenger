package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.config.JdbcQuerySupport;
import com.avandocmsg.messenger.api.metrics.JdbcQueryMetrics;
import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MessageRepository {
    private static final Logger log = LoggerFactory.getLogger(MessageRepository.class);
    /** Сообщение видимо по TTL: нет лимита или возраст меньше {@code visibility_ttl_seconds} секунд с {@code created_at}. */
    public static final String SQL_MSG_VISIBILITY_TTL_VISIBLE =
        "(m.visibility_ttl_seconds IS NULL OR EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - m.created_at)) < m.visibility_ttl_seconds)";
    /** List reads: omit E2EE ciphertext (web client uses preview/decrypt paths). */
    private static final String SQL_LIST_CONTENT_PROJECTION =
        "CASE WHEN m.type LIKE 'e2ee-%' THEN NULL ELSE m.content END AS content";
    private static final String SQL_LIST_REPLY_PREVIEW_CONTENT =
        "CASE WHEN p.type LIKE 'e2ee-%' THEN NULL ELSE p.content END AS reply_preview_content";
    private static final String SQL_MESSAGE_COLUMNS =
        "m.id, m.chat_id, m.sender_id, m.type, m.content, m.reply_to_msg_id, m.thread_id, m.deleted, m.created_at, "
            + "m.edited_at, m.visibility_ttl_seconds, m.attachment_file_id, m.voice_duration_ms";
    private static final String SQL_LIST_MESSAGE_COLUMNS =
        "m.id, m.chat_id, m.sender_id, m.type, "
            + SQL_LIST_CONTENT_PROJECTION
            + ", m.reply_to_msg_id, m.thread_id, m.deleted, m.created_at, "
            + "m.edited_at, m.visibility_ttl_seconds, m.attachment_file_id, m.voice_duration_ms";
    private static final String SQL_THREAD_VISIBILITY_VISIBLE =
        "(tr.visibility_ttl_seconds IS NULL OR EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - tr.created_at)) < tr.visibility_ttl_seconds)";
    private static final String SQL_THREAD_REPLY_COUNT =
        ", (SELECT COUNT(*) FROM messages tr WHERE tr.thread_id = m.id AND tr.deleted = false AND "
            + SQL_THREAD_VISIBILITY_VISIBLE + ") AS thread_reply_count";
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
    private final Clock clock;
    private final int queryTimeoutSeconds;
    private MessageMentionRepository mentionRepository;

    public MessageRepository(DataSource dataSource, Clock clock) {
        this(dataSource, null, clock, 0);
    }

    public MessageRepository(DataSource dataSource, DataSource readDataSource, Clock clock) {
        this(dataSource, readDataSource, clock, 0);
    }

    public MessageRepository(DataSource dataSource, DataSource readDataSource, Clock clock, int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.readDataSource = readDataSource != null ? readDataSource : dataSource;
        this.clock = clock;
        this.queryTimeoutSeconds = Math.max(0, queryTimeoutSeconds);
    }

    private void applyQueryTimeout(PreparedStatement stmt) throws SQLException {
        JdbcQuerySupport.applyTimeout(stmt, queryTimeoutSeconds);
    }

    private DataSource read() {
        return readDataSource;
    }

    public void setMentionRepository(MessageMentionRepository mentionRepository) {
        this.mentionRepository = mentionRepository;
    }

    private void attachMentions(List<MessageResponse> messages) {
        if (mentionRepository == null || messages == null || messages.isEmpty()) {
            return;
        }
        var ids = new ArrayList<UUID>(messages.size());
        for (var m : messages) {
            try {
                ids.add(UUID.fromString(m.id()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        var summaries = mentionRepository.findSummariesByMessageIds(ids);
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            try {
                var summary = summaries.get(UUID.fromString(m.id()));
                if (summary == null) {
                    continue;
                }
                messages.set(i, new MessageResponse(
                    m.id(), m.chatId(), m.senderId(), m.type(), m.content(), m.replyToMsgId(),
                    m.deleted(), m.createdAt(), m.editedAt(), m.visibilityTtlSeconds(), m.attachmentFileId(),
                    m.threadId(), m.threadReplyCount(),
                    summary.userIds().isEmpty() ? null : summary.userIds(),
                    summary.mentionAll() ? true : null,
                    m.durationMs(),
                    m.linkPreview(),
                    m.replyPreview()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
    }

    private void attachLinkPreviews(List<MessageResponse> messages) {
        if (messages == null || messages.isEmpty() || dataSource == null) {
            return;
        }
        var ids = new ArrayList<UUID>(messages.size());
        for (var m : messages) {
            try {
                ids.add(UUID.fromString(m.id()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        var sql = new StringBuilder(
            "SELECT message_id, url, title FROM message_link_previews WHERE message_id IN (");
        for (int i = 0; i < ids.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")");
        var previews = new HashMap<UUID, com.avandocmsg.messenger.api.messages.dto.MessageLinkPreview>();
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            for (var id : ids) {
                stmt.setObject(idx++, id);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var msgId = rs.getObject("message_id", UUID.class);
                    previews.put(msgId, new com.avandocmsg.messenger.api.messages.dto.MessageLinkPreview(
                        rs.getString("url"),
                        rs.getString("title")));
                }
            }
        } catch (Exception e) {
            log.debug("attachLinkPreviews skipped: {}", e.getMessage());
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            var m = messages.get(i);
            try {
                var preview = previews.get(UUID.fromString(m.id()));
                if (preview == null) {
                    continue;
                }
                messages.set(i, new MessageResponse(
                    m.id(), m.chatId(), m.senderId(), m.type(), m.content(), m.replyToMsgId(),
                    m.deleted(), m.createdAt(), m.editedAt(), m.visibilityTtlSeconds(), m.attachmentFileId(),
                    m.threadId(), m.threadReplyCount(), m.mentionUserIds(), m.mentionAll(),
                    m.durationMs(), preview, m.replyPreview()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds) {
        return insert(id, chatId, senderId, type, content, replyToMsgId, null, clientMsgId, visibilityTtlSeconds, null);
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds,
                                  UUID attachmentFileId) {
        return insert(id, chatId, senderId, type, content, replyToMsgId, null, clientMsgId,
            visibilityTtlSeconds, attachmentFileId);
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, UUID threadId, String clientMsgId,
                                  Integer visibilityTtlSeconds, UUID attachmentFileId) {
        return insert(id, chatId, senderId, type, content, replyToMsgId, threadId, clientMsgId,
            visibilityTtlSeconds, attachmentFileId, null);
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, UUID threadId, String clientMsgId,
                                  Integer visibilityTtlSeconds, UUID attachmentFileId, Integer voiceDurationMs) {
        var sql = """
            INSERT INTO messages (id, chat_id, sender_id, type, content, reply_to_msg_id, thread_id, client_msg_id,
                visibility_ttl_seconds, attachment_file_id, voice_duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, chatId);
            stmt.setObject(3, senderId);
            stmt.setString(4, type != null ? type : "text");
            stmt.setString(5, content);
            stmt.setObject(6, replyToMsgId);
            stmt.setObject(7, threadId);
            stmt.setObject(8, clientMsgId);
            stmt.setObject(9, visibilityTtlSeconds);
            stmt.setObject(10, attachmentFileId);
            if (voiceDurationMs != null) {
                stmt.setInt(11, voiceDurationMs);
            } else {
                stmt.setObject(11, null);
            }
            stmt.executeUpdate();
            return new MessageResponse(id.toString(), chatId.toString(), senderId.toString(),
                type != null ? type : "text", content, replyToMsgId != null ? replyToMsgId.toString() : null,
                false, clock.instant(), null, visibilityTtlSeconds,
                attachmentFileId != null ? attachmentFileId.toString() : null,
                threadId != null ? threadId.toString() : null, null, null, null,
                voiceDurationMs, null, null);
        } catch (Exception e) {
            log.error("Failed to insert message", e);
            return null;
        }
    }

    public Optional<UUID> findLatestMessageId(UUID chatId) {
        var sql = """
            SELECT m.id FROM messages m
            WHERE m.chat_id = ? AND m.deleted = false AND """ + SQL_MSG_VISIBILITY_TTL_VISIBLE + """
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
        } catch (Exception e) {
            log.error("findLatestMessageId failed", e);
        }
        return Optional.empty();
    }

    public Optional<MessageResponse> findById(UUID id) {
        var sql = "SELECT " + SQL_MESSAGE_COLUMNS + SQL_REPLY_PREVIEW_COLUMNS
            + " FROM messages m" + SQL_REPLY_PREVIEW_JOIN
            + " WHERE m.id = ? AND " + SQL_MSG_VISIBILITY_TTL_VISIBLE;
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    var msg = mapMessage(rs);
                    attachMentions(List.of(msg));
                    attachLinkPreviews(List.of(msg));
                    return Optional.of(msg);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find message {}", id, e);
        }
        return Optional.empty();
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before) {
        return findByChatId(chatId, limit, before, null, null);
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId) {
        return findByChatId(chatId, limit, before, filterUserId, null);
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId, UUID threadId) {
        var listColumns = SQL_LIST_MESSAGE_COLUMNS;
        if (threadId == null) {
            listColumns = listColumns + SQL_THREAD_REPLY_COUNT;
        }
        var sql = new StringBuilder(
            "SELECT " + listColumns + SQL_LIST_REPLY_PREVIEW_COLUMNS
                + " FROM messages m" + SQL_REPLY_PREVIEW_JOIN + " WHERE m.chat_id = ? AND "
                + SQL_MSG_VISIBILITY_TTL_VISIBLE);
        if (threadId == null) {
            sql.append(" AND m.thread_id IS NULL");
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
                    result.add(mapMessage(rs));
                }
            }
        } catch (Exception e) {
            if (e instanceof SQLException sqlEx && isQueryTimeout(sqlEx)) {
                JdbcQueryMetrics.queryTimeout();
            }
            log.error("Failed to find messages for chat {}", chatId, e);
        }
        attachMentions(result);
        attachLinkPreviews(result);
        return result;
    }

    private static boolean isQueryTimeout(SQLException e) {
        if (e instanceof java.sql.SQLTimeoutException) {
            return true;
        }
        var sqlState = e.getSQLState();
        return sqlState != null && sqlState.startsWith("570");
    }

    public boolean updateContent(UUID msgId, UUID editedBy, String newContent) {
        var selectSql = "SELECT content FROM messages WHERE id = ?";
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String oldContent;
                try (var stmt = conn.prepareStatement(selectSql)) {
                    stmt.setObject(1, msgId);
                    try (var rs = stmt.executeQuery()) {
                        if (!rs.next()) return false;
                        oldContent = rs.getString("content");
                    }
                }
                var versionSql = "INSERT INTO message_versions (message_id, content, edited_by, created_at) VALUES (?, ?, ?, now())";
                try (var stmt = conn.prepareStatement(versionSql)) {
                    stmt.setObject(1, msgId);
                    stmt.setString(2, oldContent);
                    stmt.setObject(3, editedBy);
                    stmt.executeUpdate();
                }
                var updateSql = "UPDATE messages SET content = ?, edited_at = now() WHERE id = ?";
                try (var stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, newContent);
                    stmt.setObject(2, msgId);
                    stmt.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to update message {}", msgId, e);
            return false;
        }
    }

    public boolean delete(UUID msgId) {
        var sql = "UPDATE messages SET deleted = true WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, msgId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to delete message {}", msgId, e);
            return false;
        }
    }

    public List<MessageVersionResponse> findVersions(UUID msgId) {
        var sql = "SELECT id, message_id, content, edited_by, created_at FROM message_versions " +
                  "WHERE message_id = ? ORDER BY created_at DESC";
        var result = new ArrayList<MessageVersionResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, msgId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new MessageVersionResponse(
                        rs.getLong("id"),
                        rs.getObject("message_id", UUID.class).toString(),
                        rs.getString("content"),
                        rs.getObject("edited_by", UUID.class).toString(),
                        rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find versions for message {}", msgId, e);
        }
        return result;
    }

    public boolean addReaction(UUID messageId, UUID userId, String reaction) {
        var sql = "INSERT INTO message_reactions (message_id, user_id, reaction, created_at) VALUES (?, ?, ?, now()) " +
                  "ON CONFLICT (message_id, user_id, reaction) DO NOTHING";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, messageId);
            stmt.setObject(2, userId);
            stmt.setString(3, reaction);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to add reaction to message {}", messageId, e);
            return false;
        }
    }

    public boolean removeReaction(UUID messageId, UUID userId, String reaction) {
        var sql = "DELETE FROM message_reactions WHERE message_id = ? AND user_id = ? AND reaction = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, messageId);
            stmt.setObject(2, userId);
            stmt.setString(3, reaction);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to remove reaction from message {}", messageId, e);
            return false;
        }
    }

    public List<ReactionResponse> getReactions(UUID messageId) {
        var sql = "SELECT message_id, user_id, reaction, created_at FROM message_reactions WHERE message_id = ? ORDER BY created_at";
        var result = new ArrayList<ReactionResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, messageId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new ReactionResponse(
                        rs.getObject("message_id", UUID.class).toString(),
                        rs.getObject("user_id", UUID.class).toString(),
                        rs.getString("reaction"),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get reactions for message {}", messageId, e);
        }
        return result;
    }

    public boolean pinMessage(UUID chatId, UUID messageId, UUID pinnedBy) {
        if (isPinned(chatId, messageId)) {
            return false;
        }
        var sql = "INSERT INTO pinned_messages (chat_id, message_id, pinned_by, created_at) VALUES (?, ?, ?, now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, messageId);
            stmt.setObject(3, pinnedBy);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to pin message {} in chat {}", messageId, chatId, e);
            return false;
        }
    }

    private boolean isPinned(UUID chatId, UUID messageId) {
        var sql = "SELECT 1 FROM pinned_messages WHERE chat_id = ? AND message_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, messageId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("Failed to check pin state for message {} in chat {}", messageId, chatId, e);
            return false;
        }
    }

    public boolean unpinMessage(UUID chatId, UUID messageId) {
        var sql = "DELETE FROM pinned_messages WHERE chat_id = ? AND message_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, messageId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to unpin message {} from chat {}", messageId, chatId, e);
            return false;
        }
    }

    public List<PinnedMessageResponse> getPinnedMessages(UUID chatId) {
        var sql = "SELECT chat_id, message_id, pinned_by, created_at FROM pinned_messages WHERE chat_id = ? ORDER BY created_at DESC";
        var result = new ArrayList<PinnedMessageResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new PinnedMessageResponse(
                        rs.getObject("chat_id", UUID.class).toString(),
                        rs.getObject("message_id", UUID.class).toString(),
                        rs.getObject("pinned_by", UUID.class).toString(),
                        rs.getTimestamp("created_at").toInstant()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get pinned messages for chat {}", chatId, e);
        }
        return result;
    }

    /**
     * Viewer may fetch file metadata/download if a non-E2EE chat message shares this file id in {@code content}
     * (trimmed), viewer is a non-banned member of that chat, and block rules match the message feed
     * (mutual block with sender hides the message).
     */
    public record FileMessageRef(UUID messageId, UUID chatId) {}

    /**
     * Последнее видимое сообщение с вложением: plaintext {@code content} = file id или {@code attachment_file_id}.
     */
    public Optional<FileMessageRef> findLatestMessageRefForViewer(UUID fileId, UUID viewerId) {
        var sql = """
            SELECT m.id, m.chat_id
            FROM messages m
            INNER JOIN chat_members cm ON cm.chat_id = m.chat_id AND cm.user_id = ? AND cm.banned = false
            WHERE (trim(m.content) = ? OR m.attachment_file_id = ?)
              AND m.deleted = false
              AND """ + SQL_MSG_VISIBILITY_TTL_VISIBLE + """
              AND m.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
              AND m.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
            ORDER BY m.created_at DESC
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
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
        } catch (Exception e) {
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
                (trim(m.content) = ? AND m.type NOT LIKE 'e2ee-%')
                OR m.attachment_file_id = ?
              )
              AND m.deleted = false
              AND """ + SQL_MSG_VISIBILITY_TTL_VISIBLE + """
              AND m.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
              AND m.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, viewerId);
            stmt.setString(2, fileId.toString());
            stmt.setObject(3, fileId);
            stmt.setObject(4, viewerId);
            stmt.setObject(5, viewerId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("viewerMayAccessFileViaSharedNonE2eeMessage failed", e);
            return false;
        }
    }

    /** Plaintext-only search (non-{@code e2ee-*} types); ACL via chats + blocks. */
    public List<MessageResponse> searchPlaintextForUser(UUID userId, List<UUID> chatIds, String queryText, int limit) {
        if (chatIds.isEmpty()) {
            return List.of();
        }
        var sql = """
            SELECT m.id, m.chat_id, m.sender_id, m.type, m.content, m.reply_to_msg_id, m.deleted, m.created_at,
                m.edited_at, m.visibility_ttl_seconds, m.attachment_file_id
            FROM messages m
            WHERE m.chat_id = ANY (?)
              AND m.deleted = false
              AND """ + SQL_MSG_VISIBILITY_TTL_VISIBLE + """
              AND m.type NOT LIKE 'e2ee-%'
              AND POSITION(lower(CAST (? AS text)) IN lower(coalesce(m.content, ''))) > 0
              AND EXISTS (SELECT 1 FROM chat_members cm WHERE cm.chat_id = m.chat_id AND cm.user_id = ? AND cm.banned = false)
              AND m.sender_id NOT IN (SELECT blocked_id FROM blocks WHERE blocker_id = ?)
              AND m.sender_id NOT IN (SELECT blocker_id FROM blocks WHERE blocked_id = ?)
            ORDER BY m.created_at DESC
            LIMIT ?
            """;
        var result = new ArrayList<MessageResponse>();
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
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
                    result.add(mapMessage(rs));
                }
            }
        } catch (Exception e) {
            log.error("searchPlaintextForUser failed", e);
        }
        return result;
    }

    /** Loads messages by ids with ACL; preserves order of {@code orderedIds} up to {@code limit}. */
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
              AND """ + SQL_MSG_VISIBILITY_TTL_VISIBLE + """
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
                    var row = mapMessage(rs);
                    byId.put(UUID.fromString(row.id()), row);
                }
            }
        } catch (Exception e) {
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
                    if (out.size() >= limit) break;
                }
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }
        return out;
    }

    private MessageResponse mapMessage(java.sql.ResultSet rs) throws Exception {
        var ts = rs.getTimestamp("created_at");
        var editedTs = rs.getTimestamp("edited_at");
        var replyTo = rs.getObject("reply_to_msg_id", UUID.class);
        var threadId = hasColumn(rs, "thread_id") ? rs.getObject("thread_id", UUID.class) : null;
        var ttl = (Integer) rs.getObject("visibility_ttl_seconds");
        var attachmentFileId = rs.getObject("attachment_file_id", UUID.class);
        Integer threadReplyCount = null;
        if (hasColumn(rs, "thread_reply_count")) {
            var count = rs.getObject("thread_reply_count");
            if (count instanceof Number n) {
                threadReplyCount = n.intValue();
            }
        }
        Integer durationMs = null;
        if (hasColumn(rs, "voice_duration_ms")) {
            durationMs = (Integer) rs.getObject("voice_duration_ms");
        }
        return new MessageResponse(
            rs.getObject("id", UUID.class).toString(),
            rs.getObject("chat_id", UUID.class).toString(),
            rs.getObject("sender_id", UUID.class).toString(),
            rs.getString("type"),
            rs.getString("content"),
            replyTo != null ? replyTo.toString() : null,
            rs.getBoolean("deleted"),
            ts != null ? ts.toInstant() : null,
            editedTs != null ? editedTs.toInstant() : null,
            ttl,
            attachmentFileId != null ? attachmentFileId.toString() : null,
            threadId != null ? threadId.toString() : null,
            threadReplyCount,
            null,
            null,
            durationMs,
            null,
            mapReplyPreview(rs)
        );
    }

    private static com.avandocmsg.messenger.api.messages.dto.MessageReplyPreview mapReplyPreview(
        java.sql.ResultSet rs
    ) throws Exception {
        if (!hasColumn(rs, "reply_preview_id")) {
            return null;
        }
        var previewId = rs.getObject("reply_preview_id", UUID.class);
        if (previewId == null) {
            return null;
        }
        var deleted = rs.getBoolean("reply_preview_deleted");
        var previewType = hasColumn(rs, "reply_preview_type") ? rs.getString("reply_preview_type") : null;
        String snippet = null;
        if (!deleted) {
            var content = hasColumn(rs, "reply_preview_content") ? rs.getString("reply_preview_content") : null;
            if (content != null && !content.isBlank()) {
                snippet = content.length() > 120 ? content.substring(0, 120) : content;
            } else if (previewType != null && previewType.startsWith("e2ee-")) {
                snippet = null;
            }
        }
        return new com.avandocmsg.messenger.api.messages.dto.MessageReplyPreview(
            previewId.toString(),
            rs.getObject("reply_preview_sender_id", UUID.class).toString(),
            snippet,
            deleted
        );
    }

    private static boolean hasColumn(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }
}
