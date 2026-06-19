package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.RetentionPolicyPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public final class JdbcRetentionPolicyAdapter implements RetentionPolicyPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcRetentionPolicyAdapter.class);
    private final DataSource dataSource;

    public JdbcRetentionPolicyAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<StoredRow> findByOrgId(UUID orgId) {
        if (dataSource == null) {
            return Optional.empty();
        }
        var sql = """
            SELECT org_id, hot_message_body_max_age_days, hot_metadata_min_age_days,
                   archive_metadata_enabled, deep_archive_enabled, legal_hold, updated_at, updated_by
            FROM org_retention_policy WHERE org_id = ?
            """;
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, orgId);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                var updatedBy = rs.getObject("updated_by", UUID.class);
                return Optional.of(new StoredRow(
                    rs.getObject("org_id", UUID.class),
                    (Integer) rs.getObject("hot_message_body_max_age_days"),
                    (Integer) rs.getObject("hot_metadata_min_age_days"),
                    rs.getBoolean("archive_metadata_enabled"),
                    rs.getBoolean("deep_archive_enabled"),
                    rs.getBoolean("legal_hold"),
                    rs.getTimestamp("updated_at").toInstant(),
                    updatedBy != null ? updatedBy.toString() : null
                ));
            }
        } catch (Exception e) {
            log.error("find retention policy failed orgId={}", orgId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean upsert(UUID orgId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                          boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                          UUID updatedBy) {
        if (dataSource == null) {
            return false;
        }
        var updateSql = """
            UPDATE org_retention_policy SET
                hot_message_body_max_age_days = ?,
                hot_metadata_min_age_days = ?,
                archive_metadata_enabled = ?,
                deep_archive_enabled = ?,
                legal_hold = ?,
                updated_at = now(),
                updated_by = ?
            WHERE org_id = ?
            """;
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement(updateSql)) {
                stmt.setObject(1, hotMessageBodyMaxAgeDays);
                stmt.setObject(2, hotMetadataMinAgeDays);
                stmt.setBoolean(3, archiveMetadataEnabled);
                stmt.setBoolean(4, deepArchiveEnabled);
                stmt.setBoolean(5, legalHold);
                stmt.setObject(6, updatedBy);
                stmt.setObject(7, orgId);
                if (stmt.executeUpdate() > 0) {
                    return true;
                }
            }
            var insertSql = """
                INSERT INTO org_retention_policy (
                    org_id, hot_message_body_max_age_days, hot_metadata_min_age_days,
                    archive_metadata_enabled, deep_archive_enabled, legal_hold, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                """;
            try (var stmt = conn.prepareStatement(insertSql)) {
                stmt.setObject(1, orgId);
                stmt.setObject(2, hotMessageBodyMaxAgeDays);
                stmt.setObject(3, hotMetadataMinAgeDays);
                stmt.setBoolean(4, archiveMetadataEnabled);
                stmt.setBoolean(5, deepArchiveEnabled);
                stmt.setBoolean(6, legalHold);
                stmt.setObject(7, updatedBy);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            log.error("upsert retention policy failed orgId={}", orgId, e);
            return false;
        }
    }
}
