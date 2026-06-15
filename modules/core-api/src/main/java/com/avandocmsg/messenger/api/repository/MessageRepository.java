package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.messages.dto.MessageResponse;
import com.avandocmsg.messenger.api.messages.dto.MessageVersionResponse;
import com.avandocmsg.messenger.api.messages.dto.PinnedMessageResponse;
import com.avandocmsg.messenger.api.messages.dto.ReactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
    private final DataSource dataSource;
    private final DataSource readDataSource;
    private final Clock clock;

    public MessageRepository(DataSource dataSource, Clock clock) {
        this(dataSource, null, clock);
    }

    public MessageRepository(DataSource dataSource, DataSource readDataSource, Clock clock) {
        this.dataSource = dataSource;
        this.readDataSource = readDataSource != null ? readDataSource : dataSource;
        this.clock = clock;
    }

    private DataSource read() {
        return readDataSource;
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds) {
        return insert(id, chatId, senderId, type, content, replyToMsgId, clientMsgId, visibilityTtlSeconds, null);
    }

    public MessageResponse insert(UUID id, UUID chatId, UUID senderId, String type, String content,
                                  UUID replyToMsgId, String clientMsgId, Integer visibilityTtlSeconds,
                                  UUID attachmentFileId) {
        var sql = """
            INSERT INTO messages (id, chat_id, sender_id, type, content, reply_to_msg_id, client_msg_id,
                visibility_ttl_seconds, attachment_file_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, chatId);
            stmt.setObject(3, senderId);
            stmt.setString(4, type != null ? type : "text");
            stmt.setString(5, content);
            stmt.setObject(6, replyToMsgId);
            stmt.setString(7, clientMsgId);
            stmt.setObject(8, visibilityTtlSeconds);
            stmt.setObject(9, attachmentFileId);
            stmt.executeUpdate();
            return new MessageResponse(id.toString(), chatId.toString(), senderId.toString(),
                type != null ? type : "text", content, replyToMsgId != null ? replyToMsgId.toString() : null,
                false, clock.instant(), null, visibilityTtlSeconds,
                attachmentFileId != null ? attachmentFileId.toString() : null);
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
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
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
        var sql = """
            SELECT id, chat_id, sender_id, type, content, reply_to_msg_id, deleted, created_at, edited_at, visibility_ttl_seconds,
                attachment_file_id
            FROM messages m WHERE m.id = ? AND (m.visibility_ttl_seconds IS NULL OR EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - m.created_at)) < m.visibility_ttl_seconds)
            """;
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMessage(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find message {}", id, e);
        }
        return Optional.empty();
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before) {
        return findByChatId(chatId, limit, before, null);
    }

    public List<MessageResponse> findByChatId(UUID chatId, int limit, UUID before, UUID filterUserId) {
        var sql = new StringBuilder(
            "SELECT m.id, m.chat_id, m.sender_id, m.type, "
                + SQL_LIST_CONTENT_PROJECTION
                + ", m.reply_to_msg_id, m.deleted, m.created_at, "
                + "m.edited_at, m.visibility_ttl_seconds, m.attachment_file_id "
                + "FROM messages m WHERE m.chat_id = ? AND "
                + SQL_MSG_VISIBILITY_TTL_VISIBLE);
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
            int idx = 1;
            stmt.setObject(idx++, chatId);
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
            log.error("Failed to find messages for chat {}", chatId, e);
        }
        return result;
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
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
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
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
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
        var ttl = (Integer) rs.getObject("visibility_ttl_seconds");
        var attachmentFileId = rs.getObject("attachment_file_id", UUID.class);
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
            attachmentFileId != null ? attachmentFileId.toString() : null
        );
    }
}
