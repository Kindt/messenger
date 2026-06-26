package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.domain.ChatId;
import com.avandocmsg.messenger.core.domain.FileId;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.AvatarAccessPort;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link AvatarAccessPort}. */
public final class JdbcAvatarAccessRepository implements AvatarAccessPort {

    private final DataSource dataSource;

    public JdbcAvatarAccessRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean viewerMayAccessAsAvatar(UserId viewerId, FileId fileId) {
        if (viewerId == null || fileId == null) {
            return false;
        }
        var userOwner = findUserOwner(fileId.value());
        if (userOwner.isPresent()) {
            return mayViewUserAvatar(viewerId.value(), userOwner.get());
        }
        var chatOwner = findChatOwner(fileId.value());
        if (chatOwner.isPresent()) {
            return isActiveChatMember(chatOwner.get(), viewerId.value());
        }
        var orgLogo = findOrgLogoOwner(fileId.value());
        if (orgLogo.isPresent()) {
            return sameOrgMember(viewerId.value(), orgLogo.get());
        }
        return false;
    }

    @Override
    public Optional<UserId> findUserIdByAvatarFile(FileId fileId) {
        if (fileId == null) {
            return Optional.empty();
        }
        return findUserOwner(fileId.value()).map(UserId::of);
    }

    @Override
    public Optional<ChatId> findChatIdByAvatarFile(FileId fileId) {
        if (fileId == null) {
            return Optional.empty();
        }
        return findChatOwner(fileId.value()).map(ChatId::of);
    }

    private Optional<UUID> findUserOwner(UUID fileId) {
        var sql = "SELECT id FROM users WHERE avatar_file_id = ? LIMIT 1";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, fileId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("id", UUID.class));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<UUID> findChatOwner(UUID fileId) {
        var sql = "SELECT id FROM chats WHERE avatar_file_id = ? LIMIT 1";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, fileId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("id", UUID.class));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private boolean mayViewUserAvatar(UUID viewerId, UUID targetUserId) {
        if (viewerId.equals(targetUserId)) {
            return true;
        }
        var sql = """
            SELECT u.hidden, u.org_id AS target_org,
                   COALESCE(u.avatar_hidden, false) AS avatar_hidden,
                   (SELECT v.org_id FROM users v WHERE v.id = ?) AS viewer_org,
                   (SELECT o.avatar_policy FROM organizations o WHERE o.id = u.org_id) AS avatar_policy
            FROM users u
            WHERE u.id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, viewerId);
            stmt.setObject(2, targetUserId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                if (rs.getBoolean("hidden")) {
                    return false;
                }
                if (rs.getBoolean("avatar_hidden")) {
                    return false;
                }
                var policy = rs.getString("avatar_policy");
                if ("org_hidden".equals(policy)) {
                    return false;
                }
                var targetOrg = rs.getObject("target_org", UUID.class);
                var viewerOrg = rs.getObject("viewer_org", UUID.class);
                if (targetOrg == null || viewerOrg == null || !targetOrg.equals(viewerOrg)) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return !blockExists(viewerId, targetUserId);
    }

    @Override
    public boolean userMayUploadAvatar(UserId userId) {
        if (userId == null) {
            return false;
        }
        var sql = """
            SELECT o.avatar_policy
            FROM users u
            LEFT JOIN organizations o ON o.id = u.org_id
            WHERE u.id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, userId.value());
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                var policy = rs.getString("avatar_policy");
                return policy == null || (!"disabled".equals(policy) && !"ldap_only".equals(policy));
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isActiveChatMember(UUID chatId, UUID userId) {
        var sql = """
            SELECT 1 FROM chat_members
            WHERE chat_id = ? AND user_id = ? AND banned = false
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, chatId);
            stmt.setObject(2, userId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean blockExists(UUID user1, UUID user2) {
        var sql = """
            SELECT 1 FROM blocks
            WHERE (blocker_id = ? AND blocked_id = ?)
               OR (blocker_id = ? AND blocked_id = ?)
            LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, user1);
            stmt.setObject(2, user2);
            stmt.setObject(3, user2);
            stmt.setObject(4, user1);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return true;
        }
    }

    private Optional<UUID> findOrgLogoOwner(UUID fileId) {
        var sql = "SELECT id FROM organizations WHERE logo_file_id = ? LIMIT 1";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, fileId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getObject("id", UUID.class));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private boolean sameOrgMember(UUID viewerId, UUID orgId) {
        var sql = "SELECT 1 FROM users WHERE id = ? AND org_id = ? LIMIT 1";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, viewerId);
            stmt.setObject(2, orgId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
