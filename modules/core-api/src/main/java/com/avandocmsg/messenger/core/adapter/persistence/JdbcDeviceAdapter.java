package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.api.devices.dto.DeviceResponse;
import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;
import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.port.DevicePort;
import com.avandocmsg.messenger.core.port.UuidGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcDeviceAdapter implements DevicePort {
    private static final Logger log = LoggerFactory.getLogger(JdbcDeviceAdapter.class);
    private final DataSource dataSource;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    public JdbcDeviceAdapter(DataSource dataSource, Clock clock, UuidGenerator uuidGenerator) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    public DeviceResponse upsertPushDevice(UUID userId, String deviceName, String pushProvider, String pushToken) {
        var updateSql = """
            UPDATE devices SET push_provider = ?, push_token = ?, last_active_at = now()
            WHERE user_id = ? AND device_name = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(updateSql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, pushProvider);
            stmt.setString(2, pushToken);
            stmt.setObject(3, userId);
            stmt.setString(4, deviceName);
            var updated = stmt.executeUpdate();
            if (updated > 0) {
                return findByUserAndName(userId, deviceName).orElse(null);
            }
        } catch (Exception e) {
            log.error("upsertPushDevice update failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }

        var id = uuidGenerator.randomUuid();
        var insertSql = """
            INSERT INTO devices (id, user_id, device_name, push_provider, push_token, created_at, last_active_at)
            VALUES (?, ?, ?, ?, ?, now(), now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(insertSql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, id);
            stmt.setObject(2, userId);
            stmt.setString(3, deviceName);
            stmt.setString(4, pushProvider);
            stmt.setString(5, pushToken);
            stmt.executeUpdate();
            return new DeviceResponse(id.toString(), deviceName, pushProvider, true, clock.instant());
        } catch (Exception e) {
            log.error("upsertPushDevice insert failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public List<DeviceResponse> listForUser(UUID userId) {
        var sql = """
            SELECT id, device_name, push_provider, created_at,
              (push_token IS NOT NULL AND btrim(push_token) <> '') AS push_active
            FROM devices
            WHERE user_id = ?
            ORDER BY last_active_at DESC NULLS LAST, created_at DESC
            LIMIT ?
            """;
        var out = new ArrayList<DeviceResponse>();
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
                stmt.setObject(1, userId);
                stmt.setInt(2, JdbcListLimits.DEVICES);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapDeviceRow(rs));
                }
            }
            }
        } catch (SQLException e) {
            log.error("listForUser failed", e);
        }
        return out;
    }

    @Override
    public boolean clearPushToken(UUID userId, String deviceName) {
        var sql = """
            UPDATE devices
            SET push_token = NULL, push_provider = NULL, last_active_at = now()
            WHERE user_id = ? AND device_name = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, userId);
            stmt.setString(2, deviceName);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("clearPushToken failed", e);
            return false;
        }
    }

    private Optional<DeviceResponse> findByUserAndName(UUID userId, String deviceName) {
        var sql = """
            SELECT id, device_name, push_provider, created_at,
              (push_token IS NOT NULL AND btrim(push_token) <> '') AS push_active
            FROM devices WHERE user_id = ? AND device_name = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, userId);
            stmt.setString(2, deviceName);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapDeviceRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("findByUserAndName failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return Optional.empty();
    }

    private static DeviceResponse mapDeviceRow(java.sql.ResultSet rs) throws SQLException {
        return new DeviceResponse(
            rs.getObject("id", UUID.class).toString(),
            rs.getString("device_name"),
            rs.getString("push_provider"),
            rs.getBoolean("push_active"),
            rs.getTimestamp("created_at").toInstant());
    }
}
