package com.avandocmsg.messenger.api.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlatformModuleOverrideRepository {

    private static final Logger log = LoggerFactory.getLogger(PlatformModuleOverrideRepository.class);
    private final DataSource dataSource;

    public PlatformModuleOverrideRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, PlatformModuleOverrideRow> findAll() {
        if (dataSource == null) {
            return Map.of();
        }
        var sql = """
            SELECT module_id, disabled, override_reason, force_enabled, updated_at, updated_by
            FROM platform_module_overrides
            """;
        var map = new LinkedHashMap<String, PlatformModuleOverrideRow>();
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                var row = new PlatformModuleOverrideRow(
                    rs.getString("module_id"),
                    rs.getBoolean("disabled"),
                    rs.getString("override_reason"),
                    rs.getBoolean("force_enabled"),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getObject("updated_by", UUID.class)
                );
                map.put(row.moduleId(), row);
            }
        } catch (SQLException e) {
            log.warn("platform_module_overrides unavailable, treating as empty", e);
        }
        return map;
    }

    public Optional<PlatformModuleOverrideRow> findByModuleId(String moduleId) {
        return Optional.ofNullable(findAll().get(moduleId));
    }

    public void upsert(String moduleId, boolean disabled, PlatformModuleReason reason,
                       boolean forceEnabled, UUID updatedBy) {
        if (dataSource == null) {
            throw new IllegalStateException("platform_module_overrides requires DataSource");
        }
        var sql = """
            INSERT INTO platform_module_overrides
              (module_id, disabled, override_reason, force_enabled, updated_at, updated_by)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (module_id) DO UPDATE SET
              disabled = EXCLUDED.disabled,
              override_reason = EXCLUDED.override_reason,
              force_enabled = EXCLUDED.force_enabled,
              updated_at = EXCLUDED.updated_at,
              updated_by = EXCLUDED.updated_by
            """;
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, moduleId);
            ps.setBoolean(2, disabled);
            ps.setString(3, reason != null ? reason.code() : null);
            ps.setBoolean(4, forceEnabled);
            ps.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
            if (updatedBy != null) {
                ps.setObject(6, updatedBy);
            } else {
                ps.setObject(6, null);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to upsert platform_module_overrides", e);
        }
    }

    public void delete(String moduleId) {
        var sql = "DELETE FROM platform_module_overrides WHERE module_id = ?";
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, moduleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete platform_module_override", e);
        }
    }
}
