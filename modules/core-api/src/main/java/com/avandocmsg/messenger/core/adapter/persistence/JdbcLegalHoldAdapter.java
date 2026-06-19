package com.avandocmsg.messenger.core.adapter.persistence;

import com.avandocmsg.messenger.core.port.LegalHoldPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

public final class JdbcLegalHoldAdapter implements LegalHoldPort {
    private static final Logger log = LoggerFactory.getLogger(JdbcLegalHoldAdapter.class);
    private final DataSource dataSource;

    public JdbcLegalHoldAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<LegalHoldRow> findOrg(UUID orgId) {
        return findOne("""
            SELECT legal_hold, legal_hold_files, legal_hold_deep_archive
            FROM org_retention_policy WHERE org_id = ?
            """, orgId);
    }

    @Override
    public Optional<LegalHoldRow> findChat(UUID chatId) {
        return findOne("""
            SELECT legal_hold, legal_hold_files, legal_hold_deep_archive
            FROM chat_retention_policy WHERE chat_id = ?
            """, chatId);
    }

    @Override
    public boolean upsertOrg(UUID orgId, LegalHoldRow row, UUID updatedBy) {
        return upsertOrgChat(true, orgId, row, updatedBy);
    }

    @Override
    public boolean upsertChat(UUID chatId, LegalHoldRow row, UUID updatedBy) {
        return upsertOrgChat(false, chatId, row, updatedBy);
    }

    private Optional<LegalHoldRow> findOne(String sql, UUID id) {
        if (dataSource == null) {
            return Optional.empty();
        }
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("legal hold find failed id={}", id, e);
        }
        return Optional.empty();
    }

    private boolean upsertOrgChat(boolean org, UUID id, LegalHoldRow row, UUID updatedBy) {
        if (dataSource == null) {
            return false;
        }
        var table = org ? "org_retention_policy" : "chat_retention_policy";
        var idCol = org ? "org_id" : "chat_id";
        var update = """
            UPDATE %s SET legal_hold = ?, legal_hold_files = ?, legal_hold_deep_archive = ?,
            updated_at = now(), updated_by = ? WHERE %s = ?
            """.formatted(table, idCol);
        var insert = """
            INSERT INTO %s (%s, archive_metadata_enabled, deep_archive_enabled,
              legal_hold, legal_hold_files, legal_hold_deep_archive, updated_at, updated_by)
            VALUES (?, true, true, ?, ?, ?, now(), ?)
            """.formatted(table, idCol);
        try (var conn = dataSource.getConnection()) {
            try (var ps = conn.prepareStatement(update)) {
                bindHold(ps, row, updatedBy, id);
                if (ps.executeUpdate() > 0) {
                    return true;
                }
            }
            try (var ps = conn.prepareStatement(insert)) {
                ps.setObject(1, id);
                ps.setBoolean(2, row.legalHold());
                ps.setBoolean(3, row.legalHoldFiles());
                ps.setBoolean(4, row.legalHoldDeepArchive());
                ps.setObject(5, updatedBy);
                return ps.executeUpdate() > 0;
            }
        } catch (Exception e) {
            log.error("legal hold upsert failed id={}", id, e);
            return false;
        }
    }

    private static void bindHold(java.sql.PreparedStatement ps, LegalHoldRow row, UUID updatedBy, UUID id)
        throws java.sql.SQLException {
        ps.setBoolean(1, row.legalHold());
        ps.setBoolean(2, row.legalHoldFiles());
        ps.setBoolean(3, row.legalHoldDeepArchive());
        ps.setObject(4, updatedBy);
        ps.setObject(5, id);
    }

    private static LegalHoldRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LegalHoldRow(
            rs.getBoolean("legal_hold"),
            rs.getBoolean("legal_hold_files"),
            rs.getBoolean("legal_hold_deep_archive"));
    }
}
