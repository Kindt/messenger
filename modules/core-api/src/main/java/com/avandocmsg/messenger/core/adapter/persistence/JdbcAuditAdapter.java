package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.port.AuditPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAuditAdapter implements AuditPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcAuditAdapter.class);
    private final DataSource dataSource;

    public JdbcAuditAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson) {
        if (dataSource == null) {
            return;
        }
        var sql = """
            INSERT INTO audit_events (actor_user_id, action, resource_type, resource_id, details_json)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, actorUserId);
            stmt.setString(2, action);
            stmt.setString(3, resourceType);
            stmt.setString(4, resourceId);
            stmt.setString(5, detailsJson);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.warn("audit insert failed: {}", e.getMessage());
        }
    }

    @Override
    public List<AuditRow> listRecent(int limit) {
        return listRecent(limit, null, null, null);
    }

    @Override
    public List<AuditRow> listRecent(int limit, String actionEquals) {
        return listRecent(limit, actionEquals, null, null);
    }

    @Override
    public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals) {
        return listRecent(limit, actionEquals, resourceTypeEquals, null);
    }

    @Override
    public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals, String resourceIdEquals) {
        if (dataSource == null) {
            return List.of();
        }
        int lim = Math.min(Math.max(limit, 1), 500);
        var filterAction = actionEquals != null && !actionEquals.isBlank();
        var filterResource = resourceTypeEquals != null && !resourceTypeEquals.isBlank();
        var filterResourceId = resourceIdEquals != null && !resourceIdEquals.isBlank();
        var action = filterAction ? truncate64(actionEquals.trim()) : "";
        var resourceType = filterResource ? truncate64(resourceTypeEquals.trim()) : "";
        var resourceId = filterResourceId ? truncate128(resourceIdEquals.trim()) : "";

        var conditions = new ArrayList<String>();
        if (filterAction) {
            conditions.add("action = ?");
        }
        if (filterResource) {
            conditions.add("resource_type = ?");
        }
        if (filterResourceId) {
            conditions.add("resource_id = ?");
        }
        var where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        var sql = """
            SELECT id, occurred_at, actor_user_id, action, resource_type, resource_id, details_json
            FROM audit_events"""
            + where + """
             ORDER BY occurred_at DESC LIMIT ?
            """;
        var out = new ArrayList<AuditRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            int idx = 1;
            if (filterAction) {
                stmt.setString(idx++, action);
            }
            if (filterResource) {
                stmt.setString(idx++, resourceType);
            }
            if (filterResourceId) {
                stmt.setString(idx++, resourceId);
            }
            stmt.setInt(idx, lim);
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    var actor = rs.getObject("actor_user_id", UUID.class);
                    out.add(new AuditRow(
                        rs.getLong("id"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        actor != null ? actor.toString() : null,
                        rs.getString("action"),
                        rs.getString("resource_type"),
                        rs.getString("resource_id"),
                        rs.getString("details_json")));
                }
            }
        } catch (Exception e) {
            log.error("audit list failed", e);
        }
        return out;
    }

    @Override
    public long countByAction(String action) {
        if (dataSource == null || action == null || action.isBlank()) {
            return 0L;
        }
        var sql = "SELECT COUNT(*) AS c FROM audit_events WHERE action = ?";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, truncate64(action.trim()));
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("c");
                }
            }
        } catch (Exception e) {
            log.error("audit count failed action={}", action, e);
        }
        return 0L;
    }

    @Override
    public Optional<Instant> latestOccurredAtByAction(String action) {
        if (dataSource == null || action == null || action.isBlank()) {
            return Optional.empty();
        }
        var sql = """
            SELECT occurred_at FROM audit_events WHERE action = ?
            ORDER BY occurred_at DESC LIMIT 1
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
                 JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setString(1, truncate64(action.trim()));
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getTimestamp("occurred_at").toInstant());
                }
            }
        } catch (Exception e) {
            log.error("audit latest failed action={}", action, e);
        }
        return Optional.empty();
    }

    private static String truncate64(String s) {
        return s.length() <= 64 ? s : s.substring(0, 64);
    }

    private static String truncate128(String s) {
        return s.length() <= 128 ? s : s.substring(0, 128);
    }
}
