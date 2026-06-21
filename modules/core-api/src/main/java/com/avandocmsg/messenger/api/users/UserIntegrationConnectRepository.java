package com.avandocmsg.messenger.api.users;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** User ↔ plugin instance marketplace connections (lab MVP). */
public final class UserIntegrationConnectRepository {

    private final DataSource dataSource;

    public UserIntegrationConnectRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean connect(UUID userId, UUID instanceId) {
        if (listConnectedInstanceIds(userId).contains(instanceId)) {
            return false;
        }
        var sql = """
            INSERT INTO user_integration_connections (user_id, plugin_instance_id)
            VALUES (?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, instanceId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                return false;
            }
            throw new IllegalStateException("connect integration failed", e);
        }
    }

    public boolean disconnect(UUID userId, UUID instanceId) {
        var sql = "DELETE FROM user_integration_connections WHERE user_id = ? AND plugin_instance_id = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, instanceId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("disconnect integration failed", e);
        }
    }

    public Set<UUID> listConnectedInstanceIds(UUID userId) {
        var sql = "SELECT plugin_instance_id FROM user_integration_connections WHERE user_id = ?";
        var out = new HashSet<UUID>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getObject("plugin_instance_id", UUID.class));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("list connected integrations failed", e);
        }
        return out;
    }
}
