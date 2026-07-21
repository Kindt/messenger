package com.avandocmsg.messenger.core.adapter.persistence;


import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;
import com.avandocmsg.messenger.core.port.AuditPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAuditAdapter implements AuditPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcAuditAdapter.class);

    private static final String LIST_BASE = """
        SELECT id, occurred_at, actor_user_id, action, resource_type, resource_id, details_json
        FROM audit_events
        """;
    private static final String LIST_ORDER_LIMIT = " ORDER BY occurred_at DESC LIMIT ?";
    private static final String LIST_NO_FILTER = LIST_BASE + LIST_ORDER_LIMIT;
    private static final String LIST_ACTION = LIST_BASE + " WHERE action = ?" + LIST_ORDER_LIMIT;
    private static final String LIST_RESOURCE = LIST_BASE + " WHERE resource_type = ?" + LIST_ORDER_LIMIT;
    private static final String LIST_RESOURCE_ID = LIST_BASE + " WHERE resource_id = ?" + LIST_ORDER_LIMIT;
    private static final String LIST_ACTION_RESOURCE =
        LIST_BASE + " WHERE action = ? AND resource_type = ?" + LIST_ORDER_LIMIT;
    private static final String LIST_ACTION_RESOURCE_ID =
        LIST_BASE + " WHERE action = ? AND resource_id = ?" + LIST_ORDER_LIMIT;
    private static final String LIST_RESOURCE_RESOURCE_ID =
        LIST_BASE + " WHERE resource_type = ? AND resource_id = ?" + LIST_ORDER_LIMIT;
    private static final String LIST_ALL_FILTERS =
        LIST_BASE + " WHERE action = ? AND resource_type = ? AND resource_id = ?" + LIST_ORDER_LIMIT;

    private final DataSource dataSource;

    public JdbcAuditAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson) { // NOSONAR java:S6213 - name required by AuditPort.record
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
            throw new IllegalStateException("JDBC operation failed", e);
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
        var sql = listRecentSql(filterAction, filterResource, filterResourceId);
        var out = new ArrayList<AuditRow>();
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            bindListFilters(stmt, new ListFilterBind(
                filterAction, action, filterResource, resourceType, filterResourceId, resourceId, lim));
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(mapAuditRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("audit list failed", e);
            throw new IllegalStateException("JDBC operation failed", e);
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
            throw new IllegalStateException("JDBC operation failed", e);
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
            throw new IllegalStateException("JDBC operation failed", e);
        }
        return Optional.empty();
    }

    /** Returns one of eight static SQL templates (filter predicates are compile-time constants). */
    private static String listRecentSql(boolean filterAction, boolean filterResource, boolean filterResourceId) {
        int key = (filterAction ? 1 : 0) | (filterResource ? 2 : 0) | (filterResourceId ? 4 : 0);
        return switch (key) {
            case 1 -> LIST_ACTION;
            case 2 -> LIST_RESOURCE;
            case 3 -> LIST_ACTION_RESOURCE;
            case 4 -> LIST_RESOURCE_ID;
            case 5 -> LIST_ACTION_RESOURCE_ID;
            case 6 -> LIST_RESOURCE_RESOURCE_ID;
            case 7 -> LIST_ALL_FILTERS;
            default -> LIST_NO_FILTER;
        };
    }

    private record ListFilterBind(
        boolean filterAction,
        String action,
        boolean filterResource,
        String resourceType,
        boolean filterResourceId,
        String resourceId,
        int lim
    ) {}

    private static void bindListFilters(PreparedStatement stmt, ListFilterBind filters) throws SQLException {
        int idx = 1;
        if (filters.filterAction()) {
            stmt.setString(idx++, filters.action());
        }
        if (filters.filterResource()) {
            stmt.setString(idx++, filters.resourceType());
        }
        if (filters.filterResourceId()) {
            stmt.setString(idx++, filters.resourceId());
        }
        stmt.setInt(idx, filters.lim());
    }

    private static AuditRow mapAuditRow(ResultSet rs) throws SQLException {
        var actor = rs.getObject("actor_user_id", UUID.class);
        return new AuditRow(
            rs.getLong("id"),
            rs.getTimestamp("occurred_at").toInstant(),
            actor != null ? actor.toString() : null,
            rs.getString("action"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            rs.getString("details_json"));
    }

    private static String truncate64(String s) {
        return s.length() <= 64 ? s : s.substring(0, 64);
    }

    private static String truncate128(String s) {
        return s.length() <= 128 ? s : s.substring(0, 128);
    }
}
