package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.domain.UserProfile;
import com.avandocmsg.messenger.core.port.UserRepositoryPort;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JDBC adapter for {@link UserRepositoryPort} (single-user profile read). */
public final class JdbcUserRepositoryAdapter implements UserRepositoryPort {
    private static final String SELECT_USER = """
        SELECT id, username, display_name, phone, hidden, created_at,
               presence_status, last_seen_at, org_id, privacy_disable_read_receipts
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
            rs.getBoolean("privacy_disable_read_receipts"));
    }
}
