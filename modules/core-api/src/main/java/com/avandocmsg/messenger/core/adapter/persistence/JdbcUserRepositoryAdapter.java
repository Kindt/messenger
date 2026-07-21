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
            throw new IllegalStateException("JDBC operation failed", e);
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
        boolean touchPresence = presenceStatus != null;
        boolean touchCustom = customStatusText != null;
        boolean touchDndSet = dndUntil != null;
        boolean touchDndClear = !touchDndSet && clearDndUntil;
        if (!touchPresence && !touchCustom && !touchDndSet && !touchDndClear) {
            return false;
        }
        // Fully static SQL — optional columns via CASE/COALESCE, no concatenated identifiers.
        var sql = """
            UPDATE users SET
              presence_status = CASE WHEN ? THEN ? ELSE presence_status END,
              custom_status_text = CASE WHEN ? THEN ? ELSE custom_status_text END,
              dnd_until = CASE
                WHEN ? THEN ?
                WHEN ? THEN NULL
                ELSE dnd_until
              END,
              last_seen_at = now(),
              updated_at = now()
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            int i = 1;
            stmt.setBoolean(i++, touchPresence);
            stmt.setString(i++, presenceStatus);
            stmt.setBoolean(i++, touchCustom);
            stmt.setString(i++, customStatusText);
            stmt.setBoolean(i++, touchDndSet);
            if (touchDndSet) {
                stmt.setTimestamp(i++, Timestamp.from(dndUntil));
            } else {
                stmt.setTimestamp(i++, null);
            }
            stmt.setBoolean(i++, touchDndClear);
            stmt.setObject(i, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            throw new IllegalStateException("JDBC operation failed", e);
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
            throw new IllegalStateException("JDBC operation failed", e);
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
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public boolean updateAvatar(UserId id, java.util.UUID avatarFileId) {
        if (dataSource == null) {
            return false;
        }
        var sql = """
            UPDATE users SET avatar_file_id = ?, updated_at = now() WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            if (avatarFileId == null) {
                stmt.setObject(1, null);
            } else {
                stmt.setObject(1, avatarFileId);
            }
            stmt.setObject(2, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            throw new IllegalStateException("JDBC operation failed", e);
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
            throw new IllegalStateException("JDBC operation failed", e);
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
        var avatarId = profile.avatarFileId() != null && !profile.avatarFileId().isBlank()
            ? com.avandocmsg.messenger.core.domain.FileId.of(java.util.UUID.fromString(profile.avatarFileId()))
            : null;
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
            profile.dndUntil(),
            profile.avatarHidden(),
            avatarId);
    }

    @Override
    public boolean updateAvatarHidden(UserId id, boolean avatarHidden) {
        if (dataSource == null) {
            return false;
        }
        var sql = """
            UPDATE users SET avatar_hidden = ?, updated_at = now() WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setBoolean(1, avatarHidden);
            stmt.setObject(2, id.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }
}
