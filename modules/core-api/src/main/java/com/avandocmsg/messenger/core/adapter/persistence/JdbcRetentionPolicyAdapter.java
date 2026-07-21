package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.RetentionPolicyPort;

import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;
import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;

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
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareRead(conn);
            try (var stmt = conn.prepareStatement(sql)) {
                JdbcQuerySupport.applyDefaultTimeout(stmt);
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
            }
        } catch (Exception e) {
            log.error("find retention policy failed orgId={}", orgId, e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    @Override
    public boolean upsert(UUID orgId, Integer hotMessageBodyMaxAgeDays, Integer hotMetadataMinAgeDays,
                          boolean archiveMetadataEnabled, boolean deepArchiveEnabled, boolean legalHold,
                          UUID updatedBy) {
        if (dataSource == null) {
            return false;
        }
        try (var conn = dataSource.getConnection()) {
            JdbcConnectionSupport.prepareWrite(conn);
            if (JdbcDialect.isPostgres(conn)) {
                return upsertOnConflict(conn, new UpsertParams(
                    orgId, hotMessageBodyMaxAgeDays, hotMetadataMinAgeDays,
                    archiveMetadataEnabled, deepArchiveEnabled, legalHold, updatedBy));
            }
            return upsertLegacy(conn, new UpsertParams(
                orgId, hotMessageBodyMaxAgeDays, hotMetadataMinAgeDays,
                archiveMetadataEnabled, deepArchiveEnabled, legalHold, updatedBy));
        } catch (Exception e) {
            log.error("upsert retention policy failed orgId={}", orgId, e);
            throw new IllegalStateException("JDBC operation failed", e);
        }
    }

    private record UpsertParams(
        UUID orgId,
        Integer hotMessageBodyMaxAgeDays,
        Integer hotMetadataMinAgeDays,
        boolean archiveMetadataEnabled,
        boolean deepArchiveEnabled,
        boolean legalHold,
        UUID updatedBy
    ) {}

    private boolean upsertOnConflict(java.sql.Connection conn, UpsertParams p) throws java.sql.SQLException {
        var sql = """
            INSERT INTO org_retention_policy (
                org_id, hot_message_body_max_age_days, hot_metadata_min_age_days,
                archive_metadata_enabled, deep_archive_enabled, legal_hold, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, now(), ?)
            ON CONFLICT (org_id) DO UPDATE SET
                hot_message_body_max_age_days = EXCLUDED.hot_message_body_max_age_days,
                hot_metadata_min_age_days = EXCLUDED.hot_metadata_min_age_days,
                archive_metadata_enabled = EXCLUDED.archive_metadata_enabled,
                deep_archive_enabled = EXCLUDED.deep_archive_enabled,
                legal_hold = EXCLUDED.legal_hold,
                updated_at = now(),
                updated_by = EXCLUDED.updated_by
            """;
        try (var stmt = conn.prepareStatement(sql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, p.orgId());
            stmt.setObject(2, p.hotMessageBodyMaxAgeDays());
            stmt.setObject(3, p.hotMetadataMinAgeDays());
            stmt.setBoolean(4, p.archiveMetadataEnabled());
            stmt.setBoolean(5, p.deepArchiveEnabled());
            stmt.setBoolean(6, p.legalHold());
            stmt.setObject(7, p.updatedBy());
            return stmt.executeUpdate() > 0;
        }
    }

    private boolean upsertLegacy(java.sql.Connection conn, UpsertParams p) throws java.sql.SQLException {
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
        try (var stmt = conn.prepareStatement(updateSql)) {
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, p.hotMessageBodyMaxAgeDays());
            stmt.setObject(2, p.hotMetadataMinAgeDays());
            stmt.setBoolean(3, p.archiveMetadataEnabled());
            stmt.setBoolean(4, p.deepArchiveEnabled());
            stmt.setBoolean(5, p.legalHold());
            stmt.setObject(6, p.updatedBy());
            stmt.setObject(7, p.orgId());
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
            JdbcQuerySupport.applyDefaultTimeout(stmt);
            stmt.setObject(1, p.orgId());
            stmt.setObject(2, p.hotMessageBodyMaxAgeDays());
            stmt.setObject(3, p.hotMetadataMinAgeDays());
            stmt.setBoolean(4, p.archiveMetadataEnabled());
            stmt.setBoolean(5, p.deepArchiveEnabled());
            stmt.setBoolean(6, p.legalHold());
            stmt.setObject(7, p.updatedBy());
            return stmt.executeUpdate() > 0;
        }
    }
}
