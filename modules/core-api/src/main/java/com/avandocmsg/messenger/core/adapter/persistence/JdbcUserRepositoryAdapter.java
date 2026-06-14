package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link UserRepositoryPort}. */
public final class JdbcUserRepositoryAdapter implements UserRepositoryPort {
    private static final String SELECT_USER = """
        SELECT id, username, display_name, phone, hidden, created_at,
               presence_status, last_seen_at, org_id, privacy_disable_read_receipts, ui_locale
        FROM users
        WHERE id = ?
        """;

    private final DataSource dataSource;

    public JdbcUserRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<UserProfile> findById(UserId id) {
        if (dataSource == null) {
            return Optional.empty();
        }
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(SELECT_USER)) {
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

    @Override
    public boolean updateProfile(UserId id, String displayName, String phone) {
        if (dataSource == null) {
            return false;
        }
        var sql = """
            UPDATE users SET display_name = COALESCE(?, display_name), phone = COALESCE(?, phone), updated_at = now()
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, displayName);
            stmt.setString(2, phone);
            stmt.setObject(3, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean updatePresence(UserId id, String presenceStatus) {
        if (dataSource == null) {
            return false;
        }
        var sql = """
            UPDATE users SET presence_status = ?, last_seen_at = now(), updated_at = now() WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, presenceStatus);
            stmt.setObject(2, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean updatePrivacy(UserId id, boolean disableReadReceipts) {
        if (dataSource == null) {
            return false;
        }
        var sql = """
            UPDATE users SET privacy_disable_read_receipts = ?, updated_at = now() WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, disableReadReceipts);
            stmt.setObject(2, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean updateUiLocale(UserId id, String uiLocale) {
        if (dataSource == null) {
            return false;
        }
        var sql = """
            UPDATE users SET ui_locale = ?, updated_at = now() WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uiLocale);
            stmt.setObject(2, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean touchHeartbeat(UserId id) {
        if (dataSource == null) {
            return false;
        }
        var sql = "UPDATE users SET last_seen_at = now(), updated_at = now() WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static UserProfile mapRow(java.sql.ResultSet rs) throws Exception {
        var lastSeenTs = rs.getTimestamp("last_seen_at");
        Instant lastSeen = lastSeenTs != null ? lastSeenTs.toInstant() : null;
        var org = rs.getObject("org_id", UUID.class);
        return new UserProfile(
            UserId.of(rs.getObject("id", UUID.class)),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("phone"),
            rs.getBoolean("hidden"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("presence_status"),
            lastSeen,
            org != null ? org.toString() : null,
            rs.getBoolean("privacy_disable_read_receipts"),
            rs.getString("ui_locale"));
    }
}
