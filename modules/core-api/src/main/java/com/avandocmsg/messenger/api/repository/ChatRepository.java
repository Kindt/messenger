package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.metrics.JdbcQueryMetrics;
import com.avandocmsg.messenger.api.config.JdbcQuerySupport;
import com.avandocmsg.messenger.api.chats.dto.ChatMemberResponse;
import com.avandocmsg.messenger.api.chats.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avandocmsg.messenger.core.port.UuidGenerator;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChatRepository {
    private static final Logger log = LoggerFactory.getLogger(ChatRepository.class);
    private final DataSource dataSource;
    private final DataSource readDataSource;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final int queryTimeoutSeconds;

    public ChatRepository(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this(dataSource, null, clock, uuidGenerator, 0);
    }

    public ChatRepository(DataSource dataSource, DataSource readDataSource, Clock clock, UuidGenerator uuidGenerator) {
        this(dataSource, readDataSource, clock, uuidGenerator, 0);
    }

    public ChatRepository(DataSource dataSource, DataSource readDataSource, Clock clock, UuidGenerator uuidGenerator,
                          int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.readDataSource = readDataSource != null ? readDataSource : dataSource;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
        this.queryTimeoutSeconds = Math.max(0, queryTimeoutSeconds);
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

    private void logReadFailure(String operation, Object id, Exception e) {
        if (e instanceof SQLException sqlEx && isQueryTimeout(sqlEx)) {
            JdbcQueryMetrics.queryTimeout();
        }
        log.error("{} failed id={}", operation, id, e);
    }

    private DataSource read() {
        return readDataSource;
    }

    private DataSource write() {
        return dataSource;
    }

    public boolean chatExists(UUID chatId) {
        if (dataSource == null) {
            return false;
        }
        var sql = "SELECT 1 FROM chats WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("chatExists failed chatId={}", chatId, e);
            return false;
        }
    }

    /**
     * Org для слоя политики ретенции чата: сначала {@code users.org_id} владельца чата; иначе первая org среди
     * участников с непустым {@code org_id}, порядок ролей owner → admin → member.
     */
    public Optional<UUID> findOrgIdForRetentionOverlay(UUID chatId) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var ownerSql = """
            SELECT u.org_id
            FROM chats c
            JOIN users u ON u.id = c.owner_id
            WHERE c.id = ? AND u.org_id IS NOT NULL
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(ownerSql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("org_id", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("findOrgIdForRetentionOverlay (owner) failed chatId={}", chatId, e);
            return Optional.empty();
        }
        var memberSql = """
            SELECT u.org_id
            FROM chat_members cm
            JOIN users u ON u.id = cm.user_id
            WHERE cm.chat_id = ? AND u.org_id IS NOT NULL
            ORDER BY CASE cm.role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END, cm.user_id
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(memberSql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("org_id", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("findOrgIdForRetentionOverlay (members) failed chatId={}", chatId, e);
        }
        return Optional.empty();
    }

    public ChatResponse createGroup(UUID chatId, String title, UUID ownerId) {
        var sql = "INSERT INTO chats (id, title, type, owner_id, created_at, updated_at) VALUES (?, ?, 'group', ?, now(), now())";
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, chatId);
                stmt.setString(2, title);
                stmt.setObject(3, ownerId);
                stmt.executeUpdate();
            }
            addMemberInternal(conn, chatId, ownerId, "owner");
            conn.commit();
            return new ChatResponse(chatId.toString(), title, "group", ownerId.toString(), 1, false, false, null, clock.instant());
        } catch (Exception e) {
            log.error("Failed to create group chat", e);
            return null;
        }
    }

    public ChatResponse createChannel(UUID chatId, String title, UUID ownerId) {
        var sql = """
            INSERT INTO chats (id, title, type, owner_id, channel_post_policy, created_at, updated_at)
            VALUES (?, ?, 'channel', ?, 'admins_only', now(), now())
            """;
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, chatId);
                stmt.setString(2, title);
                stmt.setObject(3, ownerId);
                stmt.executeUpdate();
            }
            addMemberInternal(conn, chatId, ownerId, "owner");
            conn.commit();
            return new ChatResponse(chatId.toString(), title, "channel", ownerId.toString(), 1, false, false, null,
                clock.instant(), null, null, "admins_only");
        } catch (Exception e) {
            log.error("Failed to create channel", e);
            return null;
        }
    }

    public Optional<String> getChatType(UUID chatId) {
        var sql = "SELECT type FROM chats WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("type"));
                }
            }
        } catch (Exception e) {
            log.error("getChatType failed chatId={}", chatId, e);
        }
        return Optional.empty();
    }

    public ChatResponse createP2P(UUID chatId, UUID user1Id, UUID user2Id) {
        var sql = "INSERT INTO chats (id, title, type, owner_id, created_at, updated_at) VALUES (?, '', 'p2p', ?, now(), now())";
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, chatId);
                stmt.setObject(2, user1Id);
                stmt.executeUpdate();
            }
            addMemberInternal(conn, chatId, user1Id, "member");
            addMemberInternal(conn, chatId, user2Id, "member");
            conn.commit();
            return new ChatResponse(chatId.toString(), "", "p2p", null, 2, false, false, null, clock.instant());
        } catch (Exception e) {
            log.error("Failed to create P2P chat", e);
            return null;
        }
    }

    public Optional<UUID> findP2PChat(UUID user1Id, UUID user2Id) {
        var sql = """
            SELECT c.id FROM chats c
            WHERE c.type = 'p2p'
            AND EXISTS (SELECT 1 FROM chat_members WHERE chat_id = c.id AND user_id = ?)
            AND EXISTS (SELECT 1 FROM chat_members WHERE chat_id = c.id AND user_id = ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, user1Id);
            stmt.setObject(2, user2Id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("id", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find P2P chat", e);
        }
        return Optional.empty();
    }

    public List<ChatResponse> listByUser(UUID userId) {
        var sql = """
            SELECT c.id, c.title, c.type, c.owner_id, cm.muted, cm.personal_filter_active,
                   c.ttl_seconds, c.created_at, c.channel_post_policy, cm.archived_at, cm.folder_tag,
                   (SELECT COUNT(*) FROM chat_members WHERE chat_id = c.id) AS member_count
            FROM chats c
            JOIN chat_members cm ON cm.chat_id = c.id
            WHERE cm.user_id = ? AND cm.banned = false
              AND (
                c.type <> 'p2p'
                OR NOT EXISTS (
                  SELECT 1 FROM chat_members cm2
                  WHERE cm2.chat_id = c.id AND cm2.user_id <> ?
                    AND (
                      EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id = ? AND b.blocked_id = cm2.user_id)
                      OR EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id = cm2.user_id AND b.blocked_id = ?)
                    )
                )
              )
            ORDER BY c.updated_at DESC
            """;
        var result = new ArrayList<ChatResponse>();
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, userId);
            stmt.setObject(2, userId);
            stmt.setObject(3, userId);
            stmt.setObject(4, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapChat(rs));
                }
            }
        } catch (Exception e) {
            logReadFailure("listByUser", userId, e);
        }
        return result;
    }

    /** Вторая сторона P2P-чата (если чат не p2p или участник один — пусто). */
    public Optional<UUID> findOtherP2PMember(UUID chatId, UUID userId) {
        var sql = """
            SELECT cm.user_id
            FROM chat_members cm
            INNER JOIN chats c ON c.id = cm.chat_id AND c.type = 'p2p'
            WHERE cm.chat_id = ? AND cm.user_id <> ?
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("user_id", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("findOtherP2PMember failed", e);
        }
        return Optional.empty();
    }

    public Optional<ChatResponse> findById(UUID chatId, UUID userId) {
        var sql = """
            SELECT c.id, c.title, c.type, c.owner_id, cm.muted, cm.personal_filter_active,
                   c.ttl_seconds, c.created_at, c.channel_post_policy, cm.archived_at, cm.folder_tag,
                   (SELECT COUNT(*) FROM chat_members WHERE chat_id = c.id) AS member_count
            FROM chats c
            INNER JOIN chat_members cm ON cm.chat_id = c.id AND cm.user_id = ? AND cm.banned = false
            WHERE c.id = ?
              AND (
                c.type <> 'p2p'
                OR NOT EXISTS (
                  SELECT 1 FROM chat_members cm2
                  WHERE cm2.chat_id = c.id AND cm2.user_id <> ?
                    AND (
                      EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id = ? AND b.blocked_id = cm2.user_id)
                      OR EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id = cm2.user_id AND b.blocked_id = ?)
                    )
                )
              )
            """;
        try (var conn = read().getConnection();
             var stmt = conn.prepareStatement(sql)) {
            applyQueryTimeout(stmt);
            stmt.setObject(1, userId);
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            stmt.setObject(4, userId);
            stmt.setObject(5, userId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapChat(rs));
                }
            }
        } catch (Exception e) {
            logReadFailure("findById", chatId, e);
        }
        return Optional.empty();
    }

    public boolean updateTitle(UUID chatId, String title) {
        var sql = "UPDATE chats SET title = ?, updated_at = now() WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setObject(2, chatId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to update chat title {}", chatId, e);
            return false;
        }
    }

    public boolean setMuted(UUID chatId, UUID userId, boolean muted) {
        var sql = "UPDATE chat_members SET muted = ? WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, muted);
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to set mute", e);
            return false;
        }
    }

    public boolean setArchived(UUID chatId, UUID userId, boolean archived) {
        var sql = archived
            ? "UPDATE chat_members SET archived_at = now() WHERE chat_id = ? AND user_id = ?"
            : "UPDATE chat_members SET archived_at = NULL WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to set archive", e);
            return false;
        }
    }

    public boolean setFolderTag(UUID chatId, UUID userId, String folderTag) {
        var sql = "UPDATE chat_members SET folder_tag = ? WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            if (folderTag == null || folderTag.isBlank()) {
                stmt.setNull(1, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(1, folderTag.trim());
            }
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to set folder tag", e);
            return false;
        }
    }

    public boolean addMember(UUID chatId, UUID userId, String role) {
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                var check = "SELECT banned FROM chat_members WHERE chat_id = ? AND user_id = ?";
                try (var stmt = conn.prepareStatement(check)) {
                    stmt.setObject(1, chatId);
                    stmt.setObject(2, userId);
                    try (var rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            if (rs.getBoolean("banned")) {
                                return false;
                            }
                            return true;
                        }
                    }
                }
                addMemberInternal(conn, chatId, userId, role);
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to add member {} to chat {}", userId, chatId, e);
            return false;
        }
    }

    public boolean removeMember(UUID chatId, UUID userId) {
        var sql = "DELETE FROM chat_members WHERE chat_id = ? AND user_id = ? AND role != 'owner'";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to remove member", e);
            return false;
        }
    }

    public boolean setRole(UUID chatId, UUID userId, String role) {
        var sql = "UPDATE chat_members SET role = ? WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, role);
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to set role", e);
            return false;
        }
    }

    public List<ChatMemberResponse> listMembers(UUID chatId) {
        var sql = """
            SELECT cm.user_id, u.username, u.display_name, cm.role, cm.joined_at, cm.muted, cm.banned
            FROM chat_members cm
            JOIN users u ON u.id = cm.user_id
            WHERE cm.chat_id = ?
            ORDER BY cm.joined_at
            """;
        var result = new ArrayList<ChatMemberResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new ChatMemberResponse(
                        rs.getObject("user_id", UUID.class).toString(),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getBoolean("muted"),
                        rs.getBoolean("banned"),
                        rs.getTimestamp("joined_at").toInstant()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to list members of chat {}", chatId, e);
        }
        return result;
    }

    /** Chat owner ({@code chats.owner_id}) for system actions such as retention-triggered export. */
    public Optional<UUID> findOwnerId(UUID chatId) {
        var sql = "SELECT owner_id FROM chats WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    var ownerId = rs.getObject("owner_id", UUID.class);
                    return ownerId != null ? Optional.of(ownerId) : Optional.empty();
                }
            }
        } catch (Exception e) {
            log.error("Failed to find owner for chat {}", chatId, e);
        }
        return Optional.empty();
    }

    public String getMemberRole(UUID chatId, UUID userId) {
        var sql = "SELECT role FROM chat_members WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("role");
                }
            }
        } catch (Exception e) {
            log.error("Failed to get member role", e);
        }
        return null;
    }

    public boolean isMemberBanned(UUID chatId, UUID userId) {
        var sql = "SELECT banned FROM chat_members WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean("banned");
            }
        } catch (Exception e) {
            log.error("Failed to check banned status", e);
            return false;
        }
    }

    public boolean setPersonalFilterActive(UUID chatId, UUID userId, boolean active) {
        var sql = "UPDATE chat_members SET personal_filter_active = ? WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, active);
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to set personal filter", e);
            return false;
        }
    }

    public boolean isPersonalFilterActive(UUID chatId, UUID userId) {
        var sql = "SELECT personal_filter_active FROM chat_members WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean("personal_filter_active");
            }
        } catch (Exception e) {
            log.error("Failed to check personal filter", e);
            return false;
        }
    }

    public boolean setBanned(UUID chatId, UUID userId, boolean banned) {
        var sql = "UPDATE chat_members SET banned = ? WHERE chat_id = ? AND user_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, banned);
            stmt.setObject(2, chatId);
            stmt.setObject(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to set banned status", e);
            return false;
        }
    }

    public List<UUID> listChatIdsForUser(UUID userId) {
        var sql = """
            SELECT c.id FROM chats c
            INNER JOIN chat_members cm ON cm.chat_id = c.id
            WHERE cm.user_id = ? AND cm.banned = false
              AND (
                c.type <> 'p2p'
                OR NOT EXISTS (
                  SELECT 1 FROM chat_members cm2
                  WHERE cm2.chat_id = c.id AND cm2.user_id <> ?
                    AND (
                      EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id = ? AND b.blocked_id = cm2.user_id)
                      OR EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id = cm2.user_id AND b.blocked_id = ?)
                    )
                )
              )
            """;
        var result = new ArrayList<UUID>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, userId);
            stmt.setObject(3, userId);
            stmt.setObject(4, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getObject("id", UUID.class));
                }
            }
        } catch (Exception e) {
            log.error("Failed to list chat ids for {}", userId, e);
        }
        return result;
    }

    private void addMemberInternal(java.sql.Connection conn, UUID chatId, UUID userId, String role) throws Exception {
        var sql = "INSERT INTO chat_members (chat_id, user_id, role, joined_at) VALUES (?, ?, ?, now())";
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            stmt.setString(3, role);
            stmt.executeUpdate();
        }
    }

    private ChatResponse mapChat(ResultSet rs) throws Exception {
        Boolean archived = null;
        if (hasColumn(rs, "archived_at")) {
            var archivedTs = rs.getTimestamp("archived_at");
            archived = archivedTs != null;
        }
        String folderTag = hasColumn(rs, "folder_tag") ? rs.getString("folder_tag") : null;
        String channelPolicy = hasColumn(rs, "channel_post_policy") ? rs.getString("channel_post_policy") : null;
        return new ChatResponse(
            rs.getObject("id", UUID.class).toString(),
            rs.getString("title"),
            rs.getString("type"),
            rs.getObject("owner_id", UUID.class) != null ? rs.getObject("owner_id", UUID.class).toString() : null,
            rs.getInt("member_count"),
            rs.getBoolean("muted"),
            rs.getBoolean("personal_filter_active"),
            rs.getObject("ttl_seconds", Integer.class),
            rs.getTimestamp("created_at").toInstant(),
            archived,
            folderTag,
            channelPolicy);
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
