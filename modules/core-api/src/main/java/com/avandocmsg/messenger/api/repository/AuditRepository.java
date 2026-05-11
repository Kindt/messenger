package com.avandocmsg.messenger.api.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditRepository {
    private static final Logger log = LoggerFactory.getLogger(AuditRepository.class);
    private final DataSource dataSource;

    public AuditRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void record(UUID actorUserId, String action, String resourceType, String resourceId, String detailsJson) {
        var sql = """
            INSERT INTO audit_events (actor_user_id, action, resource_type, resource_id, details_json)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
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

    public List<AuditRow> listRecent(int limit) {
        return listRecent(limit, null, null, null);
    }

    /**
     * @param actionEquals если не {@code null} и не пусто — только строки с таким {@code action} (колонка VARCHAR(64)).
     */
    public List<AuditRow> listRecent(int limit, String actionEquals) {
        return listRecent(limit, actionEquals, null, null);
    }

    /**
     * @param actionEquals    фильтр по {@code action} (VARCHAR(64)), опционально
     * @param resourceTypeEquals фильтр по {@code resource_type} (VARCHAR(64)), опционально; при двух фильтрах — AND
     */
    public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals) {
        return listRecent(limit, actionEquals, resourceTypeEquals, null);
    }

    /**
     * @param actionEquals       фильтр по {@code action} (VARCHAR(64)), опционально
     * @param resourceTypeEquals фильтр по {@code resource_type} (VARCHAR(64)), опционально
     * @param resourceIdEquals   фильтр по {@code resource_id} (VARCHAR(128)), опционально; все заданные фильтры — AND
     */
    public List<AuditRow> listRecent(int limit, String actionEquals, String resourceTypeEquals, String resourceIdEquals) {
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

    private static String truncate64(String s) {
        return s.length() <= 64 ? s : s.substring(0, 64);
    }

    private static String truncate128(String s) {
        return s.length() <= 128 ? s : s.substring(0, 128);
    }

    public record AuditRow(long id, Instant occurredAt, String actorUserId, String action,
                           String resourceType, String resourceId, String detailsJson) {}
}
