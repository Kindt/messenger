package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;

import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.api.users.dto.UserProfile;
import com.avandocmsg.messenger.api.users.dto.UserSearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class JdbcUserJdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcUserJdbcRepository.class);
    private final DataSource dataSource;

    private static final String SELECT_USER = """
        SELECT id, username, display_name, phone, email, external_id, hidden, created_at,
               presence_status, last_seen_at, org_id, privacy_disable_read_receipts, ui_locale,
               custom_status_text, dnd_until
        FROM users
        """;

    public JdbcUserJdbcRepository(DataSource dataSource) {
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
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
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
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
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
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
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
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
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

    public Optional<UserProfile> findByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        var sql = SELECT_USER + " WHERE external_id = ? LIMIT 1";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, externalId.trim());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find user by external_id: {}", externalId, e);
        }
        return Optional.empty();
    }

    public Optional<UserProfile> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        var sql = SELECT_USER + " WHERE lower(email) = lower(?)";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, email.trim());
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find user by email: {}", email, e);
        }
        return Optional.empty();
    }

    public Optional<UserProfile> findByOrgAndExternalId(UUID orgId, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        var sql = SELECT_USER + " WHERE org_id = ? AND external_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            stmt.setString(2, externalId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find user by org/externalId: org={} ext={}", orgId, externalId, e);
        }
        return Optional.empty();
    }

    public List<UserProfile> listByOrg(UUID orgId, int offset, int limit) {
        var sql = SELECT_USER + " WHERE org_id = ? ORDER BY username OFFSET ? LIMIT ?";
        var result = new ArrayList<UserProfile>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            stmt.setInt(2, Math.max(offset, 0));
            stmt.setInt(3, Math.min(Math.max(limit, 1), 200));
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("listByOrg failed orgId={}", orgId, e);
        }
        return result;
    }

    public int countByOrg(UUID orgId) {
        var sql = "SELECT count(*) FROM users WHERE org_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("countByOrg failed orgId={}", orgId, e);
        }
        return 0;
    }

    /**
     * Upsert user from LDAP directory sync by external_id or email within org.
     */
    public boolean upsertFromDirectory(UUID id, UUID orgId, String externalId, String username,
                                       String email, String displayName) {
        var un = normalizeUsername(username);
        var dn = displayName != null && !displayName.isBlank() ? displayName : un;
        var existing = findByOrgAndExternalId(orgId, externalId);
        if (existing.isEmpty() && email != null && !email.isBlank()) {
            existing = findByEmail(email).filter(p -> orgId.toString().equals(p.orgId()));
        }
        if (existing.isPresent()) {
            var sql = """
                UPDATE users SET username = ?, display_name = ?, email = ?, external_id = ?,
                  org_id = ?, hidden = false, updated_at = now()
                WHERE id = ?
                """;
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                     JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setString(1, un);
                stmt.setString(2, dn);
                stmt.setString(3, blankToNull(email));
                stmt.setString(4, externalId);
                stmt.setObject(5, orgId);
                stmt.setObject(6, UUID.fromString(existing.get().id()));
                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                log.error("upsertFromDirectory update failed ext={}", externalId, e);
                return false;
            }
        }
        var sql = """
            INSERT INTO users (id, username, display_name, email, external_id, org_id, hidden, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, false, now(), now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            stmt.setString(2, un);
            stmt.setString(3, dn);
            stmt.setString(4, blankToNull(email));
            stmt.setString(5, externalId);
            stmt.setObject(6, orgId);
            stmt.executeUpdate();
            return true;
        } catch (Exception e) {
            log.warn("upsertFromDirectory insert failed username={}: {}", un, e.getMessage());
            return false;
        }
    }

    public boolean upsertFromScim(UUID id, UUID orgId, String username, String email,
                                  String externalId, String displayName, boolean active) {
        if (findById(id).isPresent()) {
            return updateFromScim(id, orgId, username, email, externalId, displayName, active);
        }
        return insertFromScim(id, orgId, username, email, externalId, displayName, active);
    }

    private boolean insertFromScim(UUID id, UUID orgId, String username, String email,
                                   String externalId, String displayName, boolean active) {
        var un = normalizeUsername(username);
        var dn = displayName != null && !displayName.isBlank() ? displayName : un;
        var sql = """
            INSERT INTO users (id, username, display_name, email, external_id, org_id, hidden, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            stmt.setString(2, un);
            stmt.setString(3, dn);
            stmt.setString(4, blankToNull(email));
            stmt.setString(5, blankToNull(externalId));
            stmt.setObject(6, orgId);
            stmt.setBoolean(7, !active);
            stmt.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("insertFromScim failed id={}", id, e);
            return false;
        }
    }

    private boolean updateFromScim(UUID id, UUID orgId, String username, String email,
                                     String externalId, String displayName, boolean active) {
        var un = normalizeUsername(username);
        var dn = displayName != null && !displayName.isBlank() ? displayName : un;
        var sql = """
            UPDATE users SET username = ?, display_name = ?, email = ?, external_id = ?,
              org_id = ?, hidden = ?, updated_at = now()
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, un);
            stmt.setString(2, dn);
            stmt.setString(3, blankToNull(email));
            stmt.setString(4, blankToNull(externalId));
            stmt.setObject(5, orgId);
            stmt.setBoolean(6, !active);
            stmt.setObject(7, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("updateFromScim failed id={}", id, e);
            return false;
        }
    }

    public boolean setActive(UUID id, boolean active) {
        var sql = "UPDATE users SET hidden = ?, updated_at = now() WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setBoolean(1, !active);
            stmt.setObject(2, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("setActive failed id={}", id, e);
            return false;
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "user";
        }
        var trimmed = username.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean isReadReceiptsDisabled(UUID id) {
        var sql = "SELECT privacy_disable_read_receipts FROM users WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
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

    public List<UserProfile> search(String query, int limit) {
        var result = new ArrayList<UserProfile>();
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            var postgres = JdbcDialect.isPostgres(conn);
            var clause = userSearchClause(postgres, "username", "display_name");
            var sql = SELECT_USER +
                      " WHERE hidden = false AND " + clause + " " +
                      "ORDER BY username LIMIT ?";
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                bindUserSearchParams(stmt, postgres, query, 1);
                stmt.setInt(3, limit);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
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
        var result = new ArrayList<UserSearchHit>();
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            var postgres = JdbcDialect.isPostgres(conn);
            var clause = userSearchClause(postgres, "u.username", "u.display_name");
            var sql = """
                SELECT u.id, u.username, u.display_name
                FROM users u
                WHERE u.hidden = false AND u.id <> ?
                  AND """ + clause + """
                  AND NOT EXISTS (
                    SELECT 1 FROM blocks b
                    WHERE (b.blocker_id = ? AND b.blocked_id = u.id)
                       OR (b.blocker_id = u.id AND b.blocked_id = ?)
                  )
                ORDER BY u.username
                LIMIT ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, viewerId);
            bindUserSearchParams(stmt, postgres, safe, 2);
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
            }
        } catch (Exception e) {
            log.error("searchForViewer failed", e);
        }
        return result;
    }

    static String userSearchClause(boolean postgres, String usernameCol, String displayNameCol) {
        if (postgres) {
            return "(lower(" + usernameCol + ") LIKE lower(?) OR lower(" + displayNameCol + ") LIKE lower(?))";
        }
        return "(POSITION(lower(CAST (? AS text)) IN lower(coalesce(" + usernameCol + ", ''))) > 0"
            + " OR POSITION(lower(CAST (? AS text)) IN lower(coalesce(" + displayNameCol + ", ''))) > 0)";
    }

    static String userSearchBindValue(boolean postgres, String query) {
        var q = query != null ? query.trim() : "";
        if (postgres) {
            return "%" + q + "%";
        }
        return q.toLowerCase(Locale.ROOT);
    }

    private static void bindUserSearchParams(
            PreparedStatement stmt, boolean postgres, String query, int startIndex)
            throws SQLException {
        var val = userSearchBindValue(postgres, query);
        stmt.setString(startIndex, val);
        stmt.setString(startIndex + 1, val);
    }

    private UserProfile mapRow(ResultSet rs) throws Exception {
        var lastSeenTs = rs.getTimestamp("last_seen_at");
        Instant lastSeen = lastSeenTs != null ? lastSeenTs.toInstant() : null;
        var org = rs.getObject("org_id", UUID.class);
        var dndTs = hasColumn(rs, "dnd_until") ? rs.getTimestamp("dnd_until") : null;
        Instant dndUntil = dndTs != null ? dndTs.toInstant() : null;
        var customStatus = hasColumn(rs, "custom_status_text") ? rs.getString("custom_status_text") : null;
        return new UserProfile(
            rs.getObject("id", UUID.class).toString(),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("external_id"),
            rs.getBoolean("hidden"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("presence_status"),
            lastSeen,
            org != null ? org.toString() : null,
            rs.getBoolean("privacy_disable_read_receipts"),
            rs.getString("ui_locale"),
            customStatus,
            dndUntil
        );
    }

    private static boolean hasColumn(ResultSet rs, String column) {
        try {
            rs.findColumn(column);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
