package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.MigrationImportJobPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcMigrationImportJobAdapter implements MigrationImportJobPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcMigrationImportJobAdapter.class);
    private final DataSource dataSource;

    public JdbcMigrationImportJobAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UUID insert(UUID orgId, String source, String configJson, UUID createdBy) {
        var id = UUID.randomUUID();
        var sql = """
            INSERT INTO migration_import_jobs (id, org_id, source, status, config_json, created_by, created_at, updated_at)
            VALUES (?, ?, ?, 'pending', ?, ?, now(), now())
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.setObject(2, orgId);
            stmt.setString(3, source);
            stmt.setString(4, configJson != null ? configJson : "{}");
            stmt.setObject(5, createdBy);
            stmt.executeUpdate();
            return id;
        } catch (Exception e) {
            log.error("migration import insert failed", e);
            return null;
        }
    }

    @Override
    public Optional<JobRow> findById(UUID id) {
        var sql = """
            SELECT id, org_id, source, status, config_json, result_json, created_by
            FROM migration_import_jobs WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, id);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("migration import find failed {}", id, e);
        }
        return Optional.empty();
    }

    @Override
    public List<JobRow> listForOrg(UUID orgId, int limit) {
        var sql = """
            SELECT id, org_id, source, status, config_json, result_json, created_by
            FROM migration_import_jobs
            WHERE org_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            stmt.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (var rs = stmt.executeQuery()) {
                var out = new ArrayList<JobRow>();
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
                return out;
            }
        } catch (Exception e) {
            log.error("migration import list failed org={}", orgId, e);
            return List.of();
        }
    }

    @Override
    public boolean updateStatus(UUID id, String status, String resultJson) {
        var sql = """
            UPDATE migration_import_jobs
            SET status = ?, result_json = ?, updated_at = now()
            WHERE id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, resultJson != null ? resultJson : "{}");
            stmt.setObject(3, id);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("migration import status update failed {}", id, e);
            return false;
        }
    }

    private static JobRow mapRow(java.sql.ResultSet rs) throws Exception {
        return new JobRow(
            rs.getObject("id", UUID.class),
            rs.getObject("org_id", UUID.class),
            rs.getString("source"),
            rs.getString("status"),
            rs.getString("config_json"),
            rs.getString("result_json"),
            rs.getObject("created_by", UUID.class));
    }
}
