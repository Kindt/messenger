package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Optional;

/** JDBC adapter for {@link UserRepositoryPort}. */
public final class JdbcUserRepositoryAdapter implements UserRepositoryPort {
    private final JdbcUserJdbcRepository jdbc;
    private final DataSource dataSource;

    public JdbcUserRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcUserJdbcRepository(dataSource);
    }

    public JdbcUserRepositoryAdapter(JdbcUserJdbcRepository jdbc) {
        this.jdbc = jdbc;
        this.dataSource = null;
    }

    @Override
    public Optional<UserProfile> findById(UserId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbc.findById(id.value()).map(JdbcUserRepositoryAdapter::toDomain);
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
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
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
        return updateUserStatus(id, presenceStatus, null, null, false);
    }

    @Override
    public boolean updateUserStatus(UserId id, String presenceStatus, String customStatusText,
                                    java.time.Instant dndUntil, boolean clearDndUntil) {
        if (dataSource == null) {
            return false;
        }
        var updates = new java.util.ArrayList<String>();
        var params = new java.util.ArrayList<Object>();
        if (presenceStatus != null) {
            updates.add("presence_status = ?");
            params.add(presenceStatus);
        }
        if (customStatusText != null) {
            updates.add("custom_status_text = ?");
            params.add(customStatusText);
        }
        if (dndUntil != null) {
            updates.add("dnd_until = ?");
            params.add(Timestamp.from(dndUntil));
        } else if (clearDndUntil) {
            updates.add("dnd_until = NULL");
        }
        if (updates.isEmpty()) {
            return false;
        }
        updates.add("last_seen_at = now()");
        updates.add("updated_at = now()");
        var sql = "UPDATE users SET " + String.join(", ", updates) + " WHERE id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            int idx = 1;
            for (var param : params) {
                if (param instanceof Timestamp ts) {
                    stmt.setTimestamp(idx++, ts);
                } else {
                    stmt.setObject(idx++, param);
                }
            }
            stmt.setObject(idx, id.value());
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
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
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
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
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
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void upsertFromKeycloak(UserId id, String username, String displayName) {
        if (id == null) {
            return;
        }
        jdbc.upsertFromKeycloak(id.value(), username, displayName);
    }

    @Override
    public boolean createLocalUser(UserId id, String username, String displayName) {
        if (id == null) {
            return false;
        }
        return jdbc.create(id.value(), username, displayName);
    }

    private static UserProfile toDomain(com.avandocmsg.messenger.api.users.dto.UserProfile profile) {
        return new UserProfile(
            UserId.of(java.util.UUID.fromString(profile.id())),
            profile.username(),
            profile.displayName(),
            profile.phone(),
            profile.hidden(),
            profile.createdAt(),
            profile.presenceStatus(),
            profile.lastSeenAt(),
            profile.orgId(),
            profile.privacyDisableReadReceipts(),
            profile.uiLocale(),
            profile.customStatusText(),
            profile.dndUntil());
    }
}
