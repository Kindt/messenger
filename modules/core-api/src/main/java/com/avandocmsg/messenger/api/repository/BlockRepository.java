package com.avandocmsg.messenger.api.repository;

import com.avandocmsg.messenger.api.blocks.dto.BlockedUserResponse;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlockRepository {
    private final DataSource dataSource;

    public BlockRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean exists(UUID blockerId, UUID blockedId) {
        var sql = "SELECT 1 FROM blocks WHERE blocker_id = ? AND blocked_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, blockerId);
            stmt.setObject(2, blockedId);
            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean block(UUID blockerId, UUID blockedId) {
        var sql = "INSERT INTO blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, now()) ON CONFLICT DO NOTHING";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, blockerId);
            stmt.setObject(2, blockedId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean unblock(UUID blockerId, UUID blockedId) {
        var sql = "DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, blockerId);
            stmt.setObject(2, blockedId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<BlockedUserResponse> listBlockedUsers(UUID blockerId) {
        var sql = """
            SELECT u.id, u.username, u.display_name, b.created_at AS blocked_at
            FROM blocks b
            JOIN users u ON u.id = b.blocked_id
            WHERE b.blocker_id = ?
            ORDER BY b.created_at DESC
            """;
        var out = new ArrayList<BlockedUserResponse>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, blockerId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("blocked_at");
                    Instant blockedAt = ts != null ? ts.toInstant() : Instant.EPOCH;
                    out.add(new BlockedUserResponse(
                        rs.getObject("id", UUID.class).toString(),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        blockedAt));
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        return out;
    }
}
