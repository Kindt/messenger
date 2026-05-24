package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserRepository {
    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);
    private final DataSource dataSource;

    private static final String SELECT_USER = """
        SELECT id, username, display_name, phone, hidden, created_at,
               presence_status, last_seen_at, org_id, privacy_disable_read_receipts
        FROM users
        """;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * После успешного логина через Keycloak: строка в {@code users} с {@code id = sub} из JWT.
     */
    public void upsertFromKeycloak(UUID id, String username, String displayName) {
        var un = username != null && !username.isBlank() ? username : "user";
        var dn = displayName != null && !displayName.isBlank() ? displayName : un;
        var sql = """
            INSERT INTO users (id, username, display_name, created_at, updated_at)
            VALUES (?, ?, ?, now(), now())
            ON CONFLICT (id) DO UPDATE SET
              username = EXCLUDED.username,
              display_name = EXCLUDED.display_name,
              updated_at = now()
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setString(2, un);
            stmt.setString(3, dn);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.warn("upsertFromKeycloak failed for {}: {}", id, e.getMessage());
        }
    }

    public boolean create(UUID id, String username, String displayName) {
        var sql = "INSERT INTO users (id, username, display_name, created_at, updated_at) VALUES (?, ?, ?, now(), now())";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setString(2, username);
            stmt.setString(3, displayName != null ? displayName : username);
            stmt.executeUpdate();
            log.info("User created: {} ({})", username, id);
            return true;
        } catch (Exception e) {
            log.error("Failed to create user: {}", username, e);
            return false;
        }
    }

    public Optional<UserProfile> findById(UUID id) {
        var sql = SELECT_USER + " WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find user: {}", id, e);
        }
        return Optional.empty();
    }

    public Optional<UserProfile> findByUsername(String username) {
        var sql = SELECT_USER + " WHERE username = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find user by username: {}", username, e);
        }
        return Optional.empty();
    }

    public boolean updateProfile(UUID id, String displayName, String phone) {
        var sql = "UPDATE users SET display_name = COALESCE(?, display_name), phone = COALESCE(?, phone), updated_at = now() WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, displayName);
            stmt.setString(2, phone);
            stmt.setObject(3, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to update profile: {}", id, e);
            return false;
        }
    }

    public boolean updatePresence(UUID id, String presenceStatus) {
        var sql = """
            UPDATE users SET presence_status = ?, last_seen_at = now(), updated_at = now() WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, presenceStatus);
            stmt.setObject(2, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed to update presence: {}", id, e);
            return false;
        }
    }

    public boolean isReadReceiptsDisabled(UUID id) {
        var sql = "SELECT privacy_disable_read_receipts FROM users WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("privacy_disable_read_receipts");
                }
            }
        } catch (Exception e) {
            log.warn("isReadReceiptsDisabled failed for {}: {}", id, e.getMessage());
        }
        return false;
    }

    public boolean updatePrivacyDisableReadReceipts(UUID id, boolean disabled) {
        var sql = """
            UPDATE users SET privacy_disable_read_receipts = ?, updated_at = now() WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, disabled);
            stmt.setObject(2, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("updatePrivacyDisableReadReceipts failed: {}", id, e);
            return false;
        }
    }

    /** Только отметка активности (например периодический heartbeat клиента). */
    public boolean touchHeartbeat(UUID id) {
        var sql = "UPDATE users SET last_seen_at = now(), updated_at = now() WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("Failed heartbeat: {}", id, e);
            return false;
        }
    }

    public List<UserProfile> search(String query, int limit) {
        var sql = SELECT_USER +
                  " WHERE hidden = false AND (username ILIKE ? OR display_name ILIKE ?) " +
                  "ORDER BY username LIMIT ?";
        var result = new ArrayList<UserProfile>();
        var pattern = "%" + query + "%";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setInt(3, limit);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to search users: {}", query, e);
        }
        return result;
    }

    /**
     * Поиск пользователей для автодополнения: без себя, скрытых и пар с блокировкой с текущим пользователем.
     */
    public List<UserSearchHit> searchForViewer(UUID viewerId, String query, int limit) {
        var safe = query != null ? query.trim() : "";
        if (safe.isEmpty()) {
            return List.of();
        }
        if (safe.length() > 64) {
            safe = safe.substring(0, 64);
        }
        var lim = Math.min(Math.max(limit, 1), 50);
        var pattern = "%" + safe + "%";
        var sql = """
            SELECT u.id, u.username, u.display_name
            FROM users u
            WHERE u.hidden = false AND u.id <> ?
              AND (u.username ILIKE ? OR u.display_name ILIKE ?)
              AND NOT EXISTS (
                SELECT 1 FROM blocks b
                WHERE (b.blocker_id = ? AND b.blocked_id = u.id)
                   OR (b.blocker_id = u.id AND b.blocked_id = ?)
              )
            ORDER BY u.username
            LIMIT ?
            """;
        var result = new ArrayList<UserSearchHit>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, viewerId);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setObject(4, viewerId);
            stmt.setObject(5, viewerId);
            stmt.setInt(6, lim);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new UserSearchHit(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("username"),
                        rs.getString("display_name")));
                }
            }
        } catch (Exception e) {
            log.error("searchForViewer failed", e);
        }
        return result;
    }

    private UserProfile mapRow(ResultSet rs) throws Exception {
        var lastSeenTs = rs.getTimestamp("last_seen_at");
        Instant lastSeen = lastSeenTs != null ? lastSeenTs.toInstant() : null;
        var org = rs.getObject("org_id", UUID.class);
        return new UserProfile(
            rs.getObject("id", UUID.class).toString(),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getBoolean("hidden"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("presence_status"),
            lastSeen,
            org != null ? org.toString() : null,
            rs.getBoolean("privacy_disable_read_receipts")
        );
    }
}
