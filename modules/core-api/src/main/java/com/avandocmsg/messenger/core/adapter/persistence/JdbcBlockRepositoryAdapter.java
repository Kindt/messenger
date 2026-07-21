package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;
import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.domain.BlockedUser;
import com.avandocmsg.messenger.core.domain.UserId;
import com.avandocmsg.messenger.core.port.BlockRepositoryPort;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JDBC adapter for {@link BlockRepositoryPort}. */
public final class JdbcBlockRepositoryAdapter implements BlockRepositoryPort {
    private final DataSource dataSource;

    public JdbcBlockRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean exists(UserId blockerId, UserId blockedId) {
        var sql = "SELECT 1 FROM blocks WHERE blocker_id = ? AND blocked_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, blockerId.value());
            stmt.setObject(2, blockedId.value());
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public boolean block(UserId blockerId, UserId blockedId) {
        var sql = "INSERT INTO blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, now()) ON CONFLICT DO NOTHING";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, blockerId.value());
            stmt.setObject(2, blockedId.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public boolean unblock(UserId blockerId, UserId blockedId) {
        var sql = "DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, blockerId.value());
            stmt.setObject(2, blockedId.value());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public List<BlockedUser> listBlockedUsers(UserId blockerId) {
        var sql = """
            SELECT u.id, u.username, u.display_name, b.created_at AS blocked_at
            FROM blocks b
            JOIN users u ON u.id = b.blocked_id
            WHERE b.blocker_id = ?
            ORDER BY b.created_at DESC
            LIMIT ?
            """;
        var out = new ArrayList<BlockedUser>();
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setObject(1, blockerId.value());
                stmt.setInt(2, JdbcListLimits.BLOCKED_USERS);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Timestamp ts = rs.getTimestamp("blocked_at");
                        Instant blockedAt = ts != null ? ts.toInstant() : Instant.EPOCH;
                        out.add(new BlockedUser(
                            UserId.of(rs.getObject("id", UUID.class)),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            blockedAt));
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return out;
    }
}
